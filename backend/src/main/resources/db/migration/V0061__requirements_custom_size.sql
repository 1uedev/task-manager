-- TASK-100 (DEC-130, F3 of the review of TASK-098): the values of the custom fields of a revision fit together whatever the
-- configuration allows. At most 30 fields of at most 20000 code points each, written as JSON with up to 6 bytes for one code
-- point (an escaped control character), need at most about 3.6 MB; the limit of 4 MiB holds them with room. The service checks
-- the same limit before it writes and answers 400 customFields.too_large, never 500.
alter table requirement_revision drop constraint requirement_revision_custom_check;
alter table requirement_revision add constraint requirement_revision_custom_check check (octet_length(custom) <= 4194304);
