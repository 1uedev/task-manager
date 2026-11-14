-- Git links of tickets and requirements (TASK-112, DEC-142). Only stable identifiers of the provider are stored: the
-- instance, the identifier of the repository, the kind of object and the full commit SHA or the number. Names, paths, titles
-- and messages come live from the provider with the token of the person who looks and are never stored. A link belongs to
-- a ticket or a requirement, not to a revision, and is never deleted by itself.
create table git_link (
    id uuid primary key,
    company_id uuid not null,
    subject_kind text not null check (subject_kind in ('ticket', 'requirement')),
    subject_id uuid not null,
    project_id uuid not null,
    provider_id uuid not null references git_provider (id),
    repository_id text not null check (repository_id ~ '^[0-9]{1,20}$'),
    object_kind text not null check (object_kind in ('repository', 'commit', 'pull_request', 'merge_request')),
    object_key text not null check (
        (object_kind = 'repository' and object_key = '')
        or (object_kind = 'commit' and object_key ~ '^[0-9a-f]{40}([0-9a-f]{24})?$')
        or (object_kind in ('pull_request', 'merge_request') and object_key ~ '^[1-9][0-9]{0,9}$')),
    linked_at timestamptz not null,
    linked_by uuid not null
);
create unique index git_link_once on git_link (subject_kind, subject_id, provider_id, repository_id, object_kind, object_key);
create index git_link_subject on git_link (company_id, subject_kind, subject_id, linked_at);

-- The history of a requirement names the link and the removal of a Git object (TASK-112).
alter table requirement_event drop constraint requirement_event_kind_check;
alter table requirement_event add constraint requirement_event_kind_check check (kind in ('created', 'changed', 'criterion_added',
    'criterion_changed', 'criterion_removed', 'criteria_moved', 'revision_opened', 'submitted', 'approved', 'returned', 'discarded',
    'restored', 'ticket_linked', 'ticket_switched', 'ticket_unlinked', 'page_linked', 'page_unlinked', 'git_linked', 'git_unlinked'));
