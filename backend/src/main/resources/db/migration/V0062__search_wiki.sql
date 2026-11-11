-- Wiki pages in the search projection (TASK-102, DEC-132). One row per page and one row per language version and stage: the
-- published revision ('published') and the current draft ('draft'). The projection holds no rights; the search checks
-- wiki.read, wiki.edit and wiki.publish of the caller at every search (DEC-069). Text is indexed as terms like the other texts
-- of the projection (DEC-069 addendum): words of letters and digits after NFKC and lower case, without stemming.
create table search_wiki_page (
    page_id uuid primary key,
    company_id uuid not null,
    project_id uuid not null,
    source_language text not null check (source_language in ('de', 'en')),
    archived boolean not null
);

create index search_wiki_page_project on search_wiki_page (company_id, project_id);

create table search_wiki_text (
    page_id uuid not null references search_wiki_page (page_id),
    language text not null check (language in ('de', 'en')),
    stage text not null check (stage in ('published', 'draft')),
    revision integer not null check (revision > 0),
    title text not null,
    body text not null,
    published_at timestamptz,
    title_terms text not null default '',
    body_terms text not null default '',
    title_tsv tsvector generated always as (to_tsvector('simple', title_terms)) stored,
    body_tsv tsvector generated always as (to_tsvector('simple', body_terms)) stored,
    primary key (page_id, language, stage),
    check ((stage = 'published') = (published_at is not null))
);

create index search_wiki_text_title on search_wiki_text using gin (title_tsv);
create index search_wiki_text_body on search_wiki_text using gin (body_tsv);

-- The migration reads no tables of other modules: the next start of the web application builds the projection again with the
-- wiki pages (DEC-069).
delete from search_state;
