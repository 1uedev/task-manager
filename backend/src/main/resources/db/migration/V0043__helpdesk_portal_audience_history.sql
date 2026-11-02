-- The audience of existing answers of the support and mail answers from their history (TASK-080, DEC-110, finding F1 of
-- docs/REVIEW_TASK-079_PR-239.md). V0041 gave an existing answer of the support the customer its ticket had at the upgrade
-- and an existing mail answer the organization its contact had then; a change before the upgrade thus showed old entries to
-- the new organization. Holger chose on 2 November 2026 "Beheben, unklare Altdaten verbergen": the audience is taken from the
-- history alone, and where the history does not tell it for certain it stays null, which reaches no portal.
--
-- Every row is computed again, also on an installation that already applied V0041. Posts of portal accounts keep the
-- organization of their author, which never changes.

-- An answer of the support was written for the customer the ticket had then: the newest change of the ticket field
-- customer before the answer. The answer and a change of the customer take the same lock of the customer link before they
-- read the time (TASK-062, also before TASK-079), so the times follow the order in which they happened. No change before it,
-- or a change at the very same time, leaves it unclear.
update helpdesk_portal_post p set organization_id = case
        when exists (select 1 from ticket_change t
                     where t.ticket_id = p.ticket_id and t.field = 'customer' and t.occurred_at = p.created_at) then null
        else (select cast(cast(t.new_value as jsonb) ->> 'id' as uuid) from ticket_change t
              where t.ticket_id = p.ticket_id and t.field = 'customer' and t.occurred_at < p.created_at
              order by t.occurred_at desc, t.id desc limit 1)
    end
    where p.author_kind = 'support';

-- A mail answer went to its contact and so to the organization the contact belonged to then: the newest creation or change
-- of the contact before the answer, as the audit of the customer administration recorded it. Writing an answer does not lock
-- the contact, so a change of the contact may have run at the same time; a change within one minute of the answer leaves it
-- unclear.
update helpdesk_reply r set customer_id = case
        when exists (select 1 from helpdesk_audit_event e
                     where e.target_type = 'contact' and e.target_id = r.contact_id
                       and e.operation in ('contact.created', 'contact.changed')
                       and e.occurred_at between r.created_at - interval '1 minute' and r.created_at + interval '1 minute') then null
        else (select cast(cast(e.detail as jsonb) ->> 'customerId' as uuid) from helpdesk_audit_event e
              where e.target_type = 'contact' and e.target_id = r.contact_id
                and e.operation in ('contact.created', 'contact.changed') and e.occurred_at < r.created_at
              order by e.occurred_at desc, e.id desc limit 1)
    end;
