-- Draft of a project configuration (TASK-044, DEC-073): the configuration an administration works on before it publishes it
-- as the next version. A project has at most one draft. The revision moves with every save, so two editors cannot overwrite
-- each other; the base version is the published version the draft started from.
create table project_configuration_draft (
    project_id uuid primary key references project (id),
    base_version bigint not null check (base_version > 0),
    revision bigint not null check (revision > 0),
    document jsonb not null,
    updated_at timestamptz not null,
    updated_by uuid not null
);
