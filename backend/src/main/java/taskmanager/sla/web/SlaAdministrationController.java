package taskmanager.sla.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.helpdesk.CustomerDirectory;
import taskmanager.projects.ProjectConfiguration;
import taskmanager.sla.internal.Calendar;
import taskmanager.sla.internal.Rule;
import taskmanager.sla.internal.SlaAdministration;
import taskmanager.sla.internal.WorkCalendar;

/**
 * Business hours calendars and SLA rules (TASK-068). Every endpoint needs the company right {@code sla.manage}; the endpoint
 * refuses a request that clearly lacks it, the service decides in its transaction. Changes need the version of If-Match.
 */
@RestController
@RequestMapping("/api/v1/sla")
class SlaAdministrationController {
    private final SlaAdministration administration;
    private final SlaRequests requests;

    SlaAdministrationController(SlaAdministration administration, SlaRequests requests) {
        this.administration = administration;
        this.requests = requests;
    }

    record CalendarBody(UUID id, String name, String timeZone, List<WorkCalendar.Day> week, List<WorkCalendar.Exception> exceptions,
            long version, Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy) {
        static CalendarBody of(Calendar c) {
            return new CalendarBody(c.id(), c.name(), c.definition().timeZone(), c.definition().week(), c.definition().exceptions(), c.version(),
                c.createdAt(), c.createdBy(), c.updatedAt(), c.updatedBy());
        }
    }

    record CalendarRequest(String name, String timeZone, List<WorkCalendar.Day> week, List<WorkCalendar.Exception> exceptions) {
        WorkCalendar.Definition definition() {
            return new WorkCalendar.Definition(timeZone, week, exceptions);
        }
    }

    record RuleBody(UUID id, String name, UUID projectId, UUID customerId, String ticketType, UUID calendarId, Rule.Budgets budgets,
            long version, Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy) {
        static RuleBody of(Rule r) {
            return new RuleBody(r.id(), r.name(), r.projectId(), r.customerId(), r.ticketType(), r.calendarId(), r.budgets(), r.version(),
                r.createdAt(), r.createdBy(), r.updatedAt(), r.updatedBy());
        }
    }

    record RuleRequest(String name, UUID projectId, UUID customerId, String ticketType, UUID calendarId, Rule.Budgets budgets) {
    }

    record ProjectOptionBody(UUID id, String key, String name, List<ProjectConfiguration.TicketType> ticketTypes) {
    }

    record CustomerOptionBody(UUID id, String name, List<UUID> projects) {
        static CustomerOptionBody of(CustomerDirectory.Summary c) {
            return new CustomerOptionBody(c.id(), c.name(), c.projects());
        }
    }

    record OptionsBody(List<ProjectOptionBody> projects, List<CustomerOptionBody> customers) {
    }

    @GetMapping("/calendars")
    List<CalendarBody> calendars() {
        requests.administrator();
        return SlaRequests.retrying(administration::calendars).stream().map(CalendarBody::of).toList();
    }

    @GetMapping("/calendars/{id}")
    ResponseEntity<CalendarBody> calendar(@PathVariable UUID id) {
        requests.administrator();
        Calendar calendar = SlaRequests.retrying(() -> administration.calendar(id));
        return ResponseEntity.ok().eTag(SlaRequests.etag(calendar.version())).body(CalendarBody.of(calendar));
    }

    @PostMapping("/calendars")
    ResponseEntity<CalendarBody> createCalendar(@RequestBody(required = false) CalendarRequest body) {
        var actor = requests.administrator();
        CalendarRequest request = body == null ? new CalendarRequest(null, null, null, null) : body;
        Calendar calendar = SlaRequests.retrying(() -> administration.createCalendar(actor.accountId(), request.name(), request.definition()));
        return ResponseEntity.status(201).eTag(SlaRequests.etag(calendar.version())).body(CalendarBody.of(calendar));
    }

    @PutMapping("/calendars/{id}")
    ResponseEntity<CalendarBody> updateCalendar(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody(required = false) CalendarRequest body) {
        var actor = requests.administrator();
        long version = SlaRequests.expectedVersion(ifMatch);
        CalendarRequest request = body == null ? new CalendarRequest(null, null, null, null) : body;
        Calendar calendar = SlaRequests.retrying(() -> administration.updateCalendar(actor.accountId(), id, version, request.name(),
            request.definition()));
        return ResponseEntity.ok().eTag(SlaRequests.etag(calendar.version())).body(CalendarBody.of(calendar));
    }

    @GetMapping("/rules")
    List<RuleBody> rules() {
        requests.administrator();
        return SlaRequests.retrying(administration::rules).stream().map(RuleBody::of).toList();
    }

    /** The projects with their ticket types and the customers a rule can name. */
    @GetMapping("/options")
    OptionsBody options() {
        requests.administrator();
        SlaAdministration.Options options = SlaRequests.retrying(administration::options);
        return new OptionsBody(options.projects().stream().map(p -> new ProjectOptionBody(p.id(), p.key(), p.name(), p.ticketTypes())).toList(),
            options.customers().stream().map(CustomerOptionBody::of).toList());
    }

    @PostMapping("/rules")
    ResponseEntity<RuleBody> createRule(@RequestBody(required = false) RuleRequest body) {
        var actor = requests.administrator();
        RuleRequest request = body == null ? new RuleRequest(null, null, null, null, null, null) : body;
        Rule rule = SlaRequests.retrying(() -> administration.createRule(actor.accountId(), request.name(), request.projectId(), request.customerId(),
            blankToNull(request.ticketType()), request.calendarId(), request.budgets()));
        return ResponseEntity.status(201).eTag(SlaRequests.etag(rule.version())).body(RuleBody.of(rule));
    }

    @PutMapping("/rules/{id}")
    ResponseEntity<RuleBody> updateRule(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody(required = false) RuleRequest body) {
        var actor = requests.administrator();
        long version = SlaRequests.expectedVersion(ifMatch);
        RuleRequest request = body == null ? new RuleRequest(null, null, null, null, null, null) : body;
        Rule rule = SlaRequests.retrying(() -> administration.updateRule(actor.accountId(), id, version, request.name(), request.customerId(),
            blankToNull(request.ticketType()), request.calendarId(), request.budgets()));
        return ResponseEntity.ok().eTag(SlaRequests.etag(rule.version())).body(RuleBody.of(rule));
    }

    @DeleteMapping("/rules/{id}")
    ResponseEntity<Void> removeRule(@PathVariable UUID id, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        var actor = requests.administrator();
        long version = SlaRequests.expectedVersion(ifMatch);
        SlaRequests.retrying(() -> {
            administration.removeRule(actor.accountId(), id, version);
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
