package taskmanager.time.internal;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The tables of the time module. Nobody else reads them. */
@Repository
class TimeRepository {
    private static final String COLUMNS = "id, company_id, project_id, ticket_id, person_id, work_date, minutes, description, cancelled_at, "
        + "cancelled_by, created_at, created_by, updated_at, updated_by, version";

    private final JdbcClient jdbc;

    TimeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static TimeEntry map(ResultSet rs, int row) throws SQLException {
        return new TimeEntry(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getObject("project_id", UUID.class),
            rs.getObject("ticket_id", UUID.class), rs.getObject("person_id", UUID.class), rs.getDate("work_date").toLocalDate(),
            rs.getInt("minutes"), rs.getString("description"), instant(rs.getTimestamp("cancelled_at")), rs.getObject("cancelled_by", UUID.class),
            rs.getTimestamp("created_at").toInstant(), rs.getObject("created_by", UUID.class), rs.getTimestamp("updated_at").toInstant(),
            rs.getObject("updated_by", UUID.class), rs.getLong("version"));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    Optional<TimeEntry> find(UUID companyId, UUID id) {
        return jdbc.sql("select " + COLUMNS + " from time_entry where company_id = ? and id = ?").params(companyId, id).query(TimeRepository::map)
            .optional();
    }

    /** Locks the entry {@code FOR UPDATE} until the commit, so changes of one entry run one after the other. */
    Optional<TimeEntry> lock(UUID companyId, UUID id) {
        return jdbc.sql("select " + COLUMNS + " from time_entry where company_id = ? and id = ? for update").params(companyId, id)
            .query(TimeRepository::map).optional();
    }

    void insert(TimeEntry entry) {
        jdbc.sql("insert into time_entry (" + COLUMNS + ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
            .params(entry.id(), entry.companyId(), entry.projectId(), entry.ticketId(), entry.personId(), Date.valueOf(entry.date()),
                entry.minutes(), entry.description(), ts(entry.cancelledAt()), entry.cancelledBy(), ts(entry.createdAt()), entry.createdBy(),
                ts(entry.updatedAt()), entry.updatedBy(), entry.version())
            .update();
    }

    void update(TimeEntry entry) {
        jdbc.sql("""
                update time_entry set ticket_id = ?, work_date = ?, minutes = ?, description = ?, cancelled_at = ?, cancelled_by = ?,
                    updated_at = ?, updated_by = ?, version = ? where id = ?
                """)
            .params(entry.ticketId(), Date.valueOf(entry.date()), entry.minutes(), entry.description(), ts(entry.cancelledAt()),
                entry.cancelledBy(), ts(entry.updatedAt()), entry.updatedBy(), entry.version(), entry.id())
            .update();
    }

    void revision(UUID entryId, TimeRevision revision) {
        jdbc.sql("""
                insert into time_entry_revision (entry_id, revision, kind, occurred_at, actor_id, ticket_id, work_date, minutes, description, reason)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
            .params(entryId, revision.revision(), revision.kind(), ts(revision.occurredAt()), revision.actorId(), revision.ticketId(),
                Date.valueOf(revision.date()), revision.minutes(), revision.description(), revision.reason())
            .update();
    }

    int lastRevision(UUID entryId) {
        return jdbc.sql("select coalesce(max(revision), 0) from time_entry_revision where entry_id = ?").param(entryId).query(Integer.class).single();
    }

    List<TimeRevision> revisions(UUID entryId) {
        return jdbc.sql("""
                select revision, kind, occurred_at, actor_id, ticket_id, work_date, minutes, description, reason
                from time_entry_revision where entry_id = ? order by revision
                """)
            .param(entryId)
            .query((rs, row) -> new TimeRevision(rs.getInt("revision"), rs.getString("kind"), rs.getTimestamp("occurred_at").toInstant(),
                rs.getObject("actor_id", UUID.class), rs.getObject("ticket_id", UUID.class), rs.getDate("work_date").toLocalDate(),
                rs.getInt("minutes"), rs.getString("description"), rs.getString("reason")))
            .list();
    }

    /** The sum of one person on one ticket (or without ticket) over the matching entries. */
    record Sum(UUID personId, UUID ticketId, long count, long minutes) {
    }

    /** The minutes of one work date over the matching entries that are not cancelled. */
    record DaySum(LocalDate date, long minutes) {
    }

    /**
     * The entries of one project that match; {@code person} limits them to one person, {@code ticket} to one ticket, the dates
     * include both ends. At most {@code limit} rows, newest work date first.
     */
    List<TimeEntry> entries(UUID companyId, UUID projectId, UUID person, UUID ticket, LocalDate from, LocalDate to, boolean cancelled,
            int limit) {
        List<Object> params = new ArrayList<>();
        String where = where(companyId, projectId, person, ticket, from, to, cancelled, params);
        params.add(limit);
        return jdbc.sql("select " + COLUMNS + " from time_entry where " + where + " order by work_date desc, created_at desc, id limit ?")
            .params(params).query(TimeRepository::map).list();
    }

    /**
     * Count and minutes of every matching entry of one project per person and ticket, without any limit (TASK-092, F1). The
     * count includes cancelled entries when the filter lists them, the minutes never do.
     */
    List<Sum> sums(UUID companyId, UUID projectId, UUID person, UUID ticket, LocalDate from, LocalDate to, boolean cancelled) {
        List<Object> params = new ArrayList<>();
        String where = where(companyId, projectId, person, ticket, from, to, cancelled, params);
        return jdbc.sql("select person_id, ticket_id, count(*) as entries, coalesce(sum(minutes) filter (where cancelled_at is null), 0) as minutes"
                + " from time_entry where " + where + " group by person_id, ticket_id")
            .params(params)
            .query((rs, row) -> new Sum(rs.getObject("person_id", UUID.class), rs.getObject("ticket_id", UUID.class), rs.getLong("entries"),
                rs.getLong("minutes")))
            .list();
    }

    /** The minutes of the matching entries of one project per work date, cancelled ones never counted (TASK-092, F3). */
    List<DaySum> days(UUID companyId, UUID projectId, UUID person, UUID ticket, LocalDate from, LocalDate to) {
        List<Object> params = new ArrayList<>();
        String where = where(companyId, projectId, person, ticket, from, to, false, params);
        return jdbc.sql("select work_date, sum(minutes) as minutes from time_entry where " + where + " group by work_date")
            .params(params)
            .query((rs, row) -> new DaySum(rs.getDate("work_date").toLocalDate(), rs.getLong("minutes")))
            .list();
    }

    private static String where(UUID companyId, UUID projectId, UUID person, UUID ticket, LocalDate from, LocalDate to, boolean cancelled,
            List<Object> params) {
        StringBuilder sql = new StringBuilder("company_id = ? and project_id = ?");
        params.add(companyId);
        params.add(projectId);
        if (person != null) {
            sql.append(" and person_id = ?");
            params.add(person);
        }
        if (ticket != null) {
            sql.append(" and ticket_id = ?");
            params.add(ticket);
        }
        if (from != null) {
            sql.append(" and work_date >= ?");
            params.add(Date.valueOf(from));
        }
        if (to != null) {
            sql.append(" and work_date <= ?");
            params.add(Date.valueOf(to));
        }
        if (!cancelled) {
            sql.append(" and cancelled_at is null");
        }
        return sql.toString();
    }
}
