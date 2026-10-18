-- Owner: identity (TASK-024, correction R3 of the security review in PR #77).
-- A one time password is consumed by the first successful login. The forced password change then happens
-- inside that restricted session; if the session ends first, an administrator must issue a new password.
alter table identity_account add column one_time_used_at timestamptz;
alter table identity_account add constraint identity_account_one_time_used_pairing
    check (one_time_used_at is null or password_change_required);
