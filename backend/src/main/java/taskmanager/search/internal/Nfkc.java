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
