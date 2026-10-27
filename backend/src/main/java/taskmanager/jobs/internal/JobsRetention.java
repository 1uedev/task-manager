package taskmanager.jobs.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.jobs.RecurringTask;

/** Deletes finished jobs after their period and forgets executors not seen for a day (TASK-054). */
@Component
class JobsRetention implements RecurringTask {
    private static final Logger LOG = LoggerFactory.getLogger(JobsRetention.class);
    private final JobRepository jobs;
    private final JobsProperties properties;
    private final Clock clock;

    JobsRetention(JobRepository jobs, JobsProperties properties, Clock clock) {
        this.jobs = jobs;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "jobs.retention";
    }

    @Override
    public Duration interval() {
        return properties.retentionInterval();
    }

    @Override
    @Transactional
    public void run() {
        Instant now = clock.instant();
        int deleted = jobs.purge(now.minus(properties.keepSucceeded()), now.minus(properties.keepFailed()), now.minus(Duration.ofDays(1)));
        if (deleted > 0) {
            LOG.info("{} finished jobs deleted after their retention period.", deleted);
        }
    }
}
