-- Database roles of the operations template (TASK-136, finding F3 of the review of TASK-132). Run by db-roles.sh as the
-- administration account of the database (POSTGRES_USER), at the creation of the database and again after an update of
-- the template; every statement can run any number of times.
--
--   POSTGRES_USER         administration only: creates roles, owns the database and the extension pg_trgm. No process of
--                         the application uses it.
--   taskmanager_migrate   owns the schema public and its objects; only the command "migrate" uses it.
--   taskmanager_app       the web process, the worker and the other commands: reads and writes rows of the business tables,
--                         never changes the schema, owns nothing and manages no roles. It only reads the history of the
--                         migrations and the hold of the clean up, and may not call the hold functions (TASK-137, N3).
--   taskmanager_backup    the backup service: replication for the base backup, the restore point and the switch of the
--                         write ahead log, and reading only what backup.sh reads (granted by migration V0072).
--
-- The psql variables app_password, migrate_password, backup_password and owner come from db-roles.sh.

-- The passwords below are literals of their statements. PostgreSQL never writes a statement of this session into its log,
-- also not when a statement fails or the server logs every statement (TASK-137, finding N12 of the review of TASK-136).
set log_statement = 'none';
set log_min_error_statement = 'panic';
set log_min_duration_statement = -1;
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

-- The extension pg_trgm of V0033 belongs to the administration account in every installation (TASK-137, N3): PostgreSQL
-- cannot hand an extension to another owner, an installation from before TASK-136 created it with that account, and a new
-- one creates it here before the first migration, so that V0033 finds it. No migration changes it.
create extension if not exists pg_trgm with schema public;

-- The migration role owns the schema public; only it creates objects there (TASK-137, N3).
alter schema public owner to taskmanager_migrate;
revoke create on schema public from public;
grant usage, create on schema public to taskmanager_migrate;
grant usage on schema public to taskmanager_app, taskmanager_backup;
revoke all on database :"DBNAME" from public;
grant connect on database :"DBNAME" to taskmanager_migrate, taskmanager_app, taskmanager_backup;
-- The trusted extension pg_trgm of V0033 needs the right to create in the database and some migrations use temporary
-- tables; neither needs ownership or superuser.
grant create, temporary on database :"DBNAME" to taskmanager_migrate;

-- An installation from before TASK-136: the objects belong to the administration account, which ran the migrations then.
-- They move to the migration role, so that the next migrations can change them. Sequences bound to a column follow their
-- table; objects of an extension stay with the extension (TASK-137, N3).
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
          and not exists (select from pg_depend d where d.classid = 'pg_proc'::regclass and d.objid = p.oid and d.deptype = 'e')
        union all
        select format('alter type %I.%I owner to taskmanager_migrate', n.nspname, t.typname)
        from pg_type t join pg_namespace n on n.oid = t.typnamespace
        where n.nspname = 'public' and t.typtype in ('e', 'd', 'c') and pg_get_userbyid(t.typowner) = owner
          and not exists (select from pg_class c where c.oid = t.typrelid and c.relkind <> 'c')
          and not exists (select from pg_depend d where d.classid = 'pg_type'::regclass and d.objid = t.oid and d.deptype = 'e')
    loop
        execute item.statement;
    end loop;
    -- The script of TASK-136 also moved routines of the extension; they go back to the owner of the extension.
    for item in
        select format('alter routine %I.%I(%s) owner to %I', n.nspname, p.proname, pg_get_function_identity_arguments(p.oid),
                      pg_get_userbyid(e.extowner)) as statement
        from pg_proc p join pg_namespace n on n.oid = p.pronamespace
        join pg_depend d on d.classid = 'pg_proc'::regclass and d.objid = p.oid and d.deptype = 'e'
        join pg_extension e on e.oid = d.refobjid
        where p.proowner <> e.extowner
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
-- Not its work (TASK-137, N3; migration V0073 does the same when the migrations create these objects): the history of the
-- migrations and the hold of the clean up are only read, the hold functions belong to the backup service.
do $$
begin
    if to_regclass('public.flyway_schema_history') is not null then
        revoke insert, update, delete, truncate, references, trigger on public.flyway_schema_history from taskmanager_app;
    end if;
    if to_regclass('public.files_purge_hold') is not null then
        revoke insert, update, delete, truncate, references, trigger on public.files_purge_hold from taskmanager_app;
    end if;
    if to_regprocedure('public.files_purge_hold_begin(text)') is not null then
        revoke execute on function public.files_purge_hold_begin(text), public.files_purge_hold_end(text) from taskmanager_app;
    end if;
end;
$$;

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
