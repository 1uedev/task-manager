-- Links of requirements (TASK-097, DEC-127). A requirement links tickets of its own project, each pair once. The link names
-- the revision of the requirement it refers to: the current one when it is made, a newer one only after an explicit switch.
-- A requirement also links wiki pages of its own project, without a revision. Tickets and pages belong to other modules,
-- so their identifiers carry no foreign key; tickets and pages are never deleted. A link is organisational: it opens no
-- revision and changes neither the version of the requirement nor that of the ticket, but it is recorded in the history.
create table requirement_ticket_link (
    requirement_id uuid not null references requirement (id),
    ticket_id uuid not null,
    company_id uuid not null,
    revision integer not null check (revision > 0),
    linked_at timestamptz not null,
    linked_by uuid not null,
    primary key (requirement_id, ticket_id)
);
create index requirement_ticket_link_ticket on requirement_ticket_link (ticket_id);

create table requirement_page_link (
    requirement_id uuid not null references requirement (id),
    page_id uuid not null,
    company_id uuid not null,
    linked_at timestamptz not null,
    linked_by uuid not null,
    primary key (requirement_id, page_id)
);

alter table requirement_event drop constraint requirement_event_kind_check;
alter table requirement_event add constraint requirement_event_kind_check check (kind in ('created', 'changed', 'criterion_added',
    'criterion_changed', 'criterion_removed', 'criteria_moved', 'revision_opened', 'submitted', 'approved', 'returned', 'discarded',
    'restored', 'ticket_linked', 'ticket_switched', 'ticket_unlinked', 'page_linked', 'page_unlinked'));
