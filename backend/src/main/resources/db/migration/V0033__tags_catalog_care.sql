-- Catalog care of tags (TASK-072, DEC-102): description, color from a fixed palette, merging and the trigram index for the
-- suggestions. The tables stay owned by the tags module.

-- Trusted extension since PostgreSQL 13: the owner of the database may create it without being a superuser.
create extension if not exists pg_trgm;

alter table tag
    add column description text check (description is null or char_length(description) between 1 and 500),
    -- the name of a color of the palette, never a free color value
    add column color text check (color is null or color in ('red', 'orange', 'amber', 'yellow', 'lime', 'green', 'teal', 'cyan',
        'blue', 'indigo', 'violet', 'pink')),
    -- a merged tag points to the tag that took its assignments; it has no assignments and no place in its catalog any more
    add column merged_into uuid references tag (id),
    add column merged_at timestamptz,
    add constraint tag_merge_complete check ((merged_into is null) = (merged_at is null)),
    add constraint tag_merge_not_self check (merged_into is null or merged_into <> id);

-- A merged tag frees its name: the name exists once per catalog among the tags that are not merged.
drop index tag_name_per_catalog;
create unique index tag_name_per_catalog on tag (company_id, coalesce(project_id, '00000000-0000-0000-0000-000000000000'::uuid), normalized)
    where merged_into is null;

-- Parts of names anywhere in the name, for the suggestions and the catalog search.
create index tag_normalized_trgm on tag using gin (normalized gin_trgm_ops);

create index tag_merged_into on tag (merged_into) where merged_into is not null;
