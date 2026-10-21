-- Search projection (TASK-029, DEC-069): the search module keeps its own copy of the searchable fields of projects, tickets
-- and tags. The owning modules publish every change inside their transaction, so the copy commits or rolls back with the
-- change. Rights are not copied: every search checks the current rights of the caller before it matches, ranks or counts.
-- Text is indexed per field without language specific stemming ('simple'), so German and English words match alike.
create table search_project (
    project_id uuid primary key,
    company_id uuid not null,
    key text not null,
    name text not null,
    description text,
    -- the names of the assigned tags, separated by spaces, kept by the search module
    tags_text text not null default '',
    document tsvector generated always as (
        to_tsvector('simple', key || ' ' || name || ' ' || coalesce(description, '') || ' ' || tags_text)) stored
);

create index search_project_company on search_project (company_id, key);
create index search_project_document on search_project using gin (document);

create table search_ticket (
    ticket_id uuid primary key,
    company_id uuid not null,
    project_id uuid not null,
    number bigint not null,
    type text not null,
    status text not null,
    assignee uuid,
    title text not null,
    description text,
    tags_text text not null default '',
    -- one vector per field, so a search only matches the fields the caller may read
    title_tsv tsvector generated always as (to_tsvector('simple', title)) stored,
    description_tsv tsvector generated always as (to_tsvector('simple', coalesce(description, ''))) stored,
    tags_tsv tsvector generated always as (to_tsvector('simple', tags_text)) stored
);

create unique index search_ticket_number on search_ticket (project_id, number);
create index search_ticket_company on search_ticket (company_id, project_id);
create index search_ticket_status on search_ticket (project_id, status);
create index search_ticket_assignee on search_ticket (project_id, assignee);
create index search_ticket_title on search_ticket using gin (title_tsv);
create index search_ticket_description on search_ticket using gin (description_tsv);
create index search_ticket_tags on search_ticket using gin (tags_tsv);

create table search_tag (
    tag_id uuid primary key,
    company_id uuid not null,
    -- null for the catalog of the company
    project_id uuid,
    name text not null
);

create table search_ticket_tag (
    ticket_id uuid not null references search_ticket (ticket_id),
    tag_id uuid not null references search_tag (tag_id),
    primary key (ticket_id, tag_id)
);

create index search_ticket_tag_tag on search_ticket_tag (tag_id);

create table search_project_tag (
    project_id uuid not null references search_project (project_id),
    tag_id uuid not null references search_tag (tag_id),
    primary key (project_id, tag_id)
);

create index search_project_tag_tag on search_project_tag (tag_id);

-- One row per company whose projection was built from the owning modules. The migration reads no tables of other modules:
-- at the first start after it, the search module builds the projection of a company without a row (DEC-069).
create table search_state (
    company_id uuid primary key,
    built_at timestamptz not null
);
