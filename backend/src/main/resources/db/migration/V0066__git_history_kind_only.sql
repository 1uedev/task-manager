-- The history of Git links names only the kind of the object (TASK-114, DEC-144, finding F6 of the review of TASK-112).
-- Until now an entry also held the internal identifier of the link ("commit:<id>"), which allowed a reader of the history to
-- relate it to the list of links and to count hidden objects. Existing entries keep their place and time and lose only the
-- identifier; new entries are written as the kind alone.
update ticket_change set old_value = split_part(old_value, ':', 1) where field = 'git' and old_value like '%:%';
update ticket_change set new_value = split_part(new_value, ':', 1) where field = 'git' and new_value like '%:%';
update requirement_event set from_value = split_part(from_value, ':', 1)
    where kind in ('git_linked', 'git_unlinked') and from_value like '%:%';
update requirement_event set to_value = split_part(to_value, ':', 1)
    where kind in ('git_linked', 'git_unlinked') and to_value like '%:%';
