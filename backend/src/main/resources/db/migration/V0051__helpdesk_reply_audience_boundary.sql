-- The boundary of V0047 without the time zone of the session (TASK-084, DEC-114, finding F1 of
-- docs/REVIEW_TASK-083_PR-245.md). V0047 keeps the organization the application stored for a mail answer written after V0043
-- and computes older answers again by the rule of TASK-083. It read the time V0043 was installed (flyway_schema_history,
-- a timestamp without time zone, written as the local time of the session then) in the time zone of the session now. When
-- the two differed, the boundary moved by hours: an old answer with the unreliable organization of V0043 counted as stored
-- by the application and kept it, or a correct answer stored by the application was computed again. Holger chose on
-- 3 November 2026 "Ja, so beheben".
--
-- The boundary now assumes nothing about the time zone. The stored local time lies at most 12 hours behind the instant it
-- names (UTC-12 is the westernmost offset; eastern offsets name an earlier instant), so read as UTC and moved on by 12
-- hours it is never before the real installation. Every answer before it is computed again by the rule of TASK-083: it keeps
-- an organization only when every creation and change of its contact up to its time names that same organization. For an
-- answer the application stored, this gives the stored organization or, when the contact had moved before, none; answers
-- from the hours right after the installation of V0043 to such contacts are therefore hidden. Answers after the boundary
-- keep what the application stored. The rule is the one of V0047, which stays as it is.
with boundary as (
    select coalesce((select (installed_on at time zone 'UTC') + interval '12 hours' from flyway_schema_history
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
