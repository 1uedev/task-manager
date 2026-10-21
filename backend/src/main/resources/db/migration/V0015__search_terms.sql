-- Search terms (TASK-029, finding F1 of the independent review, DEC-069 addendum): the search module stores the words of
-- every searchable text as the search splits a query (Unicode NFKC, lower case, words of letters and digits) and builds
-- the vectors from these terms, so a search and the index agree on every word. The original values stay for the answers.
-- The migration reads no tables of other modules: it forgets that the projection was built, and the next start of the web
-- application builds it again with the terms.
alter table search_tag add column terms text not null default '';

alter table search_ticket drop column title_tsv;
alter table search_ticket drop column description_tsv;
alter table search_ticket drop column tags_tsv;
alter table search_ticket add column title_terms text not null default '';
alter table search_ticket add column description_terms text not null default '';
-- tags_text now holds the terms of the assigned tags
alter table search_ticket add column title_tsv tsvector generated always as (to_tsvector('simple', title_terms)) stored;
alter table search_ticket add column description_tsv tsvector generated always as (to_tsvector('simple', description_terms)) stored;
alter table search_ticket add column tags_tsv tsvector generated always as (to_tsvector('simple', tags_text)) stored;
create index search_ticket_title on search_ticket using gin (title_tsv);
create index search_ticket_description on search_ticket using gin (description_tsv);
create index search_ticket_tags on search_ticket using gin (tags_tsv);

alter table search_project drop column document;
alter table search_project add column terms text not null default '';
alter table search_project add column document tsvector generated always as (
    to_tsvector('simple', terms || ' ' || tags_text)) stored;
create index search_project_document on search_project using gin (document);

update search_ticket set tags_text = '';
update search_project set tags_text = '';
delete from search_state;
