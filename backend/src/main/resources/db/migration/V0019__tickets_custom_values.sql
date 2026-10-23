-- Custom field values of tickets (TASK-044, DEC-073): the values by field key, each in the canonical text form of its type.
-- A ticket without custom values holds the empty object. The history keeps using ticket_change with the field
-- identifier custom.<key>.
alter table ticket add column custom jsonb not null default '{}'::jsonb check (jsonb_typeof(custom) = 'object');
