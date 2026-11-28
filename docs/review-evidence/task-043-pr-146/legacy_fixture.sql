-- Only on the disposable fixture of http_probes.py. Emulates the NULL predecessor of an entry created before V0016.
-- This is fixture preparation, not an allegation that an API caller can alter the audit table.
with chosen as (
  select a.id, a.previous_effect from permissions_audit a
  join permissions_group g on g.id=a.group_id
  where g.name='Review target 43' and a.operation='field.read' and a.effect='ALLOW' and a.previous_effect='DENY'
  order by a.rights_version limit 1
), changed as (
  update permissions_audit a set previous_effect=null, occurred_at='2020-01-01T12:00:00Z'
  from chosen c where a.id=c.id
  returning a.id, a.rights_version, c.previous_effect as known_fixture_predecessor, a.previous_effect
)
select row_to_json(changed) from changed;
