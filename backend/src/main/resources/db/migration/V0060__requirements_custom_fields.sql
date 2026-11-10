-- Custom fields of requirements (TASK-098, DEC-128): the project configuration names them in requirementFields, each revision
-- keeps their values as a JSON object of the key and the canonical text of the value, like the criteria. A field without value
-- has no entry. An approved revision keeps its values for ever. Changes are history events "changed" with the field
-- custom.<key>, so the existing event kinds suffice.
alter table requirement_revision add column custom text not null default '{}'
    check (octet_length(custom) <= 1048576);
