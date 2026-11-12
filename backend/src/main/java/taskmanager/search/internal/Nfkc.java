package taskmanager.search.internal;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NFKC with a time that grows with the length of the text (F2 of the Codex review of TASK-106). {@link Normalizer} puts a run
 * of combining marks into canonical order one mark at a time, which takes quadratic time for a long run; a text of a few
 * thousand such marks then costs seconds. The result here is always that of {@link Normalizer} for NFKC: a text without a long
 * run goes there directly, otherwise every character is decomposed on its own, each run of marks is put into canonical order
 * by a stable sort, and NFKC of that text, which is already decomposed and ordered, composes it in one pass.
 */
final class Nfkc {
    /** Runs of marks up to this length are left to {@link Normalizer}, whose ordering of short runs is fast. */
    static final int SHORT_RUN = 32;

    private Nfkc() {
    }

    static String of(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return longestRun(text) <= SHORT_RUN ? Normalizer.normalize(text, Normalizer.Form.NFKC) : ordered(text);
    }

    /** The way through decomposition and ordered runs, always; package private for the test that compares both ways. */
    static String ordered(String text) {
        Probe probe = new Probe();
        List<Integer> decomposed = new ArrayList<>(text.length());
        text.codePoints().forEach(codePoint -> {
            for (int part : probe.decomposition(codePoint)) {
                decomposed.add(part);
            }
        });
        StringBuilder result = new StringBuilder(text.length());
        int i = 0;
        while (i < decomposed.size()) {
            if (!probe.nonStarter(decomposed.get(i))) {
                result.appendCodePoint(decomposed.get(i++));
                continue;
            }
            int start = i;
            while (i < decomposed.size() && probe.nonStarter(decomposed.get(i))) {
                i++;
            }
            List<Integer> run = new ArrayList<>(decomposed.subList(start, i));
            run.sort(probe::order);
            run.forEach(result::appendCodePoint);
        }
        return Normalizer.normalize(result, Normalizer.Form.NFKC);
    }

    /**
     * NFKC of a text with the original place of every character of the result (F3 of the Codex review of TASK-107): for each code
     * point of the normal form {code point, start, end}, where start and end are UTF-16 offsets of the original characters it was
     * made of. Every character is decomposed on its own and keeps its place, each run of marks is put into canonical order by a
     * stable sort, and the canonical composition of Unicode then joins a starter with each following character that is not
     * blocked and forms a primary composite with it; the joined character covers the places of both. Whether two characters
     * compose is asked of {@link Normalizer} itself, so exclusions and Hangul follow it. The time grows with the length of the
     * text, as each step looks only at the last starter and the last character kept after it. {@code null} when the result is
     * not exactly {@link #of}, which no text in the tests reaches; the caller then marks nothing rather than something else.
     */
    static List<int[]> origins(String text) {
        List<int[]> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        Probe probe = new Probe();
        List<int[]> decomposed = new ArrayList<>(text.length());
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int end = i + Character.charCount(codePoint);
            for (int part : probe.decomposition(codePoint)) {
                decomposed.add(new int[] {part, i, end});
            }
            i = end;
        }
        for (int i = 0; i < decomposed.size(); ) {
            if (!probe.nonStarter(decomposed.get(i)[0])) {
                i++;
                continue;
            }
            int start = i;
            while (i < decomposed.size() && probe.nonStarter(decomposed.get(i)[0])) {
                i++;
            }
            decomposed.subList(start, i).sort((first, second) -> probe.order(first[0], second[0]));
        }
        int starter = -1;
        int[] last = null;
        for (int[] next : decomposed) {
            boolean nextStarter = !probe.nonStarter(next[0]);
            if (starter >= 0) {
                boolean blocked = last != null && (nextStarter || !probe.nonStarter(last[0]) || probe.order(last[0], next[0]) >= 0);
                int composite = blocked ? -1 : probe.compose(result.get(starter)[0], next[0]);
                if (composite >= 0) {
                    int[] joined = result.get(starter);
                    result.set(starter, new int[] {composite, Math.min(joined[1], next[1]), Math.max(joined[2], next[2])});
                    continue;
                }
            }
            result.add(next.clone());
            if (nextStarter) {
                starter = result.size() - 1;
                last = null;
            } else {
                last = next;
            }
        }
        StringBuilder composed = new StringBuilder(text.length());
        result.forEach(character -> composed.appendCodePoint(character[0]));
        return of(text).contentEquals(composed) ? result : null;
    }

    /** The longest run of combining marks, the halfwidth sound marks that decompose into them counted as well. */
    private static int longestRun(String text) {
        int longest = 0;
        int run = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            boolean mark = type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK
                || codePoint == 0xFF9E || codePoint == 0xFF9F;
            run = mark ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        return longest;
    }

    /**
     * The canonical combining class compared through {@link Normalizer} itself, as Java offers no direct way to read it: NFD
     * swaps two adjacent marks exactly when the first has the higher class and the second is not a starter.
     */
    private static final class Probe {
        private final Map<Integer, int[]> decompositions = new HashMap<>();
        private final Map<Integer, Boolean> nonStarters = new HashMap<>();
        private final Map<Long, Integer> orders = new HashMap<>();
        private final Map<Long, Integer> composites = new HashMap<>();

        int[] decomposition(int codePoint) {
            if (codePoint < 0xA0) {
                return new int[] {codePoint};
            }
            return decompositions.computeIfAbsent(codePoint,
                key -> Normalizer.normalize(new String(Character.toChars(key)), Normalizer.Form.NFKD).codePoints().toArray());
        }

        /** A character with a combining class above zero: it moves before the grave accent, or the overlay tilde before it. */
        boolean nonStarter(int codePoint) {
            if (codePoint < 0x300) {
                return false;
            }
            return nonStarters.computeIfAbsent(codePoint, key -> swaps(0x0301, key) || swaps(key, 0x0334));
        }

        /** The order of two non-starters by their combining class; equal classes keep their order in a stable sort. */
        int order(int first, int second) {
            if (first == second) {
                return 0;
            }
            return orders.computeIfAbsent(((long) first << 21) | second, key -> swaps(first, second) ? 1 : swaps(second, first) ? -1 : 0);
        }

        /** The primary composite of a starter and the character after it, or -1: NFC of the pair is then a single character. */
        int compose(int starter, int next) {
            return composites.computeIfAbsent(((long) starter << 21) | next, key -> {
                String pair = new StringBuilder().appendCodePoint(starter).appendCodePoint(next).toString();
                String composed = Normalizer.normalize(pair, Normalizer.Form.NFC);
                return composed.codePointCount(0, composed.length()) == 1 ? composed.codePointAt(0) : -1;
            });
        }

        private static boolean swaps(int first, int second) {
            if (first == second) {
                return false;
            }
            String pair = new StringBuilder().appendCodePoint(first).appendCodePoint(second).toString();
            String swapped = new StringBuilder().appendCodePoint(second).appendCodePoint(first).toString();
            return Normalizer.normalize(pair, Normalizer.Form.NFD).equals(swapped);
        }
    }
}
