package taskmanager.sla.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.helpdesk.CustomerDirectory;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.AdministrationRights;
import taskmanager.platform.ApiProblemException;
import taskmanager.platform.CompanyContext;
import taskmanager.projects.ProjectAccess;
import taskmanager.projects.ProjectChanged;
import taskmanager.projects.ProjectConfiguration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Calendars and SLA rules (TASK-068). Everything here needs the company right {@code sla.manage}, checked first in the
 * transaction. Every change stores the next version and an audit entry; cycles that already run keep the versions they
 * began with (rule 6). Two active rules that match the same tickets equally well are refused with 409 (rule 3), also when
 * two administrations store them at the same time: the unique index decides.
 */
@Service
public class SlaAdministration {
    static final int NAME_MAX = 80;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SlaRepository repository;
    private final ObjectProvider<AdministrationRights> rights;
    private final CompanyContext company;
    private final ProjectAccess projects;
    private final CustomerDirectory customers;
    private final Clock clock;

    SlaAdministration(SlaRepository repository, ObjectProvider<AdministrationRights> rights, CompanyContext company, ProjectAccess projects,
            CustomerDirectory customers, Clock clock) {
        this.repository = repository;
        this.rights = rights;
        this.company = company;
        this.projects = projects;
        this.customers = customers;
        this.clock = clock;
    }

    /** What a rule can name: the projects with their ticket types and the customers with the projects they are released for. */
    public record Options(List<ProjectOption> projects, List<CustomerDirectory.Summary> customers) {
    }

    public record ProjectOption(UUID id, String key, String name, List<ProjectConfiguration.TicketType> ticketTypes) {
    }

    /** A snapshot of {@code sla.manage} for the current request; never authorizes a change. */
    public boolean holdsManage() {
        var port = AdministrationRights.unique(rights);
        return port != null && port.held().contains(AdministrationRight.SLA_MANAGE);
    }

    /** The authoritative check of {@code sla.manage}, first in the transaction; returns the company. */
    private UUID requireManage() {
        var port = AdministrationRights.unique(rights);
        if (port == null) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        port.require(AdministrationRight.SLA_MANAGE);
        return company.find().orElseThrow(() -> ApiProblemException.of(403, "auth.forbidden"));
    }

    // ------------------------------------------------------------------------------------------------ calendars

    @Transactional
    public List<Calendar> calendars() {
        return repository.calendars(requireManage());
    }

    @Transactional
    public Calendar calendar(UUID id) {
        return repository.calendar(requireManage(), id).orElseThrow(() -> ApiProblemException.of(404, "sla.calendar_not_found"));
    }

    @Transactional
    public Calendar createCalendar(UUID actor, String rawName, WorkCalendar.Definition definition) {
        UUID companyId = requireManage();
        String name = checked(rawName, definition);
        if (repository.calendarNameTaken(companyId, name, null)) {
            throw ApiProblemException.of(409, "sla.calendar_name_taken");
        }
        Instant now = clock.instant();
        Calendar calendar = new Calendar(UUID.randomUUID(), companyId, name, definition, 1, now, actor, now, actor);
        try {
            repository.insertCalendar(calendar);
        } catch (DuplicateKeyException taken) {
            throw ApiProblemException.of(409, "sla.calendar_name_taken");
        }
        repository.audit(companyId, "calendar", calendar.id(), "calendar.created", describe(calendar), actor, now);
        return calendar;
    }

    @Transactional
    public Calendar updateCalendar(UUID actor, UUID id, long expectedVersion, String rawName, WorkCalendar.Definition definition) {
        UUID companyId = requireManage();
        Calendar current = repository.lockCalendar(companyId, id).orElseThrow(() -> ApiProblemException.of(404, "sla.calendar_not_found"));
        if (current.version() != expectedVersion) {
            throw ApiProblemException.of(412, "sla.version_conflict");
        }
        String name = checked(rawName, definition);
        if (repository.calendarNameTaken(companyId, name, id)) {
            throw ApiProblemException.of(409, "sla.calendar_name_taken");
        }
        if (current.name().equals(name) && current.definition().equals(definition)) {
            return current;
        }
        Instant now = clock.instant();
        Calendar changed = new Calendar(id, companyId, name, definition, current.version() + 1, current.createdAt(), current.createdBy(), now, actor);
        try {
            repository.updateCalendar(changed);
        } catch (DuplicateKeyException taken) {
            throw ApiProblemException.of(409, "sla.calendar_name_taken");
        }
        repository.audit(companyId, "calendar", id, "calendar.changed", describe(changed), actor, now);
        return changed;
    }

    private static String checked(String rawName, WorkCalendar.Definition definition) {
        List<String> violations = new ArrayList<>();
        String name = name(rawName, violations);
        violations.addAll(WorkCalendar.violations(definition));
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        return name;
    }

    // ------------------------------------------------------------------------------------------------ rules

    @Transactional
    public List<Rule> rules() {
        return repository.rules(requireManage());
    }

    @Transactional
    public Options options() {
        UUID companyId = requireManage();
        List<ProjectOption> list = new ArrayList<>();
        for (ProjectChanged project : projects.searchSources(companyId)) {
            List<ProjectConfiguration.TicketType> types = projects.currentConfigurationVersion(project.projectId())
                .flatMap(version -> projects.configuration(project.projectId(), version)).map(ProjectConfiguration::ticketTypes).orElse(List.of());
            list.add(new ProjectOption(project.projectId(), project.key(), project.name(), types));
        }
        return new Options(list, customers.customers(companyId));
    }

    @Transactional
    public Rule createRule(UUID actor, String rawName, UUID projectId, UUID customerId, String ticketType, UUID calendarId, Rule.Budgets budgets) {
        UUID companyId = requireManage();
        String name = checkedRule(companyId, rawName, projectId, customerId, ticketType, calendarId, budgets);
        Instant now = clock.instant();
        Rule rule = new Rule(UUID.randomUUID(), companyId, name, projectId, customerId, ticketType, calendarId, budgets, true, 1, now, actor, now, actor);
        try {
            repository.insertRule(rule);
        } catch (DuplicateKeyException taken) {
            throw ApiProblemException.of(409, "sla.rule_conflict");
        }
        repository.audit(companyId, "rule", rule.id(), "rule.created", describe(rule), actor, now);
        return rule;
    }

    /** Changes a rule; the project of a rule stays, a rule for another project is a new rule. */
    @Transactional
    public Rule updateRule(UUID actor, UUID id, long expectedVersion, String rawName, UUID customerId, String ticketType, UUID calendarId,
            Rule.Budgets budgets) {
        UUID companyId = requireManage();
        Rule current = repository.lockRule(companyId, id).orElseThrow(() -> ApiProblemException.of(404, "sla.rule_not_found"));
        if (current.version() != expectedVersion) {
            throw ApiProblemException.of(412, "sla.version_conflict");
        }
        String name = checkedRule(companyId, rawName, current.projectId(), customerId, ticketType, calendarId, budgets);
        Rule changed = new Rule(id, companyId, name, current.projectId(), customerId, ticketType, calendarId, budgets, true, current.version() + 1,
            current.createdAt(), current.createdBy(), clock.instant(), actor);
        if (changed.name().equals(current.name()) && java.util.Objects.equals(customerId, current.customerId())
                && java.util.Objects.equals(ticketType, current.ticketType()) && calendarId.equals(current.calendarId())
                && budgets.equals(current.budgets())) {
            return current;
        }
        try {
            repository.updateRule(changed);
        } catch (DuplicateKeyException taken) {
            throw ApiProblemException.of(409, "sla.rule_conflict");
        }
        repository.audit(companyId, "rule", id, "rule.changed", describe(changed), actor, changed.updatedAt());
        return changed;
    }

    /** Removes a rule for new cycles; cycles that already use it keep it. */
    @Transactional
    public void removeRule(UUID actor, UUID id, long expectedVersion) {
        UUID companyId = requireManage();
        Rule current = repository.lockRule(companyId, id).orElseThrow(() -> ApiProblemException.of(404, "sla.rule_not_found"));
        if (current.version() != expectedVersion) {
            throw ApiProblemException.of(412, "sla.version_conflict");
        }
        Instant now = clock.instant();
        repository.updateRule(new Rule(id, companyId, current.name(), current.projectId(), current.customerId(), current.ticketType(),
            current.calendarId(), current.budgets(), false, current.version() + 1, current.createdAt(), current.createdBy(), now, actor));
        repository.audit(companyId, "rule", id, "rule.removed", null, actor, now);
    }

    private String checkedRule(UUID companyId, String rawName, UUID projectId, UUID customerId, String ticketType, UUID calendarId,
            Rule.Budgets budgets) {
        List<String> violations = new ArrayList<>();
        String name = name(rawName, violations);
        ProjectConfiguration configuration = projectId == null ? null : projects.currentConfigurationVersion(projectId)
            .flatMap(version -> projects.configuration(projectId, version)).orElse(null);
        if (configuration == null || projects.searchSources(companyId).stream().noneMatch(p -> p.projectId().equals(projectId))) {
            violations.add("projectId.unknown");
        }
        if (customerId != null) {
            var customer = customers.customer(companyId, customerId);
            if (customer.isEmpty() || projectId == null || !customer.get().projects().contains(projectId)) {
                violations.add("customerId.not_released");
            }
        }
        if (ticketType != null) {
            if (customerId == null) {
                violations.add("ticketType.requires_customer");
            } else if (configuration != null && !configuration.hasTicketType(ticketType)) {
                violations.add("ticketType.unknown");
            }
        }
        if (calendarId == null || repository.calendar(companyId, calendarId).isEmpty()) {
            violations.add("calendarId.unknown");
        }
        violations.addAll(Rule.Budgets.violations(budgets));
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        return name;
    }

    private static String name(String raw, List<String> violations) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > NAME_MAX || name.indexOf('\u0000') >= 0) {
            violations.add("name.invalid");
        }
        return name;
    }

    private static String describe(Object value) {
        return JSON.writeValueAsString(value);
    }
}
