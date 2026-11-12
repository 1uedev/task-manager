package taskmanager.search.internal;

import java.util.List;

/**
 * Marks a word in a text of almost 1 MiB whose characters decompose into many (F2 of the Codex review of TASK-108); run by
 * {@link SearchSnippetTest} in a JVM of its own with a small heap. Prints the marks.
 */
final class SnippetHeapProbe {
    private SnippetHeapProbe() {
    }

    static String text() {
        // U+FDFA becomes 18 code points under NFKC; with a space it takes 4 bytes in UTF-8.
        return "wort " + "ﷺ ".repeat(239_999);
    }

    public static void main(String[] args) {
        String text = text();
        List<SearchSnippet.Mark> marks = SearchSnippet.marks(text, List.of("wort"));
        System.out.println(marks);
    }
}
