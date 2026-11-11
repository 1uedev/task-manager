-- Requirements in the search projection (TASK-103, DEC-133). One row per requirement and one row per stage: the current
-- revision ('current') and, once approved, the newest approved revision ('approved'), each with its criteria and its custom
-- values. The projection holds no rights; the search checks requirement.read and the field rules of the caller at every
-- search (DEC-069). Text is indexed as terms like the other texts of the projection.
create table search_requirement (
    requirement_id uuid primary key,
    company_id uuid not null,
    project_id uuid not null,
    number bigint not null check (number > 0),
    status text not null,
    owner_id uuid,
    current_revision integer not null check (current_revision > 0),
    approved_revision integer check (approved_revision > 0)
);

create index search_requirement_project on search_requirement (company_id, project_id);

create table search_requirement_text (
    requirement_id uuid not null references search_requirement (requirement_id),
    stage text not null check (stage in ('current', 'approved')),
    revision integer not null check (revision > 0),
    title text not null,
    description text not null,
    priority text not null,
    title_terms text not null default '',
    description_terms text not null default '',
    criteria_terms text not null default '',
    title_tsv tsvector generated always as (to_tsvector('simple', title_terms)) stored,
    description_tsv tsvector generated always as (to_tsvector('simple', description_terms)) stored,
    criteria_tsv tsvector generated always as (to_tsvector('simple', criteria_terms)) stored,
    primary key (requirement_id, stage)
);

create index search_requirement_text_title on search_requirement_text using gin (title_tsv);
create index search_requirement_text_description on search_requirement_text using gin (description_tsv);
create index search_requirement_text_criteria on search_requirement_text using gin (criteria_tsv);

-- The criteria one by one, for the excerpt that names the criterion a word was found in.
create table search_requirement_criterion (
    requirement_id uuid not null,
    stage text not null,
    criterion_id integer not null check (criterion_id > 0),
    position integer not null,
    text text not null,
    primary key (requirement_id, stage, criterion_id),
    foreign key (requirement_id, stage) references search_requirement_text (requirement_id, stage)
);

create table search_requirement_custom (
    requirement_id uuid not null,
    stage text not null,
    field_key text not null,
    value text not null,
    terms text not null default '',
    tsv tsvector generated always as (to_tsvector('simple', terms)) stored,
    primary key (requirement_id, stage, field_key),
    foreign key (requirement_id, stage) references search_requirement_text (requirement_id, stage)
);

create index search_requirement_custom_value on search_requirement_custom (field_key, value);
create index search_requirement_custom_tsv on search_requirement_custom using gin (tsv);

-- The migration reads no tables of other modules: the next start of the web application builds the projection again with
-- the requirements (DEC-069).
delete from search_state;
