-- Projects (TASK-026, DEC-065): projects of the company, their members and the versions of their configuration.
-- Members are accounts of the identity module and configurations are immutable once written; a change adds a version.
create table project (
    id uuid primary key,
    company_id uuid not null,
    key text not null check (key ~ '^[A-Z][A-Z0-9]{1,9}$'),
    name text not null check (char_length(name) between 1 and 80),
    description text check (description is null or char_length(description) <= 2000),
    configuration_version bigint not null check (configuration_version > 0),
    version bigint not null check (version > 0),
    created_at timestamptz not null,
    created_by uuid not null,
    updated_at timestamptz not null,
    updated_by uuid not null,
    unique (company_id, key)
);

create table project_configuration (
    project_id uuid not null references project (id),
    version bigint not null check (version > 0),
    document jsonb not null,
    created_at timestamptz not null,
    created_by uuid not null,
    primary key (project_id, version)
);

create table project_member (
    project_id uuid not null references project (id),
    account_id uuid not null,
    added_at timestamptz not null,
    added_by uuid not null,
    primary key (project_id, account_id)
);

create table project_audit_event (
    id bigint generated always as identity primary key,
    occurred_at timestamptz not null,
    project_id uuid not null references project (id),
    actor_account_id uuid not null,
    event text not null,
    detail text
);
