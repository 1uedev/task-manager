package taskmanager.tags.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The tables of the tags module. Nobody else reads them. */
@Repository
class TagRepository {
    private static final String COLUMNS = "id, company_id, project_id, name, normalized, description, color, archived, merged_into, "
        + "version, created_at, created_by, updated_at, updated_by";
    private static final String TAG_COLUMNS = "t.id, t.company_id, t.project_id, t.name, t.normalized, t.description, t.color, "
        + "t.archived, t.merged_into, t.version, t.created_at, t.created_by, t.updated_at, t.updated_by";

    /**
     * The catalog condition in the form of the unique index {@code tag_name_per_catalog}, so PostgreSQL can use it with a
     * parameter (hint H5 of the TASK-028 review); the parameter is {@code null} for the company catalog. Since TASK-072 the
     * index holds only tags that are not merged, so every query that should use it also says {@code merged_into is null}.
     */
    private static final String IN_CATALOG = "coalesce(project_id, '00000000-0000-0000-0000-000000000000'::uuid)"
        + " = coalesce(cast(? as uuid), '00000000-0000-0000-0000-000000000000'::uuid)";

    private final JdbcClient jdbc;

    TagRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Tag map(ResultSet rs, int row) throws SQLException {
        return new Tag(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getObject("project_id", UUID.class),
            rs.getString("name"), rs.getString("normalized"), rs.getString("description"), rs.getString("color"),
            rs.getBoolean("archived"), rs.getObject("merged_into", UUID.class), rs.getLong("version"),
            rs.getTimestamp("created_at").toInstant(), rs.getObject("created_by", UUID.class),
            rs.getTimestamp("updated_at").toInstant(), rs.getObject("updated_by", UUID.class));
    }

    /** A {@code like} pattern that finds the text anywhere in a normalized name; {@code null} for no search. */
    static String contains(String search) {
        return search == null ? null : "%" + escape(search) + "%";
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * One page of a catalog, ordered by the normalized name: the company catalog for {@code projectId == null}. Merged tags
     * are not part of a catalog. The search text is found anywhere in the normalized name, through the trigram index
     * (TASK-072); {@code after} is the normalized name of the last tag of the previous page.
     */
    List<Tag> catalog(UUID companyId, UUID projectId, String search, boolean includeArchived, String after, int limit) {
        return jdbc.sql("select " + COLUMNS + " from tag where company_id = ? and " + IN_CATALOG + " and merged_into is null"
                + " and (? or not archived) and (cast(? as text) is null or normalized like cast(? as text) escape '\\')"
                + " and (cast(? as text) is null or normalized > cast(? as text)) order by normalized limit ?")
            .params(companyId, projectId, includeArchived, contains(search), contains(search), after, after, limit)
            .query(TagRepository::map).list();
    }

    /**
     * Up to {@code limit} tags for the suggestions: not archived, not merged, from the company catalog and, when
     * {@code projectId} is given, from its catalog. Names that start with the text come first, then names with a word that
     * starts with it, then names that contain it; within each, by name.
     */
    List<Tag> suggestions(UUID companyId, UUID projectId, String search, int limit) {
        String starts = search == null ? null : escape(search) + "%";
        String word = search == null ? null : "% " + escape(search) + "%";
        return jdbc.sql("select " + COLUMNS + " from tag where company_id = ? and (project_id is null or project_id = ?)"
                + " and merged_into is null and not archived and (cast(? as text) is null or normalized like cast(? as text) escape '\\')"
                + " order by case when cast(? as text) is null or normalized like cast(? as text) escape '\\' then 0"
                + " when normalized like cast(? as text) escape '\\' then 1 else 2 end, normalized, project_id nulls first, id limit ?")
            .params(companyId, projectId, contains(search), contains(search), starts, starts, word, limit)
            .query(TagRepository::map).list();
    }

    Optional<Tag> find(UUID companyId, UUID id) {
        return jdbc.sql("select " + COLUMNS + " from tag where company_id = ? and id = ?").params(companyId, id)
            .query(TagRepository::map).optional();
    }

    /** The tags among {@code ids} in the company catalog or in the catalog of {@code projectId}, merged ones included. */
    List<Tag> lookup(UUID companyId, UUID projectId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select " + COLUMNS + " from tag where company_id = :company and (project_id is null or project_id = :project)"
                + " and id in (:ids) order by normalized, id")
            .param("company", companyId).param("project", projectId).param("ids", List.copyOf(ids))
            .query(TagRepository::map).list();
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

    /** Finds the tag of the catalog with this normalized name and locks it shared. Merged tags have no name any more. */
    Optional<Tag> lockByName(UUID companyId, UUID projectId, String normalized) {
        return jdbc.sql("select " + COLUMNS + " from tag where company_id = ? and " + IN_CATALOG + " and merged_into is null"
                + " and normalized = ? for share")
            .params(companyId, projectId, normalized).query(TagRepository::map).optional();
    }

    boolean nameTaken(UUID companyId, UUID projectId, String normalized, UUID except) {
        return jdbc.sql("select exists (select 1 from tag where company_id = ? and " + IN_CATALOG + " and merged_into is null"
                + " and normalized = ? and id <> ?)")
            .params(companyId, projectId, normalized, except).query(Boolean.class).single();
    }

    /**
     * Inserts the tag unless its catalog already holds the name. A concurrent insert of the same name makes this wait for
     * its commit; {@code false} then means the name is taken.
     */
    boolean insert(Tag tag) {
        return jdbc.sql("insert into tag (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict do nothing")
            .params(tag.id(), tag.companyId(), tag.projectId(), tag.name(), tag.normalized(), tag.description(), tag.color(),
                tag.archived(), tag.mergedInto(), tag.version(), ts(tag.createdAt()), tag.createdBy(), ts(tag.updatedAt()), tag.updatedBy())
            .update() == 1;
    }

    void update(Tag tag) {
        jdbc.sql("update tag set name = ?, normalized = ?, description = ?, color = ?, archived = ?, version = ?, updated_at = ?,"
                + " updated_by = ? where id = ?")
            .params(tag.name(), tag.normalized(), tag.description(), tag.color(), tag.archived(), tag.version(), ts(tag.updatedAt()),
                tag.updatedBy(), tag.id())
            .update();
    }

    // ------------------------------------------------------------------------------------------------ merging

    /** The number of assignments of the tag to tickets and projects together. */
    long assignments(UUID tagId) {
        return jdbc.sql("select (select count(*) from tag_ticket where tag_id = ?) + (select count(*) from tag_project where tag_id = ?)")
            .params(tagId, tagId).query(Long.class).single();
    }

    /**
     * Moves every assignment of {@code source} to {@code target}: assignments that the target already has are removed, the
     * others change their tag. The ticket history is not touched (rule 8 of TASK-072).
     */
    void moveAssignments(UUID source, UUID target) {
        jdbc.sql("delete from tag_ticket a where a.tag_id = ? and exists (select 1 from tag_ticket b where b.ticket_id = a.ticket_id and b.tag_id = ?)")
            .params(source, target).update();
        jdbc.sql("update tag_ticket set tag_id = ? where tag_id = ?").params(target, source).update();
        jdbc.sql("delete from tag_project a where a.tag_id = ? and exists (select 1 from tag_project b where b.project_id = a.project_id and b.tag_id = ?)")
            .params(source, target).update();
        jdbc.sql("update tag_project set tag_id = ? where tag_id = ?").params(target, source).update();
    }

    /**
     * Marks the source as merged into the target, which frees its name, and lets every tag that was merged into the source
     * before point to the target, so a merged tag always points to a tag that is not merged.
     */
    void markMerged(UUID source, UUID target, long version, Instant now, UUID actor) {
        jdbc.sql("update tag set merged_into = ?, merged_at = ?, version = ?, updated_at = ?, updated_by = ? where id = ?")
            .params(target, ts(now), version, ts(now), actor, source).update();
        jdbc.sql("update tag set merged_into = ? where merged_into = ?").params(target, source).update();
    }

    // ------------------------------------------------------------------------------------------------ usage

    /** Per tag among {@code tags}, the number of its tickets in the given projects. */
    Map<UUID, Long> ticketUsage(Collection<UUID> tags, Collection<UUID> projects) {
        if (tags.isEmpty() || projects.isEmpty()) {
            return Map.of();
        }
        return counts(jdbc.sql("select tag_id, count(*) as n from tag_ticket where tag_id in (:tags) and project_id in (:projects) group by tag_id")
            .param("tags", List.copyOf(tags)).param("projects", List.copyOf(projects)));
    }

    /** Per tag among {@code tags}, the number of its projects among the given ones, or among all for {@code null}. */
    Map<UUID, Long> projectUsage(Collection<UUID> tags, Collection<UUID> projects) {
        if (tags.isEmpty() || (projects != null && projects.isEmpty())) {
            return Map.of();
        }
        var statement = projects == null
            ? jdbc.sql("select tag_id, count(*) as n from tag_project where tag_id in (:tags) group by tag_id")
            : jdbc.sql("select tag_id, count(*) as n from tag_project where tag_id in (:tags) and project_id in (:projects) group by tag_id")
                .param("projects", List.copyOf(projects));
        return counts(statement.param("tags", List.copyOf(tags)));
    }

    private static Map<UUID, Long> counts(JdbcClient.StatementSpec statement) {
        Map<UUID, Long> counts = new HashMap<>();
        statement.query((rs, row) -> counts.put(rs.getObject("tag_id", UUID.class), rs.getLong("n"))).list();
        return counts;
    }

    // ------------------------------------------------------------------------------------------------ assignments

    List<Tag> ticketTags(UUID ticketId) {
        return jdbc.sql("select " + TAG_COLUMNS + " from tag_ticket a join tag t on t.id = a.tag_id where a.ticket_id = ? order by t.normalized, t.id")
            .param(ticketId).query(TagRepository::map).list();
    }

    /**
     * Serializes the assignments to one ticket or project until the end of the transaction, so the limit of tags per object
     * holds under concurrent assignments (TASK-072). The lock is taken after the tag and object locks of DEC-068 and only
     * the insert follows it.
     */
    void lockObject(String kind, UUID objectId) {
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(?, 0))").param(kind + ":" + objectId).query((rs, row) -> 1).list();
    }

    boolean ticketHas(UUID ticketId, UUID tagId) {
        return jdbc.sql("select exists (select 1 from tag_ticket where ticket_id = ? and tag_id = ?)").params(ticketId, tagId)
            .query(Boolean.class).single();
    }

    long ticketCount(UUID ticketId) {
        return jdbc.sql("select count(*) from tag_ticket where ticket_id = ?").param(ticketId).query(Long.class).single();
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

    boolean projectHas(UUID projectId, UUID tagId) {
        return jdbc.sql("select exists (select 1 from tag_project where project_id = ? and tag_id = ?)").params(projectId, tagId)
            .query(Boolean.class).single();
    }

    long projectCount(UUID projectId) {
        return jdbc.sql("select count(*) from tag_project where project_id = ?").param(projectId).query(Long.class).single();
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
