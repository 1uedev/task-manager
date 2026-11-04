-- Customer visible attachments (TASK-076, DEC-106). Every attachment is internal or visible to the customer of its ticket.
-- Existing attachments stay internal; files of the portal and of mails of the customer start as visible to the customer.
alter table file_attachment add column visibility varchar(10) not null default 'internal';
alter table file_attachment add constraint file_attachment_visibility check (visibility in ('internal', 'customer'));
