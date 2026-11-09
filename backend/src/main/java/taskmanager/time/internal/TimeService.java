package taskmanager.time.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.IdentityVerification;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.Decision;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import taskmanager.tickets.TicketAccess;

/**
 * Time entries (TASK-069, DEC-099) after the rules Holger confirmed on 30 October 2026:
 * <ul>
 *   <li>{@code time.book}: book own time, read, correct and cancel own entries;</li>
 *   <li>{@code time.read}: read the entries of everybody in the project, with sums and export;</li>
 *   <li>{@code time.manage}: book for other active members and correct or cancel their entries; it includes {@code time.read}.</li>
 * </ul>
 * Reading a ticket shows no times, and portal accounts never see any. A ticket of an entry is shown only to a reader with
 * {@code ticket.read} in its project, its title only with the read rule of {@code title}; for everybody else the entry has
 * no ticket. A change checks its action with {@code Permissions.require}, which holds the rights lock until the commit, and
 * locks the entry {@code FOR UPDATE}, so a revocation or a second change in between is seen. Sums use only the effective
 * values of entries that are not cancelled.
 */
@Service
public class TimeService {
    public static final String BOOK = "time.book";
    public static final String READ = "time.read";
    public static final String MANAGE = "time.manage";
    public static final String RESOURCE_TYPE = "time_entry";
    public static final int MAX_MINUTES = 1440;
    public static final int MAX_DESCRIPTION = 1000;
    public static final int MAX_REASON = 500;
    /** Entries a list returns; count and sums always cover every matching entry (TASK-092, F1). */
    public static final int LIST_LIMIT = 500;
    /** Entries an export contains at most; a larger export is refused, the sums of a list have no limit. */
    public static final int EXPORT_LIMIT = 50_000;
    private static final String TICKET_READ = "ticket.read";
    private static final String TITLE = "title";

    private final TimeRepository repository;
    private final ProjectAccess projects;
    private final TicketAccess tickets;
    private final Permissions permissions;
    private final IdentityVerification identity;
    private final Clock clock;

    TimeService(TimeRepository repository, ProjectAccess projects, TicketAccess tickets, Permissions permissions, IdentityVerification identity,
            Clock clock) {
        this.repository = repository;
        this.projects = projects;
        this.tickets = tickets;
        this.permissions = permissions;
        this.identity = identity;
        this.clock = clock;
    }

    /** The time actions of the caller in one project. */
    public record Access(boolean book, boolean read, boolean manage) {
        static final Access NONE = new Access(false, false, false);

        boolean any() {
            return book || read || manage;
        }

        boolean readsAll() {
            return read || manage;
        }
    }

    /** A project in which the caller holds at least one time action. */
    public record ProjectView(UUID projectId, String key, String name, Access access) {
    }

    /** The ticket of an entry as the reader may see it; {@code title} is {@code null} without the read rule. */
    public record TicketRef(long number, String title) {
    }

    /**
     * An entry as the caller sees it; {@code changeable} says whether the caller may correct or cancel it. {@code ticketHidden}
     * says that the entry has a ticket the caller may not see, without saying which (TASK-092, F2).
     */
    public record EntryView(TimeEntry entry, String projectKey, TicketRef ticket, boolean ticketHidden, boolean own, boolean changeable) {
    }

    public record RevisionView(TimeRevision revision, TicketRef ticket, boolean ticketHidden) {
    }

    public record Detail(EntryView entry, List<RevisionView> revisions) {
    }

    public record PersonSum(UUID personId, long minutes) {
    }

    /** The sum of a ticket; {@code ticket} is {@code null} for the entries without a ticket the caller may see. */
    public record TicketSum(UUID projectId, String projectKey, TicketRef ticket, long minutes) {
    }

    /** The minutes of one work date. */
    public record DaySum(LocalDate date, long minutes) {
    }

    public record Listing(List<EntryView> entries, boolean truncated, long count, long totalMinutes, List<PersonSum> perPerson,
            List<TicketSum> perTicket, List<DaySum> perDate) {
    }

    /** The filter of a list or an export; {@code mine} limits it to the entries of the caller, whatever the caller may read. */
    public record Filter(UUID projectId, Long ticketNumber, UUID personId, LocalDate from, LocalDate to, boolean cancelled, boolean mine) {
    }

    /**
     * What a booking or a correction sets; {@code personId} only at a booking, {@code reason} and {@code removeHiddenTicket} only
     * at a correction. Without a ticket number a correction keeps a ticket the caller may not see, unless
     * {@code removeHiddenTicket} asks to remove it (TASK-092, F2).
     */
    public record Input(LocalDate date, Integer minutes, String description, Long ticketNumber, UUID personId, String reason,
            boolean removeHiddenTicket) {
    }

    // ------------------------------------------------------------------------------------------------ reading

    /** The projects of the caller with their time actions, by key. */
    @Transactional
    public List<ProjectView> projects(AccessContext actor) {
        List<ProjectView> result = new ArrayList<>();
        if (actor.external()) {
            return result;
        }
        for (UUID projectId : projects.memberships(actor.companyId(), actor.userId())) {
            Access access = access(actor, projectId);
            if (access.any()) {
                projects.summary(actor.companyId(), projectId)
                    .ifPresent(summary -> result.add(new ProjectView(projectId, summary.key(), summary.name(), access)));
            }
        }
        result.sort(Comparator.comparing(ProjectView::key));
        return result;
    }

    /** The members of a project for a caller who reads or books the times of everybody there. */
    @Transactional
    public List<UUID> people(AccessContext actor, UUID projectId) {
        Access access = access(actor, projectId);
        if (!access.any()) {
            throw projectNotFound();
        }
        if (!access.readsAll()) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        return projects.members(projectId);
    }

    @Transactional
    public Listing list(AccessContext actor, Filter filter) {
        return listing(actor, filter, LIST_LIMIT, true);
    }

    /**
     * Every effective matching entry for the export, oldest work date first, with the sums. Above {@link #EXPORT_LIMIT} entries
     * the listing is truncated and carries no entries; the caller refuses it.
     */
    @Transactional
    public Listing export(AccessContext actor, Filter filter) {
        Listing listing = listing(actor, new Filter(filter.projectId(), filter.ticketNumber(), filter.personId(), filter.from(), filter.to(), false,
            filter.mine()), EXPORT_LIMIT, false);
        List<EntryView> entries = new ArrayList<>(listing.entries());
        entries.sort(Comparator.comparing((EntryView view) -> view.entry().date()).thenComparing(view -> view.entry().createdAt()));
        return new Listing(entries, listing.truncated(), listing.count(), listing.totalMinutes(), listing.perPerson(), listing.perTicket(),
            listing.perDate());
    }

    /** One project of a listing with what the caller may see there. */
    private record Part(UUID projectId, Access access, Tickets tickets, UUID person, UUID ticket) {
    }

    /**
     * Count and sums come from aggregates over every matching entry the caller may read, in the database and without a limit;
     * only the returned rows are limited to {@code limit} (TASK-092, F1). With {@code rowsWhenTruncated} false a listing above
     * the limit loads no rows at all.
     */
    private Listing listing(AccessContext actor, Filter filter, int limit, boolean rowsWhenTruncated) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("to.before_from"));
        }
        if (filter.ticketNumber() != null && filter.projectId() == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("ticket.without_project"));
        }
        if (filter.mine() && filter.personId() != null && !filter.personId().equals(actor.userId())) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("person.with_mine"));
        }
        List<UUID> scope = filter.projectId() != null ? List.of(filter.projectId())
            : actor.external() ? List.of() : projects.memberships(actor.companyId(), actor.userId());
        List<Part> parts = new ArrayList<>();
        for (UUID projectId : scope) {
            Access access = access(actor, projectId);
            if (!access.any()) {
                if (filter.projectId() != null) {
                    throw projectNotFound();
                }
                continue;
            }
            Tickets ticketsOf = tickets(actor, projectId);
            UUID ticket = null;
            if (filter.ticketNumber() != null) {
                ticket = ticketsOf.readable() ? tickets.find(actor.companyId(), projectId, filter.ticketNumber()).orElse(null) : null;
                if (ticket == null) {
                    throw ApiProblemException.of(404, "ticket.not_found");
                }
            }
            UUID person = filter.mine() ? actor.userId() : filter.personId();
            if (!access.readsAll()) {
                if (person != null && !person.equals(actor.userId())) {
                    continue;
                }
                person = actor.userId();
            }
            parts.add(new Part(projectId, access, ticketsOf, person, ticket));
        }
        long count = 0;
        long total = 0;
        Map<UUID, Long> perPerson = new LinkedHashMap<>();
        Map<String, TicketSum> perTicket = new LinkedHashMap<>();
        Map<LocalDate, Long> perDate = new HashMap<>();
        for (Part part : parts) {
            for (TimeRepository.Sum sum : repository.sums(actor.companyId(), part.projectId(), part.person(), part.ticket(), filter.from(),
                    filter.to(), filter.cancelled())) {
                count += sum.count();
                if (sum.minutes() == 0) {
                    continue;
                }
                total += sum.minutes();
                perPerson.merge(sum.personId(), sum.minutes(), Long::sum);
                TicketRef ref = part.tickets().ref(sum.ticketId());
                String key = part.projectId() + "#" + (ref == null ? "" : ref.number());
                TicketSum previous = perTicket.get(key);
                perTicket.put(key, new TicketSum(part.projectId(), part.tickets().projectKey(), ref,
                    (previous == null ? 0 : previous.minutes()) + sum.minutes()));
            }
            for (TimeRepository.DaySum day : repository.days(actor.companyId(), part.projectId(), part.person(), part.ticket(), filter.from(),
                    filter.to())) {
                perDate.merge(day.date(), day.minutes(), Long::sum);
            }
        }
        boolean truncated = count > limit;
        List<TimeEntry> found = new ArrayList<>();
        if (!truncated || rowsWhenTruncated) {
            for (Part part : parts) {
                found.addAll(repository.entries(actor.companyId(), part.projectId(), part.person(), part.ticket(), filter.from(), filter.to(),
                    filter.cancelled(), limit + 1));
            }
        }
        found.sort(Comparator.comparing(TimeEntry::date).reversed().thenComparing(TimeEntry::createdAt, Comparator.reverseOrder()));
        Map<UUID, Part> byProject = new HashMap<>();
        parts.forEach(part -> byProject.put(part.projectId(), part));
        List<EntryView> views = new ArrayList<>();
        for (TimeEntry entry : found.subList(0, Math.min(found.size(), limit))) {
            Part part = byProject.get(entry.projectId());
            views.add(view(actor, entry, part.access(), part.tickets()));
        }
        List<PersonSum> persons = perPerson.entrySet().stream().map(e -> new PersonSum(e.getKey(), e.getValue()))
            .sorted(Comparator.comparing(PersonSum::minutes).reversed()).toList();
        List<TicketSum> ticketSums = perTicket.values().stream().sorted(Comparator.comparing(TicketSum::minutes).reversed()).toList();
        List<DaySum> days = perDate.entrySet().stream().map(e -> new DaySum(e.getKey(), e.getValue()))
            .sorted(Comparator.comparing(DaySum::date)).toList();
        return new Listing(views, truncated, count, total, persons, ticketSums, days);
    }

    @Transactional
    public Detail detail(AccessContext actor, UUID id) {
        TimeEntry entry = visibleEntry(actor, id);
        Access access = access(actor, entry.projectId());
        Tickets ticketsOf = tickets(actor, entry.projectId());
        List<RevisionView> revisions = repository.revisions(id).stream()
            .map(revision -> new RevisionView(revision, ticketsOf.ref(revision.ticketId()), ticketsOf.hidden(revision.ticketId()))).toList();
        return new Detail(view(actor, entry, access, ticketsOf), revisions);
    }

    // ------------------------------------------------------------------------------------------------ changes

    @Transactional
    public Detail book(AccessContext actor, UUID projectId, Input input) {
        Access access = access(actor, projectId);
        if (!access.any()) {
            throw projectNotFound();
        }
        UUID person = input.personId() == null ? actor.userId() : input.personId();
        boolean own = person.equals(actor.userId());
        String action = own ? (access.book() ? BOOK : MANAGE) : MANAGE;
        if (own ? !(access.book() || access.manage()) : !access.manage()) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        require(actor, projectId, action);
        List<String> violations = new ArrayList<>();
        if (!own && !(projects.isMember(projectId, person) && identity.accountIsActiveInternal(person))) {
            violations.add("person.not_member");
        }
        Values values = validate(input, violations);
        UUID ticket = ticketOf(actor, projectId, input.ticketNumber(), violations);
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        Instant now = clock.instant();
        TimeEntry entry = new TimeEntry(UUID.randomUUID(), actor.companyId(), projectId, ticket, person, values.date(), values.minutes(),
            values.description(), null, null, now, actor.userId(), now, actor.userId(), 1);
        repository.insert(entry);
        repository.revision(entry.id(), new TimeRevision(1, TimeRevision.CREATED, now, actor.userId(), ticket, values.date(), values.minutes(),
            values.description(), null));
        return detail(actor, entry.id());
    }

    @Transactional
    public Detail correct(AccessContext actor, UUID id, long expectedVersion, Input input) {
        TimeEntry entry = changeable(actor, id, expectedVersion);
        boolean own = entry.personId().equals(actor.userId());
        List<String> violations = new ArrayList<>();
        Values values = validate(input, violations);
        String reason = reason(input.reason(), own, violations);
        // A ticket the caller may not see stays unless a new one is chosen or its removal is asked for (TASK-092, F2).
        boolean keep = input.ticketNumber() == null && !input.removeHiddenTicket() && tickets(actor, entry.projectId()).hidden(entry.ticketId());
        UUID ticket = keep ? entry.ticketId() : ticketOf(actor, entry.projectId(), input.ticketNumber(), violations);
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        boolean same = values.date().equals(entry.date()) && values.minutes() == entry.minutes() && values.description().equals(entry.description())
            && java.util.Objects.equals(ticket, entry.ticketId());
        if (!same) {
            Instant now = clock.instant();
            TimeEntry changed = new TimeEntry(entry.id(), entry.companyId(), entry.projectId(), ticket, entry.personId(), values.date(),
                values.minutes(), values.description(), null, null, entry.createdAt(), entry.createdBy(), now, actor.userId(), entry.version() + 1);
            repository.update(changed);
            repository.revision(id, new TimeRevision(repository.lastRevision(id) + 1, TimeRevision.CORRECTED, now, actor.userId(), ticket,
                values.date(), values.minutes(), values.description(), reason));
        }
        return detail(actor, id);
    }

    @Transactional
    public Detail cancel(AccessContext actor, UUID id, long expectedVersion, String reasonText) {
        TimeEntry entry = changeable(actor, id, expectedVersion);
        List<String> violations = new ArrayList<>();
        String reason = reason(reasonText, entry.personId().equals(actor.userId()), violations);
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        Instant now = clock.instant();
        repository.update(new TimeEntry(entry.id(), entry.companyId(), entry.projectId(), entry.ticketId(), entry.personId(), entry.date(),
            entry.minutes(), entry.description(), now, actor.userId(), entry.createdAt(), entry.createdBy(), now, actor.userId(), entry.version() + 1));
        repository.revision(id, new TimeRevision(repository.lastRevision(id) + 1, TimeRevision.CANCELLED, now, actor.userId(), entry.ticketId(),
            entry.date(), entry.minutes(), entry.description(), reason));
        return detail(actor, id);
    }

    /** The entry locked for a change by the caller, after the action of the change was required. */
    private TimeEntry changeable(AccessContext actor, UUID id, long expectedVersion) {
        TimeEntry entry = visibleEntry(actor, id);
        Access access = access(actor, entry.projectId());
        boolean own = entry.personId().equals(actor.userId());
        if (own ? !(access.book() || access.manage()) : !access.manage()) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        require(actor, entry.projectId(), own && access.book() ? BOOK : MANAGE);
        TimeEntry locked = repository.lock(actor.companyId(), id).orElseThrow(TimeService::entryNotFound);
        if (locked.version() != expectedVersion) {
            throw ApiProblemException.of(412, "time.version_conflict");
        }
        if (locked.cancelled()) {
            throw ApiProblemException.of(409, "time.entry_cancelled");
        }
        return locked;
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private record Values(LocalDate date, int minutes, String description) {
    }

    private Values validate(Input input, List<String> violations) {
        LocalDate latest = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).plusDays(1);
        if (input.date() == null) {
            violations.add("date.required");
        } else if (input.date().isAfter(latest)) {
            violations.add("date.future");
        }
        if (input.minutes() == null || input.minutes() < 1 || input.minutes() > MAX_MINUTES) {
            violations.add("minutes.invalid");
        }
        String description = input.description() == null ? "" : input.description().strip();
        if (description.isEmpty()) {
            violations.add("description.required");
        } else if (description.length() > MAX_DESCRIPTION) {
            violations.add("description.too_long");
        }
        return new Values(input.date(), input.minutes() == null ? 0 : input.minutes(), description);
    }

    private static String reason(String text, boolean own, List<String> violations) {
        String reason = text == null ? "" : text.strip();
        if (reason.isEmpty()) {
            if (!own) {
                violations.add("reason.required");
            }
            return null;
        }
        if (reason.length() > MAX_REASON) {
            violations.add("reason.too_long");
        }
        return reason;
    }

    /** The ticket of the given number when the caller may read tickets of the project; else a violation. */
    private UUID ticketOf(AccessContext actor, UUID projectId, Long number, List<String> violations) {
        if (number == null) {
            return null;
        }
        Optional<UUID> ticket = tickets(actor, projectId).readable() ? tickets.find(actor.companyId(), projectId, number) : Optional.empty();
        if (ticket.isEmpty()) {
            violations.add("ticket.unknown");
            return null;
        }
        return ticket.get();
    }

    private TimeEntry visibleEntry(AccessContext actor, UUID id) {
        TimeEntry entry = repository.find(actor.companyId(), id).orElseThrow(TimeService::entryNotFound);
        Access access = access(actor, entry.projectId());
        boolean own = entry.personId().equals(actor.userId());
        if (!(access.readsAll() || (own && access.book()))) {
            throw entryNotFound();
        }
        return entry;
    }

    private EntryView view(AccessContext actor, TimeEntry entry, Access access, Tickets ticketsOf) {
        boolean own = entry.personId().equals(actor.userId());
        boolean changeable = !entry.cancelled() && (own ? access.book() || access.manage() : access.manage());
        return new EntryView(entry, ticketsOf.projectKey(), ticketsOf.ref(entry.ticketId()), ticketsOf.hidden(entry.ticketId()), own, changeable);
    }

    private Access access(AccessContext actor, UUID projectId) {
        if (actor.external() || projectId == null) {
            return Access.NONE;
        }
        Supplier<ResourceContext> resource = projects.resource(actor.companyId(), projectId, actor.userId(), RESOURCE_TYPE, false);
        boolean book = permissions.inspect(actor, resource, BOOK, Set.of()).allowed();
        boolean read = permissions.inspect(actor, resource, READ, Set.of()).allowed();
        boolean manage = permissions.inspect(actor, resource, MANAGE, Set.of()).allowed();
        return new Access(book, read, manage);
    }

    private void require(AccessContext actor, UUID projectId, String action) {
        try {
            permissions.require(actor, projects.resource(actor.companyId(), projectId, actor.userId(), RESOURCE_TYPE, false), action, Set.of(),
                Set.of());
        } catch (PermissionDenied denied) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
    }

    /** What the caller sees of the tickets of one project. */
    private final class Tickets {
        private final String projectKey;
        private final boolean readable;
        private final boolean titles;
        private final Map<UUID, TicketRef> cache = new HashMap<>();

        Tickets(String projectKey, boolean readable, boolean titles) {
            this.projectKey = projectKey;
            this.readable = readable;
            this.titles = titles;
        }

        String projectKey() {
            return projectKey;
        }

        boolean readable() {
            return readable;
        }

        /** The entry has a ticket the caller may not see. */
        boolean hidden(UUID ticketId) {
            return ticketId != null && ref(ticketId) == null;
        }

        TicketRef ref(UUID ticketId) {
            if (ticketId == null || !readable) {
                return null;
            }
            return cache.computeIfAbsent(ticketId, id -> tickets.locate(id)
                .map(location -> new TicketRef(location.number(), titles ? tickets.title(id).orElse(null) : null)).orElse(null));
        }
    }

    private Tickets tickets(AccessContext actor, UUID projectId) {
        Supplier<ResourceContext> resource = projects.resource(actor.companyId(), projectId, actor.userId(), TicketAccess.RESOURCE_TYPE, false);
        Decision read = permissions.inspect(actor, resource, TICKET_READ, Set.of(TITLE));
        return new Tickets(projects.key(projectId).orElse(""), read.allowed(), read.allowed() && read.readableFields().contains(TITLE));
    }

    private static ApiProblemException projectNotFound() {
        return ApiProblemException.of(404, "project.not_found");
    }

    private static ApiProblemException entryNotFound() {
        return ApiProblemException.of(404, "time.entry_not_found");
    }
}
