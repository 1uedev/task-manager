-- The audience of existing mail answers without a time window (TASK-083, DEC-113, findings F1 and F2 of
-- docs/REVIEW_TASK-080_PR-241.md). V0043 took the organization of the newest creation or change of the contact before an
-- answer and left it unclear only within one minute of a change. Writing an answer does not lock the contact, so a change of
-- the contact that took its time earlier but was held longer than that minute could still have been unseen by the answer;
-- the old answer then reached the new organization (F1). The same window hid safe answers next to the creation of a contact
-- or a mere change of its name or addresses (F2). Holger chose on 3 November 2026 "Ja, so beheben".
--
-- The rule now needs no window: an answer keeps an organization only when every creation and change of its contact up to
-- the time of the answer names that same organization. Any change of the contact writes its audit entry with the time it
-- takes after it locked the contact, and an answer reads its contact before it takes its own time; so every change the answer
-- may have seen, and every change that was still open then, lies at or before the time of the answer. When they all agree,
-- the answer went to that organization; when the contact had moved before, it stays null and reaches no portal. Changes of
-- name or addresses keep the organization and do not count.
--
-- Answers written by the application since V0043 keep the organization it stored at their confirmation, which is exact
-- (F2). The boundary is the time V0043 was installed, as the history of the migrations records it in the time zone of the
-- database session; answers before it, including those of TASK-079 that V0043 recomputed, are computed again by the rule.
with boundary as (
    select coalesce((select installed_on at time zone current_setting('TimeZone') from flyway_schema_history
                      where version = '0043' and success), 'infinity'::timestamptz) as installed
),
audience as (
    select r.id, case when count(distinct coalesce(cast(e.detail as jsonb) ->> 'customerId', '')) = 1
                      then min(cast(e.detail as jsonb) ->> 'customerId') end as customer
        from helpdesk_reply r
        left join helpdesk_audit_event e on e.target_type = 'contact' and e.target_id = r.contact_id
            and e.operation in ('contact.created', 'contact.changed') and e.occurred_at <= r.created_at
        group by r.id
)
update helpdesk_reply r set customer_id = cast(nullif(a.customer, '') as uuid)
    from audience a, boundary b
    where a.id = r.id and r.created_at < b.installed;
