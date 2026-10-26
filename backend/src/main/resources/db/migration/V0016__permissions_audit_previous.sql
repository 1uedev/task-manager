-- Owner: permissions (TASK-043, P2). The state a change replaced: ALLOW or DENY, null when there was none.
-- Entries written before this migration keep null; their previous state is not known.
ALTER TABLE permissions_audit ADD COLUMN previous_effect varchar(10) CHECK (previous_effect IN ('ALLOW', 'DENY'));
