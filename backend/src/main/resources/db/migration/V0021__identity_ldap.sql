-- Owner: identity (TASK-046, DEC-075).
-- LDAP sign in: accounts that the directory authenticates and the one connection setting.

-- An account is either local (its password hash decides) or from the directory. A directory account is assigned by the
-- immutable identifier of the directory entry, never by the login name; it has no usable local password.
alter table identity_account
    add column auth_kind text not null default 'local',
    add column external_id text,
    add constraint identity_account_auth_kind check (auth_kind in ('local', 'ldap')),
    add constraint identity_account_external_pairing check ((auth_kind = 'ldap') = (external_id is not null)),
    add constraint identity_account_external_length check (external_id is null or char_length(external_id) between 1 and 512);
create unique index identity_account_external_key on identity_account (external_id) where external_id is not null;

-- One row at most. The password of the search account is not stored here; it comes from the environment of the operation.
create table identity_ldap_setting (
    id smallint primary key,
    enabled boolean not null,
    url text not null,
    security text not null,
    bind_dn text,
    search_base text not null,
    user_filter text not null,
    id_attribute text not null,
    name_attribute text,
    updated_at timestamptz not null,
    constraint identity_ldap_setting_single check (id = 1),
    constraint identity_ldap_setting_security check (security in ('none', 'starttls', 'ldaps')),
    constraint identity_ldap_setting_lengths check (
        char_length(url) between 1 and 300 and char_length(search_base) between 1 and 500 and char_length(user_filter) between 1 and 500
        and char_length(id_attribute) between 1 and 100 and (bind_dn is null or char_length(bind_dn) between 1 and 500)
        and (name_attribute is null or char_length(name_attribute) between 1 and 100))
);
