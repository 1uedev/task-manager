package taskmanager.requirements.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** The tables of the requirements module. Nobody else reads them. */
@Repository
class RequirementRepository {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String REQUIREMENT = "r.id, r.company_id, r.project_id, r.number, r.status, r.owner_id, r.current_revision, "
        + "r.approved_revision, r.next_criterion, r.created_at, r.created_by, r.updated_at, r.updated_by, r.version";
    private static final String REVISION = "v.requirement_id, v.revision, v.title, v.description, v.priority, v.criteria, v.opened_at, "
        + "v.opened_by, v.saved_at, v.saved_by, v.approved_at, v.approved_by, v.custom";

    private final JdbcClient jdbc;

    RequirementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Requirement(UUID id, UUID companyId, UUID projectId, long number, String status, UUID ownerId, int currentRevision,
            Integer approvedRevision, int nextCriterion, Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy, long version) {
        Requirement with(String newStatus, UUID newOwner, int revision, Integer approved, int next, Instant at, UUID by) {
            return new Requirement(id, companyId, projectId, number, newStatus, newOwner, revision, approved, next, createdAt, createdBy, at, by,
                version + 1);
        }
    }

    /** One criterion with its fixed number inside the requirement. */
    record Criterion(int id, String text) {
    }

    /**
     * The content of one revision. {@code custom} holds the values of the custom fields of requirements (TASK-098) by their
     * key in their canonical text; a field without a value has no entry.
     */
    record Revision(UUID requirementId, int revision, String title, String description, String priority, List<Criterion> criteria,
            Instant openedAt, UUID openedBy, Instant savedAt, UUID savedBy, Instant approvedAt, UUID approvedBy, Map<String, String> custom) {
        Revision {
            custom = Collections.unmodifiableMap(new TreeMap<>(custom == null ? Map.of() : custom));
        }

        boolean approved() {
            return approvedAt != null;
        }

        /** The same revision approved by {@code by} at {@code at}. */
        Revision approvedBy(Instant at, UUID by) {
            return new Revision(requirementId, revision, title, description, priority, criteria, openedAt, openedBy, savedAt, savedBy, at, by, custom);
        }
    }

    /** A requirement of a list with the title and priority of its current revision. */
    record Row(Requirement requirement, String title, String priority) {
    }

    record Event(long id, int revision, String kind, String field, Integer criterion, String fromValue, String toValue, String reason,
            UUID actorId, Instant occurredAt) {
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Requirement requirement(ResultSet rs) throws SQLException {
        return new Requirement(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getObject("project_id", UUID.class),
            rs.getLong("number"), rs.getString("status"), rs.getObject("owner_id", UUID.class), rs.getInt("current_revision"),
            integer(rs, "approved_revision"), rs.getInt("next_criterion"), instant(rs.getTimestamp("created_at")), rs.getObject("created_by", UUID.class),
            instant(rs.getTimestamp("updated_at")), rs.getObject("updated_by", UUID.class), rs.getLong("version"));
    }

    private static Revision revision(ResultSet rs) throws SQLException {
        return new Revision(rs.getObject("requirement_id", UUID.class), rs.getInt("revision"), rs.getString("title"), rs.getString("description"),
            rs.getString("priority"), criteria(rs.getString("criteria")), instant(rs.getTimestamp("opened_at")), rs.getObject("opened_by", UUID.class),
            instant(rs.getTimestamp("saved_at")), rs.getObject("saved_by", UUID.class), instant(rs.getTimestamp("approved_at")),
            rs.getObject("approved_by", UUID.class), custom(rs.getString("custom")));
    }

    /**
     * The largest stored form of the custom values of one revision in bytes, as V0061 checks it (TASK-100, DEC-130). It holds
     * every combination the configuration allows: 30 fields of 20000 code points with up to 6 bytes each as JSON.
     */
    static final int CUSTOM_MAX_BYTES = 4 * 1024 * 1024;

    /** Whether the stored form of the values stays within {@link #CUSTOM_MAX_BYTES}. */
    static boolean customFits(Map<String, String> values) {
        return custom(values).getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= CUSTOM_MAX_BYTES;
    }

    static Map<String, String> custom(String json) {
        Map<String, String> result = new TreeMap<>();
        JSON.readTree(json).properties().forEach(entry -> result.put(entry.getKey(), entry.getValue().asString()));
        return result;
    }

    static String custom(Map<String, String> values) {
        ObjectNode object = JSON.createObjectNode();
        new TreeMap<>(values).forEach(object::put);
        return JSON.writeValueAsString(object);
    }

    static List<Criterion> criteria(String json) {
        List<Criterion> result = new ArrayList<>();
        for (JsonNode node : JSON.readTree(json)) {
            result.add(new Criterion(node.get("id").asInt(), node.get("text").asString()));
        }
        return List.copyOf(result);
    }

    static String criteria(List<Criterion> criteria) {
        ArrayNode array = JSON.createArrayNode();
        criteria.forEach(criterion -> array.addObject().put("id", criterion.id()).put("text", criterion.text()));
        return JSON.writeValueAsString(array);
    }

    /** The next number of a requirement in the project; the counter row stays locked until the commit. */
    long nextNumber(UUID projectId) {
        return jdbc.sql("""
                insert into requirement_counter (project_id, last_number) values (?, 1)
                on conflict (project_id) do update set last_number = requirement_counter.last_number + 1
                returning last_number
                """).param(projectId).query(Long.class).single();
    }

    /** The identifiers of every requirement of the company (TASK-103). */
    List<UUID> ids(UUID companyId) {
        return jdbc.sql("select id from requirement where company_id = ? order by id").param(companyId).query(UUID.class).list();
    }

    Optional<Requirement> find(UUID companyId, UUID id) {
        return jdbc.sql("select " + REQUIREMENT + " from requirement r where r.company_id = ? and r.id = ?").params(companyId, id)
            .query((rs, n) -> requirement(rs)).optional();
    }

    Optional<Requirement> lock(UUID companyId, UUID id) {
        return jdbc.sql("select " + REQUIREMENT + " from requirement r where r.company_id = ? and r.id = ? for update").params(companyId, id)
            .query((rs, n) -> requirement(rs)).optional();
    }

    /** The requirement held {@code FOR SHARE}: a link of another module leaves the requirement and its version as they are. */
    Optional<Requirement> lockShared(UUID companyId, UUID id) {
        return jdbc.sql("select " + REQUIREMENT + " from requirement r where r.company_id = ? and r.id = ? for share").params(companyId, id)
            .query((rs, n) -> requirement(rs)).optional();
    }

    Optional<Revision> revision(UUID requirementId, int revision) {
        return jdbc.sql("select " + REVISION + " from requirement_revision v where v.requirement_id = ? and v.revision = ?")
            .params(requirementId, revision).query((rs, n) -> revision(rs)).optional();
    }

    List<Revision> revisions(UUID requirementId) {
        return jdbc.sql("select " + REVISION + " from requirement_revision v where v.requirement_id = ? order by v.revision")
            .param(requirementId).query((rs, n) -> revision(rs)).list();
    }

    /**
     * One page of the requirements of a project, ordered by number after {@code after}. A {@code null} filter does not
     * filter; without a status filter discarded requirements are left out. {@code text} is looked for in the current title.
     */
    List<Row> list(UUID companyId, UUID projectId, String status, String priority, UUID owner, String text, long after, int limit) {
        StringBuilder sql = new StringBuilder("select " + REQUIREMENT + ", v.title, v.priority from requirement r "
            + "join requirement_revision v on v.requirement_id = r.id and v.revision = r.current_revision "
            + "where r.company_id = ? and r.project_id = ? and r.number > ?");
        List<Object> params = new ArrayList<>(List.of(companyId, projectId, after));
        if (status == null) {
            sql.append(" and r.status <> 'discarded'");
        } else {
            sql.append(" and r.status = ?");
            params.add(status);
        }
        if (priority != null) {
            sql.append(" and v.priority = ?");
            params.add(priority);
        }
        if (owner != null) {
            sql.append(" and r.owner_id = ?");
            params.add(owner);
        }
        if (text != null) {
            sql.append(" and strpos(lower(v.title), lower(?)) > 0");
            params.add(text);
        }
        sql.append(" order by r.number limit ?");
        params.add(limit);
        return jdbc.sql(sql.toString()).params(params)
            .query((rs, n) -> new Row(requirement(rs), rs.getString("title"), rs.getString("priority"))).list();
    }

    void insert(Requirement r) {
        jdbc.sql("""
                insert into requirement (id, company_id, project_id, number, status, owner_id, current_revision, approved_revision,
                    next_criterion, created_at, created_by, updated_at, updated_by, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(r.id(), r.companyId(), r.projectId(), r.number(), r.status(), r.ownerId(), r.currentRevision(), r.approvedRevision(),
            r.nextCriterion(), ts(r.createdAt()), r.createdBy(), ts(r.updatedAt()), r.updatedBy(), r.version()).update();
    }

    void update(Requirement r) {
        jdbc.sql("""
                update requirement set status = ?, owner_id = ?, current_revision = ?, approved_revision = ?, next_criterion = ?,
                    updated_at = ?, updated_by = ?, version = ?
                where id = ?
                """).params(r.status(), r.ownerId(), r.currentRevision(), r.approvedRevision(), r.nextCriterion(), ts(r.updatedAt()), r.updatedBy(),
            r.version(), r.id()).update();
    }

    void insert(Revision v) {
        jdbc.sql("""
                insert into requirement_revision (requirement_id, revision, title, description, priority, criteria, opened_at, opened_by,
                    saved_at, saved_by, approved_at, approved_by, custom)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(v.requirementId(), v.revision(), v.title(), v.description(), v.priority(), criteria(v.criteria()), ts(v.openedAt()),
            v.openedBy(), ts(v.savedAt()), v.savedBy(), ts(v.approvedAt()), v.approvedBy(), custom(v.custom())).update();
    }

    /** Changes a revision that is not approved; an approved revision is never changed again. */
    void update(Revision v) {
        int changed = jdbc.sql("""
                update requirement_revision set title = ?, description = ?, priority = ?, criteria = ?, saved_at = ?, saved_by = ?,
                    approved_at = ?, approved_by = ?, custom = ?
                where requirement_id = ? and revision = ? and approved_at is null
                """).params(v.title(), v.description(), v.priority(), criteria(v.criteria()), ts(v.savedAt()), v.savedBy(), ts(v.approvedAt()),
            v.approvedBy(), custom(v.custom()), v.requirementId(), v.revision()).update();
        if (changed != 1) {
            throw new IllegalStateException("An approved revision is never changed");
        }
    }

    void event(UUID companyId, UUID requirementId, int revision, String kind, String field, Integer criterion, String from, String to,
            String reason, UUID actor, Instant at) {
        jdbc.sql("""
                insert into requirement_event (company_id, requirement_id, revision, kind, field, criterion, from_value, to_value, reason,
                    actor_id, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(companyId, requirementId, revision, kind, field, criterion, from, to, reason, actor, ts(at)).update();
    }

    List<Event> events(UUID requirementId) {
        return jdbc.sql("""
                select id, revision, kind, field, criterion, from_value, to_value, reason, actor_id, occurred_at
                from requirement_event where requirement_id = ? order by id
                """).param(requirementId)
            .query((rs, n) -> new Event(rs.getLong("id"), rs.getInt("revision"), rs.getString("kind"), rs.getString("field"),
                integer(rs, "criterion"), rs.getString("from_value"), rs.getString("to_value"), rs.getString("reason"),
                rs.getObject("actor_id", UUID.class), instant(rs.getTimestamp("occurred_at"))))
            .list();
    }

    // ------------------------------------------------------------------------------------------------ links (TASK-097)

    record TicketLink(UUID requirementId, UUID ticketId, int revision, Instant linkedAt, UUID linkedBy) {
    }

    record PageLink(UUID requirementId, UUID pageId, Instant linkedAt, UUID linkedBy) {
    }

    private static TicketLink ticketLink(ResultSet rs) throws SQLException {
        return new TicketLink(rs.getObject("requirement_id", UUID.class), rs.getObject("ticket_id", UUID.class), rs.getInt("revision"),
            instant(rs.getTimestamp("linked_at")), rs.getObject("linked_by", UUID.class));
    }

    List<TicketLink> ticketLinks(UUID requirementId) {
        return jdbc.sql("select requirement_id, ticket_id, revision, linked_at, linked_by from requirement_ticket_link where requirement_id = ?")
            .param(requirementId).query((rs, n) -> ticketLink(rs)).list();
    }

    /** The links of a ticket with requirements of the company. */
    List<TicketLink> linksOfTicket(UUID companyId, UUID ticketId) {
        return jdbc.sql("""
                select requirement_id, ticket_id, revision, linked_at, linked_by from requirement_ticket_link
                where company_id = ? and ticket_id = ?
                """).params(companyId, ticketId).query((rs, n) -> ticketLink(rs)).list();
    }

    Optional<TicketLink> ticketLink(UUID requirementId, UUID ticketId) {
        return jdbc.sql("""
                select requirement_id, ticket_id, revision, linked_at, linked_by from requirement_ticket_link
                where requirement_id = ? and ticket_id = ?
                """).params(requirementId, ticketId).query((rs, n) -> ticketLink(rs)).optional();
    }

    void insert(UUID companyId, TicketLink link) {
        jdbc.sql("""
                insert into requirement_ticket_link (requirement_id, ticket_id, company_id, revision, linked_at, linked_by)
                values (?, ?, ?, ?, ?, ?)
                """).params(link.requirementId(), link.ticketId(), companyId, link.revision(), ts(link.linkedAt()), link.linkedBy()).update();
    }

    void switchRevision(UUID requirementId, UUID ticketId, int revision) {
        jdbc.sql("update requirement_ticket_link set revision = ? where requirement_id = ? and ticket_id = ?").params(revision, requirementId, ticketId)
            .update();
    }

    void deleteTicketLink(UUID requirementId, UUID ticketId) {
        jdbc.sql("delete from requirement_ticket_link where requirement_id = ? and ticket_id = ?").params(requirementId, ticketId).update();
    }

    List<PageLink> pageLinks(UUID requirementId) {
        return jdbc.sql("select requirement_id, page_id, linked_at, linked_by from requirement_page_link where requirement_id = ? order by linked_at, page_id")
            .param(requirementId)
            .query((rs, n) -> new PageLink(rs.getObject("requirement_id", UUID.class), rs.getObject("page_id", UUID.class),
                instant(rs.getTimestamp("linked_at")), rs.getObject("linked_by", UUID.class)))
            .list();
    }

    boolean pageLinked(UUID requirementId, UUID pageId) {
        return jdbc.sql("select count(*) from requirement_page_link where requirement_id = ? and page_id = ?").params(requirementId, pageId)
            .query(Long.class).single() > 0;
    }

    void insert(UUID companyId, PageLink link) {
        jdbc.sql("insert into requirement_page_link (requirement_id, page_id, company_id, linked_at, linked_by) values (?, ?, ?, ?, ?)")
            .params(link.requirementId(), link.pageId(), companyId, ts(link.linkedAt()), link.linkedBy()).update();
    }

    void deletePageLink(UUID requirementId, UUID pageId) {
        jdbc.sql("delete from requirement_page_link where requirement_id = ? and page_id = ?").params(requirementId, pageId).update();
    }
}
