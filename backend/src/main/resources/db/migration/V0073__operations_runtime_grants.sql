-- What the runtime role may do with the technical tables of the operations template (TASK-137, finding N3 of the review of
-- TASK-136). deploy/db-roles.sql gives taskmanager_app the rows of every table and the right to call every function, also for
-- what the migrations create later. Two tables and two functions are not its work: the history of the migrations belongs to
-- the command "migrate", and the hold of the clean up belongs to the backup service. The runtime role keeps reading both:
-- the start reads the history to check the schema, and the clean up of the file store reads whether a hold exists.
-- Installations without that role, such as the demonstration, are not touched.

do $$
begin
    if exists (select from pg_roles where rolname = 'taskmanager_app') then
        revoke insert, update, delete, truncate, references, trigger on flyway_schema_history, files_purge_hold
            from taskmanager_app;
        grant select on flyway_schema_history, files_purge_hold to taskmanager_app;
        revoke execute on function files_purge_hold_begin(text), files_purge_hold_end(text) from taskmanager_app;
    end if;
end;
$$;
