package taskmanager.tags.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The tables of the tags module. Nobody else reads them. */
@Repository
class TagRepository {
    private static final String COLUMNS =
        "id, company_id, project_id, name, normalized, archived, version, created_at, created_by, updated_at, updated_by";
    private static final String TAG_COLUMNS = "t.id, t.company_id, t.project_id, t.name, t.normalized, t.archived, t.version, "
        + "t.created_at, t.created_by, t.updated_at, t.updated_by";

    /**
     * The catalog condition in the form of the unique index {@code tag_name_per_catalog}, so PostgreSQL can use it with a
     * parameter (hint H5 of the TASK-028 review); the parameter is {@code null} for the company catalog.
     */
    private static final String IN_CATALOG = "coalesce(project_id, '00000000-0000-0000-0000-000000000000'::uuid)"
        + " = coalesce(cast(? as uuid), '00000000-0000-0000-0000-000000000000'::uuid)";

    private final JdbcClient jdbc;

    TagRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Tag map(ResultSet rs, int row) throws SQLException {
        return new Tag(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getObject("project_id", UUID.class),
            rs.getString("name"), rs.getString("normalized"), rs.getBoolean("archived"), rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(), rs.getObject("created_by", UUID.class),
            rs.getTimestamp("updated_at").toInstant(), rs.getObject("updated_by", UUID.class));
    }

    /**
     * One page of a catalog, ordered by the normalized name: the company catalog for {@code projectId == null}. The search
     * text is compared with the normalized name; {@code after} is the normalized name of the last tag of the previous page.
     */
    List<Tag> catalog(UUID companyId, UUID projectId, String search, boolean includeArchived, String after, int limit) {
        return jdbc.sql("select " + COLUMNS + " from tag where company_id = ? and " + IN_CATALOG
                + " and (? or not archived) and (cast(? as text) is null or position(cast(? as text) in normalized) > 0)"
                + " and (cast(? as text) is null or normalized > cast(? as text)) order by normalized limit ?")
            .params(companyId, projectId, includeArchived, search, search, after, after, limit)
            .query(TagRepository::map).list();
    }

    Optional<Tag> find(UUID companyId, UUID id) {
        return jdbc.sql("select " + COLUMNS + " from tag where company_id = ? and id = ?").params(companyId, id)
            .query(TagRepository::map).optional();
    }

    /**
     * Reads the tag of exactly this catalog and locks it exclusively until the end of the transaction. A tag of another
     * catalog is neither returned nor locked, so it answers like an unknown one without waiting (review F3 of TASK-028).
     */
    Optional<Tag> lockForChange(UUID companyId, UUID id, UUID projectId) {
        return jdbc.sql("select " + COLUMNS + " from tag where company_id = ? and id = ? and " + IN_CATALOG + " for update")
            .params(companyId, id, projectId).query(TagRepository::map).optional();
    }

    /**
     * Reads a tag of the company catalog, or of the catalog of {@code projectId} when it is given, and locks it shared until
     * the end of the transaction. Tags of other catalogs are neither returned nor locked (review F3 of TASK-028).
     */
    Optional<Tag> lockForAssignment(UUID companyId, UUID id, UUID projectId) {
        return jdbc.sql("select " + COLUMNS + " from tag where company_id = ? and id = ? and (project_id is null or project_id = ?) for share")
            .params(companyId, id, projectId).query(TagRepository::map).optional();
    }

    /** Finds the tag of the catalog with this normalized name and locks it shared. */
    Optional<Tag> lockByName(UUID companyId, UUID projectId, String normalized) {
        return jdbc.sql("select " + COLUMNS + " from tag where company_id = ? and " + IN_CATALOG + " and normalized = ? for share")
            .params(companyId, projectId, normalized).query(TagRepository::map).optional();
    }

    boolean nameTaken(UUID companyId, UUID projectId, String normalized, UUID except) {
        return jdbc.sql("select exists (select 1 from tag where company_id = ? and " + IN_CATALOG + " and normalized = ? and id <> ?)")
            .params(companyId, projectId, normalized, except).query(Boolean.class).single();
    }

    /**
     * Inserts the tag unless its catalog already holds the name. A concurrent insert of the same name makes this wait for
     * its commit; {@code false} then means the name is taken.
     */
    boolean insert(Tag tag) {
        return jdbc.sql("insert into tag (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict do nothing")
            .params(tag.id(), tag.companyId(), tag.projectId(), tag.name(), tag.normalized(), tag.archived(), tag.version(),
                ts(tag.createdAt()), tag.createdBy(), ts(tag.updatedAt()), tag.updatedBy())
            .update() == 1;
    }

    void update(Tag tag) {
        jdbc.sql("update tag set name = ?, normalized = ?, archived = ?, version = ?, updated_at = ?, updated_by = ? where id = ?")
            .params(tag.name(), tag.normalized(), tag.archived(), tag.version(), ts(tag.updatedAt()), tag.updatedBy(), tag.id())
            .update();
    }

    List<Tag> ticketTags(UUID ticketId) {
        return jdbc.sql("select " + TAG_COLUMNS + " from tag_ticket a join tag t on t.id = a.tag_id where a.ticket_id = ? order by t.normalized, t.id")
            .param(ticketId).query(TagRepository::map).list();
    }

    boolean assignTicket(UUID ticketId, UUID tagId, UUID projectId, Instant now, UUID actor) {
        return jdbc.sql("insert into tag_ticket (ticket_id, tag_id, project_id, assigned_at, assigned_by) values (?, ?, ?, ?, ?) on conflict do nothing")
            .params(ticketId, tagId, projectId, ts(now), actor).update() == 1;
    }

    boolean unassignTicket(UUID ticketId, UUID tagId) {
        return jdbc.sql("delete from tag_ticket where ticket_id = ? and tag_id = ?").params(ticketId, tagId).update() == 1;
    }

    List<Tag> projectTags(UUID projectId) {
        return jdbc.sql("select " + TAG_COLUMNS + " from tag_project a join tag t on t.id = a.tag_id where a.project_id = ? order by t.normalized, t.id")
            .param(projectId).query(TagRepository::map).list();
    }

    boolean assignProject(UUID projectId, UUID tagId, Instant now, UUID actor) {
        return jdbc.sql("insert into tag_project (project_id, tag_id, assigned_at, assigned_by) values (?, ?, ?, ?) on conflict do nothing")
            .params(projectId, tagId, ts(now), actor).update() == 1;
    }

    boolean unassignProject(UUID projectId, UUID tagId) {
        return jdbc.sql("delete from tag_project where project_id = ? and tag_id = ?").params(projectId, tagId).update() == 1;
    }

    void audit(UUID companyId, UUID tagId, String operation, String targetType, UUID targetId, String detail, UUID actor, Instant now) {
        jdbc.sql("""
                insert into tag_audit_event (company_id, tag_id, operation, target_type, target_id, detail, actor_account_id, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """).params(companyId, tagId, operation, targetType, targetId, detail, actor, ts(now)).update();
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
