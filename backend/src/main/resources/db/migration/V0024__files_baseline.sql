-- Files and attachments (TASK-051, DEC-080): the attachments of tickets. The content lives in the file store outside the
-- database (a persistent volume); this table holds the metadata and decides what is visible. Tickets and projects are
-- referenced by identifier only; the files module never reads their tables.
create table file_attachment (
    id uuid primary key,
    company_id uuid not null,
    ticket_id uuid not null,
    project_id uuid not null,
    -- the cleaned name: no path, no control or format characters
    name text not null check (char_length(name) between 1 and 255),
    size_bytes bigint not null check (size_bytes > 0),
    sha256 text not null check (sha256 ~ '^[0-9a-f]{64}$'),
    uploaded_at timestamptz not null,
    uploaded_by uuid not null,
    -- A removed attachment is hidden at once; its content is deleted after a period (purged_at), the row stays for the history.
    removed_at timestamptz,
    removed_by uuid,
    purged_at timestamptz,
    check ((removed_at is null) = (removed_by is null)),
    check (purged_at is null or removed_at is not null)
);
create index file_attachment_ticket on file_attachment (ticket_id, uploaded_at, id);
create index file_attachment_to_purge on file_attachment (removed_at) where removed_at is not null and purged_at is null;

-- Every upload, removal and deletion of content, with time and account.
create table file_audit_event (
    id bigint generated always as identity primary key,
    company_id uuid not null,
    attachment_id uuid not null,
    ticket_id uuid not null,
    operation text not null,
    detail text,
    actor_account_id uuid,
    occurred_at timestamptz not null
);
create index file_audit_event_attachment on file_audit_event (attachment_id, id);
