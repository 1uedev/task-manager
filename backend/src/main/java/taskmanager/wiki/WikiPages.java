package taskmanager.wiki;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import taskmanager.permissions.AccessContext;

/**
 * What other modules learn about wiki pages (TASK-097, DEC-127): the requirements module links pages of the same project and
 * shows their published title. Pages are never deleted, so an identifier once linked stays valid; an archived page or one
 * without a published version is not shown.
 */
public interface WikiPages {
    /**
     * The published titles of the pages of the project that the caller may read: with {@code wiki.read}, not archived and with a
     * published language version, in {@code language} or the fallback of TASK-094 (never a draft). A page of another project,
     * an unknown, archived or unpublished page and every page of a caller without {@code wiki.read} are missing from the answer.
     */
    Map<UUID, String> publishedTitles(AccessContext actor, UUID projectId, Collection<UUID> pageIds, String language);

    /**
     * One page above a found page (TASK-102, DEC-132): {@code available} with identifier and title, else neither, shown as
     * "Seite nicht verfügbar".
     */
    record PathEntry(UUID id, String title, boolean available) {
        public static PathEntry unavailable() {
            return new PathEntry(null, null, false);
        }
    }

    /**
     * The path of each page above it from the top for a search hit (TASK-102, DEC-132), every level kept. With {@code drafts}
     * (the search of drafts, only for a caller with a draft right) every parent that is not archived shows its draft title;
     * otherwise a parent with a published version that is not archived shows its published title in {@code language} or its
     * fallback, and every other parent is unavailable, without identifier or title. Pages the caller may not see are missing
     * from the answer.
     */
    Map<UUID, List<PathEntry>> paths(AccessContext actor, Collection<UUID> pageIds, String language, boolean drafts);

    /** Every page of the company as the search module keeps it (TASK-102), one after the other, for a rebuild of its projection. */
    void searchSources(UUID companyId, Consumer<WikiPageChanged> sink);
}
