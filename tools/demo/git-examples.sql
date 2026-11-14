-- Example Git links of the demonstration (TASK-113, DEC-143, rule 3). Run once after "demo seed --profile demo":
--     docker compose exec -T db psql -U taskmanager -d taskmanager -v ON_ERROR_STOP=1 < tools/demo/git-examples.sql
--
-- Releasing a repository and linking an object needs a connection that can read it, which a fresh demonstration does not
-- have and cannot have without a registered GitHub App and GitLab.com application. So this script stores what the API would
-- store after such a check: only the instance, the stable identifier of the public repository, the kind and the full SHA
-- or the number, never a name, path or title. Without a connection the links appear as "nicht verfügbar"; once the person
-- connected the own account they show the live data of the provider. The history entries name the links as the API does.
--
-- The objects are public: the repository of this project at GitHub.com (1uedev/task-manager, the merge commit of
-- pull request 317) and the project gitlab-org/gitlab at GitLab.com (merge request 1 and the last commit of its branch).
begin;

-- Stops with an error when the demo data set or the two instances are missing, or when the examples exist already.
do $$
begin
    if (select count(*) from git_provider where removed_at is null and name in ('GitHub.com', 'GitLab.com')) <> 2 then
        raise exception 'the instances GitHub.com and GitLab.com of the demo data set are missing; run demo seed first';
    end if;
    if not exists (select 1 from project where key = 'WEB') or not exists (select 1 from project where key = 'HELP') then
        raise exception 'the projects WEB and HELP of the demo data set are missing; run demo seed first';
    end if;
    if exists (select 1 from git_link) then
        raise exception 'Git links exist already; the examples were stored before';
    end if;
end $$;

create temporary table example (
    project_key text, subject_kind text, number bigint, provider_name text, repository_id text, object_kind text, object_key text,
    position int
) on commit drop;
insert into example values
    ('WEB', 'ticket', 1, 'GitHub.com', '1416618579', 'repository', '', 1),
    ('WEB', 'ticket', 1, 'GitHub.com', '1416618579', 'commit', 'ecbf9b8387afe27ce9b2518f1af94eadb1c45ce9', 2),
    ('WEB', 'ticket', 1, 'GitHub.com', '1416618579', 'pull_request', '317', 3),
    ('HELP', 'requirement', 1, 'GitLab.com', '278964', 'merge_request', '1', 4),
    ('HELP', 'requirement', 1, 'GitLab.com', '278964', 'commit', '0e91c7eb0fd77e56b7cfdd4fd0be0fce9e1c8a6c', 5);

create temporary table stored on commit drop as
select gen_random_uuid() as id, p.company_id, e.subject_kind,
       case e.subject_kind when 'ticket' then t.id else r.id end as subject_id,
       p.id as project_id, g.id as provider_id, e.repository_id, e.object_kind, e.object_key,
       a.id as actor, clock_timestamp() + make_interval(secs => e.position / 1000.0) as linked_at, r.current_revision
from example e
join project p on p.key = e.project_key
join git_provider g on g.name = e.provider_name and g.removed_at is null
cross join (select id from identity_account where username = 'admin') a
left join ticket t on e.subject_kind = 'ticket' and t.project_id = p.id and t.number = e.number
left join requirement r on e.subject_kind = 'requirement' and r.project_id = p.id and r.number = e.number;

do $$
begin
    if (select count(*) from stored where subject_id is not null and actor is not null) <> 5 then
        raise exception 'ticket WEB-1, requirement ANF-1 of HELP or the account admin is missing';
    end if;
end $$;

-- The release of each repository for its project, as "Repository freigeben" stores it.
insert into git_repository (id, company_id, project_id, provider_id, external_id, released_at, released_by)
select gen_random_uuid(), company_id, project_id, provider_id, repository_id, min(linked_at), min(actor::text)::uuid
from stored group by company_id, project_id, provider_id, repository_id;

insert into git_link (id, company_id, subject_kind, subject_id, project_id, provider_id, repository_id, object_kind, object_key,
                      linked_at, linked_by)
select id, company_id, subject_kind, subject_id, project_id, provider_id, repository_id, object_kind, object_key, linked_at, actor
from stored;

-- History: the ticket names the link at its current version, the requirement at its current revision.
insert into ticket_change (ticket_id, version, occurred_at, actor_account_id, field, old_value, new_value)
select s.subject_id, t.version, s.linked_at, s.actor, 'git', null, s.object_kind || ':' || s.id
from stored s join ticket t on t.id = s.subject_id
where s.subject_kind = 'ticket';

insert into requirement_event (company_id, requirement_id, revision, kind, field, criterion, from_value, to_value, reason, actor_id,
                               occurred_at)
select company_id, subject_id, current_revision, 'git_linked', 'git', null, null, object_kind || ':' || id, null, actor, linked_at
from stored
where subject_kind = 'requirement';

commit;

select 'Git examples stored: ' || count(*) || ' links' from git_link;
