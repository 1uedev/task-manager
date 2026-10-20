-- Tickets (TASK-027, DEC-067): the tickets of a project, their numbers per project and the history of their changes.
-- Projects are referenced by identifier only; the tickets module never reads the tables of the projects module.
create table ticket (
    id uuid primary key,
    company_id uuid not null,
    project_id uuid not null,
    number bigint not null check (number > 0),
    title text not null check (char_length(title) between 1 and 200),
    description text check (description is null or char_length(description) <= 20000),
    type text not null,
    status text not null,
    assignee uuid,
    configuration_version bigint not null check (configuration_version > 0),
    version bigint not null check (version > 0),
    created_at timestamptz not null,
    created_by uuid not null,
    updated_at timestamptz not null,
    updated_by uuid not null,
    unique (project_id, number)
);

-- The last number handed out per project; the row is locked while a ticket is created.
create table ticket_counter (
    project_id uuid primary key,
    last_number bigint not null check (last_number > 0)
);

-- One row per changed field and ticket version; version 1 holds the values at creation.
create table ticket_change (
    id bigint generated always as identity primary key,
    ticket_id uuid not null references ticket (id),
    version bigint not null check (version > 0),
    occurred_at timestamptz not null,
    actor_account_id uuid not null,
    field text not null,
    old_value text,
    new_value text
);

create index ticket_change_ticket on ticket_change (ticket_id, id);
