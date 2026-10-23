-- Owner: permissions (TASK-043, finding F4 of its review). Whether the entry knows the state the change replaced.
-- Entries written before V0016 do not; null in previous_effect alone cannot tell "nothing before" from "not recorded".
-- An entry with a recorded previous state keeps it. Entries between V0016 and this migration without one are marked as
-- not recorded too: claiming "nothing before" without proof would be worse than admitting the gap.
ALTER TABLE permissions_audit ADD COLUMN previous_recorded boolean NOT NULL DEFAULT false;
UPDATE permissions_audit SET previous_recorded = true WHERE previous_effect IS NOT NULL;
