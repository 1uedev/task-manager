-- Language versions of a wiki page (TASK-094, DEC-124). The language of wiki_page stays the source language, the first
-- version of the page; a second version in the other language has its own title, draft, revisions and publication in
-- wiki_variant and wiki_revision, which already carry the language. Every revision of a translation remembers which revision
-- of the source language it translates, so a newer publication of the source can be shown as "source changed" with a
-- comparison. Revisions of the source language never carry it. Adding a language is an event of its own.
alter table wiki_revision add column source_revision integer check (source_revision is null or source_revision > 0);

alter table wiki_event drop constraint wiki_event_kind_check;
alter table wiki_event add constraint wiki_event_kind_check
    check (kind in ('created', 'saved', 'published', 'withdrawn', 'reverted', 'moved', 'archived', 'unarchived', 'translated'));
