-- Git providers, the connections of persons and the repositories released per project (TASK-111, DEC-141). Only stable
-- identifiers of the provider are stored, never names, paths or titles from Git; projects and accounts are referenced by
-- identifier only, the git module never reads their tables.

-- A provider instance the administration registered: GitHub.com, GitLab.com or a GitLab of its own, with the registered
-- OAuth application. The secret of the application is encrypted with the key of the operation and never given back. A
-- removed instance stays for the repositories that refer to it; removing deletes the connections at once.
create table git_provider (
    id uuid primary key,
    company_id uuid not null,
    kind text not null check (kind in ('github', 'gitlab_com', 'gitlab')),
    name text not null check (char_length(name) between 1 and 80),
    base_url text not null check (char_length(base_url) between 8 and 200),
    client_id text not null check (char_length(client_id) between 1 and 200),
    client_secret_cipher bytea,
    ca_certificate text check (ca_certificate is null or char_length(ca_certificate) <= 20000),
    version bigint not null check (version > 0),
    created_at timestamptz not null,
    created_by uuid not null,
    updated_at timestamptz not null,
    updated_by uuid not null,
    removed_at timestamptz,
    removed_by uuid
);
create unique index git_provider_name on git_provider (company_id, lower(name)) where removed_at is null;

-- The connection of one person with one provider: the account at the provider by its stable identifier and the tokens,
-- encrypted with the key of the operation. State renew means the tokens are gone and the person has to connect again.
create table git_connection (
    provider_id uuid not null references git_provider (id),
    account_id uuid not null,
    company_id uuid not null,
    external_user_id text not null check (char_length(external_user_id) between 1 and 100),
    state text not null check (state in ('active', 'renew')),
    access_cipher bytea,
    refresh_cipher bytea,
    access_expires_at timestamptz,
    refresh_expires_at timestamptz,
    connected_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (provider_id, account_id),
    check (state = 'renew' or access_cipher is not null)
);
create index git_connection_account on git_connection (account_id);

-- A started OAuth flow: the hash of the state the browser carries, the PKCE verifier and who started it. Used once, valid
-- for ten minutes.
create table git_oauth_state (
    state_hash bytea primary key,
    company_id uuid not null,
    provider_id uuid not null references git_provider (id),
    account_id uuid not null,
    verifier text not null,
    created_at timestamptz not null,
    expires_at timestamptz not null
);

-- A repository of a provider released for one project, by the stable identifier of the provider. Links to tickets and
-- requirements (TASK-112) are possible only inside released repositories.
create table git_repository (
    id uuid primary key,
    company_id uuid not null,
    project_id uuid not null,
    provider_id uuid not null references git_provider (id),
    external_id text not null check (char_length(external_id) between 1 and 100),
    released_at timestamptz not null,
    released_by uuid not null
);
create unique index git_repository_release on git_repository (project_id, provider_id, external_id);

-- What happened to providers, connections and releases: fixed short names and identifiers, never a secret, token or
-- metadata from Git. Without actor when the system ended a connection, as at the deactivation of an account.
create table git_audit_event (
    id bigserial primary key,
    company_id uuid not null,
    subject_kind text not null,
    subject_id uuid not null,
    operation text not null,
    detail jsonb,
    actor uuid,
    occurred_at timestamptz not null
);
create index git_audit_event_subject on git_audit_event (company_id, subject_kind, subject_id);
