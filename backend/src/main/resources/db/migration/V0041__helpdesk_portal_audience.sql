-- The audience of the portal conversation (TASK-079, DEC-109, finding F1 of docs/REVIEW_TASK-076-078_A11.md). Posts, sent
-- mail answers and attachments visible to the customer keep the organization they were written for. After a change of the
-- customer of a ticket the new organization sees only what is written for it from then on (Holger's choice of 2 November 2026,
-- "Nur neue Einträge"). A null organization reaches no portal.
alter table helpdesk_portal_post add column organization_id uuid;
alter table helpdesk_reply add column customer_id uuid;

-- Existing rows: the most exact source each row has, otherwise the customer the ticket has now.
-- Posts of a portal account belong to its organization, which never changes; answers of the support to the current customer.
update helpdesk_portal_post p set organization_id = a.organization_id
    from identity_account a where p.author_kind = 'customer' and a.id = p.author_id;
update helpdesk_portal_post p set organization_id = c.customer_id
    from helpdesk_ticket_customer c where p.author_kind = 'support' and c.ticket_id = p.ticket_id;
-- A mail answer went to a contact; its organization is the one of the contact.
update helpdesk_reply r set customer_id = c.customer_id from helpdesk_contact c where c.id = r.contact_id;

create index helpdesk_portal_post_audience on helpdesk_portal_post (ticket_id, organization_id);
