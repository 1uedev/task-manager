package taskmanager.requirements;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * What the git module needs of requirements (TASK-112, DEC-142): the project of a requirement, its lock and the entries of
 * links and removals of Git objects in its history. A link belongs to the requirement, not to a revision (rule 8); it leaves
 * the version and the revisions as they are. The rights are checked by the caller with the field {@link #FIELD} of the
 * resource type {@code requirement}.
 */
public interface RequirementGitRecords {
    /** The field of the requirement that holds its Git links; its read rule decides who sees them and their history entries. */
    String FIELD = "git";

    /** The project of the requirement, or empty for an unknown identifier or one of another company. Needs no lock. */
    Optional<UUID> projectOf(UUID companyId, UUID requirementId);

    /**
     * Holds the requirement row {@code FOR SHARE} until the commit, after the rights and the project; empty when it no longer
     * exists. Needs a transaction.
     */
    Optional<UUID> lockForGitChange(UUID companyId, UUID requirementId);

    /**
     * Records a link ({@code removed} is {@code null}) or a removal ({@code added} is {@code null}) at the current revision. The
     * value names only the kind of the object and the internal identifier of the link. Needs the lock of
     * {@link #lockForGitChange} in the same transaction.
     */
    void recordGitChange(UUID companyId, UUID requirementId, Instant occurredAt, UUID actor, String removed, String added);
}
