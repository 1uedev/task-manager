package taskmanager.permissions.internal;

import java.sql.Connection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.CompanyAdministration;
import taskmanager.permissions.Decision;
import taskmanager.permissions.IdentityAccess;
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
    private final JdbcTemplate jdbc;
    private final ObjectProvider<IdentityAccess> identity;

    public JdbcPermissions(JdbcTemplate jdbc, ObjectProvider<IdentityAccess> identity) {
        this.jdbc = jdbc;
        this.identity = identity;
    }

    @Override
    public long currentVersion(UUID companyId) {
        var versions = jdbc.queryForList("select revision from permissions_revision where company_id = ?", Long.class, companyId);
        if (versions.size() != 1) throw new PermissionDenied();
        return versions.getFirst();
    }

    @Override
    public Decision inspect(AccessContext actor, Supplier<ResourceContext> resource, String action, Set<String> fields) {
        try {
            Policy policy = lock(actor, false);
            return policy.decide(actor, resource(resource), key(action), keys(fields));
        } catch (PermissionDenied denied) {
            return Decision.denied();
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void require(AccessContext actor, Supplier<ResourceContext> resource, String action,
            Set<String> readFields, Set<String> writeFields) {
        Policy policy = lock(actor, false);
        Set<String> read = keys(readFields), write = keys(writeFields), all = new HashSet<>(read);
        all.addAll(write);
        Decision decision = policy.decide(actor, resource(resource), key(action), all);
        if (!decision.allowed() || !decision.readableFields().containsAll(read)
                || !decision.writableFields().containsAll(write)) throw new PermissionDenied();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Group createGroup(AccessContext actor, String name) {
        companyManager(actor, lock(actor, true));
        if (name == null || name.isBlank() || name.length() > 80) throw new PermissionDenied();
        UUID group = UUID.randomUUID();
        jdbc.update("insert into permissions_group(company_id, id, name) values (?, ?, ?)", actor.companyId(), group, name.strip());
        changed(actor, "group.created", group, null, null, null, null, null, null);
        return new Group(group, name.strip());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void setMember(AccessContext actor, UUID groupId, UUID userId, boolean enabled) {
        companyManager(actor, lock(actor, true));
        group(actor, groupId);
        if (userId == null || !adapter().userExists(actor.companyId(), userId)) throw new PermissionDenied();
        if (enabled) jdbc.update("insert into permissions_membership(company_id, group_id, user_id) values (?, ?, ?) on conflict do nothing",
            actor.companyId(), groupId, userId);
        else jdbc.update("delete from permissions_membership where company_id = ? and group_id = ? and user_id = ?",
            actor.companyId(), groupId, userId);
        if (!enabled) companyManagerRemains(actor);
        changed(actor, "membership.changed", groupId, userId, null, null, null, null, enabled ? "ALLOW" : "REMOVE");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void setAction(AccessContext actor, UUID groupId, Supplier<ResourceContext> scope, String action, boolean enabled) {
        Policy policy = lock(actor, true);
        ResourceContext resource = resource(scope);
        key(action);
        administer(actor, policy, resource);
        // Delegates may neither delegate administration nor distribute an action they do not hold.
        if (!isCompanyManager(actor, policy) && (action.startsWith("permissions.")
                || !policy.decide(actor, resource, action, Set.of()).allowed())) throw new PermissionDenied();
        group(actor, groupId);
        jdbc.update("delete from permissions_action where company_id = ? and group_id = ? and project_id is not distinct from ? and action_key = ?",
            actor.companyId(), groupId, resource.projectId(), action);
        if (enabled) jdbc.update("insert into permissions_action(company_id, group_id, project_id, action_key) values (?, ?, ?, ?)",
            actor.companyId(), groupId, resource.projectId(), action);
        else if (resource.projectId() == null && MANAGE.equals(action)) companyManagerRemains(actor);
        changed(actor, "action.changed", groupId, null, resource.projectId(), action, null, null, enabled ? "ALLOW" : "REMOVE");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void setField(AccessContext actor, UUID groupId, Supplier<ResourceContext> scope, String field,
            Operation operation, Effect effect) {
        Policy policy = lock(actor, true);
        ResourceContext resource = resource(scope);
        key(field);
        if (operation == null || effect == null) throw new PermissionDenied();
        administer(actor, policy, resource);
        // Also constrain removal of denies: removing a deny can grant access through another group.
        if (!isCompanyManager(actor, policy) && (!policy.field(resource.resourceType(), field, "READ", resource.projectId())
                || !policy.field(resource.resourceType(), field, "WRITE", resource.projectId())))
            throw new PermissionDenied();
        group(actor, groupId);
        // A delegate may only withdraw restrictions that the delegate set as a delegate; a deny of anyone else, one set
        // with company administration (even by the same person before losing that role) and one of unknown origin stay untouched.
        boolean company = isCompanyManager(actor, policy);
        if (!company && jdbc.query("select effect, set_by, set_by_company from permissions_field where company_id = ? and group_id = ? and project_id is not distinct from ? and resource_type = ? and field_key = ? and operation = ?",
                (rs, row) -> "DENY".equals(rs.getString(1)) && !(actor.userId().equals(rs.getObject(2, UUID.class))
                    && Boolean.FALSE.equals(rs.getObject(3, Boolean.class))),
                actor.companyId(), groupId, resource.projectId(), resource.resourceType(), field, operation.name()).contains(true))
            throw new PermissionDenied();
        jdbc.update("delete from permissions_field where company_id = ? and group_id = ? and project_id is not distinct from ? and resource_type = ? and field_key = ? and operation = ?",
            actor.companyId(), groupId, resource.projectId(), resource.resourceType(), field, operation.name());
        if (effect != Effect.REMOVE) jdbc.update("insert into permissions_field(company_id, group_id, project_id, resource_type, field_key, operation, effect, set_by, set_by_company) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            actor.companyId(), groupId, resource.projectId(), resource.resourceType(), field, operation.name(), effect.name(), actor.userId(), company);
        changed(actor, "field." + operation.name().toLowerCase(java.util.Locale.ROOT), groupId, null,
            resource.projectId(), null, resource.resourceType(), field, effect.name());
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
        changed(companyId, administratorId, "group.created", group, null, null, null, null, null, null);
        jdbc.update("insert into permissions_action(company_id, group_id, project_id, action_key) values (?, ?, null, ?)", companyId, group, MANAGE);
        changed(companyId, administratorId, "action.changed", group, null, null, MANAGE, null, null, "ALLOW");
        for (String right : granted) {
            jdbc.update("insert into permissions_action(company_id, group_id, project_id, action_key) values (?, ?, null, ?)", companyId, group, right);
            changed(companyId, administratorId, "action.changed", group, null, null, right, null, null, "ALLOW");
        }
        jdbc.update("insert into permissions_membership(company_id, group_id, user_id) values (?, ?, ?)", companyId, group, administratorId);
        changed(companyId, administratorId, "membership.changed", group, administratorId, null, null, null, null, "ALLOW");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean remainsWithout(UUID companyId, UUID userId) {
        if (companyId == null || userId == null) return false;
        readCommittedWithLockTimeout();
        // The same exclusive lock as every rights change, taken before any identity lock of the caller.
        if (jdbc.queryForList("select revision from permissions_revision where company_id = ? for update", Long.class, companyId).size() != 1)
            return false;
        var holders = companyManagers(companyId);
        if (!holders.contains(userId)) return true;
        return holders.stream().filter(holder -> !holder.equals(userId)).anyMatch(holder -> adapter().userActive(companyId, holder));
    }

    @Override
    public boolean isAdministrator(UUID companyId, UUID userId) {
        return companyId != null && userId != null && companyManagers(companyId).contains(userId);
    }

    @Override
    public List<Group> groups(AccessContext actor) {
        companyManager(actor, lock(actor, false));
        return jdbc.query("select id, name from permissions_group where company_id = ? order by name, id",
            (rs, row) -> new Group(rs.getObject("id", UUID.class), rs.getString("name")), actor.companyId());
    }

    @Override
    public List<Audit> audit(AccessContext actor, int limit) {
        Policy policy = lock(actor, false);
        if (limit < 1 || limit > 100 || !policy.decide(actor, ResourceContext.company(actor.companyId()), AUDIT, Set.of()).allowed())
            throw new PermissionDenied();
        return jdbc.query("select * from permissions_audit where company_id = ? order by rights_version desc limit ?",
            (rs, row) -> new Audit(rs.getObject("id", UUID.class), rs.getObject("actor_id", UUID.class),
                rs.getTimestamp("occurred_at").toInstant(), rs.getString("operation"), rs.getObject("group_id", UUID.class),
                rs.getObject("subject_id", UUID.class), rs.getObject("project_id", UUID.class), rs.getString("action_key"),
                rs.getString("resource_type"), rs.getString("field_key"), rs.getString("effect"), rs.getLong("rights_version")),
            actor.companyId(), limit);
    }

    private Policy lock(AccessContext actor, boolean exclusive) {
        if (actor == null || actor.passwordChangeRequired()) throw new PermissionDenied();
        readCommittedWithLockTimeout();
        var versions = jdbc.queryForList("select revision from permissions_revision where company_id = ? for "
            + (exclusive ? "update" : "share"), Long.class, actor.companyId());
        if (versions.size() != 1 || versions.getFirst() != actor.rightsVersion() || !adapter().isCurrent(actor))
            throw new PermissionDenied();
        var actions = jdbc.query("select a.project_id, a.action_key from permissions_action a join permissions_membership m on a.company_id=m.company_id and a.group_id=m.group_id where m.company_id=? and m.user_id=?",
            (rs, row) -> new Policy.Action(rs.getObject(1, UUID.class), rs.getString(2)), actor.companyId(), actor.userId());
        var fields = jdbc.query("select f.project_id, f.resource_type, f.field_key, f.operation, f.effect from permissions_field f join permissions_membership m on f.company_id=m.company_id and f.group_id=m.group_id where m.company_id=? and m.user_id=?",
            (rs, row) -> new Policy.Field(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)),
            actor.companyId(), actor.userId());
        return new Policy(actions, fields);
    }

    /** An older snapshot must not reuse pre-revocation group rows after waiting for the lock. */
    private void readCommittedWithLockTimeout() {
        if (!Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                connection.getTransactionIsolation() == Connection.TRANSACTION_READ_COMMITTED))) throw new PermissionDenied();
        jdbc.queryForObject("select set_config('lock_timeout', '5s', true)", String.class);
    }

    private IdentityAccess adapter() {
        var candidates = identity.stream().limit(2).toList();
        if (candidates.size() != 1) throw new PermissionDenied();
        return candidates.getFirst();
    }

    private static boolean isCompanyManager(AccessContext actor, Policy policy) {
        return policy.decide(actor, ResourceContext.company(actor.companyId()), MANAGE, Set.of()).allowed();
    }

    private static void companyManager(AccessContext actor, Policy policy) {
        if (!isCompanyManager(actor, policy)) throw new PermissionDenied();
    }

    private static void administer(AccessContext actor, Policy policy, ResourceContext resource) {
        if (!actor.companyId().equals(resource.companyId())) throw new PermissionDenied();
        if (!isCompanyManager(actor, policy) && (resource.projectId() == null
                || !policy.decide(actor, resource, MANAGE, Set.of()).allowed())) throw new PermissionDenied();
    }

    /**
     * Runs after the change under the exclusive revision lock; a denial rolls the whole transaction back.
     * Only accounts the identity adapter reports as active count: neither a group without a real member nor a
     * deactivated account keeps the company administration alive.
     */
    private void companyManagerRemains(AccessContext actor) {
        if (companyManagers(actor.companyId()).stream().noneMatch(user -> adapter().userActive(actor.companyId(), user))) throw new PermissionDenied();
    }

    private List<UUID> companyManagers(UUID companyId) {
        return jdbc.queryForList("select distinct m.user_id from permissions_membership m join permissions_action a on a.company_id = m.company_id and a.group_id = m.group_id where a.company_id = ? and a.project_id is null and a.action_key = ? order by m.user_id",
            UUID.class, companyId, MANAGE);
    }

    private void group(AccessContext actor, UUID groupId) {
        if (groupId == null || jdbc.queryForObject("select count(*) from permissions_group where company_id=? and id=?",
                Integer.class, actor.companyId(), groupId) != 1) throw new PermissionDenied();
    }

    private void changed(AccessContext actor, String operation, UUID group, UUID subject, UUID project,
            String action, String type, String field, String effect) {
        changed(actor.companyId(), actor.userId(), operation, group, subject, project, action, type, field, effect);
    }

    private void changed(UUID company, UUID actor, String operation, UUID group, UUID subject, UUID project,
            String action, String type, String field, String effect) {
        long version = jdbc.queryForObject("update permissions_revision set revision=revision+1 where company_id=? returning revision",
            Long.class, company);
        jdbc.update("insert into permissions_audit(company_id, id, actor_id, operation, group_id, subject_id, project_id, action_key, resource_type, field_key, effect, rights_version) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            company, UUID.randomUUID(), actor, operation, group, subject, project, action, type, field, effect, version);
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
