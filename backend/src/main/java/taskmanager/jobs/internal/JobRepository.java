package taskmanager.jobs.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The tables of the jobs module (TASK-054, V0025). Every method runs in the transaction of its caller. Times are written with
 * the precision PostgreSQL keeps, so a value read back compares equal.
 */
@Repository
public class JobRepository {
    /** A job reserved by an executor; {@code repeated} when a run of it started before this one (TASK-055). */
    public record Claimed(UUID id, UUID companyId, String kind, int attempt, int maxAttempts, boolean repeated) {
    }

    /** Counts per state and the age of the oldest due job. */
    public record Summary(Map<String, Long> states, Instant oldestDue, long activeWorkers) {
    }

    private static final String COLUMNS = "id, seq, company_id, kind, state, attempts, max_attempts, run_at, created_at, started_at, "
        + "finished_at, error_code, error_reference";

    private final JdbcClient jdbc;

    JobRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    }

    /** Inserts the job unless its key is taken; returns the identifier of the job that carries the key. */
    UUID insert(UUID id, UUID companyId, String kind, Map<String, String> payload, String dedupKey, Instant runAt, int maxAttempts,
            Instant now) {
        String[] keys = payload.keySet().toArray(String[]::new);
        String[] values = payload.values().toArray(String[]::new);
        int inserted = jdbc.sql("""
                insert into job (id, company_id, kind, payload, dedup_key, state, max_attempts, run_at, created_at)
                values (?, ?, ?, jsonb_object(?::text[], ?::text[]), ?, 'queued', ?, ?, ?)
                on conflict (coalesce(company_id, '00000000-0000-0000-0000-000000000000'::uuid), kind, dedup_key)
                    where dedup_key is not null do nothing""")
            .params(id, companyId, kind, keys, values, dedupKey, maxAttempts, ts(runAt), ts(now)).update();
        if (inserted == 1) {
            return id;
        }
        return jdbc.sql("""
                select id from job where coalesce(company_id, '00000000-0000-0000-0000-000000000000'::uuid)
                    = coalesce(?::uuid, '00000000-0000-0000-0000-000000000000'::uuid) and kind = ? and dedup_key = ?""")
            .params(companyId, kind, dedupKey).query(UUID.class).single();
    }

    /**
     * Reserves the job that is due longest for the owner, skipping jobs another executor holds. A job whose {@code started_at}
     * is already set ran before: the start of a run stays when the job waits again, also after a retry of the administration.
     */
    Optional<Claimed> claim(String owner, Instant now, Instant leaseUntil) {
        return jdbc.sql("""
                with next as (
                    select id, started_at is not null as ran from job where state = 'queued' and run_at <= ?
                    order by run_at, seq limit 1 for update skip locked)
                update job set state = 'running', attempts = attempts + 1, lease_owner = ?, lease_until = ?, started_at = ?
                from next where job.id = next.id
                returning job.id, job.company_id, job.kind, job.attempts, job.max_attempts, next.ran""")
            .params(ts(now), owner, ts(leaseUntil), ts(now))
            .query((rs, row) -> new Claimed(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getString("kind"),
                rs.getInt("attempts"), rs.getInt("max_attempts"), rs.getBoolean("ran")))
            .optional();
    }

    Map<String, String> payload(UUID id) {
        Map<String, String> payload = new LinkedHashMap<>();
        jdbc.sql("select key, value from job, jsonb_each_text(job.payload) where job.id = ? order by key").param(id)
            .query(rs -> {
                payload.put(rs.getString("key"), rs.getString("value"));
            });
        return payload;
    }

    /** Renews the leases the owner holds; returns how many it still holds. */
    int renew(String owner, List<UUID> ids, Instant leaseUntil) {
        if (ids.isEmpty()) {
            return 0;
        }
        return jdbc.sql("update job set lease_until = ? where state = 'running' and lease_owner = ? and id = any(?)")
            .params(ts(leaseUntil), owner, ids.toArray(UUID[]::new)).update();
    }

    /** Marks the job done if the owner still holds it. */
    boolean succeed(UUID id, String owner, Instant now) {
        return jdbc.sql("""
                update job set state = 'succeeded', finished_at = ?, lease_owner = null, lease_until = null, error_code = null,
                    error_reference = null
                where id = ? and state = 'running' and lease_owner = ?""")
            .params(ts(now), id, owner).update() == 1;
    }

    /**
     * Records a failed attempt if the owner still holds the job: it waits for the next attempt at {@code retryAt}, or fails for
     * good when {@code retryAt} is {@code null}.
     */
    boolean fail(UUID id, String owner, Instant now, Instant retryAt, String code, UUID reference) {
        if (retryAt == null) {
            return jdbc.sql("""
                    update job set state = 'failed', finished_at = ?, lease_owner = null, lease_until = null, error_code = ?, error_reference = ?
                    where id = ? and state = 'running' and lease_owner = ?""")
                .params(ts(now), code, reference, id, owner).update() == 1;
        }
        return jdbc.sql("""
                update job set state = 'queued', run_at = ?, lease_owner = null, lease_until = null, error_code = ?, error_reference = ?
                where id = ? and state = 'running' and lease_owner = ?""")
            .params(ts(retryAt), code, reference, id, owner).update() == 1;
    }

    /**
     * Gives a job back that the stop of its executor interrupted (TASK-055): the attempt does not count, the job waits again at
     * once, also when it was its last attempt. Only while the owner still holds it.
     */
    boolean giveBack(UUID id, String owner, Instant now, String code, UUID reference) {
        return jdbc.sql("""
                update job set state = 'queued', attempts = attempts - 1, run_at = ?, lease_owner = null, lease_until = null,
                    error_code = ?, error_reference = ?
                where id = ? and state = 'running' and lease_owner = ?""")
            .params(ts(now), code, reference, id, owner).update() == 1;
    }

    /**
     * Gives the jobs back whose lease expired, because their executor ended without reporting: those with attempts left wait
     * again at once, the others fail for good. Returns how many it gave back.
     */
    int releaseExpired(Instant now) {
        return jdbc.sql("""
                update job set
                    state = case when attempts >= max_attempts then 'failed' else 'queued' end,
                    finished_at = case when attempts >= max_attempts then ? else null end,
                    run_at = ?, lease_owner = null, lease_until = null, error_code = 'job.lease_expired', error_reference = null
                where id in (select id from job where state = 'running' and lease_until < ? for update skip locked)""")
            .params(ts(now), ts(now), ts(now)).update();
    }

    /** The due time of the job that waits longest, over every company. */
    Optional<Instant> oldestDue(Instant now) {
        return jdbc.sql("select min(run_at) from job where state = 'queued' and run_at <= ?").param(ts(now))
            .query((rs, row) -> instant(rs, 1)).optional().flatMap(Optional::ofNullable);
    }

    /** Whether a job of the kind waits or runs. */
    boolean pending(String kind) {
        return jdbc.sql("select exists (select 1 from job where kind = ? and state in ('queued', 'running'))").param(kind)
            .query(Boolean.class).single();
    }

    /** Creates the schedule of a recurring task, due now; an existing one takes the new interval and is due no later than it. */
    void schedule(String name, long intervalSeconds, Instant now) {
        jdbc.sql("""
                insert into job_schedule (name, interval_seconds, next_run_at) values (?, ?, ?)
                on conflict (name) do update set interval_seconds = excluded.interval_seconds,
                    next_run_at = least(job_schedule.next_run_at, ?::timestamptz + make_interval(secs => excluded.interval_seconds))""")
            .params(name, intervalSeconds, ts(now), ts(now)).update();
    }

    /** The due schedules among the names, locked; another executor skips them. */
    List<String> lockDueSchedules(List<String> names, Instant now) {
        if (names.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select name from job_schedule where name = any(?) and next_run_at <= ? order by name for update skip locked")
            .params(names.toArray(String[]::new), ts(now)).query(String.class).list();
    }

    void advanceSchedule(String name, Instant now, boolean enqueued) {
        jdbc.sql("""
                update job_schedule set next_run_at = ?::timestamptz + make_interval(secs => interval_seconds),
                    last_enqueued_at = case when ? then ?::timestamptz else last_enqueued_at end
                where name = ?""")
            .params(ts(now), enqueued, ts(now), name).update();
    }

    void seen(String owner, Instant startedAt, Instant now) {
        jdbc.sql("""
                insert into job_worker (owner, started_at, seen_at) values (?, ?, ?)
                on conflict (owner) do update set seen_at = excluded.seen_at""")
            .params(owner, ts(startedAt), ts(now)).update();
    }

    void gone(String owner) {
        jdbc.sql("delete from job_worker where owner = ?").param(owner).update();
    }

    /** Deletes old finished jobs and executors that were not seen for long; returns how many jobs it deleted. */
    int purge(Instant succeededBefore, Instant failedBefore, Instant workersBefore) {
        jdbc.sql("delete from job_worker where seen_at < ?").param(ts(workersBefore)).update();
        return jdbc.sql("""
                delete from job where (state = 'succeeded' and finished_at < ?)
                    or (state in ('failed', 'cancelled') and finished_at < ?)""")
            .params(ts(succeededBefore), ts(failedBefore)).update();
    }

    Summary summary(UUID companyId, Instant now, Instant workersSince) {
        Map<String, Long> states = new LinkedHashMap<>();
        for (String state : List.of("queued", "running", "succeeded", "failed", "cancelled")) {
            states.put(state, 0L);
        }
        jdbc.sql("select state, count(*) as n from job where " + visible() + " group by state").params(companyId)
            .query(rs -> {
                states.put(rs.getString("state"), rs.getLong("n"));
            });
        Instant oldest = jdbc.sql("select min(run_at) from job where state = 'queued' and run_at <= ? and " + visible())
            .params(ts(now), companyId).query((rs, row) -> instant(rs, 1)).optional().orElse(null);
        long workers = jdbc.sql("select count(*) from job_worker where seen_at >= ?").param(ts(workersSince)).query(Long.class).single();
        return new Summary(states, oldest, workers);
    }

    /** The jobs of the company and of the installation, newest first, below the given position. */
    List<Job> list(UUID companyId, String state, Long before, int limit) {
        return jdbc.sql("select " + COLUMNS + " from job where " + visible() + " and (?::text is null or state = ?) "
                + "and (?::bigint is null or seq < ?) order by seq desc limit ?")
            .params(companyId, state, state, before, before, limit).query(JobRepository::job).list();
    }

    Optional<Job> lock(UUID companyId, UUID id) {
        return jdbc.sql("select " + COLUMNS + " from job where id = ? and " + visible() + " for update").params(id, companyId)
            .query(JobRepository::job).optional();
    }

    /** The state of the job under a share lock that a retry or cancel of the administration waits for (TASK-063). */
    Optional<String> shareState(UUID id) {
        return jdbc.sql("select state from job where id = ? for share").param(id).query(String.class).optional();
    }

    Optional<Job> find(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from job where id = ?").param(id).query(JobRepository::job).optional();
    }

    /** Puts the job back with all its attempts; {@code started_at} stays, so its next run knows an earlier one ran (TASK-055). */
    void requeue(UUID id, Instant now) {
        jdbc.sql("""
                update job set state = 'queued', attempts = 0, run_at = ?, finished_at = null, error_code = null, error_reference = null
                where id = ?""")
            .params(ts(now), id).update();
    }

    void cancel(UUID id, Instant now) {
        jdbc.sql("update job set state = 'cancelled', finished_at = ? where id = ?").params(ts(now), id).update();
    }

    void audit(UUID companyId, UUID jobId, String operation, UUID actorAccountId, String actor, Instant now) {
        jdbc.sql("insert into job_audit_event (company_id, job_id, operation, actor_account_id, actor, occurred_at) values (?, ?, ?, ?, ?, ?)")
            .params(companyId, jobId, operation, actorAccountId, actor, ts(now)).update();
    }

    /** Jobs of the company and tasks of the installation; the parameter is the company. */
    private static String visible() {
        return "(company_id is null or company_id = ?::uuid)";
    }

    private static Job job(ResultSet rs, int row) throws SQLException {
        return new Job(rs.getObject("id", UUID.class), rs.getLong("seq"), rs.getObject("company_id", UUID.class), rs.getString("kind"),
            rs.getString("state"), rs.getInt("attempts"), rs.getInt("max_attempts"), instant(rs, "run_at"), instant(rs, "created_at"),
            instant(rs, "started_at"), instant(rs, "finished_at"), rs.getString("error_code"), rs.getObject("error_reference", UUID.class));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
