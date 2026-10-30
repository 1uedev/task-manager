package taskmanager.helpdesk.internal;

import java.time.Clock;
import java.time.Duration;
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
 * its own under its lock, through {@link ReplyService#settleOrphan}.
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

    /** Settles the replies whose job ended; returns how many changed. */
    int settle() {
        int settled = 0;
        for (UUID id : repository.unsettledReplies(BATCH)) {
            Boolean changed = transaction.execute(status -> repository.lockReply(id)
                .map(reply -> replies.settleOrphan(reply, clock.instant()).version() != reply.version()).orElse(false));
            if (Boolean.TRUE.equals(changed)) {
                LOG.warn("Reply {} waited for a job that ended without an outcome; it is settled", id);
                settled++;
            }
        }
        return settled;
    }
}
