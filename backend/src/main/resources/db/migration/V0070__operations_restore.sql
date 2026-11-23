-- Backup and restore (TASK-131, DEC-161).

-- Every content of the file store the database refers to: attachments whose content was not deleted yet and images. The
-- backup service reads this view at each backup point and refuses to call the point complete when one of them is missing in
-- the file store. The rows of the files module stay its own; only this view is the agreed reading for the backup service.
create view operations_backup_content as
    select id, size_bytes as size, sha256 from file_attachment where purged_at is null
    union all
    select id, size, sha256 from file_image;

-- A restore that was not released yet. The backup service writes a row after the database reached its backup point; the web
-- server and the worker refuse to start while a row exists, and the command after-restore removes it once it ran.
create table operations_restore_pending (
    point text primary key check (char_length(point) between 1 and 200),
    restored_at timestamptz not null
);

-- What the command after-restore did: who ran it, when, for which backup point, and the counts of its steps. No content.
create table operations_audit_event (
    id bigint generated always as identity primary key,
    operation text not null check (operation ~ '^[a-z][a-z0-9_.]{0,63}$'),
    point text not null check (char_length(point) between 1 and 200),
    operator text not null check (char_length(operator) between 1 and 200),
    detail jsonb not null,
    occurred_at timestamptz not null
);
