package taskmanager.files.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.Decision;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import taskmanager.tickets.TicketAccess;

/**
 * TASK-051: what the upload does when the file store or the transaction fails. The consistency rule is "content first, row
 * second": content may remain without a row (the clean up removes it), a row never without content.
 */
class AttachmentServiceTest {
    private final AttachmentRepository attachments = mock(AttachmentRepository.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final ProjectAccess projects = mock(ProjectAccess.class);
    private final TicketAccess tickets = mock(TicketAccess.class);
    private final Permissions permissions = mock(Permissions.class);
    private final AccessContext actor = new AccessContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, false, false, null);
    private final UUID project = UUID.randomUUID();
    private final UUID ticket = UUID.randomUUID();
    private final FileStorage.Staged staged = new FileStorage.Staged(Path.of("/staging/upload.part"), 10, "a".repeat(64), new byte[] {'x'});
    private AttachmentService service;

    @BeforeEach
    void wire() throws Exception {
        FilesProperties properties = new FilesProperties("/unused", 1000, java.time.Duration.ofHours(24), java.time.Duration.ofDays(7),
            java.time.Duration.ofHours(1), List.of("exe"));
        service = new AttachmentService(attachments, storage, projects, tickets, permissions, Clock.systemUTC(), properties,
            mock(PlatformTransactionManager.class));
        when(storage.stage(any(), anyLong())).thenReturn(staged);
        @SuppressWarnings("unchecked")
        Supplier<ResourceContext> resource = mock(Supplier.class);
        when(projects.resource(any(), any(), any(), anyString(), anyBoolean())).thenReturn(resource);
        when(permissions.inspect(any(), any(), anyString(), any())).thenReturn(new Decision(true, Set.of(), Set.of()));
        when(tickets.find(any(), any(), anyLong())).thenReturn(Optional.of(ticket));
    }

    private void upload() {
        service.upload(actor, project, 1, "datei.txt", 10, new ByteArrayInputStream(new byte[10]));
    }

    @Test
    void anUploadTheClientBreaksOffIsNoFailureOfTheStore() throws Exception {
        when(storage.stage(any(), anyLong())).thenThrow(new FileStorage.UploadInterrupted(new IOException("connection reset")));
        ApiProblemException refused = assertThrows(ApiProblemException.class, this::upload);
        assertEquals(400, refused.status());
        assertEquals("attachment.incomplete", refused.code());
        verifyNoInteractions(attachments);
    }

    @Test
    void aStoreThatCannotBeWrittenAnswersRetryableAndTouchesNoRow() throws Exception {
        when(storage.stage(any(), anyLong())).thenThrow(new IOException("disk full"));
        ApiProblemException refused = assertThrows(ApiProblemException.class, this::upload);
        assertEquals(503, refused.status());
        assertEquals("attachment.storage_unavailable", refused.code());
        verifyNoInteractions(attachments);
    }

    @Test
    void aStoreThatCannotPutTheFileInPlaceDiscardsTheStagedFile() throws Exception {
        org.mockito.Mockito.doThrow(new IOException("read only")).when(storage).store(any(), any());
        assertEquals("attachment.storage_unavailable", assertThrows(ApiProblemException.class, this::upload).code());
        verify(storage).discard(staged);
        verifyNoInteractions(attachments);
    }

    @Test
    void aRefusedSizeOrTypeDiscardsTheStagedFileAndStoresNothing() throws Exception {
        when(storage.stage(any(), anyLong())).thenReturn(new FileStorage.Staged(staged.path(), 1001, staged.sha256(), staged.head()));
        assertEquals("attachment.too_large", assertThrows(ApiProblemException.class, this::upload).code());
        when(storage.stage(any(), anyLong())).thenReturn(new FileStorage.Staged(staged.path(), 10, staged.sha256(), new byte[] {'M', 'Z'}));
        assertEquals("attachment.type_blocked", assertThrows(ApiProblemException.class, this::upload).code());
        verify(storage, org.mockito.Mockito.times(2)).discard(any());
        verify(storage, never()).store(any(), any());
        verifyNoInteractions(attachments);
    }

    @Test
    void rightsWithdrawnWhileTheBodyWasReadRemoveTheContentAtOnce() throws Exception {
        org.mockito.Mockito.doThrow(new PermissionDenied()).when(permissions).require(any(), any(), eq("ticket.edit"), any(), any());
        ApiProblemException refused = assertThrows(ApiProblemException.class, this::upload);
        assertEquals(403, refused.status());
        verify(storage).store(eq(staged), any());
        verify(storage).delete(any());
        verify(attachments, never()).insert(any());
    }

    @Test
    void aFailureThatMayHaveCommittedKeepsTheContentForTheCleanUp() throws Exception {
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("connection lost")).when(attachments).insert(any());
        assertThrows(DataAccessResourceFailureException.class, this::upload);
        verify(storage).store(eq(staged), any());
        verify(storage, never()).delete(any());
    }
}
