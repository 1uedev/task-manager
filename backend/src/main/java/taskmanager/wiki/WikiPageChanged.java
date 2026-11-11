package taskmanager.wiki;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published inside the transaction that created or changed a wiki page, after the change is written (TASK-102, DEC-132), so a
 * projection kept by a listener commits or rolls back with the page. It carries the title and the visible text of the draft
 * and of the published revision of every language version; a listener must not show any of them without the wiki rights of
 * the reader: the published text needs {@code wiki.read}, the draft {@code wiki.edit} or {@code wiki.publish}, an archived page
 * {@code wiki.publish}.
 */
public record WikiPageChanged(UUID pageId, UUID companyId, UUID projectId, String sourceLanguage, boolean archived,
        List<Version> versions) {

    /** One language version; the published fields are {@code null} while it is not published. */
    public record Version(String language, int draftRevision, String draftTitle, String draftText, Integer publishedRevision,
            String publishedTitle, String publishedText, Instant publishedAt) {
    }

    public WikiPageChanged {
        versions = List.copyOf(versions);
    }
}
