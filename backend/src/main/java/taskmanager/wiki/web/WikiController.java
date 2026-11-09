package taskmanager.wiki.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.files.ImageStore;
import taskmanager.permissions.AccessContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.wiki.internal.WikiService;
import tools.jackson.databind.JsonNode;

/**
 * The wiki (TASK-093, DEC-123). The ETag of a page is the revision of its draft for people with drafts; saving, publishing and
 * taking over an older revision need it as If-Match (428 without, 412 when the draft is newer). Moving, archiving and restoring
 * carry the version of the page in the body. Images are delivered inline only with their checked type, without sniffing and
 * in a sandbox. Every page endpoint takes the optional {@code language} (TASK-094); without it the source language of the page
 * is meant.
 */
@RestController
@RequestMapping("/api/v1/wiki")
class WikiController {
    private final WikiService service;
    private final WikiRequests requests;

    WikiController(WikiService service, WikiRequests requests) {
        this.service = service;
        this.requests = requests;
    }

    record AccessBody(boolean read, boolean edit, boolean publish) {
        static AccessBody of(WikiService.Access access) {
            return new AccessBody(access.read(), access.edit(), access.publish());
        }
    }

    record ProjectBody(UUID projectId, String key, String name, AccessBody access) {
    }

    record TreeItemBody(UUID id, UUID parentId, String title, String language, boolean published, boolean changed, boolean archived, long version,
            Instant savedAt, UUID savedBy) {
    }

    record TreeBody(UUID projectId, AccessBody access, List<TreeItemBody> pages) {
    }

    record CrumbBody(UUID id, String title) {
    }

    record PublishedBody(int revision, String title, String html, Instant publishedAt, UUID publishedBy) {
    }

    record DraftBody(int revision, String title, JsonNode content, String html, Instant savedAt, UUID savedBy, boolean changed) {
    }

    record LanguageBody(String language, boolean published, boolean changed) {
    }

    record TranslationBody(Integer sourceRevision, Integer sourcePublishedRevision, boolean outdated) {
    }

    record PageBody(UUID id, UUID projectId, UUID parentId, String language, String sourceLanguage, boolean fallback, List<LanguageBody> languages,
            TranslationBody translation, boolean archived, long version, AccessBody access, List<CrumbBody> path, PublishedBody published,
            DraftBody draft, Instant createdAt, UUID createdBy) {
        static PageBody of(WikiService.PageView v) {
            WikiService.Published p = v.published();
            WikiService.Draft d = v.draft();
            WikiService.TranslationView t = v.translation();
            return new PageBody(v.id(), v.projectId(), v.parentId(), v.language(), v.sourceLanguage(), v.fallback(),
                v.languages().stream().map(l -> new LanguageBody(l.language(), l.published(), l.changed())).toList(),
                t == null ? null : new TranslationBody(t.sourceRevision(), t.sourcePublishedRevision(), t.outdated()), v.archived(), v.version(),
                AccessBody.of(v.access()),
                v.path().stream().map(c -> new CrumbBody(c.id(), c.title())).toList(),
                p == null ? null : new PublishedBody(p.revision(), p.title(), p.html(), p.publishedAt(), p.publishedBy()),
                d == null ? null : new DraftBody(d.revision(), d.title(), d.content(), d.html(), d.savedAt(), d.savedBy(), d.changed()),
                v.createdAt(), v.createdBy());
        }
    }

    record RevisionBody(int revision, String title, Instant savedAt, UUID savedBy, Integer basedOn, Integer restoredFrom, boolean published,
            Integer sourceRevision) {
    }

    record EventBody(String kind, String language, Integer revision, UUID actorId, Instant occurredAt) {
    }

    record HistoryBody(UUID pageId, String language, int draftRevision, Integer publishedRevision, List<RevisionBody> revisions,
            List<EventBody> events) {
    }

    record RevisionContentBody(int revision, String language, String title, JsonNode content, String html, Instant savedAt, UUID savedBy,
            boolean published, Integer sourceRevision) {
    }

    record ImageBody(UUID id, String src, String mediaType, long size) {
    }

    record CreateRequest(String title, UUID parentId, JsonNode content, String language) {
    }

    record DraftRequest(String title, JsonNode content, Integer sourceRevision) {
    }

    record LanguageRequest(String language) {
    }

    record PublishRequest(Integer revision) {
    }

    record MoveRequest(UUID parentId, Long version) {
    }

    record VersionRequest(Long version) {
    }

    @GetMapping("/projects")
    List<ProjectBody> projects() {
        AccessContext actor = requests.actor();
        return WikiRequests.retrying(() -> service.projects(actor)).stream()
            .map(p -> new ProjectBody(p.projectId(), p.key(), p.name(), AccessBody.of(p.access()))).toList();
    }

    @GetMapping("/projects/{projectId}/pages")
    TreeBody tree(@PathVariable UUID projectId, @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(required = false) String language) {
        AccessContext actor = requests.actor();
        WikiService.Tree tree = WikiRequests.retrying(() -> service.tree(actor, projectId, archived, language));
        return new TreeBody(tree.projectId(), AccessBody.of(tree.access()), tree.pages().stream()
            .map(i -> new TreeItemBody(i.id(), i.parentId(), i.title(), i.language(), i.published(), i.changed(), i.archived(), i.version(), i.savedAt(),
                i.savedBy()))
            .toList());
    }

    @PostMapping("/projects/{projectId}/pages")
    ResponseEntity<PageBody> create(@PathVariable UUID projectId, @RequestBody(required = false) CreateRequest body) {
        AccessContext actor = requests.actor();
        CreateRequest request = body == null ? new CreateRequest(null, null, null, null) : body;
        WikiService.PageView page = WikiRequests.retrying(() -> service.create(actor, projectId, request.title(), request.parentId(), request.content(),
            request.language()));
        return ResponseEntity.created(URI.create("/api/v1/wiki/pages/" + page.id())).headers(etag(page)).body(PageBody.of(page));
    }

    @GetMapping("/pages/{pageId}")
    ResponseEntity<PageBody> page(@PathVariable UUID pageId, @RequestParam(required = false) String language) {
        AccessContext actor = requests.actor();
        WikiService.PageView page = WikiRequests.retrying(() -> service.page(actor, pageId, language));
        return ResponseEntity.ok().headers(etag(page)).cacheControl(CacheControl.noStore()).body(PageBody.of(page));
    }

    @PutMapping("/pages/{pageId}/draft")
    ResponseEntity<PageBody> save(@PathVariable UUID pageId, @RequestParam(required = false) String language,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, @RequestBody(required = false) DraftRequest body) {
        AccessContext actor = requests.actor();
        int basedOn = revision(ifMatch);
        DraftRequest request = body == null ? new DraftRequest(null, null, null) : body;
        WikiService.PageView page = WikiRequests.retrying(() -> service.save(actor, pageId, language, basedOn, request.title(), request.content(),
            request.sourceRevision()));
        return ResponseEntity.ok().headers(etag(page)).body(PageBody.of(page));
    }

    @PostMapping("/pages/{pageId}/publish")
    ResponseEntity<PageBody> publish(@PathVariable UUID pageId, @RequestParam(required = false) String language,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, @RequestBody(required = false) PublishRequest body) {
        AccessContext actor = requests.actor();
        int basedOn = revision(ifMatch);
        Integer revision = body == null ? null : body.revision();
        if (revision == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("revision.required"));
        }
        WikiService.PageView page = WikiRequests.retrying(() -> service.publish(actor, pageId, language, basedOn, revision));
        return ResponseEntity.ok().headers(etag(page)).body(PageBody.of(page));
    }

    @PostMapping("/pages/{pageId}/withdraw")
    ResponseEntity<PageBody> withdraw(@PathVariable UUID pageId, @RequestParam(required = false) String language) {
        AccessContext actor = requests.actor();
        WikiService.PageView page = WikiRequests.retrying(() -> service.withdraw(actor, pageId, language));
        return ResponseEntity.ok().headers(etag(page)).body(PageBody.of(page));
    }

    @GetMapping("/pages/{pageId}/history")
    HistoryBody history(@PathVariable UUID pageId, @RequestParam(required = false) String language) {
        AccessContext actor = requests.actor();
        WikiService.History h = WikiRequests.retrying(() -> service.history(actor, pageId, language));
        return new HistoryBody(h.pageId(), h.language(), h.draftRevision(), h.publishedRevision(),
            h.revisions().stream().map(r -> new RevisionBody(r.revision(), r.title(), r.savedAt(), r.savedBy(), r.basedOn(), r.restoredFrom(), r.published(),
                r.sourceRevision())).toList(),
            h.events().stream().map(e -> new EventBody(e.kind(), e.language(), e.revision(), e.actorId(), e.occurredAt())).toList());
    }

    @GetMapping("/pages/{pageId}/revisions/{revision}")
    RevisionContentBody revision(@PathVariable UUID pageId, @PathVariable int revision, @RequestParam(required = false) String language) {
        AccessContext actor = requests.actor();
        WikiService.RevisionContent r = WikiRequests.retrying(() -> service.revision(actor, pageId, language, revision));
        return new RevisionContentBody(r.revision(), r.language(), r.title(), r.content(), r.html(), r.savedAt(), r.savedBy(), r.published(),
            r.sourceRevision());
    }

    @PostMapping("/pages/{pageId}/revisions/{revision}/restore")
    ResponseEntity<PageBody> revert(@PathVariable UUID pageId, @PathVariable int revision, @RequestParam(required = false) String language,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        AccessContext actor = requests.actor();
        int basedOn = revision(ifMatch);
        WikiService.PageView page = WikiRequests.retrying(() -> service.revert(actor, pageId, language, revision, basedOn));
        return ResponseEntity.ok().headers(etag(page)).body(PageBody.of(page));
    }

    @PutMapping("/pages/{pageId}/parent")
    ResponseEntity<PageBody> move(@PathVariable UUID pageId, @RequestParam(required = false) String language,
            @RequestBody(required = false) MoveRequest body) {
        AccessContext actor = requests.actor();
        long version = version(body == null ? null : body.version());
        UUID parent = body == null ? null : body.parentId();
        WikiService.PageView page = WikiRequests.retrying(() -> service.move(actor, pageId, version, parent, language));
        return ResponseEntity.ok().headers(etag(page)).body(PageBody.of(page));
    }

    @PostMapping("/pages/{pageId}/archive")
    ResponseEntity<PageBody> archive(@PathVariable UUID pageId, @RequestParam(required = false) String language,
            @RequestBody(required = false) VersionRequest body) {
        AccessContext actor = requests.actor();
        long version = version(body == null ? null : body.version());
        WikiService.PageView page = WikiRequests.retrying(() -> service.archive(actor, pageId, version, language));
        return ResponseEntity.ok().headers(etag(page)).body(PageBody.of(page));
    }

    @PostMapping("/pages/{pageId}/unarchive")
    ResponseEntity<PageBody> unarchive(@PathVariable UUID pageId, @RequestParam(required = false) String language,
            @RequestBody(required = false) VersionRequest body) {
        AccessContext actor = requests.actor();
        long version = version(body == null ? null : body.version());
        WikiService.PageView page = WikiRequests.retrying(() -> service.unarchive(actor, pageId, version, language));
        return ResponseEntity.ok().headers(etag(page)).body(PageBody.of(page));
    }

    /** Adds the version of the page in another language (TASK-094); it starts as a draft copy of the source language. */
    @PostMapping("/pages/{pageId}/languages")
    ResponseEntity<PageBody> addLanguage(@PathVariable UUID pageId, @RequestBody(required = false) LanguageRequest body) {
        AccessContext actor = requests.actor();
        String language = body == null ? null : body.language();
        WikiService.PageView page = WikiRequests.retrying(() -> service.addLanguage(actor, pageId, language));
        return ResponseEntity.created(URI.create("/api/v1/wiki/pages/" + page.id() + "?language=" + page.language())).headers(etag(page))
            .body(PageBody.of(page));
    }

    /** The raw image as the body; its type is decided by its first bytes, not by a header of the client. */
    @PostMapping("/pages/{pageId}/images")
    ResponseEntity<ImageBody> upload(@PathVariable UUID pageId, HttpServletRequest request) {
        AccessContext actor = requests.actor();
        WikiRequests.retrying(() -> {
            service.mayUpload(actor, pageId);
            return null;
        });
        WikiService.ImageView image = WikiRequests.retrying(() -> {
            try {
                return service.upload(actor, pageId, request.getContentLengthLong(), request.getInputStream());
            } catch (IOException failure) {
                throw ApiProblemException.of(400, "request.invalid");
            }
        });
        return ResponseEntity.created(URI.create(image.src())).body(new ImageBody(image.id(), image.src(), image.mediaType(), image.size()));
    }

    @GetMapping("/images/{imageId}")
    ResponseEntity<InputStreamResource> image(@PathVariable UUID imageId) {
        AccessContext actor = requests.actor();
        ImageStore.Content content = WikiRequests.retrying(() -> service.image(actor, imageId));
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.mediaType()))
            .contentLength(content.size())
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "default-src 'none'; sandbox")
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(new InputStreamResource(content.stream()));
    }

    private static HttpHeaders etag(WikiService.PageView page) {
        HttpHeaders headers = new HttpHeaders();
        if (page.draft() != null) {
            headers.setETag(WikiRequests.etag(page.draft().revision()));
        }
        return headers;
    }

    private static int revision(String ifMatch) {
        long value = WikiRequests.expectedVersion(ifMatch);
        if (value < 1 || value > Integer.MAX_VALUE) {
            throw ApiProblemException.of(412, "wiki.version_conflict");
        }
        return (int) value;
    }

    private static long version(Long version) {
        if (version == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("version.required"));
        }
        return version;
    }
}
