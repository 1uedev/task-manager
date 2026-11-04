-- The project action portal.answer (TASK-076, DEC-106): a portal account answers a request of its organization. The group
-- "Portal" created with TASK-075 receives it for all projects, as the accepted proposal 8 names it; the rights version moves on.
insert into permissions_action (company_id, group_id, project_id, action_key)
select g.company_id, g.id, null, 'portal.answer' from permissions_group g
where g.purpose = 'portal'
  and not exists (select 1 from permissions_action a where a.group_id = g.id and a.project_id is null and a.action_key = 'portal.answer');
update permissions_revision r set revision = revision + 1
where exists (select 1 from permissions_group g where g.company_id = r.company_id and g.purpose = 'portal');
