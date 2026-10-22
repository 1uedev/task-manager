package taskmanager.permissions.internal;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.CompanyAdministration;
import taskmanager.permissions.Decision;
import taskmanager.permissions.GroupNameTaken;
import taskmanager.permissions.IdentityAccess;
import taskmanager.permissions.LastAdministration;
import taskmanager.permissions.PermissionAdministration;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;

/** Only reads permissions-owned tables. The revision row orders revocations and business commits. */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class JdbcPermissions implements Permissions, PermissionAdministration, CompanyAdministration {
    private static final String MANAGE = "permissions.manage";
    private static final String AUDIT = "permissions.audit.read";
    private static final String COMPANY_GROUP = "Unternehmensverwaltung";
    private static final String LOCK_TIMEOUT = "5s";
    private final JdbcTemplate jdbc;
    private final ObjectProvider<IdentityAccess> identity;
    private final Clock clock;

    public JdbcPermissions(JdbcTemplate jdbc, ObjectProvider<IdentityAccess> identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
    }

    /** What the rights lock established: the policy of the actor and how long the checked session stays valid. */
    private record Locked(Policy policy, Instant currentUntil) {}

    @Override
    public long currentVersion(UUID companyId) {
        var versions = jdbc.queryForList("select revision from permissions_revision where company_id = ?", Long.class, companyId);
        if (versions.size() != 1) throw new PermissionDenied();
        return versions.getFirst();
    }

    @Override
    public Decision inspect(AccessContext actor, Supplier<ResourceContext> resource, String action, Set<String> fields) {
        try {
            return bounded(() -> lock(actor, false).policy().decide(actor, resource(resource), key(action), keys(fields)));
        } catch (PermissionDenied denied) {
            return Decision.denied();
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void require(AccessContext actor, Supplier<ResourceContext> resource, String action,
            Set<String> readFields, Set<String> writeFields) {
        Set<String> read = keys(readFields), write = keys(writeFields), all = new HashSet<>(read);
        all.addAll(write);
        Instant until = bounded(() -> {
            Locked locked = lock(actor, false);
            Decision decision = locked.policy().decide(actor, resource(resource), key(action), all);
            if (!decision.allowed() || !decision.readableFields().containsAll(read)
                    || !decision.writableFields().containsAll(write)) throw new PermissionDenied();
            return locked.currentUntil();
        });
        checkAgainBeforeCommit(until);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Group createGroup(AccessContext actor, String name) {
        bounded(() -> companyManager(actor, lock(actor, true).policy()));
        if (name == null || name.isBlank() || name.length() > 80) throw new PermissionDenied();
        String stripped = name.strip();
        // The exclusive revision lock orders every creation of the company, so no second one can slip in after this check.
        if (jdbc.queryForObject("select count(*) from permissions_group where company_id = ? and lower(name) = lower(?)",
                Integer.class, actor.companyId(), stripped) > 0) throw new GroupNameTaken();
        UUID group = UUID.randomUUID();
        jdbc.update("insert into permissions_group(company_id, id, name) values (?, ?, ?)", actor.companyId(), group, stripped);
        changed(actor, "group.created", group, null, null, null, null, null, null, null);
        return new Group(group, stripped);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void setMember(AccessContext actor, UUID groupId, UUID userId, boolean enabled) {
        bounded(() -> {
            companyManager(actor, lock(actor, true).policy());
            requireGroup(actor, groupId);
            if (userId == null || !adapter().userExists(actor.companyId(), userId)) throw new PermissionDenied();
            return null;
        });
        int rows = enabled
            ? jdbc.update("insert into permissions_membership(company_id, group_id, user_id) values (?, ?, ?) on conflict do nothing",
                actor.companyId(), groupId, userId)
            : jdbc.update("delete from permissions_membership where company_id = ? and group_id = ? and user_id = ?",
                actor.companyId(), groupId, userId);
        // Already so: no new rights version, no audit entry (P6).
        if (rows == 0) return;
        if (!enabled) companyManagerRemains(actor);
        changed(actor, "membership.changed", groupId, userId, null, null, null, null, enabled ? "ALLOW" : "REMOVE", enabled ? null : "ALLOW");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void setAction(AccessContext actor, UUID groupId, Supplier<ResourceContext> scope, String action, boolean enabled) {
        ResourceContext resource = bounded(() -> {
            Policy policy = lock(actor, true).policy();
            ResourceContext checked = resource(scope);
            key(action);
            administer(actor, policy, checked);
            // Delegates may neither delegate administration nor distribute an action they do not hold.
            if (!isCompanyManager(actor, policy) && (action.startsWith("permissions.")
                    || !policy.decide(actor, checked, action, Set.of()).allowed())) throw new PermissionDenied();
            requireGroup(actor, groupId);
            return checked;
        });
        boolean held = jdbc.queryForObject("select count(*) from permissions_action where company_id = ? and group_id = ? and project_id is not distinct from ? and action_key = ?",
            Integer.class, actor.companyId(), groupId, resource.projectId(), action) > 0;
        if (held == enabled) return;
        if (enabled) jdbc.update("insert into permissions_action(company_id, group_id, project_id, action_key) values (?, ?, ?, ?)",
            actor.companyId(), groupId, resource.projectId(), action);
        else {
            jdbc.update("delete from permissions_action where company_id = ? and group_id = ? and project_id is not distinct from ? and action_key = ?",
                actor.companyId(), groupId, resource.projectId(), action);
            if (resource.projectId() == null && MANAGE.equals(action)) companyManagerRemains(actor);
        }
        changed(actor, "action.changed", groupId, null, resource.projectId(), action, null, null,
            enabled ? "ALLOW" : "REMOVE", enabled ? null : "ALLOW");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void setField(AccessContext actor, UUID groupId, Supplier<ResourceContext> scope, String field,
            Operation operation, Effect effect) {
        record Checked(ResourceContext resource, boolean company) {}
        Checked checked = bounded(() -> {
            Policy policy = lock(actor, true).policy();
            ResourceContext resource = resource(scope);
            key(field);
            if (operation == null || effect == null) throw new PermissionDenied();
            administer(actor, policy, resource);
            // Also constrain removal of denies: removing a deny can grant access through another group.
            boolean company = isCompanyManager(actor, policy);
            if (!company && (!policy.field(resource.resourceType(), field, "READ", resource.projectId())
                    || !policy.field(resource.resourceType(), field, "WRITE", resource.projectId())))
                throw new PermissionDenied();
            requireGroup(actor, groupId);
            return new Checked(resource, company);
        });
        ResourceContext resource = checked.resource();
        boolean company = checked.company();
        record Rule(String effect, UUID setBy, Boolean setByCompany) {}
        var existing = jdbc.query("select effect, set_by, set_by_company from permissions_field where company_id = ? and group_id = ? and project_id is not distinct from ? and resource_type = ? and field_key = ? and operation = ?",
            (rs, row) -> new Rule(rs.getString(1), rs.getObject(2, UUID.class), rs.getObject(3, Boolean.class)),
            actor.companyId(), groupId, resource.projectId(), resource.resourceType(), field, operation.name());
        Rule previous = existing.isEmpty() ? null : existing.getFirst();
        // A delegate may only withdraw restrictions that the delegate set as a delegate; a deny of anyone else, one set
        // with company administration (even by the same person before losing that role) and one of unknown origin stay untouched.
        if (!company && previous != null && "DENY".equals(previous.effect())
                && !(actor.userId().equals(previous.setBy()) && Boolean.FALSE.equals(previous.setByCompany())))
            throw new PermissionDenied();
        // The same rule set again by the same author in the same role changes nothing (P6); another author takes it over.
        if (effect == Effect.REMOVE ? previous == null : previous != null && previous.effect().equals(effect.name())
                && actor.userId().equals(previous.setBy()) && Objects.equals(previous.setByCompany(), company)) return;
        jdbc.update("delete from permissions_field where company_id = ? and group_id = ? and project_id is not distinct from ? and resource_type = ? and field_key = ? and operation = ?",
            actor.companyId(), groupId, resource.projectId(), resource.resourceType(), field, operation.name());
        if (effect != Effect.REMOVE) jdbc.update("insert into permissions_field(company_id, group_id, project_id, resource_type, field_key, operation, effect, set_by, set_by_company) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            actor.companyId(), groupId, resource.projectId(), resource.resourceType(), field, operation.name(), effect.name(), actor.userId(), company);
        changed(actor, "field." + operation.name().toLowerCase(java.util.Locale.ROOT), groupId, null,
            resource.projectId(), null, resource.resourceType(), field, effect.name(), previous == null ? null : previous.effect());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void initialize(UUID companyId, UUID administratorId, Set<String> rights) {
        if (companyId == null || administratorId == null || rights == null || !adapter().userExists(companyId, administratorId)) throw new PermissionDenied();
        // Sorted, so the audit trail of a start is the same every time; MANAGE is always granted and never listed twice.
        var granted = new java.util.TreeSet<>(keys(rights));
        granted.remove(MANAGE);
        // The primary key refuses a company that already has rights; the surrounding transaction then rolls back.
        jdbc.update("insert into permissions_revision(company_id) values (?)", companyId);
        UUID group = UUID.randomUUID();
        jdbc.update("insert into permissions_group(company_id, id, name) values (?, ?, ?)", companyId, group, COMPANY_GROUP);
        changed(companyId, administratorId, "group.created", group, null, null, null, null, null, null, null);
        jdbc.update("insert into permissions_action(company_id, group_id, project_id, action_key) values (?, ?, null, ?)", companyId, group, MANAGE);
        changed(companyId, administratorId, "action.changed", group, null, null, MANAGE, null, null, "ALLOW", null);
        for (String right : granted) {
            jdbc.update("insert into permissions_action(company_id, group_id, project_id, action_key) values (?, ?, null, ?)", companyId, group, right);
            changed(companyId, administratorId, "action.changed", group, null, null, right, null, null, "ALLOW", null);
        }
        jdbc.update("insert into permissions_membership(company_id, group_id, user_id) values (?, ?, ?)", companyId, group, administratorId);
        changed(companyId, administratorId, "membership.changed", group, administratorId, null, null, null, null, "ALLOW", null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean remainsWithout(UUID companyId, UUID userId) {
        if (companyId == null || userId == null) return false;
        return bounded(() -> {
            // The same exclusive lock as every rights change, taken before any identity lock of the caller.
            if (jdbc.queryForList("select revision from permissions_revision where company_id = ? for update", Long.class, companyId).size() != 1)
                return false;
            var holders = companyManagers(companyId);
            if (!holders.contains(userId)) return true;
            return holders.stream().filter(holder -> !holder.equals(userId)).anyMatch(holder -> adapter().userActive(companyId, holder));
        });
    }

    @Override
    public boolean isAdministrator(UUID companyId, UUID userId) {
        return companyId != null && userId != null && companyManagers(companyId).contains(userId);
    }

    @Override
    public List<Group> groups(AccessContext actor) {
        bounded(() -> companyManager(actor, lock(actor, false).policy()));
        return jdbc.query("select id, name from permissions_group where company_id = ? order by name, id",
            (rs, row) -> new Group(rs.getObject("id", UUID.class), rs.getString("name")), actor.companyId());
    }

    @Override
    public Optional<GroupDetail> group(AccessContext actor, UUID groupId) {
        bounded(() -> companyManager(actor, lock(actor, false).policy()));
        var names = groupId == null ? List.<String>of()
            : jdbc.queryForList("select name from permissions_group where company_id = ? and id = ?", String.class, actor.companyId(), groupId);
        if (names.isEmpty()) return Optional.empty();
        var members = jdbc.queryForList("select user_id from permissions_membership where company_id = ? and group_id = ? order by user_id",
            UUID.class, actor.companyId(), groupId);
        var actions = jdbc.query("select project_id, action_key from permissions_action where company_id = ? and group_id = ? order by project_id nulls first, action_key",
            (rs, row) -> new ActionRule(rs.getObject(1, UUID.class), rs.getString(2)), actor.companyId(), groupId);
        var fields = jdbc.query("select project_id, resource_type, field_key, operation, effect from permissions_field where company_id = ? and group_id = ? order by project_id nulls first, resource_type, field_key, operation",
            (rs, row) -> new FieldRule(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                Operation.valueOf(rs.getString(4)), Effect.valueOf(rs.getString(5))), actor.companyId(), groupId);
        return Optional.of(new GroupDetail(new Group(groupId, names.getFirst()), members, actions, fields));
    }

    @Override
    public List<ProjectGroup> projectGroups(AccessContext actor, Supplier<ResourceContext> project) {
        ResourceContext resource = bounded(() -> {
            Policy policy = lock(actor, false).policy();
            ResourceContext checked = resource(project);
            if (checked.projectId() == null) throw new PermissionDenied();
            administer(actor, policy, checked);
            return checked;
        });
        // Every group of the company, because a project administration may change the rules of any of them in its project;
        // nothing else: no members, no company rules and no rules of other projects.
        var actions = jdbc.query("select group_id, action_key from permissions_action where company_id = ? and project_id = ? order by action_key",
            (rs, row) -> java.util.Map.entry(rs.getObject(1, UUID.class), new ActionRule(resource.projectId(), rs.getString(2))),
            actor.companyId(), resource.projectId());
        var fields = jdbc.query("select group_id, resource_type, field_key, operation, effect from permissions_field where company_id = ? and project_id = ? order by resource_type, field_key, operation",
            (rs, row) -> java.util.Map.entry(rs.getObject(1, UUID.class), new FieldRule(resource.projectId(), rs.getString(2), rs.getString(3),
                Operation.valueOf(rs.getString(4)), Effect.valueOf(rs.getString(5)))), actor.companyId(), resource.projectId());
        return jdbc.query("select id, name from permissions_group where company_id = ? order by name, id",
            (rs, row) -> new Group(rs.getObject("id", UUID.class), rs.getString("name")), actor.companyId()).stream()
            .map(group -> new ProjectGroup(group,
                actions.stream().filter(entry -> entry.getKey().equals(group.id())).map(java.util.Map.Entry::getValue).toList(),
                fields.stream().filter(entry -> entry.getKey().equals(group.id())).map(java.util.Map.Entry::getValue).toList()))
            .toList();
    }

    @Override
    public List<Audit> audit(AccessContext actor, Long before, int limit) {
        bounded(() -> {
            Policy policy = lock(actor, false).policy();
            if (limit < 1 || limit > 100 || (before != null && before < 1)
                    || !policy.decide(actor, ResourceContext.company(actor.companyId()), AUDIT, Set.of()).allowed())
                throw new PermissionDenied();
            return null;
        });
        // Pages by the rights version, which only grows: the next page starts below the lowest version of the last one.
        return jdbc.query("select * from permissions_audit where company_id = ? and rights_version < ? order by rights_version desc limit ?",
            (rs, row) -> new Audit(rs.getObject("id", UUID.class), rs.getObject("actor_id", UUID.class),
                rs.getTimestamp("occurred_at").toInstant(), rs.getString("operation"), rs.getObject("group_id", UUID.class),
                rs.getObject("subject_id", UUID.class), rs.getObject("project_id", UUID.class), rs.getString("action_key"),
                rs.getString("resource_type"), rs.getString("field_key"), rs.getString("effect"), rs.getString("previous_effect"),
                rs.getLong("rights_version")),
            actor.companyId(), before == null ? Long.MAX_VALUE : before, limit);
    }

    /** Run inside {@link #bounded}, which sets the lock timeout for the check. */
    private Locked lock(AccessContext actor, boolean exclusive) {
        if (actor == null || actor.passwordChangeRequired()) throw new PermissionDenied();
        var versions = jdbc.queryForList("select revision from permissions_revision where company_id = ? for "
            + (exclusive ? "update" : "share"), Long.class, actor.companyId());
        if (versions.size() != 1 || versions.getFirst() != actor.rightsVersion()) throw new PermissionDenied();
        Instant until = adapter().currentUntil(actor).orElseThrow(PermissionDenied::new);
        var actions = jdbc.query("select a.project_id, a.action_key from permissions_action a join permissions_membership m on a.company_id=m.company_id and a.group_id=m.group_id where m.company_id=? and m.user_id=?",
            (rs, row) -> new Policy.Action(rs.getObject(1, UUID.class), rs.getString(2)), actor.companyId(), actor.userId());
        var fields = jdbc.query("select f.project_id, f.resource_type, f.field_key, f.operation, f.effect from permissions_field f join permissions_membership m on f.company_id=m.company_id and f.group_id=m.group_id where m.company_id=? and m.user_id=?",
            (rs, row) -> new Policy.Field(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)),
            actor.companyId(), actor.userId());
        return new Locked(new Policy(actions, fields), until);
    }

    /**
     * Runs the check of rights, identity and resource with a lock timeout of five seconds and afterwards puts back the
     * timeout the transaction had before (P5), so the business statements of the caller keep their own. A database error
     * leaves the transaction aborted; nothing more can run in it, so the old value is not restored then.
     * An older snapshot must not reuse pre-revocation group rows after waiting for the lock, hence READ COMMITTED only.
     */
    private <T> T bounded(Supplier<T> check) {
        if (!Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                connection.getTransactionIsolation() == Connection.TRANSACTION_READ_COMMITTED))) throw new PermissionDenied();
        String previous = jdbc.queryForObject("select current_setting('lock_timeout')", String.class);
        jdbc.queryForObject("select set_config('lock_timeout', ?, true)", String.class, LOCK_TIMEOUT);
        T result;
        try {
            result = check.get();
        } catch (DataAccessException aborted) {
            throw aborted;
        } catch (RuntimeException refused) {
            restore(previous);
            throw refused;
        }
        restore(previous);
        return result;
    }

    private void bounded(Runnable check) {
        bounded(() -> {
            check.run();
            return null;
        });
    }

    private void restore(String lockTimeout) {
        jdbc.queryForObject("select set_config('lock_timeout', ?, true)", String.class, lockTimeout);
    }

    /**
     * The locks taken by the check keep rights, identity and resource stable until the commit, but not the time: a session
     * can reach the end of its period in between. Right before the commit the end is compared with the clock again; a
     * passed end rolls the whole transaction back (TASK-043). Several checks in one transaction keep the earliest end.
     */
    private void checkAgainBeforeCommit(Instant until) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) throw new PermissionDenied();
        var deadline = (CommitDeadline) TransactionSynchronizationManager.getResource(CommitDeadline.class);
        if (deadline == null) {
            deadline = new CommitDeadline(clock);
            TransactionSynchronizationManager.bindResource(CommitDeadline.class, deadline);
            TransactionSynchronizationManager.registerSynchronization(deadline);
        }
        deadline.tighten(until);
    }

    private static final class CommitDeadline implements TransactionSynchronization {
        private final Clock clock;
        private Instant until = Instant.MAX;

        CommitDeadline(Clock clock) { this.clock = clock; }

        void tighten(Instant end) { if (end.isBefore(until)) until = end; }

        @Override
        public void beforeCommit(boolean readOnly) {
            if (!clock.instant().isBefore(until)) throw new PermissionDenied();
        }

        @Override
        public void afterCompletion(int status) {
            TransactionSynchronizationManager.unbindResourceIfPossible(CommitDeadline.class);
        }
    }

    private IdentityAccess adapter() {
        var candidates = identity.stream().limit(2).toList();
        if (candidates.size() != 1) throw new PermissionDenied();
        return candidates.getFirst();
    }

    /** External accounts never administer, whatever their groups grant (P3). */
    private static boolean isCompanyManager(AccessContext actor, Policy policy) {
        return !actor.external() && policy.decide(actor, ResourceContext.company(actor.companyId()), MANAGE, Set.of()).allowed();
    }

    private static void companyManager(AccessContext actor, Policy policy) {
        if (!isCompanyManager(actor, policy)) throw new PermissionDenied();
    }

    private static void administer(AccessContext actor, Policy policy, ResourceContext resource) {
        if (actor.external() || !actor.companyId().equals(resource.companyId())) throw new PermissionDenied();
        if (!isCompanyManager(actor, policy) && (resource.projectId() == null
                || !policy.decide(actor, resource, MANAGE, Set.of()).allowed())) throw new PermissionDenied();
    }

    /**
     * Runs after the change under the exclusive revision lock; a denial rolls the whole transaction back.
     * Only accounts the identity adapter reports as active count: neither a group without a real member nor a
     * deactivated account keeps the company administration alive.
     */
    private void companyManagerRemains(AccessContext actor) {
        if (companyManagers(actor.companyId()).stream().noneMatch(user -> adapter().userActive(actor.companyId(), user))) throw new LastAdministration();
    }

    private List<UUID> companyManagers(UUID companyId) {
        return jdbc.queryForList("select distinct m.user_id from permissions_membership m join permissions_action a on a.company_id = m.company_id and a.group_id = m.group_id where a.company_id = ? and a.project_id is null and a.action_key = ? order by m.user_id",
            UUID.class, companyId, MANAGE);
    }

    private void requireGroup(AccessContext actor, UUID groupId) {
        if (groupId == null || jdbc.queryForObject("select count(*) from permissions_group where company_id=? and id=?",
                Integer.class, actor.companyId(), groupId) != 1) throw new PermissionDenied();
    }

    private void changed(AccessContext actor, String operation, UUID group, UUID subject, UUID project,
            String action, String type, String field, String effect, String previous) {
        changed(actor.companyId(), actor.userId(), operation, group, subject, project, action, type, field, effect, previous);
    }

    private void changed(UUID company, UUID actor, String operation, UUID group, UUID subject, UUID project,
            String action, String type, String field, String effect, String previous) {
        long version = jdbc.queryForObject("update permissions_revision set revision=revision+1 where company_id=? returning revision",
            Long.class, company);
        jdbc.update("insert into permissions_audit(company_id, id, actor_id, operation, group_id, subject_id, project_id, action_key, resource_type, field_key, effect, previous_effect, rights_version) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            company, UUID.randomUUID(), actor, operation, group, subject, project, action, type, field, effect, previous, version);
    }

    private static ResourceContext resource(Supplier<ResourceContext> supplier) {
        ResourceContext result = supplier == null ? null : supplier.get();
        if (result == null) throw new PermissionDenied();
        return result;
    }

    private static String key(String key) {
        if (key == null || !key.matches("[a-z][a-z0-9_.]{0,119}")) throw new PermissionDenied();
        return key;
    }

    private static Set<String> keys(Set<String> keys) {
        if (keys == null) throw new PermissionDenied();
        keys.forEach(JdbcPermissions::key);
        return Set.copyOf(keys);
    }
}
