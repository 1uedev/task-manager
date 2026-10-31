package taskmanager.sla.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** The tables of the SLA module. Nobody else reads them. */
@Repository
class SlaRepository {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID NONE = new UUID(0, 0);

    private final JdbcClient jdbc;

    SlaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------------------ calendars

    List<Calendar> calendars(UUID companyId) {
        return jdbc.sql("select * from sla_calendar where company_id = ? order by lower(name), id").param(companyId)
            .query((rs, row) -> calendar(rs)).list();
    }

    Optional<Calendar> calendar(UUID companyId, UUID id) {
        return jdbc.sql("select * from sla_calendar where company_id = ? and id = ?").params(companyId, id)
            .query((rs, row) -> calendar(rs)).optional();
    }

    Optional<Calendar> lockCalendar(UUID companyId, UUID id) {
        return jdbc.sql("select * from sla_calendar where company_id = ? and id = ? for update").params(companyId, id)
            .query((rs, row) -> calendar(rs)).optional();
    }

    boolean calendarNameTaken(UUID companyId, String name, UUID except) {
        return jdbc.sql("select count(*) from sla_calendar where company_id = ? and lower(name) = lower(?) and id <> ?")
            .params(companyId, name, except == null ? NONE : except).query(Long.class).single() > 0;
    }

    void insertCalendar(Calendar calendar) {
        jdbc.sql("""
                insert into sla_calendar (id, company_id, name, definition, version, created_at, created_by, updated_at, updated_by)
                values (?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?)
                """).params(calendar.id(), calendar.companyId(), calendar.name(), JSON.writeValueAsString(calendar.definition()),
                calendar.version(), ts(calendar.createdAt()), calendar.createdBy(), ts(calendar.updatedAt()), calendar.updatedBy()).update();
        insertCalendarVersion(calendar);
    }

    /** Stores the change and its version; a cycle that starts later uses this version. */
    void updateCalendar(Calendar calendar) {
        jdbc.sql("update sla_calendar set name = ?, definition = cast(? as jsonb), version = ?, updated_at = ?, updated_by = ? where id = ?")
            .params(calendar.name(), JSON.writeValueAsString(calendar.definition()), calendar.version(), ts(calendar.updatedAt()),
                calendar.updatedBy(), calendar.id()).update();
        insertCalendarVersion(calendar);
    }

    private void insertCalendarVersion(Calendar calendar) {
        jdbc.sql("insert into sla_calendar_version (calendar_id, version, definition, created_at, created_by) values (?, ?, cast(? as jsonb), ?, ?)")
            .params(calendar.id(), calendar.version(), JSON.writeValueAsString(calendar.definition()), ts(calendar.updatedAt()),
                calendar.updatedBy()).update();
    }

    Optional<WorkCalendar.Definition> calendarVersion(UUID calendarId, long version) {
        return jdbc.sql("select definition::text from sla_calendar_version where calendar_id = ? and version = ?").params(calendarId, version)
            .query((rs, row) -> JSON.readValue(rs.getString(1), WorkCalendar.Definition.class)).optional();
    }

    private static Calendar calendar(ResultSet rs) throws SQLException {
        return new Calendar(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getString("name"),
            JSON.readValue(rs.getString("definition"), WorkCalendar.Definition.class), rs.getLong("version"), instant(rs, "created_at"),
            rs.getObject("created_by", UUID.class), instant(rs, "updated_at"), rs.getObject("updated_by", UUID.class));
    }

    // ------------------------------------------------------------------------------------------------ rules

    List<Rule> rules(UUID companyId) {
        return jdbc.sql("select * from sla_rule where company_id = ? and active order by lower(name), id").param(companyId)
            .query((rs, row) -> rule(rs)).list();
    }

    Optional<Rule> rule(UUID companyId, UUID id) {
        return jdbc.sql("select * from sla_rule where company_id = ? and id = ?").params(companyId, id).query((rs, row) -> rule(rs)).optional();
    }

    Optional<Rule> lockRule(UUID companyId, UUID id) {
        return jdbc.sql("select * from sla_rule where company_id = ? and id = ? and active for update").params(companyId, id)
            .query((rs, row) -> rule(rs)).optional();
    }

    /** The active rules of the project that apply to a ticket of this customer and type, most specific first. */
    List<Rule> matching(UUID companyId, UUID projectId, UUID customerId, String ticketType) {
        return jdbc.sql("""
                select * from sla_rule where company_id = ? and project_id = ? and active
                  and (customer_id is null or customer_id = ?) and (ticket_type is null or ticket_type = ?)
                order by (customer_id is not null) desc, (ticket_type is not null) desc
                """).params(companyId, projectId, customerId, ticketType).query((rs, row) -> rule(rs)).list();
    }

    void insertRule(Rule rule) {
        jdbc.sql("""
                insert into sla_rule (id, company_id, name, project_id, customer_id, ticket_type, calendar_id, budgets, escalations, active, version,
                    created_at, created_by, updated_at, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?, ?, ?)
                """).params(rule.id(), rule.companyId(), rule.name(), rule.projectId(), rule.customerId(), rule.ticketType(), rule.calendarId(),
                JSON.writeValueAsString(rule.budgets()), JSON.writeValueAsString(rule.escalations()), rule.active(), rule.version(),
                ts(rule.createdAt()), rule.createdBy(), ts(rule.updatedAt()), rule.updatedBy()).update();
        insertRuleVersion(rule);
    }

    void updateRule(Rule rule) {
        jdbc.sql("""
                update sla_rule set name = ?, customer_id = ?, ticket_type = ?, calendar_id = ?, budgets = cast(? as jsonb),
                    escalations = cast(? as jsonb), active = ?, version = ?, updated_at = ?, updated_by = ? where id = ?
                """).params(rule.name(), rule.customerId(), rule.ticketType(), rule.calendarId(), JSON.writeValueAsString(rule.budgets()),
                JSON.writeValueAsString(rule.escalations()), rule.active(), rule.version(), ts(rule.updatedAt()), rule.updatedBy(), rule.id()).update();
        insertRuleVersion(rule);
    }

    private void insertRuleVersion(Rule rule) {
        jdbc.sql("""
                insert into sla_rule_version (rule_id, version, calendar_id, budgets, escalations, created_at, created_by)
                values (?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?)
                """).params(rule.id(), rule.version(), rule.calendarId(), JSON.writeValueAsString(rule.budgets()),
                JSON.writeValueAsString(rule.escalations()), ts(rule.updatedAt()), rule.updatedBy()).update();
    }

    Optional<Rule.Budgets> ruleBudgets(UUID ruleId, long version) {
        return jdbc.sql("select budgets::text from sla_rule_version where rule_id = ? and version = ?").params(ruleId, version)
            .query((rs, row) -> JSON.readValue(rs.getString(1), Rule.Budgets.class)).optional();
    }

    /** The escalation stages of a rule version, in their stored order; the position identifies a stage. */
    List<Rule.Stage> ruleStages(UUID ruleId, long version) {
        return jdbc.sql("select escalations::text from sla_rule_version where rule_id = ? and version = ?").params(ruleId, version)
            .query((rs, row) -> stages(rs.getString(1))).optional().orElse(List.of());
    }

    private static List<Rule.Stage> stages(String json) {
        return List.of(JSON.readValue(json, Rule.Stage[].class));
    }

    private static Rule rule(ResultSet rs) throws SQLException {
        return new Rule(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getString("name"),
            rs.getObject("project_id", UUID.class), rs.getObject("customer_id", UUID.class), rs.getString("ticket_type"),
            rs.getObject("calendar_id", UUID.class), JSON.readValue(rs.getString("budgets"), Rule.Budgets.class), stages(rs.getString("escalations")), rs.getBoolean("active"),
            rs.getLong("version"), instant(rs, "created_at"), rs.getObject("created_by", UUID.class), instant(rs, "updated_at"),
            rs.getObject("updated_by", UUID.class));
    }

    // ------------------------------------------------------------------------------------------------ tickets

    record TicketRow(UUID ticketId, UUID companyId, UUID projectId, String priority, String status, String statusKind) {
    }

    /**
     * The row of the ticket, created when missing, locked {@code FOR UPDATE} until the commit: every change of the deadlines of
     * one ticket runs one after the other, whichever module started it.
     */
    TicketRow lockTicket(UUID ticketId, UUID companyId, UUID projectId) {
        jdbc.sql("insert into sla_ticket (ticket_id, company_id, project_id, priority) values (?, ?, ?, 'normal') on conflict do nothing")
            .params(ticketId, companyId, projectId).update();
        return jdbc.sql("select * from sla_ticket where ticket_id = ? for update").param(ticketId).query((rs, row) -> ticketRow(rs)).single();
    }

    Optional<TicketRow> ticket(UUID ticketId) {
        return jdbc.sql("select * from sla_ticket where ticket_id = ?").param(ticketId).query((rs, row) -> ticketRow(rs)).optional();
    }

    void ticketStatus(UUID ticketId, String status, String kind) {
        jdbc.sql("update sla_ticket set status = ?, status_kind = ? where ticket_id = ?").params(status, kind, ticketId).update();
    }

    void ticketPriority(UUID ticketId, String priority) {
        jdbc.sql("update sla_ticket set priority = ? where ticket_id = ?").params(priority, ticketId).update();
    }

    private static TicketRow ticketRow(ResultSet rs) throws SQLException {
        return new TicketRow(rs.getObject("ticket_id", UUID.class), rs.getObject("company_id", UUID.class), rs.getObject("project_id", UUID.class),
            rs.getString("priority"), rs.getString("status"), rs.getString("status_kind"));
    }

    // ------------------------------------------------------------------------------------------------ cycles

    /** The cycles of the ticket, newest first. */
    List<SlaCycle> cycles(UUID ticketId) {
        return jdbc.sql("select * from sla_cycle where ticket_id = ? order by number desc").param(ticketId).query((rs, row) -> cycle(rs)).list();
    }

    Optional<SlaCycle> openCycle(UUID ticketId) {
        return jdbc.sql("select * from sla_cycle where ticket_id = ? and outcome is null").param(ticketId).query((rs, row) -> cycle(rs)).optional();
    }

    int cycleCount(UUID ticketId) {
        return jdbc.sql("select count(*) from sla_cycle where ticket_id = ?").param(ticketId).query(Integer.class).single();
    }

    void insertCycle(SlaCycle cycle, UUID companyId, UUID projectId) {
        jdbc.sql("""
                insert into sla_cycle (id, company_id, project_id, ticket_id, number, rule_id, rule_version, calendar_id, calendar_version, priority,
                    started_at, replied_at, resolved_since, outcome, reason, closed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(cycle.id(), companyId, projectId, cycle.ticketId(), cycle.number(), cycle.ruleId(), cycle.ruleVersion(),
                cycle.calendarId(), cycle.calendarVersion(), cycle.priority(), ts(cycle.startedAt()), ts(cycle.repliedAt()),
                ts(cycle.resolvedSince()), cycle.outcome(), cycle.reason(), ts(cycle.closedAt())).update();
        insertClock(cycle.id(), cycle.firstResponse());
        insertClock(cycle.id(), cycle.resolution());
    }

    void updateCycle(SlaCycle cycle) {
        jdbc.sql("""
                update sla_cycle set priority = ?, replied_at = ?, resolved_since = ?, outcome = ?, reason = ?, closed_at = ? where id = ?
                """).params(cycle.priority(), ts(cycle.repliedAt()), ts(cycle.resolvedSince()), cycle.outcome(), cycle.reason(),
                ts(cycle.closedAt()), cycle.id()).update();
        jdbc.sql("delete from sla_clock where cycle_id = ?").param(cycle.id()).update();
        insertClock(cycle.id(), cycle.firstResponse());
        insertClock(cycle.id(), cycle.resolution());
    }

    private void insertClock(UUID cycleId, SlaClock clock) {
        jdbc.sql("""
                insert into sla_clock (cycle_id, kind, state, budget_seconds, consumed_seconds, checkpoint_at, due_at, paused_at, met_at, breached_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(cycleId, clock.kind(), clock.state(), clock.budgetSeconds(), clock.consumedSeconds(), ts(clock.checkpoint()),
                ts(clock.dueAt()), ts(clock.pausedAt()), ts(clock.metAt()), ts(clock.breachedAt())).update();
    }

    private SlaCycle cycle(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<SlaClock> clocks = jdbc.sql("select * from sla_clock where cycle_id = ?").param(id).query((c, row) -> new SlaClock(c.getString("kind"),
            c.getString("state"), c.getLong("budget_seconds"), c.getLong("consumed_seconds"), instant(c, "checkpoint_at"), instant(c, "due_at"),
            instant(c, "paused_at"), instant(c, "met_at"), instant(c, "breached_at"))).list();
        SlaClock first = clocks.stream().filter(clock -> clock.kind().equals(SlaClock.FIRST_RESPONSE)).findFirst().orElseThrow();
        SlaClock resolution = clocks.stream().filter(clock -> clock.kind().equals(SlaClock.RESOLUTION)).findFirst().orElseThrow();
        return new SlaCycle(id, rs.getObject("ticket_id", UUID.class), rs.getInt("number"), rs.getObject("rule_id", UUID.class),
            rs.getLong("rule_version"), rs.getObject("calendar_id", UUID.class), rs.getLong("calendar_version"), rs.getString("priority"),
            instant(rs, "started_at"), first, resolution, instant(rs, "replied_at"), instant(rs, "resolved_since"), rs.getString("outcome"),
            rs.getString("reason"), instant(rs, "closed_at"), 0);
    }

    // ------------------------------------------------------------------------------------------------ escalations (TASK-070)

    /** A stage a clock of a cycle reached; {@code stage} is its position in the stages of the rule version. */
    record Escalation(UUID id, UUID companyId, UUID projectId, UUID ticketId, UUID cycleId, int cycleNumber, String cycleOutcome, String kind,
            int stage, int percent, UUID groupId, Instant thresholdAt, Instant reachedAt, boolean skipped) {
    }

    /** Tickets with an open cycle whose clock runs and whose rule version has stages, after {@code after}, at most {@code limit}. */
    List<UUID> watchedTickets(UUID after, int limit) {
        return jdbc.sql("""
                select distinct y.ticket_id from sla_cycle y
                  join sla_clock c on c.cycle_id = y.id and c.state = 'running'
                  join sla_rule_version v on v.rule_id = y.rule_id and v.version = y.rule_version
                 where y.outcome is null and jsonb_array_length(v.escalations) > 0 and y.ticket_id > ?
                 order by y.ticket_id limit ?
                """).params(after == null ? NONE : after, limit).query(UUID.class).list();
    }

    /** Locks the row of a ticket the module already knows, without creating one; empty when it does not know the ticket. */
    Optional<TicketRow> lockKnownTicket(UUID ticketId) {
        return jdbc.sql("select * from sla_ticket where ticket_id = ? for update").param(ticketId).query((rs, row) -> ticketRow(rs)).optional();
    }

    /** Stores a reached or skipped stage once; returns whether it was new. */
    boolean insertEscalation(Escalation escalation) {
        return jdbc.sql("""
                insert into sla_escalation (id, company_id, project_id, ticket_id, cycle_id, kind, stage, percent, group_id, threshold_at, reached_at,
                    skipped)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) on conflict (cycle_id, kind, stage) do nothing
                """).params(escalation.id(), escalation.companyId(), escalation.projectId(), escalation.ticketId(), escalation.cycleId(),
                escalation.kind(), escalation.stage(), escalation.percent(), escalation.groupId(), ts(escalation.thresholdAt()),
                ts(escalation.reachedAt()), escalation.skipped()).update() == 1;
    }

    /** The stages of a cycle, reached and skipped. */
    List<Escalation> escalationsOf(UUID cycleId) {
        return jdbc.sql(ESCALATIONS + " where e.cycle_id = ? order by e.reached_at, e.kind, e.stage").param(cycleId)
            .query((rs, row) -> escalation(rs)).list();
    }

    /** The reached stages for any of the groups, newest first. */
    List<Escalation> escalationsFor(UUID companyId, java.util.Collection<UUID> groups, int limit) {
        if (groups.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(ESCALATIONS + " where e.company_id = ? and not e.skipped and e.group_id = any(?) order by e.reached_at desc, e.id limit ?")
            .params(companyId, groups.toArray(UUID[]::new), limit).query((rs, row) -> escalation(rs)).list();
    }

    private static final String ESCALATIONS = """
            select e.*, y.number as cycle_number, y.outcome as cycle_outcome from sla_escalation e join sla_cycle y on y.id = e.cycle_id""";

    private static Escalation escalation(ResultSet rs) throws SQLException {
        return new Escalation(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class), rs.getObject("project_id", UUID.class),
            rs.getObject("ticket_id", UUID.class), rs.getObject("cycle_id", UUID.class), rs.getInt("cycle_number"), rs.getString("cycle_outcome"),
            rs.getString("kind"), rs.getInt("stage"), rs.getInt("percent"), rs.getObject("group_id", UUID.class), instant(rs, "threshold_at"),
            instant(rs, "reached_at"), rs.getBoolean("skipped"));
    }

    // ------------------------------------------------------------------------------------------------ audit

    void audit(UUID companyId, String targetType, UUID targetId, String operation, String detail, UUID actor, Instant at) {
        jdbc.sql("""
                insert into sla_audit_event (company_id, target_type, target_id, operation, detail, actor_account_id, occurred_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """).params(companyId, targetType, targetId, operation, detail, actor, ts(at)).update();
    }

    static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
