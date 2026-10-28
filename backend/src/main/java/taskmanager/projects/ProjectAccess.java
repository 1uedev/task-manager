package taskmanager.projects;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import taskmanager.permissions.ResourceContext;

/**
 * What other modules (tickets, tags, search) use of a project (TASK-026, DEC-065). They never read the tables of this module.
 *
 * <p>{@link #resource} is the project adapter of the permissions contract: pass it to {@code Permissions.require} or
 * {@code inspect}. When the permissions module calls it, after the rights and identity locks, it locks the project row
 * ({@code FOR SHARE}, or {@code FOR UPDATE} for {@code exclusive}) and reads whether the account is a member. Every change
 * of the membership locks the project row {@code FOR UPDATE}, so a removal waits for a change that already passed its check,
 * and a change that waits sees the removal. The lock is held until the end of the transaction. A missing project yields a
 * context without membership, which denies. An account that is not a member is refused before the lock, without waiting,
 * so a project locked by a concurrent change cannot be told apart from an unknown one.
 */
public interface ProjectAccess {
    Supplier<ResourceContext> resource(UUID companyId, UUID projectId, UUID accountId, String resourceType, boolean exclusive);

    /**
     * The current configuration version of the project. Call it after {@link #resource} locked the project in the same
     * transaction; a configuration change locks the project row exclusively, so the answer stays current until the commit.
     */
    Optional<Long> currentConfigurationVersion(UUID projectId);

    /** One stored version of the configuration; versions never change. */
    Optional<ProjectConfiguration> configuration(UUID projectId, long version);

    /**
     * Whether the account is a member of the project. Call it after {@link #resource} locked the project in the same
     * transaction; every change of the membership locks the project row exclusively, so the answer stays current until
     * the commit (TASK-027).
     */
    boolean isMember(UUID projectId, UUID accountId);

    /** The key of the project, for example {@code PLAT}; keys never change. */
    Optional<String> key(UUID projectId);

    /**
     * The projects of the company the account is a member of, without locks (TASK-029). A caller that relies on a membership
     * checks it again with {@link #resource}.
     */
    List<UUID> memberships(UUID companyId, UUID accountId);

    /**
     * Locks the project row {@code FOR SHARE} until the commit without asking for a membership, for work that no person
     * does, such as a ticket the mail intake creates (TASK-057); the caller decided by other means that the work is allowed.
     * Returns whether the project exists in the company. Needs a transaction.
     */
    boolean lockForIntake(UUID companyId, UUID projectId);

    /** Every project of the company with key, name and description, to rebuild a projection (TASK-029). Needs a transaction. */
    List<ProjectChanged> searchSources(UUID companyId);
}
