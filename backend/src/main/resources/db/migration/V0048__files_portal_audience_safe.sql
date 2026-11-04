-- The audience of attachments switched to the customer before TASK-079 without a time window (TASK-083, DEC-113, finding F1
-- of docs/REVIEW_TASK-080_PR-241.md). Before TASK-079 the switch did not take the lock of the customer link, so a change of
-- the customer that took its time earlier but was held longer than the one minute of V0044 could still have been open; the
-- file then reached the new customer. Holger chose on 3 November 2026 "Ja, so beheben".
--
-- Such a switch meant the customer the ticket had at its time. It keeps an organization only when every change of the ticket
-- field customer up to the time of the switch names that same organization: a change is written with the time it takes
-- before it commits, so every change committed at the switch, and every one still open then, lies at or before it. When the
-- ticket had another customer before, the file stays null and reaches the current customer only through the explicit
-- release of TASK-079.
--
-- Only files whose newest deliberate act is such a switch change. A release for a named organization (attachment.audience,
-- written by every switch since TASK-079) and the origin of a file never switched keep what V0044 gave them, which needs no
-- window.
with act as (
    select distinct on (e.attachment_id) e.attachment_id, e.operation, e.occurred_at
        from file_audit_event e
        where e.operation = 'attachment.audience' or (e.operation = 'attachment.visibility' and e.detail = 'customer')
        order by e.attachment_id, e.id desc
),
audience as (
    select a.attachment_id, case when count(distinct coalesce(cast(t.new_value as jsonb) ->> 'id', '')) = 1
                                 then min(cast(t.new_value as jsonb) ->> 'id') end as customer
        from act a
        join file_attachment f on f.id = a.attachment_id
        left join ticket_change t on t.ticket_id = f.ticket_id and t.field = 'customer' and t.occurred_at <= a.occurred_at
        where a.operation = 'attachment.visibility'
        group by a.attachment_id
)
update file_attachment f set organization_id = cast(nullif(a.customer, '') as uuid)
    from audience a
    where f.id = a.attachment_id and f.visibility = 'customer';
