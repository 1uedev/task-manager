-- TASK-059 (DEC-087): corrections of the helpdesk intake after the review of TASK-057.
--
-- F3: the same UIDVALIDITY and UID may name another mail in another account or folder. A mailbox counts its sources: each
-- change of server, port, account or folder starts a new one, and the source is part of the origin of a mail. Mailboxes and
-- mails that exist get the first source; before this migration no installation changed the source of a mailbox in use.
alter table helpdesk_mailbox add column source integer not null default 1 check (source > 0);
alter table helpdesk_message add column source integer not null default 1 check (source > 0);
alter table helpdesk_message drop constraint helpdesk_message_mailbox_id_uid_validity_uid_key;
alter table helpdesk_message add constraint helpdesk_message_origin unique (mailbox_id, source, uid_validity, uid);

-- F2: after a new UIDVALIDITY the intake compares Message-IDs up to this UID, across batches, failures and restarts,
-- until its position passed it; null when nothing is compared.
alter table helpdesk_mailbox add column compare_until bigint check (compare_until is null or compare_until >= 0);
