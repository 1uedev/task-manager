-- The audience of existing attachments visible to the customer from their history (TASK-080, DEC-110, finding F1 of
-- docs/REVIEW_TASK-079_PR-239.md). V0042 gave a file whose audience it could not find the customer its ticket had at the
-- upgrade; a file released for a former customer thus reached the new one without a release. As in V0043 the audience is
-- taken from the history alone and stays null where it is not certain (Holger's choice "Beheben, unklare Altdaten
-- verbergen"); such a file reaches the current customer only through the explicit release of TASK-079.
--
-- The newest deliberate act decides: a release for a named organization (attachment.audience, since TASK-079) or the
-- switch to customer (attachment.visibility, since TASK-076), which meant the customer the ticket had then: the newest
-- change of the ticket field customer before it. Before TASK-079 the switch did not take the lock of the customer link, so a
-- change of the customer may have run at the same time; none before it, or one within one minute of the switch, leaves it
-- unclear.
-- A file never switched keeps where it came from: the organization of the portal account that uploaded it, or the customer
-- of the mail that brought it. Every file is computed again, also on an installation that already applied V0042.
with act as (
    select distinct on (e.attachment_id) e.attachment_id, e.operation, e.detail, e.occurred_at
        from file_audit_event e
        where e.operation = 'attachment.audience' or (e.operation = 'attachment.visibility' and e.detail = 'customer')
        order by e.attachment_id, e.id desc
)
update file_attachment f set organization_id = case
        when a.operation = 'attachment.audience' then cast(a.detail as uuid)
        when a.operation = 'attachment.visibility' then case
            when exists (select 1 from ticket_change t
                         where t.ticket_id = f.ticket_id and t.field = 'customer'
                           and t.occurred_at between a.occurred_at - interval '1 minute' and a.occurred_at + interval '1 minute') then null
            else (select cast(cast(t.new_value as jsonb) ->> 'id' as uuid) from ticket_change t
                  where t.ticket_id = f.ticket_id and t.field = 'customer' and t.occurred_at < a.occurred_at
                  order by t.occurred_at desc, t.id desc limit 1)
            end
        else coalesce(
            (select u.organization_id from identity_account u where u.id = f.uploaded_by and u.organization_id is not null),
            (select m.customer_id from helpdesk_message m, jsonb_array_elements(m.attachments) note
             where m.ticket_id = f.ticket_id and note ->> 'attachmentId' = f.id::text limit 1))
    end
    from file_attachment g left join act a on a.attachment_id = g.id
    where f.visibility = 'customer' and g.id = f.id;
