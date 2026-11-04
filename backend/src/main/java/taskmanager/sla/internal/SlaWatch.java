package taskmanager.sla.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import taskmanager.jobs.RecurringTask;

/**
 * The watch over the escalation stages (TASK-070, rule 3) as the recurring task {@code sla.watch} once a minute. Each run
 * goes page by page through the tickets whose open cycle has a running clock and a rule version with stages, and stores the
 * stages due by now, each ticket in a transaction of its own ({@link TicketSla#watchTicket}). A process that was down catches
 * up with the next run: only the highest due stage of a clock is reached, lower ones are skipped.
 */
@Component
public class SlaWatch implements RecurringTask {
    private static final int BATCH = 200;

    private final SlaRepository repository;
    private final TicketSla tickets;
    private final Clock clock;

    SlaWatch(SlaRepository repository, TicketSla tickets, Clock clock) {
        this.repository = repository;
        this.tickets = tickets;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "sla.watch";
    }

    @Override
    public Duration interval() {
        return Duration.ofMinutes(1);
    }

    @Override
    public Duration timeout() {
        return Duration.ofMinutes(5);
    }

    @Override
    public void run() {
        watch(clock.instant());
    }

    /** Stores the stages due at {@code now}; returns how many were reached. Public for the tests, which choose the time. */
    public int watch(Instant now) {
        int reached = 0;
        UUID after = null;
        List<UUID> page;
        do {
            page = repository.watchedTickets(after, BATCH);
            for (UUID ticket : page) {
                reached += tickets.watchTicket(ticket, now);
            }
            after = page.isEmpty() ? after : page.get(page.size() - 1);
        } while (page.size() == BATCH);
        return reached;
    }
}
