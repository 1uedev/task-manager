-- Requirements (TASK-096, DEC-126): a requirement belongs to one project and keeps its number there for ever. Its content
-- lives in technical revisions; saving while a revision is not approved changes that revision, and the first content change
-- after an approval opens the next revision. An approved revision is never changed again (DEC-030). Nothing is deleted;
-- a discarded requirement stays with its history.
create table requirement_counter (
    project_id uuid primary key,
    last_number bigint not null check (last_number > 0)
);

create table requirement (
    id uuid primary key,
    company_id uuid not null,
    project_id uuid not null,
    number bigint not null check (number > 0),
    status text not null check (status in ('draft', 'review', 'approved', 'discarded')),
    owner_id uuid,
    current_revision integer not null check (current_revision > 0),
    approved_revision integer check (approved_revision is null or (approved_revision > 0 and approved_revision <= current_revision)),
    next_criterion integer not null check (next_criterion > 0),
    created_at timestamptz not null,
    created_by uuid not null,
    updated_at timestamptz not null,
    updated_by uuid not null,
    version bigint not null check (version > 0),
    unique (project_id, number)
);
create index requirement_project on requirement (company_id, project_id, number);

-- The content of one revision. The criteria are an ordered JSON list of objects with the fixed number of a criterion and its
-- text. approved_at and approved_by are set once, by the approval of exactly this revision, and never cleared again.
create table requirement_revision (
    requirement_id uuid not null references requirement (id),
    revision integer not null check (revision > 0),
    title text not null check (char_length(title) between 1 and 200),
    description text check (description is null or octet_length(description) <= 1048576),
    priority text not null check (priority in ('low', 'normal', 'high', 'critical')),
    criteria text not null check (octet_length(criteria) <= 1048576),
    opened_at timestamptz not null,
    opened_by uuid not null,
    saved_at timestamptz not null,
    saved_by uuid not null,
    approved_at timestamptz,
    approved_by uuid,
    primary key (requirement_id, revision),
    check ((approved_at is null) = (approved_by is null))
);

-- What happened to a requirement with person and time. A field change names the field, so the history shows it only to people
-- who may read that field; a change of a criterion names its fixed number; a status change carries the reason of a return.
create table requirement_event (
    id bigserial primary key,
    company_id uuid not null,
    requirement_id uuid not null references requirement (id),
    revision integer not null check (revision > 0),
    kind text not null check (kind in ('created', 'changed', 'criterion_added', 'criterion_changed', 'criterion_removed',
        'criteria_moved', 'revision_opened', 'submitted', 'approved', 'returned', 'discarded', 'restored')),
    field text,
    criterion integer check (criterion is null or criterion > 0),
    from_value text,
    to_value text,
    reason text check (reason is null or char_length(reason) between 1 and 2000),
    actor_id uuid not null,
    occurred_at timestamptz not null
);
create index requirement_event_requirement on requirement_event (requirement_id, id);
