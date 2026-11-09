-- The wiki of a project (TASK-093, DEC-123). A page has a fixed identifier, at most one parent page in the same project and
-- one language version per language (TASK-093 creates exactly one, TASK-094 adds a second). Every save of the shared draft of
-- a language adds a revision that is never changed again; publishing points the readers at one revision. Archiving hides a
-- page and keeps everything; nothing is ever deleted.
create table wiki_page (
    id uuid primary key,
    company_id uuid not null,
    project_id uuid not null,
    parent_id uuid references wiki_page (id),
    language text not null check (language in ('de', 'en')),
    created_at timestamptz not null,
    created_by uuid not null,
    archived_at timestamptz,
    archived_by uuid,
    version bigint not null check (version > 0),
    check ((archived_at is null) = (archived_by is null)),
    check (parent_id is null or parent_id <> id)
);
create index wiki_page_project on wiki_page (company_id, project_id);
create index wiki_page_parent on wiki_page (parent_id) where parent_id is not null;

-- Every saved state of the draft of one language of a page. The title belongs to the revision, so a new title reaches the
-- readers only with the next publication. The content is the checked and normalized document of the editor.
create table wiki_revision (
    page_id uuid not null references wiki_page (id),
    language text not null check (language in ('de', 'en')),
    revision integer not null check (revision > 0),
    title text not null check (char_length(title) between 1 and 200),
    content text not null check (octet_length(content) <= 1048576),
    schema_version integer not null check (schema_version > 0),
    saved_at timestamptz not null,
    saved_by uuid not null,
    based_on integer,
    restored_from integer,
    primary key (page_id, language, revision)
);

-- The language versions of a page: the newest revision is the shared draft, the published one is what readers see.
create table wiki_variant (
    page_id uuid not null references wiki_page (id),
    language text not null check (language in ('de', 'en')),
    draft_revision integer not null check (draft_revision > 0),
    published_revision integer,
    published_at timestamptz,
    published_by uuid,
    primary key (page_id, language),
    check ((published_revision is null) = (published_at is null) and (published_at is null) = (published_by is null)),
    check (published_revision is null or published_revision <= draft_revision)
);

-- The images uploaded to a page and the images each revision shows. An image is delivered to readers only while a published
-- revision of its page shows it, to people with drafts while it belongs to the page.
create table wiki_image (
    id uuid primary key,
    company_id uuid not null,
    page_id uuid not null references wiki_page (id),
    uploaded_at timestamptz not null,
    uploaded_by uuid not null
);
create index wiki_image_page on wiki_image (page_id);

create table wiki_revision_image (
    page_id uuid not null,
    language text not null,
    revision integer not null,
    image_id uuid not null references wiki_image (id),
    primary key (page_id, language, revision, image_id),
    foreign key (page_id, language, revision) references wiki_revision (page_id, language, revision)
);
create index wiki_revision_image_image on wiki_revision_image (image_id);

-- What happened to a page with person and time: created, saved, published, withdrawn, restored as draft, moved, archived
-- and restored.
create table wiki_event (
    id bigserial primary key,
    company_id uuid not null,
    page_id uuid not null references wiki_page (id),
    kind text not null check (kind in ('created', 'saved', 'published', 'withdrawn', 'reverted', 'moved', 'archived', 'unarchived')),
    language text check (language is null or language in ('de', 'en')),
    revision integer,
    actor_id uuid not null,
    occurred_at timestamptz not null
);
create index wiki_event_page on wiki_event (page_id, id);
