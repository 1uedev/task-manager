package taskmanager.wiki.internal;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.files.ImageStore;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import tools.jackson.databind.JsonNode;

/**
 * The wiki of a project (TASK-093, DEC-123) after the rules Holger confirmed on 9 November 2026:
 * <ul>
 *   <li>{@code wiki.read}: read published pages and their images;</li>
 *   <li>{@code wiki.edit}: read drafts and history, create pages, save the shared draft, move pages, upload images;</li>
 *   <li>{@code wiki.publish}: publish, withdraw a publication, archive and restore pages; reading drafts and history belongs to
 *       it, because the dialog of a publication shows the revision it publishes.</li>
 * </ul>
 * Every save of the draft adds a revision that is never changed; it carries the revision it was based on as If-Match and is
 * refused with 412 when the draft is newer. Publishing points the readers at exactly the revision shown. Portal accounts never
 * hold a wiki action. A change requires its action with {@code Permissions.require}, which holds the rights lock until the
 * commit, and then locks the rows it changes, so a revocation or a second change in between is seen. Changes of the tree of a
 * project run one after the other.
 */
@Service
public class WikiService {
    public static final String READ = "wiki.read";
    public static final String EDIT = "wiki.edit";
    public static final String PUBLISH = "wiki.publish";
    public static final String RESOURCE_TYPE = "wiki_page";
    public static final int TITLE_MAX = 200;
    public static final int MAX_DEPTH = 6;
    private static final String OWNER = "wiki";

    private final WikiRepository repository;
    private final ProjectAccess projects;
    private final Permissions permissions;
    private final ImageStore images;
    private final Clock clock;

    WikiService(WikiRepository repository, ProjectAccess projects, Permissions permissions, ImageStore images, Clock clock) {
        this.repository = repository;
        this.projects = projects;
        this.permissions = permissions;
        this.images = images;
        this.clock = clock;
    }

    /** The wiki actions of the caller in one project. */
    public record Access(boolean read, boolean edit, boolean publish) {
        static final Access NONE = new Access(false, false, false);

        public boolean any() {
            return read || edit || publish;
        }

        /** Drafts, history and every page that is not archived. */
        public boolean drafts() {
            return edit || publish;
        }
    }

    public record ProjectView(UUID projectId, String key, String name, Access access) {
    }

    /**
     * A page of the tree as the caller sees it: readers get the published title and the nearest page above it they may read as
     * parent, people with drafts the title of the draft and the real parent.
     */
    public record TreeItem(UUID id, UUID parentId, String title, String language, boolean published, boolean changed, boolean archived, long version,
            Instant savedAt, UUID savedBy) {
    }

    public record Tree(UUID projectId, Access access, List<TreeItem> pages) {
    }

    public record Crumb(UUID id, String title) {
    }

    public record Published(int revision, String title, String html, Instant publishedAt, UUID publishedBy) {
    }

    public record Draft(int revision, String title, JsonNode content, String html, Instant savedAt, UUID savedBy, boolean changed) {
    }

    public record PageView(UUID id, UUID projectId, UUID parentId, String language, boolean archived, long version, Access access, List<Crumb> path,
            Published published, Draft draft, Instant createdAt, UUID createdBy) {
    }

    public record RevisionView(int revision, String title, Instant savedAt, UUID savedBy, Integer basedOn, Integer restoredFrom, boolean published) {
    }

    public record EventView(String kind, String language, Integer revision, UUID actorId, Instant occurredAt) {
    }

    public record History(UUID pageId, int draftRevision, Integer publishedRevision, List<RevisionView> revisions, List<EventView> events) {
    }

    public record RevisionContent(int revision, String title, JsonNode content, String html, Instant savedAt, UUID savedBy, boolean published) {
    }

    public record ImageView(UUID id, String src, String mediaType, long size) {
    }

    // ------------------------------------------------------------------------------------------------ reading

    @Transactional
    public List<ProjectView> projects(AccessContext actor) {
        List<ProjectView> result = new ArrayList<>();
        if (actor.external()) {
            return result;
        }
        for (UUID projectId : projects.memberships(actor.companyId(), actor.userId())) {
            Access access = access(actor, projectId);
            if (access.any()) {
                projects.summary(actor.companyId(), projectId)
                    .ifPresent(summary -> result.add(new ProjectView(projectId, summary.key(), summary.name(), access)));
            }
        }
        result.sort(Comparator.comparing(ProjectView::key));
        return result;
    }

    /** The pages of a project; the archived ones instead with {@code archived}, which needs {@code wiki.publish}. */
    @Transactional
    public Tree tree(AccessContext actor, UUID projectId, boolean archived) {
        Access access = projectAccess(actor, projectId);
        if (archived && !access.publish()) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        List<WikiRepository.TreeRow> rows = repository.tree(actor.companyId(), projectId, archived);
        List<TreeItem> items = new ArrayList<>();
        if (archived || access.drafts()) {
            for (WikiRepository.TreeRow row : rows) {
                items.add(item(row, row.page().parentId(), row.draftTitle(), access));
            }
        } else {
            Map<UUID, WikiRepository.TreeRow> byId = new HashMap<>();
            rows.forEach(row -> byId.put(row.page().id(), row));
            for (WikiRepository.TreeRow row : rows) {
                if (row.variant().published()) {
                    items.add(item(row, visibleParent(byId, row.page().parentId()), row.publishedTitle(), access));
                }
            }
        }
        items.sort(Comparator.comparing((TreeItem item) -> item.title().toLowerCase(Locale.ROOT)).thenComparing(TreeItem::title)
            .thenComparing(TreeItem::id));
        return new Tree(projectId, access, items);
    }

    private static TreeItem item(WikiRepository.TreeRow row, UUID parent, String title, Access access) {
        WikiRepository.Variant variant = row.variant();
        boolean changed = !variant.published() || variant.publishedRevision() != variant.draftRevision();
        return new TreeItem(row.page().id(), parent, title, row.page().language(), variant.published(), access.drafts() && changed,
            row.page().archived(), row.page().version(), access.drafts() ? row.savedAt() : null, access.drafts() ? row.savedBy() : null);
    }

    /** The nearest page above a page that a reader sees: published and not archived. */
    private static UUID visibleParent(Map<UUID, WikiRepository.TreeRow> pages, UUID parent) {
        UUID current = parent;
        Set<UUID> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            WikiRepository.TreeRow row = pages.get(current);
            if (row == null) {
                return null;
            }
            if (row.variant().published()) {
                return current;
            }
            current = row.page().parentId();
        }
        return null;
    }

    @Transactional
    public PageView page(AccessContext actor, UUID pageId) {
        WikiRepository.Page page = visiblePage(actor, pageId);
        return view(actor, page, projectAccess(actor, page.projectId()));
    }

    @Transactional
    public History history(AccessContext actor, UUID pageId) {
        WikiRepository.Page page = visiblePage(actor, pageId);
        if (!projectAccess(actor, page.projectId()).drafts()) {
            throw pageNotFound();
        }
        WikiRepository.Variant variant = repository.variant(page.id(), page.language()).orElseThrow(WikiService::pageNotFound);
        List<RevisionView> revisions = repository.revisions(page.id(), page.language()).stream()
            .map(r -> new RevisionView(r.revision(), r.title(), r.savedAt(), r.savedBy(), r.basedOn(), r.restoredFrom(),
                variant.published() && r.revision() == variant.publishedRevision()))
            .toList();
        List<EventView> events = repository.events(page.id()).stream()
            .map(e -> new EventView(e.kind(), e.language(), e.revision(), e.actorId(), e.occurredAt())).toList();
        return new History(page.id(), variant.draftRevision(), variant.publishedRevision(), revisions, events);
    }

    @Transactional
    public RevisionContent revision(AccessContext actor, UUID pageId, int number) {
        WikiRepository.Page page = visiblePage(actor, pageId);
        Access access = projectAccess(actor, page.projectId());
        if (!access.drafts()) {
            throw pageNotFound();
        }
        WikiRepository.Variant variant = repository.variant(page.id(), page.language()).orElseThrow(WikiService::pageNotFound);
        WikiRepository.Revision revision = repository.revision(page.id(), page.language(), number)
            .orElseThrow(() -> ApiProblemException.of(404, "wiki.revision_not_found"));
        WikiDocument document = WikiDocument.stored(revision.content());
        return new RevisionContent(revision.revision(), revision.title(), document.tree(), document.html(links(actor, page.projectId(), access)),
            revision.savedAt(), revision.savedBy(), variant.published() && revision.revision() == variant.publishedRevision());
    }

    /** The content of an image for the caller: readers only while a published revision shows it. */
    @Transactional
    public ImageStore.Content image(AccessContext actor, UUID imageId) {
        UUID pageId = repository.imagePage(actor.companyId(), imageId).orElseThrow(WikiService::imageNotFound);
        WikiRepository.Page page = repository.find(actor.companyId(), pageId).orElseThrow(WikiService::imageNotFound);
        Access access = access(actor, page.projectId());
        boolean allowed = page.archived() ? access.publish()
            : access.drafts() || (access.read() && repository.imagePublished(page.id(), imageId));
        if (!allowed) {
            throw imageNotFound();
        }
        return images.open(actor.companyId(), imageId).orElseThrow(WikiService::imageNotFound);
    }

    // ------------------------------------------------------------------------------------------------ changes

    @Transactional
    public PageView create(AccessContext actor, UUID projectId, String title, UUID parentId, JsonNode content, String language) {
        Access access = projectAccess(actor, projectId);
        if (!access.edit()) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        require(actor, projectId, EDIT);
        repository.lockTree(projectId);
        List<String> violations = new ArrayList<>();
        String cleanTitle = title(title, violations);
        String lang = language == null ? "de" : language;
        if (!lang.equals("de") && !lang.equals("en")) {
            violations.add("language.invalid");
        }
        if (parentId != null) {
            Optional<WikiRepository.Page> parent = repository.find(actor.companyId(), parentId)
                .filter(p -> p.projectId().equals(projectId) && !p.archived());
            if (parent.isEmpty()) {
                violations.add("parent.unknown");
            } else if (depth(actor.companyId(), parent.get()) + 1 > MAX_DEPTH) {
                violations.add("parent.too_deep");
            }
        }
        UUID id = UUID.randomUUID();
        WikiDocument document = document(actor, projectId, id, content, violations);
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        Instant now = clock.instant();
        repository.insert(new WikiRepository.Page(id, actor.companyId(), projectId, parentId, lang, now, actor.userId(), null, null, 1));
        repository.insert(new WikiRepository.Revision(id, lang, 1, cleanTitle, document.json(), WikiDocument.SCHEMA_VERSION, now, actor.userId(), null,
            null), document.images());
        repository.insert(new WikiRepository.Variant(id, lang, 1, null, null, null));
        repository.event(actor.companyId(), id, "created", lang, 1, actor.userId(), now);
        return page(actor, id);
    }

    /** Saves the shared draft as a new revision on the basis of {@code basedOn}, the draft revision the caller had. */
    @Transactional
    public PageView save(AccessContext actor, UUID pageId, int basedOn, String title, JsonNode content) {
        WikiRepository.Page page = changeable(actor, pageId, EDIT);
        WikiRepository.Variant variant = lockVariant(page, basedOn);
        List<String> violations = new ArrayList<>();
        String cleanTitle = title(title, violations);
        WikiDocument document = document(actor, page.projectId(), page.id(), content, violations);
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        Instant now = clock.instant();
        int next = variant.draftRevision() + 1;
        repository.insert(new WikiRepository.Revision(page.id(), page.language(), next, cleanTitle, document.json(), WikiDocument.SCHEMA_VERSION, now,
            actor.userId(), variant.draftRevision(), null), document.images());
        repository.update(new WikiRepository.Variant(page.id(), page.language(), next, variant.publishedRevision(), variant.publishedAt(),
            variant.publishedBy()));
        repository.event(actor.companyId(), page.id(), "saved", page.language(), next, actor.userId(), now);
        return page(actor, page.id());
    }

    /** Publishes exactly {@code revision}, which has to be the current draft the caller saw. */
    @Transactional
    public PageView publish(AccessContext actor, UUID pageId, int basedOn, Integer revision) {
        WikiRepository.Page page = changeable(actor, pageId, PUBLISH);
        WikiRepository.Variant variant = lockVariant(page, basedOn);
        if (revision == null || revision != variant.draftRevision()) {
            throw ApiProblemException.of(412, "wiki.version_conflict");
        }
        if (variant.published() && variant.publishedRevision() == revision) {
            return page(actor, page.id());
        }
        Instant now = clock.instant();
        repository.update(new WikiRepository.Variant(page.id(), page.language(), variant.draftRevision(), revision, now, actor.userId()));
        repository.event(actor.companyId(), page.id(), "published", page.language(), revision, actor.userId(), now);
        return page(actor, page.id());
    }

    @Transactional
    public PageView withdraw(AccessContext actor, UUID pageId) {
        WikiRepository.Page page = changeable(actor, pageId, PUBLISH);
        WikiRepository.Variant variant = repository.lockVariant(page.id(), page.language()).orElseThrow(WikiService::pageNotFound);
        if (!variant.published()) {
            throw ApiProblemException.of(409, "wiki.not_published");
        }
        Instant now = clock.instant();
        repository.update(new WikiRepository.Variant(page.id(), page.language(), variant.draftRevision(), null, null, null));
        repository.event(actor.companyId(), page.id(), "withdrawn", page.language(), variant.publishedRevision(), actor.userId(), now);
        return page(actor, page.id());
    }

    /** Takes over an older revision as the new draft; it has to be published again. */
    @Transactional
    public PageView revert(AccessContext actor, UUID pageId, int number, int basedOn) {
        WikiRepository.Page page = changeable(actor, pageId, EDIT);
        WikiRepository.Variant variant = lockVariant(page, basedOn);
        WikiRepository.Revision old = repository.revision(page.id(), page.language(), number)
            .orElseThrow(() -> ApiProblemException.of(404, "wiki.revision_not_found"));
        WikiDocument document = WikiDocument.stored(old.content());
        Instant now = clock.instant();
        int next = variant.draftRevision() + 1;
        repository.insert(new WikiRepository.Revision(page.id(), page.language(), next, old.title(), document.json(), WikiDocument.SCHEMA_VERSION, now,
            actor.userId(), variant.draftRevision(), number), document.images());
        repository.update(new WikiRepository.Variant(page.id(), page.language(), next, variant.publishedRevision(), variant.publishedAt(),
            variant.publishedBy()));
        repository.event(actor.companyId(), page.id(), "reverted", page.language(), next, actor.userId(), now);
        return page(actor, page.id());
    }

    /** Moves a page below another page of the project or to the top; {@code version} is the version of the page. */
    @Transactional
    public PageView move(AccessContext actor, UUID pageId, long version, UUID parentId) {
        WikiRepository.Page found = visiblePage(actor, pageId);
        require(actor, found.projectId(), EDIT);
        repository.lockTree(found.projectId());
        WikiRepository.Page page = lockPage(actor, pageId, version);
        if (page.archived()) {
            throw ApiProblemException.of(409, "wiki.page_archived");
        }
        List<String> violations = new ArrayList<>();
        if (parentId != null) {
            List<WikiRepository.TreeRow> rows = repository.tree(actor.companyId(), page.projectId(), false);
            Map<UUID, UUID> parents = new HashMap<>();
            rows.forEach(row -> parents.put(row.page().id(), row.page().parentId()));
            if (!parents.containsKey(parentId)) {
                violations.add("parent.unknown");
            } else if (parentId.equals(page.id()) || ancestors(parents, parentId).contains(page.id())) {
                violations.add("parent.cycle");
            } else if (ancestors(parents, parentId).size() + height(parents, page.id()) > MAX_DEPTH) {
                violations.add("parent.too_deep");
            }
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        if (java.util.Objects.equals(parentId, page.parentId())) {
            return page(actor, page.id());
        }
        Instant now = clock.instant();
        repository.update(new WikiRepository.Page(page.id(), page.companyId(), page.projectId(), parentId, page.language(), page.createdAt(),
            page.createdBy(), null, null, page.version() + 1));
        repository.event(actor.companyId(), page.id(), "moved", null, null, actor.userId(), now);
        return page(actor, page.id());
    }

    /** Hides a page from readers and from the tree; its pages below move up one level. */
    @Transactional
    public PageView archive(AccessContext actor, UUID pageId, long version) {
        WikiRepository.Page found = visiblePage(actor, pageId);
        require(actor, found.projectId(), PUBLISH);
        repository.lockTree(found.projectId());
        WikiRepository.Page page = lockPage(actor, pageId, version);
        if (page.archived()) {
            throw ApiProblemException.of(409, "wiki.page_archived");
        }
        Instant now = clock.instant();
        repository.reparentChildren(page.id(), page.parentId());
        repository.update(new WikiRepository.Page(page.id(), page.companyId(), page.projectId(), page.parentId(), page.language(), page.createdAt(),
            page.createdBy(), now, actor.userId(), page.version() + 1));
        repository.event(actor.companyId(), page.id(), "archived", null, null, actor.userId(), now);
        return page(actor, page.id());
    }

    /** Brings an archived page back below its former parent, or to the top when that one is archived itself. */
    @Transactional
    public PageView unarchive(AccessContext actor, UUID pageId, long version) {
        WikiRepository.Page found = visiblePage(actor, pageId);
        require(actor, found.projectId(), PUBLISH);
        repository.lockTree(found.projectId());
        WikiRepository.Page page = lockPage(actor, pageId, version);
        if (!page.archived()) {
            throw ApiProblemException.of(409, "wiki.page_not_archived");
        }
        UUID parent = page.parentId();
        if (parent != null) {
            Optional<WikiRepository.Page> former = repository.find(actor.companyId(), parent);
            if (former.isEmpty() || former.get().archived() || depth(actor.companyId(), former.get()) + 1 > MAX_DEPTH) {
                parent = null;
            }
        }
        Instant now = clock.instant();
        repository.update(new WikiRepository.Page(page.id(), page.companyId(), page.projectId(), parent, page.language(), page.createdAt(),
            page.createdBy(), null, null, page.version() + 1));
        repository.event(actor.companyId(), page.id(), "unarchived", null, null, actor.userId(), now);
        return page(actor, page.id());
    }

    /** Stores an image for the page; the caller adds it to the draft and saves. */
    @Transactional
    public ImageView upload(AccessContext actor, UUID pageId, long declaredLength, InputStream content) {
        WikiRepository.Page page = changeable(actor, pageId, EDIT);
        ImageStore.Stored stored = images.store(actor.companyId(), OWNER, declaredLength, content, actor.userId());
        repository.insertImage(stored.id(), actor.companyId(), page.id(), clock.instant(), actor.userId());
        return new ImageView(stored.id(), WikiDocument.IMAGE_PATH + stored.id(), stored.mediaType(), stored.size());
    }

    /** Checks before a body is read that the caller may add images to the page; it holds nothing after it returns. */
    @Transactional
    public void mayUpload(AccessContext actor, UUID pageId) {
        WikiRepository.Page page = visiblePage(actor, pageId);
        if (!projectAccess(actor, page.projectId()).edit()) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        if (page.archived()) {
            throw ApiProblemException.of(409, "wiki.page_archived");
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private PageView view(AccessContext actor, WikiRepository.Page page, Access access) {
        WikiRepository.Variant variant = repository.variant(page.id(), page.language()).orElseThrow(WikiService::pageNotFound);
        WikiDocument.Links links = links(actor, page.projectId(), access);
        Published published = null;
        if (variant.published()) {
            WikiRepository.Revision revision = repository.revision(page.id(), page.language(), variant.publishedRevision()).orElseThrow();
            published = new Published(revision.revision(), revision.title(), WikiDocument.stored(revision.content()).html(links), variant.publishedAt(),
                variant.publishedBy());
        }
        Draft draft = null;
        if (access.drafts()) {
            WikiRepository.Revision revision = repository.revision(page.id(), page.language(), variant.draftRevision()).orElseThrow();
            WikiDocument document = WikiDocument.stored(revision.content());
            draft = new Draft(revision.revision(), revision.title(), document.tree(), document.html(links), revision.savedAt(), revision.savedBy(),
                !variant.published() || variant.publishedRevision() != variant.draftRevision());
        }
        List<WikiRepository.TreeRow> rows = repository.tree(actor.companyId(), page.projectId(), false);
        Map<UUID, WikiRepository.TreeRow> byId = new HashMap<>();
        rows.forEach(row -> byId.put(row.page().id(), row));
        List<Crumb> path = new ArrayList<>();
        UUID parent = access.drafts() ? page.parentId() : visibleParent(byId, page.parentId());
        Set<UUID> seen = new HashSet<>();
        while (parent != null && seen.add(parent)) {
            WikiRepository.TreeRow row = byId.get(parent);
            if (row == null) {
                break;
            }
            path.addFirst(new Crumb(parent, access.drafts() ? row.draftTitle() : row.publishedTitle()));
            parent = access.drafts() ? row.page().parentId() : visibleParent(byId, row.page().parentId());
        }
        UUID shownParent = path.isEmpty() ? null : path.getLast().id();
        if (page.archived()) {
            shownParent = page.parentId();
        }
        return new PageView(page.id(), page.projectId(), shownParent, page.language(), page.archived(), page.version(), access, path, published, draft,
            page.createdAt(), page.createdBy());
    }

    /** The address of a linked page the caller may read, else {@code null}. */
    private WikiDocument.Links links(AccessContext actor, UUID projectId, Access access) {
        Map<UUID, String> cache = new HashMap<>();
        return target -> cache.computeIfAbsent(target, id -> repository.find(actor.companyId(), id)
            .filter(page -> page.projectId().equals(projectId) && !page.archived())
            .filter(page -> access.drafts() || repository.variant(page.id(), page.language()).map(WikiRepository.Variant::published).orElse(false))
            .map(page -> "#/wiki/" + projectId + "/" + page.id())
            .orElse(null));
    }

    /** Checks the document and that its images belong to the page and its links to pages of the project. */
    private WikiDocument document(AccessContext actor, UUID projectId, UUID pageId, JsonNode content, List<String> violations) {
        WikiDocument document;
        try {
            document = WikiDocument.check(content);
        } catch (WikiDocument.Invalid invalid) {
            violations.addAll(invalid.violations());
            return WikiDocument.empty();
        }
        if (repository.imagesOf(pageId, document.images()).size() != document.images().size()) {
            violations.add("image.unknown");
        }
        for (UUID target : document.pageLinks()) {
            if (repository.find(actor.companyId(), target).filter(page -> page.projectId().equals(projectId)).isEmpty()) {
                violations.add("link.unknown_page");
                break;
            }
        }
        return document;
    }

    private static String title(String title, List<String> violations) {
        String clean = title == null ? "" : title.strip();
        if (clean.isEmpty()) {
            violations.add("title.required");
        } else if (clean.codePointCount(0, clean.length()) > TITLE_MAX) {
            violations.add("title.too_long");
        } else if (clean.codePoints().anyMatch(Character::isISOControl)) {
            violations.add("title.invalid");
        }
        return clean;
    }

    /** The level of a page counted from 1 at the top. */
    private int depth(UUID companyId, WikiRepository.Page page) {
        int depth = 1;
        UUID parent = page.parentId();
        Set<UUID> seen = new HashSet<>();
        while (parent != null && seen.add(parent)) {
            depth++;
            parent = repository.find(companyId, parent).map(WikiRepository.Page::parentId).orElse(null);
        }
        return depth;
    }

    private static List<UUID> ancestors(Map<UUID, UUID> parents, UUID page) {
        List<UUID> result = new ArrayList<>();
        UUID current = page;
        while (current != null && !result.contains(current)) {
            result.add(current);
            current = parents.get(current);
        }
        return result;
    }

    /** The levels of the subtree of a page, the page itself counted as 1. */
    private static int height(Map<UUID, UUID> parents, UUID page) {
        Map<UUID, List<UUID>> children = new HashMap<>();
        parents.forEach((child, parent) -> {
            if (parent != null) {
                children.computeIfAbsent(parent, key -> new ArrayList<>()).add(child);
            }
        });
        return height(children, page, new HashSet<>());
    }

    private static int height(Map<UUID, List<UUID>> children, UUID page, Set<UUID> seen) {
        if (!seen.add(page)) {
            return 0;
        }
        int deepest = 0;
        for (UUID child : children.getOrDefault(page, List.of())) {
            deepest = Math.max(deepest, height(children, child, seen));
        }
        return deepest + 1;
    }

    /** A page the caller may see: readers only published ones, archived ones only people who publish. */
    private WikiRepository.Page visiblePage(AccessContext actor, UUID pageId) {
        WikiRepository.Page page = repository.find(actor.companyId(), pageId).orElseThrow(WikiService::pageNotFound);
        Access access = access(actor, page.projectId());
        if (!access.any()) {
            throw pageNotFound();
        }
        if (page.archived()) {
            if (!access.publish()) {
                throw pageNotFound();
            }
            return page;
        }
        if (!access.drafts() && !repository.variant(page.id(), page.language()).map(WikiRepository.Variant::published).orElse(false)) {
            throw pageNotFound();
        }
        return page;
    }

    /** The page after its action was required, not archived; the language version is locked by the caller. */
    private WikiRepository.Page changeable(AccessContext actor, UUID pageId, String action) {
        WikiRepository.Page page = visiblePage(actor, pageId);
        Access access = access(actor, page.projectId());
        if (EDIT.equals(action) ? !access.edit() : !access.publish()) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        require(actor, page.projectId(), action);
        WikiRepository.Page locked = repository.lock(actor.companyId(), pageId).orElseThrow(WikiService::pageNotFound);
        if (locked.archived()) {
            throw ApiProblemException.of(409, "wiki.page_archived");
        }
        return locked;
    }

    private WikiRepository.Variant lockVariant(WikiRepository.Page page, int basedOn) {
        WikiRepository.Variant variant = repository.lockVariant(page.id(), page.language()).orElseThrow(WikiService::pageNotFound);
        if (variant.draftRevision() != basedOn) {
            throw ApiProblemException.of(412, "wiki.version_conflict");
        }
        return variant;
    }

    private WikiRepository.Page lockPage(AccessContext actor, UUID pageId, long version) {
        WikiRepository.Page page = repository.lock(actor.companyId(), pageId).orElseThrow(WikiService::pageNotFound);
        if (page.version() != version) {
            throw ApiProblemException.of(412, "wiki.version_conflict");
        }
        return page;
    }

    private Access projectAccess(AccessContext actor, UUID projectId) {
        Access access = access(actor, projectId);
        if (!access.any()) {
            throw ApiProblemException.of(404, "project.not_found");
        }
        return access;
    }

    private Access access(AccessContext actor, UUID projectId) {
        if (actor.external() || projectId == null) {
            return Access.NONE;
        }
        Supplier<ResourceContext> resource = projects.resource(actor.companyId(), projectId, actor.userId(), RESOURCE_TYPE, false);
        boolean read = permissions.inspect(actor, resource, READ, Set.of()).allowed();
        boolean edit = permissions.inspect(actor, resource, EDIT, Set.of()).allowed();
        boolean publish = permissions.inspect(actor, resource, PUBLISH, Set.of()).allowed();
        return new Access(read, edit, publish);
    }

    private void require(AccessContext actor, UUID projectId, String action) {
        try {
            permissions.require(actor, projects.resource(actor.companyId(), projectId, actor.userId(), RESOURCE_TYPE, false), action, Set.of(),
                Set.of());
        } catch (PermissionDenied denied) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
    }

    private static ApiProblemException pageNotFound() {
        return ApiProblemException.of(404, "wiki.page_not_found");
    }

    private static ApiProblemException imageNotFound() {
        return ApiProblemException.of(404, "wiki.image_not_found");
    }
}
