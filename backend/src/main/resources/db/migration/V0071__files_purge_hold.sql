-- The backup service holds the clean up of the file store while it makes a point (TASK-135, finding F4 of the review of
-- TASK-131). Without it the clean up could delete the content of a removed attachment after the boundary of the point, and
-- the point would name a content that its file store no longer holds.

-- A backup that is running. The clean up deletes no content while a hold younger than six hours exists; an older hold is
-- left over from a backup service that ended without removing it and no longer counts.
create table files_purge_hold (
    holder text primary key check (char_length(holder) between 1 and 200),
    started_at timestamptz not null
);

-- The agreed way for the backup service to set and remove its hold. Setting the hold takes the exclusive form of the lock
-- that every deletion of the clean up takes in shared form until its commit: when the call returns, no deletion runs any
-- more and every later one sees the hold.
create function files_purge_hold_begin(p_holder text) returns void
    language plpgsql security definer set search_path = pg_catalog, public as $$
begin
    perform pg_advisory_xact_lock(7310135001);
    insert into files_purge_hold (holder, started_at) values (p_holder, clock_timestamp())
        on conflict (holder) do update set started_at = excluded.started_at;
end;
$$;

create function files_purge_hold_end(p_holder text) returns void
    language plpgsql security definer set search_path = pg_catalog, public as $$
begin
    delete from files_purge_hold where holder = p_holder;
end;
$$;
