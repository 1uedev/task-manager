-- What the backup service may read and call (TASK-136, finding F3 of the review of TASK-132). The operations template
-- creates the role taskmanager_backup (deploy/db-roles.sql) before the first migration; it reads the schema version, the
-- list of stored contents and holds the clean up of the file store, nothing else. Installations without that role, such as
-- the demonstration, get no grant. Only the backup service holds the clean up, so nobody else may call the two functions.

revoke execute on function files_purge_hold_begin(text), files_purge_hold_end(text) from public;

do $$
begin
    if exists (select from pg_roles where rolname = 'taskmanager_backup') then
        grant select on flyway_schema_history, operations_backup_content to taskmanager_backup;
        grant execute on function files_purge_hold_begin(text), files_purge_hold_end(text) to taskmanager_backup;
    end if;
end;
$$;
