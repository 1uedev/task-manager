package taskmanager.sla.internal;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A business hours calendar (TASK-068, REQ-HD-004): an IANA time zone, open hours for each day of the week and dated
 * exceptions that replace the hours of one day (no hours: closed). Time counts only inside open hours; overlapping hours
 * count once; a start is included and an end is not. Every boundary is resolved in the zone to one instant: a local time
 * that does not exist because the clock jumps forward becomes the moment of the jump, a local time that exists twice
 * becomes its earlier instant as a start and its later instant as an end (docs/HELPDESK_SLA.md). Durations are the real
 * seconds that pass inside the resulting intervals, so a shorter or longer night in the hours counts as it really is.
 *
 * <p>A definition never changes once stored with a version; a cycle keeps the version it began with.
 */
public final class WorkCalendar {
    static final int MAX_SPANS = 10;
    static final int MAX_EXCEPTIONS = 400;
    /** How far a computation looks ahead before it gives up; with open hours every week it never gets there. */
    private static final int MAX_DAYS = 3_700;
    private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d|24:00");

    /** Open hours of one local day, {@code from} before {@code to}, as {@code HH:mm}; {@code to} may be {@code 24:00}. */
    public record Span(String from, String to) {
    }

    /** The hours of a day of the week, 1 for Monday to 7 for Sunday. */
    public record Day(int day, List<Span> hours) {
        public Day {
            hours = hours == null ? List.of() : List.copyOf(hours);
        }
    }

    /** The hours of one date instead of its day of the week; no hours means closed. */
    public record Exception(String date, List<Span> hours) {
        public Exception {
            hours = hours == null ? List.of() : List.copyOf(hours);
        }
    }

    /** What is stored and sent: the zone, the week and the exceptions. */
    public record Definition(String timeZone, List<Day> week, List<Exception> exceptions) {
        public Definition {
            week = week == null ? List.of() : List.copyOf(week);
            exceptions = exceptions == null ? List.of() : List.copyOf(exceptions);
        }
    }

    private record Minutes(int from, int to) {
    }

    private record Interval(Instant start, Instant end) {
    }

    private final ZoneId zone;
    private final Map<Integer, List<Minutes>> week = new HashMap<>();
    private final Map<LocalDate, List<Minutes>> exceptions = new HashMap<>();

    private WorkCalendar(Definition definition) {
        this.zone = ZoneId.of(definition.timeZone());
        for (Day day : definition.week()) {
            week.put(day.day(), minutes(day.hours()));
        }
        for (Exception exception : definition.exceptions()) {
            exceptions.put(LocalDate.parse(exception.date()), minutes(exception.hours()));
        }
    }

    /** The calendar of a definition without {@link #violations}. */
    public static WorkCalendar of(Definition definition) {
        List<String> violations = violations(definition);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("invalid calendar: " + violations);
        }
        return new WorkCalendar(definition);
    }

    public ZoneId zone() {
        return zone;
    }

    // ------------------------------------------------------------------------------------------------ rules

    /** Why the definition cannot be stored, as stable codes; empty when it can. */
    public static List<String> violations(Definition definition) {
        List<String> violations = new ArrayList<>();
        if (definition == null) {
            return List.of("calendar.required");
        }
        try {
            if (definition.timeZone() == null || !definition.timeZone().contains("/") && !definition.timeZone().equals("UTC")) {
                violations.add("timeZone.invalid");
            } else {
                ZoneId.of(definition.timeZone());
            }
        } catch (DateTimeException invalid) {
            violations.add("timeZone.invalid");
        }
        Set<Integer> days = new HashSet<>();
        boolean open = false;
        for (Day day : definition.week()) {
            if (day == null || day.day() < 1 || day.day() > 7) {
                add(violations, "week.day_invalid");
                continue;
            }
            if (!days.add(day.day())) {
                add(violations, "week.day_duplicate");
            }
            open |= spans(day.hours(), "week", violations);
        }
        if (!open) {
            add(violations, "week.closed");
        }
        if (definition.exceptions().size() > MAX_EXCEPTIONS) {
            add(violations, "exceptions.too_many");
        }
        Set<LocalDate> dates = new HashSet<>();
        for (Exception exception : definition.exceptions()) {
            if (exception == null || exception.date() == null) {
                add(violations, "exceptions.date_invalid");
                continue;
            }
            try {
                LocalDate date = LocalDate.parse(exception.date());
                if (!date.toString().equals(exception.date()) || !dates.add(date)) {
                    add(violations, date.toString().equals(exception.date()) ? "exceptions.date_duplicate" : "exceptions.date_invalid");
                }
            } catch (DateTimeParseException invalid) {
                add(violations, "exceptions.date_invalid");
            }
            spans(exception.hours(), "exceptions", violations);
        }
        return List.copyOf(violations);
    }

    /** Checks the spans of one day; returns whether they open the day at all. */
    private static boolean spans(List<Span> spans, String prefix, List<String> violations) {
        if (spans.size() > MAX_SPANS) {
            add(violations, prefix + ".hours_too_many");
        }
        boolean open = false;
        for (Span span : spans) {
            if (span == null || span.from() == null || span.to() == null || !TIME.matcher(span.from()).matches()
                    || !TIME.matcher(span.to()).matches() || span.from().equals("24:00")) {
                add(violations, prefix + ".hours_invalid");
                continue;
            }
            if (minute(span.from()) >= minute(span.to())) {
                add(violations, prefix + ".hours_invalid");
                continue;
            }
            open = true;
        }
        return open;
    }

    private static int minute(String time) {
        return Integer.parseInt(time.substring(0, 2)) * 60 + Integer.parseInt(time.substring(3));
    }

    private static List<Minutes> minutes(List<Span> spans) {
        return spans.stream().map(span -> new Minutes(minute(span.from()), minute(span.to()))).toList();
    }

    private static void add(List<String> violations, String code) {
        if (!violations.contains(code)) {
            violations.add(code);
        }
    }

    // ------------------------------------------------------------------------------------------------ time

    /**
     * The instant at which {@code seconds} of open time have passed since {@code start}. An amount that ends exactly at the
     * end of open hours ends there and not at the next opening. Zero is {@code start} itself.
     */
    public Instant add(Instant start, long seconds) {
        if (seconds <= 0) {
            return start;
        }
        long remaining = seconds;
        LocalDate date = LocalDate.ofInstant(start, zone).minusDays(1);
        for (int day = 0; day < MAX_DAYS; day += 7) {
            for (Interval interval : merged(date.plusDays(day), 7)) {
                if (!interval.end().isAfter(start)) {
                    continue;
                }
                Instant from = interval.start().isAfter(start) ? interval.start() : start;
                long available = Duration.between(from, interval.end()).getSeconds();
                if (remaining <= available) {
                    return from.plusSeconds(remaining);
                }
                remaining -= available;
            }
        }
        throw new IllegalStateException("no open time within " + MAX_DAYS + " days");
    }

    /** The open time between two instants in seconds; zero when {@code to} is not after {@code from}. */
    public long between(Instant from, Instant to) {
        if (!to.isAfter(from)) {
            return 0;
        }
        LocalDate first = LocalDate.ofInstant(from, zone).minusDays(1);
        LocalDate last = LocalDate.ofInstant(to, zone).plusDays(1);
        long seconds = 0;
        for (Interval interval : merged(first, (int) (last.toEpochDay() - first.toEpochDay()) + 1)) {
            Instant start = interval.start().isAfter(from) ? interval.start() : from;
            Instant end = interval.end().isBefore(to) ? interval.end() : to;
            if (end.isAfter(start)) {
                seconds += Duration.between(start, end).getSeconds();
            }
        }
        return seconds;
    }

    /** The open intervals of {@code days} dates from {@code first}, as instants, sorted and without overlaps. */
    private List<Interval> merged(LocalDate first, int days) {
        List<Interval> all = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = first.plusDays(i);
            List<Minutes> hours = exceptions.getOrDefault(date, week.getOrDefault(date.getDayOfWeek().getValue(), List.of()));
            for (Minutes span : hours) {
                Instant start = resolve(date, span.from(), true);
                Instant end = resolve(date, span.to(), false);
                if (end.isAfter(start)) {
                    all.add(new Interval(start, end));
                }
            }
        }
        all.sort(Comparator.comparing(Interval::start));
        List<Interval> merged = new ArrayList<>();
        for (Interval interval : all) {
            if (!merged.isEmpty() && !interval.start().isAfter(merged.getLast().end())) {
                Interval last = merged.removeLast();
                merged.add(new Interval(last.start(), interval.end().isAfter(last.end()) ? interval.end() : last.end()));
            } else {
                merged.add(interval);
            }
        }
        return merged;
    }

    /** One boundary of open hours as an instant, with the rules for a skipped or repeated local time. */
    private Instant resolve(LocalDate date, int minute, boolean start) {
        LocalDateTime local = minute == 24 * 60 ? date.plusDays(1).atStartOfDay() : date.atTime(LocalTime.of(minute / 60, minute % 60));
        ZoneRules rules = zone.getRules();
        List<ZoneOffset> offsets = rules.getValidOffsets(local);
        if (offsets.isEmpty()) {
            ZoneOffsetTransition gap = rules.getTransition(local);
            return gap.getInstant();
        }
        Instant first = local.toInstant(offsets.getFirst());
        Instant last = local.toInstant(offsets.getLast());
        Instant earlier = first.isBefore(last) ? first : last;
        Instant later = first.isBefore(last) ? last : first;
        return start ? earlier : later;
    }
}
