package taskmanager.projects.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import taskmanager.projects.ProjectConfiguration;
import tools.jackson.databind.json.JsonMapper;

/** The tables of the projects module. Nobody else reads them; other modules go through {@code ProjectAccess}. */
@Repository
class ProjectRepository {
    private static final String COLUMNS =
        "id, company_id, key, name, description, configuration_version, version, created_at, updated_at";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    ProjectRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Project map(ResultSet rs, int row) throws SQLException {
        return new Project(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getString("key"),
            rs.getString("name"), rs.getString("description"), rs.getLong("configuration_version"), rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    /** Reads and locks the project row of the company until the end of the transaction. */
    Optional<Project> lock(UUID companyId, UUID id, boolean exclusive) {
        return jdbc.sql("select " + COLUMNS + " from project where id = ? and company_id = ? " + (exclusive ? "for update" : "for share"))
            .params(id, companyId).query(ProjectRepository::map).optional();
    }

    /** The projects of the company, each locked {@code FOR SHARE} in the order of their identifiers. */
    List<Project> lockAll(UUID companyId) {
        return jdbc.sql("select " + COLUMNS + " from project where company_id = ? order by id for share")
            .param(companyId).query(ProjectRepository::map).list();
    }

    /** The projects of the company without locks, for a rebuild of the search projection. */
    List<Project> all(UUID companyId) {
        return jdbc.sql("select " + COLUMNS + " from project where company_id = ? order by id").param(companyId)
            .query(ProjectRepository::map).list();
    }

    /** The identifiers of the projects the account is a member of, without locks. */
    List<UUID> memberships(UUID companyId, UUID accountId) {
        return jdbc.sql("""
                select p.id from project p join project_member m on m.project_id = p.id
                where p.company_id = ? and m.account_id = ? order by p.id
                """).params(companyId, accountId).query(UUID.class).list();
    }

    boolean isMember(UUID projectId, UUID accountId) {
        return jdbc.sql("select exists(select 1 from project_member where project_id = ? and account_id = ?)")
            .params(projectId, accountId).query(Boolean.class).single();
    }

    Optional<String> key(UUID projectId) {
        return jdbc.sql("select key from project where id = ?").param(projectId).query(String.class).optional();
    }

    boolean keyTaken(UUID companyId, String key) {
        return jdbc.sql("select exists(select 1 from project where company_id = ? and key = ?)")
            .params(companyId, key).query(Boolean.class).single();
    }

    void insert(Project project, UUID actor) {
        jdbc.sql("""
                insert into project (id, company_id, key, name, description, configuration_version, version,
                    created_at, created_by, updated_at, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
            .params(project.id(), project.companyId(), project.key(), project.name(), project.description(),
                project.configurationVersion(), project.version(), ts(project.createdAt()), actor, ts(project.updatedAt()), actor)
            .update();
    }

    void update(UUID id, String name, String description, Instant now, UUID actor) {
        jdbc.sql("update project set name = ?, description = ?, version = version + 1, updated_at = ?, updated_by = ? where id = ?")
            .params(name, description, ts(now), actor, id).update();
    }

    void touch(UUID id, Instant now, UUID actor) {
        jdbc.sql("update project set version = version + 1, updated_at = ?, updated_by = ? where id = ?")
            .params(ts(now), actor, id).update();
    }

    void insertConfiguration(UUID projectId, long version, ProjectConfiguration configuration, Instant now, UUID actor) {
        jdbc.sql("insert into project_configuration (project_id, version, document, created_at, created_by) values (?, ?, cast(? as jsonb), ?, ?)")
            .params(projectId, version, JSON.writeValueAsString(configuration), ts(now), actor).update();
        jdbc.sql("update project set configuration_version = ? where id = ?").params(version, projectId).update();
    }

    Optional<ProjectConfiguration> configuration(UUID projectId, long version) {
        return jdbc.sql("select document::text from project_configuration where project_id = ? and version = ?")
            .params(projectId, version).query(String.class).optional()
            .map(document -> JSON.readValue(document, ProjectConfiguration.class));
    }

    Optional<Long> configurationVersion(UUID projectId) {
        return jdbc.sql("select configuration_version from project where id = ?").param(projectId).query(Long.class).optional();
    }

    List<Member> members(UUID projectId) {
        return jdbc.sql("select account_id, added_at, added_by from project_member where project_id = ? order by added_at, account_id")
            .param(projectId)
            .query((rs, row) -> new Member(rs.getObject("account_id", UUID.class), rs.getTimestamp("added_at").toInstant(),
                rs.getObject("added_by", UUID.class)))
            .list();
    }

    /** True if the account was not a member before. */
    boolean addMember(UUID projectId, UUID accountId, Instant now, UUID actor) {
        return jdbc.sql("insert into project_member (project_id, account_id, added_at, added_by) values (?, ?, ?, ?) on conflict do nothing")
            .params(projectId, accountId, ts(now), actor).update() == 1;
    }

    /** True if the account was a member before. */
    boolean removeMember(UUID projectId, UUID accountId) {
        return jdbc.sql("delete from project_member where project_id = ? and account_id = ?").params(projectId, accountId).update() == 1;
    }

    void audit(UUID projectId, UUID actor, String event, String detail, Instant now) {
        jdbc.sql("insert into project_audit_event (occurred_at, project_id, actor_account_id, event, detail) values (?, ?, ?, ?, ?)")
            .params(ts(now), projectId, actor, event, detail).update();
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
