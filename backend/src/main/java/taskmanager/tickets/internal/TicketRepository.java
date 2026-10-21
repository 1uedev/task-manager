package taskmanager.tickets.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The tables of the tickets module. Nobody else reads them. */
@Repository
class TicketRepository {
    private static final String COLUMNS = "id, company_id, project_id, number, title, description, type, status, assignee, "
        + "configuration_version, version, created_at, created_by, updated_at, updated_by";

    private final JdbcClient jdbc;

    TicketRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static Ticket map(ResultSet rs, int row) throws SQLException {
        return new Ticket(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class),
            rs.getObject("project_id", UUID.class), rs.getLong("number"), rs.getString("title"), rs.getString("description"),
            rs.getString("type"), rs.getString("status"), rs.getObject("assignee", UUID.class), rs.getLong("configuration_version"),
            rs.getLong("version"), rs.getTimestamp("created_at").toInstant(), rs.getObject("created_by", UUID.class),
            rs.getTimestamp("updated_at").toInstant(), rs.getObject("updated_by", UUID.class));
    }

    /** One change of one field in the history. */
    record Change(long version, Instant occurredAt, UUID actor, String field, String oldValue, String newValue) {
    }

    /** The tickets of the project, newest first, below the number {@code before} if given. */
    List<Ticket> list(UUID companyId, UUID projectId, int limit, Long before) {
        return jdbc.sql("select " + COLUMNS + " from ticket where company_id = ? and project_id = ? and number < ? order by number desc limit ?")
            .params(companyId, projectId, before == null ? Long.MAX_VALUE : before, limit).query(TicketRepository::map).list();
    }

    /** Every ticket of the company, for a rebuild of the search projection. */
    List<Ticket> all(UUID companyId) {
        return jdbc.sql("select " + COLUMNS + " from ticket where company_id = ? order by id").param(companyId)
            .query(TicketRepository::map).list();
    }

    Optional<Ticket> find(UUID companyId, UUID projectId, long number) {
        return jdbc.sql("select " + COLUMNS + " from ticket where company_id = ? and project_id = ? and number = ?")
            .params(companyId, projectId, number).query(TicketRepository::map).optional();
    }

    /** Reads and locks the ticket row until the end of the transaction. */
    Optional<Ticket> lock(UUID companyId, UUID projectId, long number) {
        return jdbc.sql("select " + COLUMNS + " from ticket where company_id = ? and project_id = ? and number = ? for update")
            .params(companyId, projectId, number).query(TicketRepository::map).optional();
    }

    /** Locks the ticket row {@code FOR SHARE} until the end of the transaction and returns its version. */
    long lockShared(UUID ticketId) {
        return jdbc.sql("select version from ticket where id = ? for share").param(ticketId).query(Long.class).single();
    }

    /** Hands out the next number of the project; the counter row stays locked until the commit. */
    long nextNumber(UUID projectId) {
        return jdbc.sql("""
                insert into ticket_counter (project_id, last_number) values (?, 1)
                on conflict (project_id) do update set last_number = ticket_counter.last_number + 1
                returning last_number
                """).param(projectId).query(Long.class).single();
    }

    void insert(Ticket ticket) {
        jdbc.sql("insert into ticket (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            .params(ticket.id(), ticket.companyId(), ticket.projectId(), ticket.number(), ticket.title(), ticket.description(),
                ticket.type(), ticket.status(), ticket.assignee(), ticket.configurationVersion(), ticket.version(),
                ts(ticket.createdAt()), ticket.createdBy(), ts(ticket.updatedAt()), ticket.updatedBy())
            .update();
    }

    void update(Ticket ticket) {
        jdbc.sql("""
                update ticket set title = ?, description = ?, type = ?, status = ?, assignee = ?, configuration_version = ?,
                    version = ?, updated_at = ?, updated_by = ?
                where id = ?
                """)
            .params(ticket.title(), ticket.description(), ticket.type(), ticket.status(), ticket.assignee(),
                ticket.configurationVersion(), ticket.version(), ts(ticket.updatedAt()), ticket.updatedBy(), ticket.id())
            .update();
    }

    void change(UUID ticketId, long version, Instant now, UUID actor, String field, String oldValue, String newValue) {
        jdbc.sql("""
                insert into ticket_change (ticket_id, version, occurred_at, actor_account_id, field, old_value, new_value)
                values (?, ?, ?, ?, ?, ?, ?)
                """).params(ticketId, version, ts(now), actor, field, oldValue, newValue).update();
    }

    List<Change> changes(UUID ticketId) {
        return jdbc.sql("select version, occurred_at, actor_account_id, field, old_value, new_value from ticket_change where ticket_id = ? order by id")
            .param(ticketId)
            .query((rs, row) -> new Change(rs.getLong("version"), rs.getTimestamp("occurred_at").toInstant(),
                rs.getObject("actor_account_id", UUID.class), rs.getString("field"), rs.getString("old_value"), rs.getString("new_value")))
            .list();
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
