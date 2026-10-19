-- Owner: platform (TASK-039).
-- The one company of this installation (DEC-006, REQ-ORG-001). The identifier is created once by the container command
-- bootstrap-admin, together with the rights revision of the company, and is never taken from a request.
-- The constant column and its unique constraint make a second row impossible.
create table platform_company (
    id uuid primary key,
    only_company boolean not null default true,
    created_at timestamptz not null default now(),
    constraint platform_company_only_one check (only_company),
    constraint platform_company_single unique (only_company)
);
