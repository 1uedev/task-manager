-- Escalations of service deadlines (TASK-070): the stages of a rule per clock, part of each rule version, and the stages a
-- cycle reached. Groups are referenced by identifier only; the SLA module never reads the tables of the permissions.

alter table sla_rule add column escalations jsonb not null default '[]';
alter table sla_rule_version add column escalations jsonb not null default '[]';

-- A stage a clock of a cycle reached. `stage` is its position in the stages of the rule version of the cycle, so each stage
-- is reached at most once (rule 3). A stage passed over after an outage is kept as `skipped` and shown nowhere.
create table sla_escalation (
    id uuid primary key,
    company_id uuid not null,
    project_id uuid not null,
    ticket_id uuid not null,
    cycle_id uuid not null references sla_cycle (id),
    kind text not null check (kind in ('first_response', 'resolution')),
    stage integer not null check (stage >= 0),
    percent integer not null check (percent between 1 and 1000),
    group_id uuid not null,
    threshold_at timestamptz not null,
    reached_at timestamptz not null,
    skipped boolean not null,
    unique (cycle_id, kind, stage)
);
create index sla_escalation_group on sla_escalation (company_id, group_id, reached_at desc) where not skipped;
create index sla_escalation_ticket on sla_escalation (ticket_id);
