-- Owner: identity (TASK-048, DEC-077).
-- Directory accounts whose stored identifier lost bytes (a binary attribute such as objectGUID was read as text and different
-- values became the same replacement characters) cannot be trusted to name one person. They are detached from the directory,
-- deactivated and their sessions end; an administration can issue a password for the account and activate it again if it belongs
-- to the person it is meant for. Identifiers of text attributes such as entryUUID are not touched.
with unreliable as (
    update identity_account
    set auth_kind = 'local', external_id = null, active = false, updated_at = now()
    where external_id ~ E'[\\u0001-\\u001f\\u007f-\\u009f\\ufffd]'
    returning id
), ended as (
    update identity_session
    set revoked_at = now(), revoke_reason = 'directory.identifier_unreliable'
    where revoked_at is null and account_id in (select id from unreliable)
)
insert into identity_audit_event (occurred_at, actor, event, account_id, detail)
select now(), 'migration', 'account.directory_identifier_unreliable', id, null from unreliable;
