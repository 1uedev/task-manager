-- Owner: identity (TASK-049, DEC-078).
-- The key that assigns an account to a directory entry changed its form with V0022 (DEC-077). A key stored before that cannot
-- tell whether it came from text that was trimmed or from bytes that were read as text, so it may be the same as the key of
-- another entry. No such account can be trusted to belong to the entry it names. Every directory account that exists now is
-- detached from the directory and deactivated, and its sessions end. An administration issues a one time password with the
-- reset and activates the account again for the person it belongs to; from then on the local password decides.
-- An installation without directory accounts is not touched.
with detached as (
    update identity_account
    set auth_kind = 'local', external_id = null, active = false, updated_at = now()
    where auth_kind = 'ldap'
    returning id
), ended as (
    update identity_session
    set revoked_at = now(), revoke_reason = 'directory.link_reset'
    where revoked_at is null and account_id in (select id from detached)
)
insert into identity_audit_event (occurred_at, actor, event, account_id, detail)
select now(), 'migration', 'account.directory_link_reset', id, null from detached;
