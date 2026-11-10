package taskmanager.wiki;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
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
}
