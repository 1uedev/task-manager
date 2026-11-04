-- Service deadlines (TASK-068, DEC-098): business hours calendars and SLA rules with their versions, the priority of a
-- ticket, and the cycles of a ticket with their two clocks, first response and resolution. Tickets, projects and customers
-- are referenced by identifier only; the SLA module never reads their tables.

-- A business hours calendar: time zone, hours per day of the week and dated exceptions as one JSON document. Every change
-- stores the next version; a cycle keeps the version it began with.
create table sla_calendar (
    id uuid primary key,
    company_id uuid not null,
    name text not null check (char_length(name) between 1 and 80),
    definition jsonb not null,
    version bigint not null check (version > 0),
    created_at timestamptz not null,
    created_by uuid not null,
    updated_at timestamptz not null,
    updated_by uuid not null
);
create unique index sla_calendar_name on sla_calendar (company_id, lower(name));

create table sla_calendar_version (
    calendar_id uuid not null references sla_calendar (id),
    version bigint not null check (version > 0),
    definition jsonb not null,
    created_at timestamptz not null,
    created_by uuid not null,
    primary key (calendar_id, version)
);

-- An SLA rule of a project: for every ticket with a customer, for one customer, or for one customer and ticket type; the
-- calendar and the budgets per priority in minutes. A removed rule stays for the cycles that use it.
create table sla_rule (
    id uuid primary key,
    company_id uuid not null,
    name text not null check (char_length(name) between 1 and 80),
    project_id uuid not null,
    customer_id uuid,
    ticket_type text,
    calendar_id uuid not null references sla_calendar (id),
    budgets jsonb not null,
    active boolean not null,
    version bigint not null check (version > 0),
    created_at timestamptz not null,
    created_by uuid not null,
    updated_at timestamptz not null,
    updated_by uuid not null,
    check (ticket_type is null or customer_id is not null)
);
-- Two rules that match the same tickets equally well cannot both be active.
create unique index sla_rule_match on sla_rule (company_id, project_id, coalesce(customer_id, '00000000-0000-0000-0000-000000000000'::uuid),
    coalesce(ticket_type, '')) where active;

create table sla_rule_version (
    rule_id uuid not null references sla_rule (id),
    version bigint not null check (version > 0),
    calendar_id uuid not null references sla_calendar (id),
    budgets jsonb not null,
    created_at timestamptz not null,
    created_by uuid not null,
    primary key (rule_id, version)
);

-- What the module knows of a ticket: its priority and the status with its kind it saw last, to tell changes apart.
create table sla_ticket (
    ticket_id uuid primary key,
    company_id uuid not null,
    project_id uuid not null,
    priority text not null check (priority in ('low', 'normal', 'high', 'critical')),
    status text,
    status_kind text check (status_kind in ('open', 'waiting', 'resolved', 'cancelled'))
);

-- One cycle of work on a ticket. At most one cycle of a ticket is open (no outcome).
create table sla_cycle (
    id uuid primary key,
    company_id uuid not null,
    project_id uuid not null,
    ticket_id uuid not null,
    number integer not null check (number > 0),
    rule_id uuid not null references sla_rule (id),
    rule_version bigint not null,
    calendar_id uuid not null references sla_calendar (id),
    calendar_version bigint not null,
    priority text not null check (priority in ('low', 'normal', 'high', 'critical')),
    started_at timestamptz not null,
    replied_at timestamptz,
    resolved_since timestamptz,
    outcome text check (outcome in ('met', 'cancelled')),
    reason text,
    closed_at timestamptz,
    unique (ticket_id, number),
    foreign key (rule_id, rule_version) references sla_rule_version (rule_id, version),
    foreign key (calendar_id, calendar_version) references sla_calendar_version (calendar_id, version),
    check ((outcome is null) = (closed_at is null))
);
create unique index sla_cycle_open on sla_cycle (ticket_id) where outcome is null;

-- The two clocks of a cycle.
create table sla_clock (
    cycle_id uuid not null references sla_cycle (id),
    kind text not null check (kind in ('first_response', 'resolution')),
    state text not null check (state in ('running', 'paused', 'met', 'cancelled')),
    budget_seconds bigint not null check (budget_seconds > 0),
    consumed_seconds bigint not null check (consumed_seconds >= 0),
    checkpoint_at timestamptz not null,
    due_at timestamptz,
    paused_at timestamptz,
    met_at timestamptz,
    breached_at timestamptz,
    primary key (cycle_id, kind),
    check ((state = 'running') = (due_at is not null) or state in ('met', 'cancelled')),
    check ((state = 'paused') = (paused_at is not null)),
    check ((state = 'met') = (met_at is not null))
);
create index sla_clock_due on sla_clock (due_at) where state = 'running';

create table sla_audit_event (
    id bigint generated always as identity primary key,
    company_id uuid not null,
    target_type text not null,
    target_id uuid not null,
    operation text not null,
    detail text,
    actor_account_id uuid,
    occurred_at timestamptz not null
);
create index sla_audit_event_target on sla_audit_event (target_id, id);
