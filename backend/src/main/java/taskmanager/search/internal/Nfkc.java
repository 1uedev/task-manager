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
        int length = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            length += probe.decomposition(codePoint).length;
            i += Character.charCount(codePoint);
        }
        int[] decomposed = new int[length];
        int size = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            for (int part : probe.decomposition(codePoint)) {
                decomposed[size++] = part;
            }
            i += Character.charCount(codePoint);
        }
        int[] unused = new int[size];
        for (int i = 0; i < size; ) {
            if (!probe.nonStarter(decomposed[i])) {
                i++;
                continue;
            }
            int start = i;
            while (i < size && probe.nonStarter(decomposed[i])) {
                i++;
            }
            order(probe, decomposed, unused, unused, start, i);
        }
        return Normalizer.normalize(new String(decomposed, 0, size), Normalizer.Form.NFKC);
    }

    /**
     * NFKC of a text with the original places of its characters: {@code text} is the normal form, and for its {@code k}th code
     * point {@code starts[k]} and {@code ends[k]} are the UTF-16 offsets of the original characters it was made of. The arrays
     * may be longer than the number of code points of {@code text}; the rest is unused.
     */
    record Origins(String text, int[] starts, int[] ends) {
    }

    /**
     * NFKC of a text with the original place of every character of the result (F3 of the Codex review of TASK-107). Every
     * character is decomposed on its own and keeps its place, each run of marks is put into canonical order by a stable sort,
     * and the canonical composition of Unicode then joins a starter with each following character that is not blocked and forms
     * a primary composite with it; the joined character covers the places of both. Whether two characters compose is asked of
     * {@link Normalizer} itself, so exclusions and Hangul follow it. The time grows with the length of the text, as each step
     * looks only at the last starter and the last character kept after it, and a run of marks is ordered by counting its
     * classes. The memory is three arrays of {@code int} as long as the decomposed text, filled in place, without an object per
     * character (F2 of the Codex review of TASK-108): about 12 bytes per code point of the decomposition. {@code null} when the
     * result is not exactly {@link #of}, which no text in the tests reaches; the caller then marks nothing rather than something
     * else.
     */
    static Origins origins(String text) {
        if (text == null || text.isEmpty()) {
            return new Origins("", new int[0], new int[0]);
        }
        Probe probe = new Probe();
        int length = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            length += probe.decomposition(codePoint).length;
            i += Character.charCount(codePoint);
        }
        int[] codePoints = new int[length];
        int[] starts = new int[length];
        int[] ends = new int[length];
        int size = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int end = i + Character.charCount(codePoint);
            for (int part : probe.decomposition(codePoint)) {
                codePoints[size] = part;
                starts[size] = i;
                ends[size++] = end;
            }
            i = end;
        }
        for (int i = 0; i < size; ) {
            if (!probe.nonStarter(codePoints[i])) {
                i++;
                continue;
            }
            int start = i;
            while (i < size && probe.nonStarter(codePoints[i])) {
                i++;
            }
            order(probe, codePoints, starts, ends, start, i);
        }
        // Composition in place: the characters kept are written to the front, never past the one read.
        int kept = 0;
        int starter = -1;
        int last = -1;
        for (int i = 0; i < size; i++) {
            int next = codePoints[i];
            boolean nextStarter = !probe.nonStarter(next);
            if (starter >= 0) {
                boolean blocked = last >= 0 && (nextStarter || !probe.nonStarter(codePoints[last]) || probe.order(codePoints[last], next) >= 0);
                int composite = blocked ? -1 : probe.compose(codePoints[starter], next);
                if (composite >= 0) {
                    codePoints[starter] = composite;
                    starts[starter] = Math.min(starts[starter], starts[i]);
                    ends[starter] = Math.max(ends[starter], ends[i]);
                    continue;
                }
            }
            codePoints[kept] = next;
            starts[kept] = starts[i];
            ends[kept] = ends[i];
            if (nextStarter) {
                starter = kept;
                last = -1;
            } else {
                last = kept;
            }
            kept++;
        }
        String composed = new String(codePoints, 0, kept);
        return of(text).equals(composed) ? new Origins(composed, starts, ends) : null;
    }

    /**
     * Puts a run of marks from {@code from} to {@code to} into canonical order, stable within one class: the distinct marks of
     * the run are sorted by class, which are few, and the run is then placed by counting, so a long run costs linear time.
     */
    private static void order(Probe probe, int[] codePoints, int[] starts, int[] ends, int from, int to) {
        int count = to - from;
        if (count < 2) {
            return;
        }
        Map<Integer, Integer> ranks = new HashMap<>();
        for (int k = from; k < to; k++) {
            ranks.putIfAbsent(codePoints[k], 0);
        }
        if (ranks.size() == 1) {
            return;
        }
        List<Integer> distinct = new ArrayList<>(ranks.keySet());
        distinct.sort(probe::order);
        int rank = 0;
        for (int k = 0; k < distinct.size(); k++) {
            if (k > 0 && probe.order(distinct.get(k - 1), distinct.get(k)) != 0) {
                rank++;
            }
            ranks.put(distinct.get(k), rank);
        }
        int[] placed = new int[rank + 2];
        int[] rankOf = new int[count];
        for (int k = 0; k < count; k++) {
            rankOf[k] = ranks.get(codePoints[from + k]);
            placed[rankOf[k] + 1]++;
        }
        for (int r = 1; r < placed.length; r++) {
            placed[r] += placed[r - 1];
        }
        int[] sortedPoints = new int[count];
        int[] sortedStarts = new int[count];
        int[] sortedEnds = new int[count];
        for (int k = 0; k < count; k++) {
            int target = placed[rankOf[k]]++;
            sortedPoints[target] = codePoints[from + k];
            sortedStarts[target] = starts[from + k];
            sortedEnds[target] = ends[from + k];
        }
        System.arraycopy(sortedPoints, 0, codePoints, from, count);
        System.arraycopy(sortedStarts, 0, starts, from, count);
        System.arraycopy(sortedEnds, 0, ends, from, count);
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
