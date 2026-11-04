-- The audience of attachments visible to the customer (TASK-079, DEC-109, finding F1 of docs/REVIEW_TASK-076-078_A11.md): the
-- organization the file was made visible for, see V0041 for posts and replies. A null organization reaches no portal.
alter table file_attachment add column organization_id uuid;
alter table file_attachment add constraint file_attachment_audience check (organization_id is null or visibility = 'customer');

-- Existing files visible to the customer: of a portal account its organization, of a mail the organization of the mail,
-- otherwise the current customer of the ticket.
update file_attachment f set organization_id = a.organization_id
    from identity_account a where f.visibility = 'customer' and a.id = f.uploaded_by and a.organization_id is not null;
update file_attachment f set organization_id = m.customer_id
    from helpdesk_message m, jsonb_array_elements(m.attachments) note
    where f.visibility = 'customer' and f.organization_id is null and m.ticket_id = f.ticket_id
      and note ->> 'attachmentId' = f.id::text;
update file_attachment f set organization_id = c.customer_id
    from helpdesk_ticket_customer c
    where f.visibility = 'customer' and f.organization_id is null and c.ticket_id = f.ticket_id
      and not exists (select 1 from helpdesk_message m, jsonb_array_elements(m.attachments) note
                      where m.ticket_id = f.ticket_id and note ->> 'attachmentId' = f.id::text);
