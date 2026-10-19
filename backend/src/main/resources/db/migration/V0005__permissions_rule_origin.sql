-- Owner: permissions. Records who last set a field rule so that a project delegate can only withdraw
-- own restrictions. Existing rows stay NULL and therefore count as set by someone else.
ALTER TABLE permissions_field ADD COLUMN set_by uuid;
