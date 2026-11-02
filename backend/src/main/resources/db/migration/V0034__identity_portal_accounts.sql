-- Portal accounts (TASK-075, DEC-105): a local account of the kind 'portal' belongs to exactly one customer organization of
-- the helpdesk and optionally to one contact of that organization. Both identifiers are references into the helpdesk module,
-- which the identity module does not read; the application entry point checks them when an account is created. The
-- organization never changes, and a portal account never becomes an internal or a directory account, nor the other way.
alter table identity_account add column kind text not null default 'internal';
alter table identity_account add column organization_id uuid;
alter table identity_account add column contact_id uuid;
alter table identity_account add constraint identity_account_kind check (kind in ('internal', 'portal'));
alter table identity_account add constraint identity_account_portal_organization
    check ((kind = 'portal') = (organization_id is not null));
alter table identity_account add constraint identity_account_portal_contact check (contact_id is null or kind = 'portal');
alter table identity_account add constraint identity_account_portal_local check (kind = 'internal' or auth_kind = 'local');
create index identity_account_organization on identity_account (organization_id) where organization_id is not null;

-- Organization and kind may never change once written; a trigger keeps that true for every writer.
create function identity_account_portal_fixed() returns trigger language plpgsql as $$
begin
    if new.kind is distinct from old.kind or new.organization_id is distinct from old.organization_id then
        raise exception 'kind and organization of an account never change' using errcode = 'check_violation';
    end if;
    return new;
end;
$$;
create trigger identity_account_portal_fixed before update on identity_account
    for each row execute function identity_account_portal_fixed();
