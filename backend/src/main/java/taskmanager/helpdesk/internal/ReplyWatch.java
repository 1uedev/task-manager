package taskmanager.helpdesk.internal;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.jobs.RecurringTask;

/**
 * Settles replies whose send job ended without recording an outcome (TASK-062, C-F1 and X-F2), as the recurring task
 * {@code helpdesk.replies} once a minute. The job of a reply runs it again after a lost attempt, and that attempt marks the
 * reply uncertain; after the last attempt, after another error of the last attempt or after a cancel in the administration
 * there is no such attempt, and without this task the reply would wait for ever. Each reply is settled in a transaction of
 * its own under its lock, through {@link ReplyService#settleOrphan}, which reads the job under a share lock so that a retry
 * of the administration and the settling exclude each other (TASK-063, F1).
 */
@Component
class ReplyWatch implements RecurringTask {
    private static final Logger LOG = LoggerFactory.getLogger(ReplyWatch.class);
    private static final int BATCH = 200;

    private final HelpdeskRepository repository;
    private final ReplyService replies;
    private final Clock clock;
    private final TransactionTemplate transaction;

    ReplyWatch(HelpdeskRepository repository, ReplyService replies, Clock clock, PlatformTransactionManager transactions) {
        this.repository = repository;
        this.replies = replies;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Override
    public String name() {
        return "helpdesk.replies";
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
        settle();
    }

    /**
     * Settles the replies whose job ended; returns how many changed. Each run goes through every waiting reply page by page,
     * so replies whose job still runs never hide later ones (TASK-063, H6).
     */
    int settle() {
        int settled = 0;
        UUID after = null;
        List<UUID> page;
        do {
            page = repository.unsettledReplies(after, BATCH);
            for (UUID id : page) {
                Boolean changed = transaction.execute(status -> repository.lockReply(id)
                    .map(reply -> replies.settleOrphan(reply, clock.instant()).version() != reply.version()).orElse(false));
                if (Boolean.TRUE.equals(changed)) {
                    LOG.warn("Reply {} waited for a job that ended without an outcome; it is settled", id);
                    settled++;
                }
            }
            after = page.isEmpty() ? after : page.get(page.size() - 1);
        } while (page.size() == BATCH);
        return settled;
    }
}
