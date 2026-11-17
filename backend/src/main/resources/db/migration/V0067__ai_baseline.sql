-- The AI foundation (TASK-125, DEC-156): provider accesses with their models, versioned skills per use case and language,
-- the assignment of one model per use case, the daily limits, the record of every execution without content and the
-- suggestions kept for at most 24 hours. Projects, accounts and objects are referenced by identifier only; the ai module
-- never reads their tables.

-- A provider access the administration registered: OpenAI (or a server compatible with it), Anthropic or Ollama. The API
-- key is encrypted with the key of the operation and never given back. The data path says where the data goes: inside the
-- own network or to an external service. A removed access stays for the record of earlier executions.
create table ai_provider (
    id uuid primary key,
    company_id uuid not null,
    kind text not null check (kind in ('openai', 'anthropic', 'ollama')),
    name text not null check (char_length(name) between 1 and 80),
    base_url text not null check (char_length(base_url) between 8 and 200),
    api_key_cipher bytea,
    ca_certificate text check (ca_certificate is null or char_length(ca_certificate) <= 20000),
    data_path text not null check (data_path in ('local', 'external')),
    enabled boolean not null,
    version bigint not null check (version > 0),
    created_at timestamptz not null,
    created_by uuid not null,
    updated_at timestamptz not null,
    updated_by uuid not null,
    removed_at timestamptz,
    removed_by uuid
);
create unique index ai_provider_name on ai_provider (company_id, lower(name)) where removed_at is null;

-- A model of one access: the identifier the provider knows, the size of its context and the most it may answer, both in
-- tokens as the provider counts them.
create table ai_model (
    id uuid primary key,
    company_id uuid not null,
    provider_id uuid not null references ai_provider (id),
    name text not null check (char_length(name) between 1 and 80),
    model_key text not null check (char_length(model_key) between 1 and 200),
    context_tokens integer not null check (context_tokens between 1000 and 10000000),
    output_tokens integer not null check (output_tokens between 100 and 100000),
    enabled boolean not null,
    version bigint not null check (version > 0),
    created_at timestamptz not null,
    created_by uuid not null,
    updated_at timestamptz not null,
    updated_by uuid not null,
    removed_at timestamptz,
    removed_by uuid
);
create unique index ai_model_name on ai_model (provider_id, lower(name)) where removed_at is null;

-- The versions of the skill of one use case and language. Without an active version the built-in skill of the application
-- applies. At most one draft and one active version at a time; an active version never changes, a new one archives it.
create table ai_skill_version (
    id uuid primary key,
    company_id uuid not null,
    use_case text not null check (use_case in ('ticket_summary', 'reply_draft', 'requirement_review', 'wiki_revision')),
    language text not null check (language in ('de', 'en')),
    number integer not null check (number > 0),
    name text not null check (char_length(name) between 1 and 80),
    content text not null check (char_length(content) between 1 and 20000),
    state text not null check (state in ('draft', 'active', 'archived')),
    created_at timestamptz not null,
    created_by uuid not null,
    updated_at timestamptz not null,
    updated_by uuid not null,
    activated_at timestamptz,
    activated_by uuid,
    archived_at timestamptz,
    unique (company_id, use_case, language, number)
);
create unique index ai_skill_version_draft on ai_skill_version (company_id, use_case, language) where state = 'draft';
create unique index ai_skill_version_active on ai_skill_version (company_id, use_case, language) where state = 'active';

-- Which model serves a use case for the whole company. Without a row, without a model or switched off, the use case is
-- unavailable; there is no fallback model.
create table ai_assignment (
    company_id uuid not null,
    use_case text not null check (use_case in ('ticket_summary', 'reply_draft', 'requirement_review', 'wiki_revision')),
    model_id uuid references ai_model (id),
    enabled boolean not null,
    version bigint not null check (version > 0),
    updated_at timestamptz not null,
    updated_by uuid not null,
    primary key (company_id, use_case)
);

-- The daily limits of the company. Without a row the defaults of the application apply (50 per person, 500 in all).
create table ai_limit (
    company_id uuid primary key,
    person_daily integer not null check (person_daily between 0 and 100000),
    company_daily integer not null check (company_daily between 0 and 1000000),
    version bigint not null check (version > 0),
    updated_at timestamptz not null,
    updated_by uuid not null
);

-- The record of every execution, without prompt or answer: who started what for which object with which skill version,
-- access, model and data path, how long it took, the tokens the provider reported and the outcome with its code. It also
-- counts the daily limits. Kind use is a function of a person, test the trial of a skill by the administration.
create table ai_run (
    id uuid primary key,
    company_id uuid not null,
    account_id uuid not null,
    kind text not null check (kind in ('use', 'test')),
    use_case text not null check (use_case in ('ticket_summary', 'reply_draft', 'requirement_review', 'wiki_revision')),
    language text not null check (language in ('de', 'en')),
    project_id uuid,
    object_kind text check (object_kind is null or char_length(object_kind) between 1 and 40),
    object_id uuid,
    object_version bigint,
    skill_version text not null check (char_length(skill_version) between 1 and 40),
    provider_id uuid not null references ai_provider (id),
    model_id uuid not null references ai_model (id),
    model_key text not null,
    data_path text not null check (data_path in ('local', 'external')),
    state text not null check (state in ('queued', 'running', 'succeeded', 'failed', 'refused', 'cancelled')),
    code text check (code is null or char_length(code) between 1 and 60),
    truncated boolean not null default false,
    input_tokens integer,
    output_tokens integer,
    started_at timestamptz not null,
    finished_at timestamptz,
    cancel_requested boolean not null default false
);
create index ai_run_company_started on ai_run (company_id, started_at desc, id);
create index ai_run_account_started on ai_run (account_id, started_at);
create index ai_run_open on ai_run (state) where state in ('queued', 'running');

-- The result of a successful execution for the person who started it, kept for at most 24 hours. The sources hold the
-- identifiers and versions of what went into the context, the content the validated answer.
create table ai_suggestion (
    run_id uuid primary key references ai_run (id),
    company_id uuid not null,
    account_id uuid not null,
    sources jsonb not null,
    content jsonb not null,
    created_at timestamptz not null,
    expires_at timestamptz not null
);
create index ai_suggestion_expires on ai_suggestion (expires_at);
create index ai_suggestion_account on ai_suggestion (account_id);

-- What happened to accesses, models, skills, assignments and limits: fixed short names and identifiers, never a key.
create table ai_audit_event (
    id bigserial primary key,
    company_id uuid not null,
    subject_kind text not null,
    subject_id text not null,
    operation text not null,
    detail jsonb,
    actor uuid,
    occurred_at timestamptz not null
);
create index ai_audit_event_subject on ai_audit_event (company_id, subject_kind, subject_id);
