-- Fixes after the second review of the AI foundation (TASK-130, DEC-160).

-- F3: which skill applies to a use case and language changes with every activation and every return to the built-in skill. A
-- counter per slot that only grows goes into the stamp of an execution, so that returning to a label used before (builtin-1)
-- never makes an earlier suggestion valid again.
create table ai_skill_selection (
    company_id uuid not null,
    use_case text not null check (use_case in ('ticket_summary', 'reply_draft', 'requirement_review', 'wiki_revision')),
    language text not null check (language in ('de', 'en')),
    selection bigint not null check (selection > 0),
    primary key (company_id, use_case, language)
);
