-- Owner: identity (TASK-024).
-- Local accounts, server side sessions, login throttling, the session duration setting and an audit trail.
-- No account is created here: the first administrator is created with the container command (backend/README.md).

create table identity_account (
    id uuid primary key,
    -- Normalized (lower case) login name. Uniqueness is therefore case insensitive.
    username text not null,
    display_name text,
    -- Argon2id hash. Never a plain text password and never the one time password itself.
    password_hash text not null,
    -- True while the password is a one time password that must be replaced at the first login.
    password_change_required boolean not null,
    one_time_expires_at timestamptz,
    active boolean not null default true,
    -- Technical marker for the accounts that may administer accounts and the session duration.
    -- Group and project rights come with the permissions module (TASK-025).
    administrator boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint identity_account_username_format check (username = lower(username) and char_length(username) between 3 and 64),
    constraint identity_account_display_name_length check (display_name is null or char_length(display_name) between 1 and 100),
    constraint identity_account_one_time_pairing check (password_change_required = (one_time_expires_at is not null))
);
create unique index identity_account_username_key on identity_account (username);

create table identity_session (
    id uuid primary key,
    account_id uuid not null references identity_account (id),
    -- SHA-256 of the random session token. The token itself exists only in the cookie.
    token_hash bytea not null,
    -- SHA-256 of the CSRF token that belongs to this session.
    csrf_hash bytea not null,
    -- Fixed period: it starts at the first successful login and is never moved by activity or rotation.
    started_at timestamptz not null,
    expires_at timestamptz not null,
    -- A restricted session only allows the forced password change and logout.
    restricted boolean not null,
    restricted_expires_at timestamptz,
    revoked_at timestamptz,
    revoke_reason text,
    created_at timestamptz not null,
    constraint identity_session_token_length check (octet_length(token_hash) = 32 and octet_length(csrf_hash) = 32),
    constraint identity_session_period check (expires_at > started_at),
    constraint identity_session_restricted_pairing check (restricted = (restricted_expires_at is not null))
);
create unique index identity_session_token_key on identity_session (token_hash);
create index identity_session_account_active on identity_session (account_id) where revoked_at is null;
create index identity_session_expires on identity_session (expires_at);

create table identity_setting (
    name text primary key,
    value text not null,
    updated_at timestamptz not null
);
-- Default of a new installation: fixed session duration of 24 hours since login (DEC-052).
insert into identity_setting (name, value, updated_at) values ('session_duration_hours', '24', now());

-- Failed login counters per attempted login name and per source address. The subject is a hash, so the
-- table has a bounded key size and stores no login names.
create table identity_login_throttle (
    subject text primary key,
    failures integer not null,
    last_failure_at timestamptz not null,
    blocked_until timestamptz
);

create table identity_audit_event (
    id bigint generated always as identity primary key,
    occurred_at timestamptz not null,
    actor text not null,
    event text not null,
    account_id uuid,
    -- Short fixed vocabulary or identifiers. Never passwords, tokens or request content.
    detail text
);
create index identity_audit_event_account on identity_audit_event (account_id);
