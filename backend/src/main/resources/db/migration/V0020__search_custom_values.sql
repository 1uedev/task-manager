-- Custom field values in the search projection (TASK-044, DEC-073): one row per custom value of a ticket. Text values are
-- indexed as terms like the other texts of the projection; every value can be filtered by its text. The projection holds
-- no rights (DEC-069).
create table search_ticket_custom (
    ticket_id uuid not null references search_ticket (ticket_id),
    field_key text not null,
    value text not null,
    terms text not null default '',
    tsv tsvector generated always as (to_tsvector('simple', terms)) stored,
    primary key (ticket_id, field_key)
);

create index search_ticket_custom_value on search_ticket_custom (field_key, value);
create index search_ticket_custom_tsv on search_ticket_custom using gin (tsv);

-- The migration reads no tables of other modules: the next start of the web application builds the projection again with
-- the custom values (DEC-069).
delete from search_state;
