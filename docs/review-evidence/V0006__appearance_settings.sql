-- Company appearance (TASK-036): one row with accent color and optional logo, plus an audit trail of changes.
create table appearance_setting (
    id smallint primary key check (id = 1),
    accent_color text not null check (accent_color ~ '^#[0-9a-f]{6}$'),
    logo bytea,
    logo_type text check (logo_type in ('image/png', 'image/jpeg', 'image/webp')),
    version bigint not null check (version > 0),
    updated_at timestamptz not null,
    updated_by uuid,
    check ((logo is null) = (logo_type is null)),
    check (logo is null or octet_length(logo) <= 524288)
);

insert into appearance_setting (id, accent_color, version, updated_at) values (1, '#f97316', 1, now());

create table appearance_audit_event (
    id bigint generated always as identity primary key,
    occurred_at timestamptz not null,
    actor_account_id uuid not null,
    actor text not null,
    event text not null,
    detail text
);
