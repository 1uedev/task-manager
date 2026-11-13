package taskmanager.requirements.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.requirements.RequirementGitRecords;

/** The requirements side of the Git links (TASK-112): the events {@code git_linked} and {@code git_unlinked} with the field {@code git}. */
@Component
class RequirementGitPort implements RequirementGitRecords {
    private final RequirementRepository repository;

    RequirementGitPort(RequirementRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<UUID> projectOf(UUID companyId, UUID requirementId) {
        return repository.find(companyId, requirementId).map(RequirementRepository.Requirement::projectId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> lockForGitChange(UUID companyId, UUID requirementId) {
        return repository.lockShared(companyId, requirementId).map(RequirementRepository.Requirement::projectId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordGitChange(UUID companyId, UUID requirementId, Instant occurredAt, UUID actor, String removed, String added) {
        if ((removed == null) == (added == null)) {
            throw new IllegalArgumentException("exactly one of removed and added");
        }
        RequirementRepository.Requirement requirement = repository.lockShared(companyId, requirementId)
            .orElseThrow(() -> new IllegalStateException("The requirement is gone"));
        repository.event(companyId, requirementId, requirement.currentRevision(), added != null ? "git_linked" : "git_unlinked", FIELD, null,
            removed, added, null, actor, occurredAt);
    }
}
