-- Database roles of the operations template (TASK-136, finding F3 of the review of TASK-132). Run by db-roles.sh as the
-- administration account of the database (POSTGRES_USER), at the creation of the database and again after an update of
-- the template; every statement can run any number of times.
--
--   POSTGRES_USER         administration only: creates roles, owns the database. No process of the application uses it.
--   taskmanager_migrate   owns the schema and its objects; only the command "migrate" uses it.
--   taskmanager_app       the web process, the worker and the other commands: reads and writes rows, never changes the
--                         schema, owns nothing and manages no roles.
--   taskmanager_backup    the backup service: replication for the base backup, the restore point and the switch of the
--                         write ahead log, and reading only what backup.sh reads (granted by migration V0072).
--
-- The psql variables app_password, migrate_password, backup_password and owner come from db-roles.sh.

select set_config('taskmanager.owner', :'owner', false);

do $$
begin
    if not exists (select from pg_roles where rolname = 'taskmanager_migrate') then
        create role taskmanager_migrate login;
    end if;
    if not exists (select from pg_roles where rolname = 'taskmanager_app') then
        create role taskmanager_app login;
    end if;
    if not exists (select from pg_roles where rolname = 'taskmanager_backup') then
        create role taskmanager_backup login replication;
    end if;
end;
$$;

alter role taskmanager_migrate with login nosuperuser nocreatedb nocreaterole noreplication nobypassrls password :'migrate_password';
alter role taskmanager_app with login nosuperuser nocreatedb nocreaterole noreplication nobypassrls password :'app_password';
alter role taskmanager_backup with login replication nosuperuser nocreatedb nocreaterole nobypassrls password :'backup_password';

-- Only the migration role creates objects; nobody else may create in the schema.
revoke create on schema public from public;
grant usage, create on schema public to taskmanager_migrate;
grant usage on schema public to taskmanager_app, taskmanager_backup;
revoke all on database :"DBNAME" from public;
grant connect on database :"DBNAME" to taskmanager_migrate, taskmanager_app, taskmanager_backup;
-- The trusted extension pg_trgm of V0033 needs the right to create in the database and some migrations use temporary
-- tables; neither needs ownership or superuser.
grant create, temporary on database :"DBNAME" to taskmanager_migrate;

-- An installation from before TASK-136: the objects belong to the administration account, which ran the migrations then.
-- They move to the migration role, so that the next migrations can change them.
do $$
declare
    owner text := current_setting('taskmanager.owner');
    item record;
begin
    for item in
        select format('alter table %I.%I owner to taskmanager_migrate', n.nspname, c.relname) as statement
        from pg_class c join pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relkind in ('r', 'p', 'v', 'm', 'f')
          and pg_get_userbyid(c.relowner) = owner
        union all
        select format('alter sequence %I.%I owner to taskmanager_migrate', n.nspname, c.relname)
        from pg_class c join pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relkind = 'S' and pg_get_userbyid(c.relowner) = owner
          and not exists (select from pg_depend d where d.objid = c.oid and d.deptype in ('a', 'i'))
        union all
        select format('alter routine %I.%I(%s) owner to taskmanager_migrate', n.nspname, p.proname,
                      pg_get_function_identity_arguments(p.oid))
        from pg_proc p join pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'public' and pg_get_userbyid(p.proowner) = owner
        union all
        select format('alter type %I.%I owner to taskmanager_migrate', n.nspname, t.typname)
        from pg_type t join pg_namespace n on n.oid = t.typnamespace
        where n.nspname = 'public' and t.typtype in ('e', 'd', 'c') and pg_get_userbyid(t.typowner) = owner
          and not exists (select from pg_class c where c.oid = t.typrelid and c.relkind <> 'c')
    loop
        execute item.statement;
    end loop;
end;
$$;

-- The runtime role: rows of every table, the sequences, the functions; for what exists and for what the migrations create.
grant select, insert, update, delete on all tables in schema public to taskmanager_app;
grant usage, select, update on all sequences in schema public to taskmanager_app;
grant execute on all routines in schema public to taskmanager_app;
alter default privileges for role taskmanager_migrate in schema public
    grant select, insert, update, delete on tables to taskmanager_app;
alter default privileges for role taskmanager_migrate in schema public
    grant usage, select, update on sequences to taskmanager_app;
alter default privileges for role taskmanager_migrate in schema public
    grant execute on routines to taskmanager_app;

-- The backup role reads only what backup.sh reads; the template of TASK-132 gave it every table, which ends here.
alter default privileges for role :"owner" in schema public revoke select on tables from taskmanager_backup;
revoke select on all tables in schema public from taskmanager_backup;
grant execute on function pg_switch_wal() to taskmanager_backup;
grant execute on function pg_create_restore_point(text) to taskmanager_backup;
do $$
begin
    if to_regclass('public.flyway_schema_history') is not null then
        grant select on public.flyway_schema_history to taskmanager_backup;
    end if;
    if to_regclass('public.operations_backup_content') is not null then
        grant select on public.operations_backup_content to taskmanager_backup;
    end if;
    if to_regprocedure('public.files_purge_hold_begin(text)') is not null then
        grant execute on function public.files_purge_hold_begin(text), public.files_purge_hold_end(text) to taskmanager_backup;
    end if;
end;
$$;
