package taskmanager.files.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.files.internal.Attachment;
import taskmanager.files.internal.AttachmentService;
import taskmanager.permissions.AccessContext;

/**
 * The attachments of a request in the portal (TASK-076, DEC-106): only those visible to the customer are listed and delivered;
 * an internal one answers 404 like an unknown one. An upload needs {@code portal.answer} with the write rule for the
 * attachments and is visible to the customer from the start. Delivery and limits as for the staff (TASK-051).
 */
@RestController
@RequestMapping("/api/v1/portal/projects/{projectId}/tickets/{number}/attachments")
class PortalAttachmentController {
    private final AttachmentService attachments;
    private final AttachmentController common;

    PortalAttachmentController(AttachmentService attachments, AttachmentController common) {
        this.attachments = attachments;
        this.common = common;
    }

    /** What the portal shows of an attachment: no checksum and no account of the staff. */
    record PortalAttachmentBody(UUID id, String name, long size, Instant uploadedAt) {
        static PortalAttachmentBody of(Attachment attachment) {
            return new PortalAttachmentBody(attachment.id(), attachment.name(), attachment.size(), attachment.uploadedAt());
        }
    }

    @GetMapping
    ResponseEntity<List<PortalAttachmentBody>> list(@PathVariable UUID projectId, @PathVariable long number) {
        AccessContext actor = common.actor();
        List<PortalAttachmentBody> body = AttachmentController.retrying(() -> attachments.portalList(actor, projectId, number)).stream()
            .map(PortalAttachmentBody::of).toList();
        return ResponseEntity.ok().header(AttachmentController.MAX_BYTES_HEADER, Long.toString(attachments.maxBytes())).body(body);
    }

    @PostMapping
    ResponseEntity<PortalAttachmentBody> upload(@PathVariable UUID projectId, @PathVariable long number,
            @RequestParam(name = "name", required = false) String name, HttpServletRequest request) {
        AccessContext actor = common.actor();
        AttachmentController.retrying(() -> {
            attachments.portalMayAdd(actor, projectId, number);
            return null;
        });
        Attachment added = AttachmentController.retrying(() -> {
            try {
                return attachments.portalUpload(actor, projectId, number, name, request.getContentLengthLong(), request.getInputStream());
            } catch (IOException failure) {
                throw taskmanager.platform.ApiProblemException.of(400, "request.invalid");
            }
        });
        return ResponseEntity.created(URI.create("/api/v1/portal/projects/" + projectId + "/tickets/" + number + "/attachments/" + added.id()))
            .body(PortalAttachmentBody.of(added));
    }

    @GetMapping("/{id}")
    ResponseEntity<InputStreamResource> download(@PathVariable UUID projectId, @PathVariable long number, @PathVariable UUID id) {
        AccessContext actor = common.actor();
        AttachmentService.Download download = AttachmentController.retrying(() -> attachments.portalDownload(actor, projectId, number, id));
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
}
