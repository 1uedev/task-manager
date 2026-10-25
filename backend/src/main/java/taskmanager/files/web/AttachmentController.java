package taskmanager.files.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.core.io.InputStreamResource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.files.internal.Attachment;
import taskmanager.files.internal.AttachmentService;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionContexts;
import taskmanager.platform.ApiProblemException;

/**
 * The attachments of a ticket (TASK-051). Every endpoint needs a complete login; the service decides who may do what. An
 * upload is the raw body of the request with the file name in the query, so that the content never has to fit in memory. A
 * download is always sent as a file to save, never to show: {@code Content-Disposition: attachment}, a neutral media type,
 * {@code nosniff} and a policy that allows nothing. A lock that could not be taken in time, or a deadlock PostgreSQL resolved,
 * ends the transaction; the answer is 503 {@code attachment.busy} with {@code Retry-After}, and the request may be repeated.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/tickets/{number}/attachments")
class AttachmentController {
    private static final Set<String> RETRYABLE_STATES = Set.of("55P03", "40P01", "40001");

    private final AttachmentService attachments;
    private final AccessContexts identity;
    private final PermissionContexts contexts;

    AttachmentController(AttachmentService attachments, AccessContexts identity, PermissionContexts contexts) {
        this.attachments = attachments;
        this.identity = identity;
        this.contexts = contexts;
    }

    record AttachmentBody(UUID id, String name, long size, String sha256, Instant uploadedAt, UUID uploadedBy) {
        static AttachmentBody of(Attachment attachment) {
            return new AttachmentBody(attachment.id(), attachment.name(), attachment.size(), attachment.sha256(), attachment.uploadedAt(),
                attachment.uploadedBy());
        }
    }

    @GetMapping
    List<AttachmentBody> list(@PathVariable UUID projectId, @PathVariable long number) {
        AccessContext actor = actor();
        return retrying(() -> attachments.list(actor, projectId, number)).stream().map(AttachmentBody::of).toList();
    }

    @PostMapping
    ResponseEntity<AttachmentBody> upload(@PathVariable UUID projectId, @PathVariable long number,
            @RequestParam(name = "name", required = false) String name, HttpServletRequest request) throws IOException {
        AccessContext actor = actor();
        retrying(() -> {
            attachments.mayAdd(actor, projectId, number);
            return null;
        });
        Attachment added = retrying(() -> {
            try {
                return attachments.upload(actor, projectId, number, name, request.getContentLengthLong(), request.getInputStream());
            } catch (IOException failure) {
                throw ApiProblemException.of(400, "request.invalid");
            }
        });
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/tickets/" + number + "/attachments/" + added.id()))
            .body(AttachmentBody.of(added));
    }

    @GetMapping("/{id}")
    ResponseEntity<InputStreamResource> download(@PathVariable UUID projectId, @PathVariable long number, @PathVariable UUID id) {
        AccessContext actor = actor();
        AttachmentService.Download download = retrying(() -> attachments.download(actor, projectId, number, id));
        Attachment attachment = download.attachment();
        return ResponseEntity.status(HttpStatus.OK)
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(attachment.size())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(attachment.name(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "default-src 'none'; sandbox")
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(new InputStreamResource(download.content()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> remove(@PathVariable UUID projectId, @PathVariable long number, @PathVariable UUID id) {
        AccessContext actor = actor();
        retrying(() -> {
            attachments.remove(actor, projectId, number, id);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    /** The permissions context of a complete login; a login that still has to change its password is refused. */
    private AccessContext actor() {
        var request = identity.require();
        if (request.level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        return contexts.current().orElseThrow(() -> ApiProblemException.of(403, "auth.forbidden"));
    }

    /** Runs one transaction of the service and answers a lock timeout or a deadlock as a retryable 503. */
    private static <T> T retrying(Supplier<T> call) {
        try {
            return call.get();
        } catch (DataAccessException failure) {
            if (retryable(failure)) {
                throw ApiProblemException.retryAfter(503, "attachment.busy", 1);
            }
            throw failure;
        }
    }

    private static boolean retryable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PessimisticLockingFailureException
                    || (cause instanceof SQLException sql && RETRYABLE_STATES.contains(sql.getSQLState()))) {
                return true;
            }
        }
        return false;
    }
}
