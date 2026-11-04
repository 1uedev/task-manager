-- Tags in the portal (TASK-077, DEC-108): every tag is internal (the default) or visible to customers, and a tag a portal
-- account created belongs to its customer organization until the support releases it for all customers. The tables stay
-- owned by the tags module; the organization is referenced by identifier only.
alter table tag
    add column visibility text not null default 'internal' check (visibility in ('internal', 'customer')),
    -- null for every customer; else the only organization that sees the tag in the portal, always of a project catalog
    add column organization_id uuid,
    add constraint tag_organization_customer check (organization_id is null or (visibility = 'customer' and project_id is not null));

-- A name exists once per catalog and audience: the tags of an organization have names of their own, so a name in the portal
-- never shows that an internal tag or a tag of another organization has it.
drop index tag_name_per_catalog;
create unique index tag_name_per_catalog on tag (company_id, coalesce(project_id, '00000000-0000-0000-0000-000000000000'::uuid),
    coalesce(organization_id, '00000000-0000-0000-0000-000000000000'::uuid), normalized)
    where merged_into is null;
