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

    /** The sum of one person on one ticket (or without ticket) of one project over the matching entries. */
    record Sum(UUID projectId, UUID personId, UUID ticketId, long count, long minutes) {
    }

    /** The minutes of one work date over the matching entries that are not cancelled. */
    record DaySum(LocalDate date, long minutes) {
    }

    /** Which entries of one project a listing reads; {@code person} and {@code ticket} limit them when set. */
    record Scope(UUID projectId, UUID person, UUID ticket) {
    }

    /** Sums, sums per day and rows of one listing, all read by one statement and so from one snapshot of the database. */
    record Snapshot(List<Sum> sums, List<DaySum> days, List<TimeEntry> rows) {
    }

    /**
     * Reads count and minutes per project, person and ticket (TASK-092, F1), the minutes per work date (F3) and at most
     * {@code rowLimit} rows, newest work date first, of the matching entries of every scope. It is one SQL statement, so even
     * under READ COMMITTED all three parts see the same committed entries; an entry stored meanwhile is in all of them or in
     * none (R1). The dates include both ends; the count includes cancelled entries when {@code cancelled} lists them, the
     * minutes never do. With {@code rowsWhenOver} false no rows are read once more than {@code rowLimit - 1} entries match.
     */
    Snapshot snapshot(UUID companyId, List<Scope> scopes, LocalDate from, LocalDate to, boolean cancelled, int rowLimit, boolean rowsWhenOver) {
        if (scopes.isEmpty()) {
            return new Snapshot(List.of(), List.of(), List.of());
        }
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("company_id = ? and (");
        params.add(companyId);
        for (int i = 0; i < scopes.size(); i++) {
            Scope scope = scopes.get(i);
            where.append(i == 0 ? "" : " or ").append("(project_id = ?");
            params.add(scope.projectId());
            if (scope.person() != null) {
                where.append(" and person_id = ?");
                params.add(scope.person());
            }
            if (scope.ticket() != null) {
                where.append(" and ticket_id = ?");
                params.add(scope.ticket());
            }
            where.append(")");
        }
        where.append(")");
        if (from != null) {
            where.append(" and work_date >= ?");
            params.add(Date.valueOf(from));
        }
        if (to != null) {
            where.append(" and work_date <= ?");
            params.add(Date.valueOf(to));
        }
        if (!cancelled) {
            where.append(" and cancelled_at is null");
        }
        params.add(rowsWhenOver);
        params.add(rowLimit);
        params.add(rowLimit);
        String nothing = "null::uuid as id, null::uuid as company_id, %s, null::text as description, null::timestamptz as cancelled_at, "
            + "null::uuid as cancelled_by, null::timestamptz as created_at, null::uuid as created_by, null::timestamptz as updated_at, "
            + "null::uuid as updated_by, null::bigint as version";
        String sql = "with m as materialized (select " + COLUMNS + " from time_entry where " + where + ")\n"
            + "select 'sum' as kind, " + String.format(nothing, "project_id, ticket_id, person_id, null::date as work_date, null::integer as minutes")
            + ", count(*) as entries, coalesce(sum(minutes) filter (where cancelled_at is null), 0) as summed from m"
            + " group by project_id, person_id, ticket_id\n"
            + "union all select 'day', " + String.format(nothing, "null::uuid, null::uuid, null::uuid, work_date, null::integer")
            + ", null, sum(minutes) from m where cancelled_at is null group by work_date\n"
            + "union all (select 'row', " + COLUMNS + ", null, null from m where ? or (select count(*) from m) < ?"
            + " order by work_date desc, created_at desc, id limit ?)";
        List<Sum> sums = new ArrayList<>();
        List<DaySum> days = new ArrayList<>();
        List<TimeEntry> rows = new ArrayList<>();
        jdbc.sql(sql).params(params).query((ResultSet rs) -> {
            switch (rs.getString("kind")) {
                case "sum" -> sums.add(new Sum(rs.getObject("project_id", UUID.class), rs.getObject("person_id", UUID.class),
                    rs.getObject("ticket_id", UUID.class), rs.getLong("entries"), rs.getLong("summed")));
                case "day" -> days.add(new DaySum(rs.getDate("work_date").toLocalDate(), rs.getLong("summed")));
                default -> rows.add(map(rs, rows.size()));
            }
        });
        return new Snapshot(sums, days, rows);
    }
}
