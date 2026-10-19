-- Owner: identity (TASK-040).
-- The technical marker "administrator" no longer decides anything: the administrative endpoints ask the permissions module
-- for the company rights accounts.manage, session_duration.manage and appearance.manage (DEC-064). The column goes, so
-- that nobody sets it in the belief that it grants access.
-- An installation that was started with TASK-039 has a group "Unternehmensverwaltung" without these three rights and no
-- interface to grant them; such a database is created anew (nothing but development databases exists, DEC-064).
alter table identity_account drop column administrator;
