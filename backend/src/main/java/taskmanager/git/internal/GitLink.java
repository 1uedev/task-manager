package taskmanager.git.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * A Git object linked to a ticket or a requirement (TASK-112). Only stable identifiers are kept: the provider, the identifier
 * of the repository, the kind of object and the full commit SHA or the number ({@code ""} for a repository).
 */
public record GitLink(UUID id, UUID companyId, String subjectKind, UUID subjectId, UUID projectId, UUID providerId, String repositoryId,
        String objectKind, String objectKey, Instant linkedAt, UUID linkedBy) {
    public static final String REPOSITORY = "repository";
    public static final String COMMIT = "commit";
    public static final String PULL_REQUEST = "pull_request";
    public static final String MERGE_REQUEST = "merge_request";

    /**
     * The value of a history entry: the kind of object only, never anything read from the provider and no identifier that would
     * relate the entry to a link (TASK-114, F6).
     */
    String historyValue() {
        return objectKind;
    }
}
