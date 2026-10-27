-- Durable background jobs (TASK-054, DEC-083). A module puts a job in this table in the transaction of its own change, so a
-- rolled back change leaves no job; an executor of the web process or of the separate worker process picks due jobs with
-- FOR UPDATE SKIP LOCKED, holds a lease while it runs one and records the outcome. The payload carries references only,
-- never confidential text or secrets. The company is empty only for tasks of the installation such as the clean up runs.
create table job (
    id uuid primary key,
    -- the order of the list for the administration (newest first) and the position of its pages
    seq bigint generated always as identity unique,
    company_id uuid,
    kind text not null check (kind ~ '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$' and char_length(kind) <= 64),
    payload jsonb not null default '{}'::jsonb check (jsonb_typeof(payload) = 'object'),
    -- A second job with the same key for the same kind and company is not created; the first one is returned instead.
    dedup_key text check (dedup_key is null or char_length(dedup_key) between 1 and 200),
    state text not null check (state in ('queued', 'running', 'succeeded', 'failed', 'cancelled')),
    attempts integer not null default 0 check (attempts >= 0),
    max_attempts integer not null check (max_attempts between 1 and 100),
    run_at timestamptz not null,
    created_at timestamptz not null,
    started_at timestamptz,
    finished_at timestamptz,
    -- the executor that holds the job while it runs, and until when; an expired lease is given out again
    lease_owner text,
    lease_until timestamptz,
    -- a stable code of the last failure and the reference under which the log holds the details; never the message itself
    error_code text check (error_code is null or error_code ~ '^[a-z][a-z0-9_.]{0,63}$'),
    error_reference uuid,
    check ((state = 'running') = (lease_owner is not null and lease_until is not null)),
    check (attempts <= max_attempts)
);
create unique index job_dedup on job (coalesce(company_id, '00000000-0000-0000-0000-000000000000'::uuid), kind, dedup_key)
    where dedup_key is not null;
create index job_due on job (run_at) where state = 'queued';
create index job_lease on job (lease_until) where state = 'running';
create index job_state on job (state, seq);
create index job_finished on job (finished_at) where state in ('succeeded', 'failed', 'cancelled');

-- Recurring tasks of the installation: when each one is due next. The executor that locks the row enqueues the run as a job
-- with the due time as its key and moves the row on in the same transaction, so a due time runs once even with several processes.
create table job_schedule (
    name text primary key check (name ~ '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$' and char_length(name) <= 64),
    interval_seconds bigint not null check (interval_seconds > 0),
    next_run_at timestamptz not null,
    last_enqueued_at timestamptz
);

-- The executors that polled lately, so the administration sees whether anybody works off the queue.
create table job_worker (
    owner text primary key check (char_length(owner) between 1 and 200),
    started_at timestamptz not null,
    seen_at timestamptz not null
);

-- Every retry and cancellation by the administration, with time and account.
create table job_audit_event (
    id bigint generated always as identity primary key,
    company_id uuid,
    job_id uuid not null,
    operation text not null,
    actor_account_id uuid not null,
    actor text not null,
    occurred_at timestamptz not null
);
create index job_audit_event_job on job_audit_event (job_id, id);
