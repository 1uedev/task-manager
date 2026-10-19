-- Owner: permissions. Records whether a field rule was set with company administration or as a project
-- delegate, so a former company administrator who keeps a delegation cannot lift a company rule. Existing rows
-- stay NULL and therefore count as set with company administration (protected).
ALTER TABLE permissions_field ADD COLUMN set_by_company boolean;
