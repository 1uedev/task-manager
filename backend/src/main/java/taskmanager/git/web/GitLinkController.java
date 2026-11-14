package taskmanager.git.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.git.internal.LinkService;
import taskmanager.git.internal.ProviderApi;
import taskmanager.platform.ApiProblemException;

/**
 * Git links of tickets and requirements (TASK-112). An object that is not available to the caller is answered with its kind,
 * the reason and, for a writer, the opaque key to remove it only; its path, title, message, address, SHA, number, instance,
 * time and author stay out of the answer (rule 4, TASK-114 F6). The choice holds only repositories the caller can read now.
 */
@RestController
class GitLinkController {
    private final LinkService links;
    private final GitRequests requests;

    GitLinkController(LinkService links, GitRequests requests) {
        this.links = links;
        this.requests = requests;
    }

    record RepositoryBody(String name, String path, String url) {
    }

    record CommitBody(String sha, String shortSha, String message, String author, Instant committedAt) {
    }

    record ChangeBody(long number, String title, String state, String author) {
    }

    record LinkBody(String key, String kind, UUID providerId, String providerName, String providerKind, Instant linkedAt, UUID linkedBy,
            boolean available, String reason, RepositoryBody repository, CommitBody commit, ChangeBody change, String url) {
    }

    record LinksBody(boolean readable, boolean writable, List<LinkBody> links) {
    }

    record ChoiceBody(UUID releaseId, UUID providerId, String providerName, String providerKind, RepositoryBody repository) {
    }

    record LinkRequest(String url, UUID releaseId, String kind, String key) {
    }

    // ------------------------------------------------------------------------------------------------ tickets

    @GetMapping("/api/v1/projects/{projectId}/tickets/{number}/git")
    LinksBody ticketLinks(@PathVariable UUID projectId, @PathVariable long number, @RequestParam(defaultValue = "false") boolean refresh) {
        return list(new LinkService.TicketTarget(projectId, number), refresh);
    }

    @GetMapping("/api/v1/projects/{projectId}/tickets/{number}/git/choices")
    List<ChoiceBody> ticketChoices(@PathVariable UUID projectId, @PathVariable long number) {
        return choices(new LinkService.TicketTarget(projectId, number));
    }

    @PostMapping("/api/v1/projects/{projectId}/tickets/{number}/git")
    ResponseEntity<LinkBody> linkTicket(@PathVariable UUID projectId, @PathVariable long number, @RequestBody(required = false) LinkRequest body) {
        return add(new LinkService.TicketTarget(projectId, number), body);
    }

    @DeleteMapping("/api/v1/projects/{projectId}/tickets/{number}/git/{key}")
    ResponseEntity<Void> unlinkTicket(@PathVariable UUID projectId, @PathVariable long number, @PathVariable String key) {
        return remove(new LinkService.TicketTarget(projectId, number), key);
    }

    // ------------------------------------------------------------------------------------------------ requirements

    @GetMapping("/api/v1/requirements/{id}/git")
    LinksBody requirementLinks(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean refresh) {
        return list(new LinkService.RequirementTarget(id), refresh);
    }

    @GetMapping("/api/v1/requirements/{id}/git/choices")
    List<ChoiceBody> requirementChoices(@PathVariable UUID id) {
        return choices(new LinkService.RequirementTarget(id));
    }

    @PostMapping("/api/v1/requirements/{id}/git")
    ResponseEntity<LinkBody> linkRequirement(@PathVariable UUID id, @RequestBody(required = false) LinkRequest body) {
        return add(new LinkService.RequirementTarget(id), body);
    }

    @DeleteMapping("/api/v1/requirements/{id}/git/{key}")
    ResponseEntity<Void> unlinkRequirement(@PathVariable UUID id, @PathVariable String key) {
        return remove(new LinkService.RequirementTarget(id), key);
    }

    // ------------------------------------------------------------------------------------------------ shared

    private LinksBody list(LinkService.Target target, boolean refresh) {
        var actor = requests.actor();
        LinkService.Listing listing = GitRequests.retrying(() -> links.list(actor, target, refresh));
        return new LinksBody(listing.readable(), listing.writable(), listing.links().stream().map(GitLinkController::body).toList());
    }

    private List<ChoiceBody> choices(LinkService.Target target) {
        var actor = requests.actor();
        return GitRequests.retrying(() -> links.choices(actor, target)).stream().map(choice -> new ChoiceBody(choice.release().id(),
            choice.release().providerId(), choice.provider().name(), choice.provider().kind().key(), repository(choice.repository()))).toList();
    }

    private ResponseEntity<LinkBody> add(LinkService.Target target, LinkRequest body) {
        var actor = requests.actor();
        if (body == null) {
            throw ApiProblemException.of(400, "request.invalid");
        }
        LinkService.Selection selection = body.url() == null && (body.releaseId() != null || body.kind() != null)
            ? new LinkService.Selection(body.releaseId(), body.kind(), body.key()) : null;
        if (body.url() != null && (body.releaseId() != null || body.kind() != null || body.key() != null)) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("url.invalid"));
        }
        LinkService.LinkView view = GitRequests.retrying(() -> links.add(actor, target, body.url(), selection));
        return ResponseEntity.status(HttpStatus.CREATED).body(body(view));
    }

    private ResponseEntity<Void> remove(LinkService.Target target, String key) {
        var actor = requests.actor();
        GitRequests.retrying(() -> {
            links.remove(actor, target, key);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    private static LinkBody body(LinkService.LinkView view) {
        var link = view.link();
        var provider = view.provider();
        String name = provider == null || provider.removed() ? null : provider.name();
        String kind = provider == null ? null : provider.kind().key();
        var live = view.live();
        if (live == null) {
            return new LinkBody(view.key(), link.objectKind(), null, null, null, null, null, false, view.reason(), null, null, null, null);
        }
        var commit = live.commit();
        var change = live.change();
        return new LinkBody(view.key(), link.objectKind(), link.providerId(), name, kind, link.linkedAt(), link.linkedBy(), true, null,
            repository(live.repository()),
            commit == null ? null : new CommitBody(commit.sha(), commit.shortSha(), commit.message(), commit.author(), commit.at()),
            change == null ? null : new ChangeBody(change.number(), change.title(), change.state(), change.author()), live.url());
    }

    private static RepositoryBody repository(ProviderApi.Repository repository) {
        return new RepositoryBody(repository.name(), repository.path(), repository.url());
    }
}
