-- New answers of customers (TASK-067, DEC-097). A mail that joins a ticket that already exists, by itself as the answer to a
-- reply or through a person in the review queue, is new until a person who may reply in the project marks it as seen.
-- Mails that opened their ticket are never new. Mails that joined a ticket before this migration stay unmarked.
alter table helpdesk_message add column joined_at timestamptz;
alter table helpdesk_message add column seen_at timestamptz;
alter table helpdesk_message add column seen_by uuid;
alter table helpdesk_message add constraint helpdesk_message_seen
    check ((seen_at is null) = (seen_by is null) and (seen_at is null or joined_at is not null));
create index helpdesk_message_new_answer on helpdesk_message (company_id, project_id, joined_at desc)
    where joined_at is not null and seen_at is null;
