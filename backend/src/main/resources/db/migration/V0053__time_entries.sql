-- The time entries (TASK-069, DEC-099): worked time of a person in a project, optionally on a ticket of the same project.
-- An entry is never deleted. A correction replaces the effective values and a cancellation takes the entry out of every
-- sum; both keep the earlier state as a revision. The date of the work is a plain date and the duration whole minutes, so
-- no time zone changes either of them.
create table time_entry (
    id uuid primary key,
    company_id uuid not null,
    project_id uuid not null,
    ticket_id uuid,
    person_id uuid not null,
    work_date date not null,
    minutes integer not null check (minutes between 1 and 1440),
    description text not null check (char_length(description) between 1 and 1000),
    cancelled_at timestamptz,
    cancelled_by uuid,
    created_at timestamptz not null,
    created_by uuid not null,
    updated_at timestamptz not null,
    updated_by uuid not null,
    version bigint not null check (version > 0),
    check ((cancelled_at is null) = (cancelled_by is null))
);
create index time_entry_project_date on time_entry (company_id, project_id, work_date);
create index time_entry_person_date on time_entry (company_id, person_id, work_date);
create index time_entry_ticket on time_entry (ticket_id) where ticket_id is not null;

-- Every state of an entry: the first one at its creation, then one per correction or cancellation with the person, the
-- time and the reason of the change. The newest revision always equals the row above.
create table time_entry_revision (
    entry_id uuid not null references time_entry (id),
    revision integer not null check (revision > 0),
    kind text not null check (kind in ('created', 'corrected', 'cancelled')),
    occurred_at timestamptz not null,
    actor_id uuid not null,
    ticket_id uuid,
    work_date date not null,
    minutes integer not null check (minutes between 1 and 1440),
    description text not null check (char_length(description) between 1 and 1000),
    reason text check (reason is null or char_length(reason) between 1 and 500),
    primary key (entry_id, revision)
);
