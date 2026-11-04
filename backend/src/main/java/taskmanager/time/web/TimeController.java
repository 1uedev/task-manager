package taskmanager.time.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.identity.AccountNames;
import taskmanager.permissions.AccessContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.time.internal.TimeEntry;
import taskmanager.time.internal.TimeRevision;
import taskmanager.time.internal.TimeService;

/**
 * Time entries (TASK-069, DEC-099). The ETag of an entry is its version; a correction and a cancellation need it as If-Match
 * (428 without, 412 when it is no longer current). The list returns at most {@link TimeService#LIST_LIMIT} entries, while its
 * sums always cover every matching entry that is not cancelled. The export is CSV with a semicolon, the header names period,
 * filters and unit.
 */
@RestController
@RequestMapping("/api/v1/time")
class TimeController {
    private final TimeService service;
    private final TimeRequests requests;
    private final AccountNames names;

    TimeController(TimeService service, TimeRequests requests, AccountNames names) {
        this.service = service;
        this.requests = requests;
        this.names = names;
    }

    record AccessBody(boolean book, boolean read, boolean manage) {
    }

    record ProjectBody(UUID projectId, String key, String name, AccessBody access) {
    }

    record TicketBody(long number, String title) {
        static TicketBody of(TimeService.TicketRef ref) {
            return ref == null ? null : new TicketBody(ref.number(), ref.title());
        }
    }

    record EntryBody(UUID id, UUID projectId, String projectKey, TicketBody ticket, UUID personId, LocalDate date, int minutes, String description,
            boolean cancelled, Instant cancelledAt, UUID cancelledBy, Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy, long version,
            boolean own, boolean changeable) {
        static EntryBody of(TimeService.EntryView view) {
            TimeEntry e = view.entry();
            return new EntryBody(e.id(), e.projectId(), view.projectKey(), TicketBody.of(view.ticket()), e.personId(), e.date(), e.minutes(),
                e.description(), e.cancelled(), e.cancelledAt(), e.cancelledBy(), e.createdAt(), e.createdBy(), e.updatedAt(), e.updatedBy(),
                e.version(), view.own(), view.changeable());
        }
    }

    record RevisionBody(int revision, String kind, Instant occurredAt, UUID actorId, TicketBody ticket, LocalDate date, int minutes, String description,
            String reason) {
        static RevisionBody of(TimeService.RevisionView view) {
            TimeRevision r = view.revision();
            return new RevisionBody(r.revision(), r.kind(), r.occurredAt(), r.actorId(), TicketBody.of(view.ticket()), r.date(), r.minutes(),
                r.description(), r.reason());
        }
    }

    record DetailBody(EntryBody entry, List<RevisionBody> revisions) {
        static DetailBody of(TimeService.Detail detail) {
            return new DetailBody(EntryBody.of(detail.entry()), detail.revisions().stream().map(RevisionBody::of).toList());
        }
    }

    record PersonSumBody(UUID personId, long minutes) {
    }

    record TicketSumBody(UUID projectId, String projectKey, TicketBody ticket, long minutes) {
    }

    record ListBody(List<EntryBody> entries, boolean truncated, long count, long totalMinutes, List<PersonSumBody> perPerson,
            List<TicketSumBody> perTicket) {
        static ListBody of(TimeService.Listing listing) {
            return new ListBody(listing.entries().stream().map(EntryBody::of).toList(), listing.truncated(), listing.count(), listing.totalMinutes(),
                listing.perPerson().stream().map(sum -> new PersonSumBody(sum.personId(), sum.minutes())).toList(),
                listing.perTicket().stream().map(sum -> new TicketSumBody(sum.projectId(), sum.projectKey(), TicketBody.of(sum.ticket()), sum.minutes()))
                    .toList());
        }
    }

    record BookRequest(String date, Integer minutes, String description, Long ticketNumber, UUID personId) {
    }

    record CorrectRequest(String date, Integer minutes, String description, Long ticketNumber, String reason) {
    }

    record CancelRequest(String reason) {
    }

    @GetMapping("/projects")
    List<ProjectBody> projects() {
        AccessContext actor = requests.actor();
        return TimeRequests.retrying(() -> service.projects(actor)).stream()
            .map(p -> new ProjectBody(p.projectId(), p.key(), p.name(), new AccessBody(p.access().book(), p.access().read(), p.access().manage())))
            .toList();
    }

    @GetMapping("/projects/{projectId}/people")
    List<UUID> people(@PathVariable UUID projectId) {
        AccessContext actor = requests.actor();
        return TimeRequests.retrying(() -> service.people(actor, projectId));
    }

    @PostMapping("/projects/{projectId}/entries")
    ResponseEntity<DetailBody> book(@PathVariable UUID projectId, @RequestBody(required = false) BookRequest body) {
        AccessContext actor = requests.actor();
        BookRequest request = body == null ? new BookRequest(null, null, null, null, null) : body;
        TimeService.Input input = new TimeService.Input(date(request.date()), request.minutes(), request.description(), request.ticketNumber(),
            request.personId(), null);
        TimeService.Detail detail = TimeRequests.retrying(() -> service.book(actor, projectId, input));
        return ResponseEntity.status(201).eTag(TimeRequests.etag(detail.entry().entry().version())).body(DetailBody.of(detail));
    }

    @GetMapping("/entries")
    ListBody list(@RequestParam(required = false) UUID project, @RequestParam(required = false) Long ticket,
            @RequestParam(required = false) UUID person, @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "false") boolean cancelled) {
        AccessContext actor = requests.actor();
        TimeService.Filter filter = new TimeService.Filter(project, ticket, person, date(from), date(to), cancelled);
        return ListBody.of(TimeRequests.retrying(() -> service.list(actor, filter)));
    }

    @GetMapping("/entries/{id}")
    ResponseEntity<DetailBody> detail(@PathVariable UUID id) {
        AccessContext actor = requests.actor();
        TimeService.Detail detail = TimeRequests.retrying(() -> service.detail(actor, id));
        return ResponseEntity.ok().eTag(TimeRequests.etag(detail.entry().entry().version())).body(DetailBody.of(detail));
    }

    @PutMapping("/entries/{id}")
    ResponseEntity<DetailBody> correct(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) CorrectRequest body) {
        AccessContext actor = requests.actor();
        long version = TimeRequests.expectedVersion(ifMatch);
        CorrectRequest request = body == null ? new CorrectRequest(null, null, null, null, null) : body;
        TimeService.Input input = new TimeService.Input(date(request.date()), request.minutes(), request.description(), request.ticketNumber(), null,
            request.reason());
        TimeService.Detail detail = TimeRequests.retrying(() -> service.correct(actor, id, version, input));
        return ResponseEntity.ok().eTag(TimeRequests.etag(detail.entry().entry().version())).body(DetailBody.of(detail));
    }

    @PostMapping("/entries/{id}/cancel")
    ResponseEntity<DetailBody> cancel(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody(required = false) CancelRequest body) {
        AccessContext actor = requests.actor();
        long version = TimeRequests.expectedVersion(ifMatch);
        String reason = body == null ? null : body.reason();
        TimeService.Detail detail = TimeRequests.retrying(() -> service.cancel(actor, id, version, reason));
        return ResponseEntity.ok().eTag(TimeRequests.etag(detail.entry().entry().version())).body(DetailBody.of(detail));
    }

    /** Every effective matching entry as CSV (UTF-8 with byte order mark, so spreadsheet programs read the umlauts). */
    @GetMapping("/export")
    ResponseEntity<byte[]> export(@RequestParam(required = false) UUID project, @RequestParam(required = false) Long ticket,
            @RequestParam(required = false) UUID person, @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        AccessContext actor = requests.actor();
        TimeService.Filter filter = new TimeService.Filter(project, ticket, person, date(from), date(to), false);
        TimeService.Listing listing = TimeRequests.retrying(() -> service.export(actor, filter));
        if (listing.truncated()) {
            throw ApiProblemException.of(400, "time.export_too_large");
        }
        Map<UUID, String> people = listing.entries().stream().map(view -> view.entry().personId()).distinct()
            .collect(Collectors.toMap(Function.identity(), id -> names.shownName(id).orElse(id.toString())));
        String csv = Csv.of(filter, listing, people);
        String name = "zeiten-" + (filter.from() == null ? "beginn" : filter.from()) + "-" + (filter.to() == null ? "heute" : filter.to()) + ".csv";
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
            .header("X-Content-Type-Options", "nosniff")
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("date.invalid"));
        }
    }

    /** The export format: semicolon separated, decimal comma, one header block of lines with {@code #}. */
    static final class Csv {
        private Csv() {
        }

        static String of(TimeService.Filter filter, TimeService.Listing listing, Map<UUID, String> people) {
            StringBuilder out = new StringBuilder("﻿");
            line(out, List.of("# Zeitraum", (filter.from() == null ? "Beginn" : filter.from().toString()) + " bis "
                + (filter.to() == null ? "heute" : filter.to().toString())));
            List<String> filters = new ArrayList<>();
            if (filter.projectId() != null) {
                filters.add("Projekt " + (listing.entries().isEmpty() ? filter.projectId() : listing.entries().getFirst().projectKey()));
            }
            if (filter.ticketNumber() != null) {
                filters.add("Ticket " + filter.ticketNumber());
            }
            if (filter.personId() != null) {
                filters.add("Person " + people.getOrDefault(filter.personId(), filter.personId().toString()));
            }
            line(out, List.of("# Filter", filters.isEmpty() ? "keine" : String.join(", ", filters)));
            line(out, List.of("# Einheit", "Minuten; Stunden mit zwei Nachkommastellen, kaufmännisch gerundet"));
            line(out, List.of("Datum", "Projekt", "Ticket", "Person", "Minuten", "Stunden", "Beschreibung"));
            for (TimeService.EntryView view : listing.entries()) {
                TimeEntry e = view.entry();
                line(out, List.of(e.date().toString(), view.projectKey(), view.ticket() == null ? "" : view.projectKey() + "-" + view.ticket().number(),
                    people.getOrDefault(e.personId(), e.personId().toString()), Integer.toString(e.minutes()), hours(e.minutes()), e.description()));
            }
            line(out, List.of("Summe", "", "", "", Long.toString(listing.totalMinutes()), hours(listing.totalMinutes()), ""));
            return out.toString();
        }

        static String hours(long minutes) {
            return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
        }

        private static void line(StringBuilder out, List<String> cells) {
            out.append(cells.stream().map(Csv::cell).collect(Collectors.joining(";"))).append("\r\n");
        }

        /** Quotes every cell; a cell that a spreadsheet would read as a formula gets a leading apostrophe. */
        static String cell(String value) {
            String text = value == null ? "" : value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
            if (!text.isEmpty() && "=+-@\t".indexOf(text.charAt(0)) >= 0) {
                text = "'" + text;
            }
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
    }
}
