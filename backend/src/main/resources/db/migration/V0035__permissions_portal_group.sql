-- The group "Portal" (TASK-075, DEC-105): created with the first portal account, found again by its purpose and not by its
-- name, which an administration may change. At most one group of a company has a purpose.
alter table permissions_group add column purpose varchar(20);
alter table permissions_group add constraint permissions_group_purpose check (purpose is null or purpose = 'portal');
create unique index permissions_group_purpose_key on permissions_group (company_id, purpose) where purpose is not null;
