-- Tags (TASK-028, DEC-068): a catalog of the company and one catalog per project, and the assignments of tags to tickets
-- and to projects. Projects and tickets are referenced by identifier only; the tags module never reads their tables.
create table tag (
    id uuid primary key,
    company_id uuid not null,
    -- null for the catalog of the company, else the project whose catalog holds the tag
    project_id uuid,
    name text not null check (char_length(name) between 1 and 50),
    -- the name for comparisons: Unicode NFKC, outer spaces removed, inner spaces collapsed, lower case
    normalized text not null,
    archived boolean not null default false,
    version bigint not null check (version > 0),
    created_at timestamptz not null,
    created_by uuid not null,
    updated_at timestamptz not null,
    updated_by uuid not null
);

-- A name exists once per catalog; the company catalog is the one without project.
create unique index tag_name_per_catalog on tag (company_id, coalesce(project_id, '00000000-0000-0000-0000-000000000000'::uuid), normalized);
create index tag_catalog on tag (company_id, project_id, normalized);

create table tag_ticket (
    ticket_id uuid not null,
    tag_id uuid not null references tag (id),
    project_id uuid not null,
    assigned_at timestamptz not null,
    assigned_by uuid not null,
    primary key (ticket_id, tag_id)
);

create table tag_project (
    project_id uuid not null,
    tag_id uuid not null references tag (id),
    assigned_at timestamptz not null,
    assigned_by uuid not null,
    primary key (project_id, tag_id)
);

-- Every change of a catalog and every assignment or removal, with time and account.
create table tag_audit_event (
    id bigint generated always as identity primary key,
    company_id uuid not null,
    tag_id uuid not null,
    operation text not null,
    target_type text,
    target_id uuid,
    detail text,
    actor_account_id uuid not null,
    occurred_at timestamptz not null
);
create index tag_audit_event_tag on tag_audit_event (tag_id, id);
