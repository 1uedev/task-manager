package taskmanager.jobs.internal;

import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.jobs.JobExecution;
import taskmanager.jobs.JobFailure;
import taskmanager.jobs.JobHandler;
import taskmanager.jobs.RecurringTask;

/**
 * Runs the jobs (TASK-054, DEC-083). It exists in the separate {@code worker} process and, unless the operation turns it off,
 * in the web process; the commands of the container never run jobs. Several executors in several processes share the table:
 * each reserves a due job with {@code FOR UPDATE SKIP LOCKED}, so no job runs twice at the same time.
 *
 * <ul>
 *   <li>Worker threads reserve the job that is due longest, run its handler outside any transaction and record the outcome:
 *       done, or a failed attempt that waits for a growing delay, or failed for good after the last attempt or a permanent
 *       failure. The outcome only counts while the executor still holds the job.</li>
 *   <li>A maintenance thread renews the leases of the running jobs, gives back the jobs of executors that ended without
 *       reporting (their lease expired) and puts due recurring tasks in the table.</li>
 *   <li>An attempt that takes longer than the timeout of its handler is interrupted and counts as failed with
 *       {@code job.timeout}. The log holds the details of every failure under the reference stored with the job.</li>
 * </ul>
 */
@Component
@ConditionalOnExpression("'${taskmanager.process:web}' == 'worker' or ('${taskmanager.process:web}' == 'web' and ${taskmanager.jobs.worker-enabled:true})")
class JobExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(JobExecutor.class);
    private static final String MDC_KEY = "correlationId";
    static final String RECURRING = "recurring.";
    private static final Duration STOP_GRACE = Duration.ofSeconds(15);

    private interface Target {
        Duration timeout();

        void run(JobExecution execution) throws Exception;
    }

    private final JobRepository jobs;
    private final JobsProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final Map<String, Target> targets = new HashMap<>();
    private final Map<String, RecurringTask> recurring = new HashMap<>();
    private final String owner;
    private final Instant startedAt;
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final Object idle = new Object();
    private final List<Thread> workers = new ArrayList<>();
    private volatile boolean running;
    private volatile boolean stopping;
    private ScheduledExecutorService maintenance;
    private volatile Instant backlogReported = Instant.MIN;

    JobExecutor(JobRepository jobs, JobsProperties properties, Clock clock, PlatformTransactionManager transactions,
            ObjectProvider<JobHandler> handlers, ObjectProvider<RecurringTask> tasks) {
        this.jobs = jobs;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        handlers.orderedStream().forEach(handler -> {
            JobService.validKind(handler.kind());
            if (handler.kind().startsWith(RECURRING)) {
                throw new IllegalStateException("The kind prefix " + RECURRING + " belongs to recurring tasks: " + handler.kind());
            }
            register(handler.kind(), new Target() {
                public Duration timeout() {
                    return handler.timeout();
                }

                public void run(JobExecution execution) throws Exception {
                    handler.handle(execution);
                }
            });
        });
        tasks.orderedStream().forEach(task -> {
            String kind = RECURRING + task.name();
            JobService.validKind(kind);
            if (task.interval().toSeconds() < 1) {
                throw new IllegalStateException("A recurring task runs at most once a second: " + task.name());
            }
            recurring.put(task.name(), task);
            register(kind, new Target() {
                public Duration timeout() {
                    return task.timeout();
                }

                public void run(JobExecution execution) throws Exception {
                    task.run();
                }
            });
        });
        this.owner = ownerName();
        this.startedAt = clock.instant();
    }

    private void register(String kind, Target target) {
        if (targets.putIfAbsent(kind, target) != null) {
            throw new IllegalStateException("Two handlers for the job kind " + kind);
        }
    }

    private static String ownerName() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (java.io.IOException | RuntimeException failure) {
            host = "unknown";
        }
        String name = host.length() > 100 ? host.substring(0, 100) : host;
        return name + "/" + ProcessHandle.current().pid() + "/" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** The name under which this executor holds jobs. */
    String owner() {
        return owner;
    }

    @EventListener(ApplicationReadyEvent.class)
    synchronized void start() {
        if (running) {
            return;
        }
        Instant now = clock.instant();
        recurring.values().forEach(task -> transaction.executeWithoutResult(status -> jobs.schedule(task.name(), task.interval().toSeconds(), now)));
        running = true;
        maintenance = Executors.newScheduledThreadPool(2, task -> {
            Thread thread = new Thread(task, "job-maintenance");
            thread.setDaemon(true);
            return thread;
        });
        long poll = Math.max(100, properties.pollInterval().toMillis());
        maintenance.scheduleWithFixedDelay(this::maintain, 0, poll, TimeUnit.MILLISECONDS);
        long renew = Math.max(1000, properties.lease().toMillis() / 3);
        maintenance.scheduleWithFixedDelay(this::renew, renew, renew, TimeUnit.MILLISECONDS);
        for (int i = 1; i <= properties.workerThreads(); i++) {
            // Daemon threads: the worker process waits for the context to close (TaskManagerApplication), the web process for its server.
            Thread thread = new Thread(this::work, "job-worker-" + i);
            thread.setDaemon(true);
            workers.add(thread);
            thread.start();
        }
        LOG.info("Job executor {} started with {} threads for the kinds {}.", owner, properties.workerThreads(), targets.keySet().stream().sorted().toList());
    }

    @PreDestroy
    synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        stopping = true;
        synchronized (idle) {
            idle.notifyAll();
        }
        long deadline = System.nanoTime() + STOP_GRACE.toNanos();
        for (Thread worker : workers) {
            join(worker, deadline);
        }
        // A job that is still running after the grace period is interrupted; it runs again without waiting.
        workers.stream().filter(Thread::isAlive).forEach(Thread::interrupt);
        long last = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        for (Thread worker : workers) {
            join(worker, last);
        }
        maintenance.shutdownNow();
        try {
            transaction.executeWithoutResult(status -> jobs.gone(owner));
        } catch (RuntimeException failure) {
            LOG.debug("The executor {} could not sign off: {}", owner, failure.toString());
        }
        workers.clear();
        LOG.info("Job executor {} stopped.", owner);
    }

    private static void join(Thread thread, long deadline) {
        long wait = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (wait <= 0) {
            return;
        }
        try {
            thread.join(wait);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void work() {
        while (running) {
            Optional<JobRepository.Claimed> claimed;
            try {
                Instant now = clock.instant();
                claimed = transaction.execute(status -> jobs.claim(owner, now, now.plus(properties.lease())));
            } catch (RuntimeException failure) {
                LOG.warn("The job executor {} could not reserve a job: {}", owner, failure.toString());
                pause();
                continue;
            }
            if (claimed == null || claimed.isEmpty()) {
                pause();
                continue;
            }
            execute(claimed.get());
        }
    }

    private void pause() {
        synchronized (idle) {
            if (!running) {
                return;
            }
            try {
                idle.wait(Math.max(100, properties.pollInterval().toMillis()));
            } catch (InterruptedException interrupted) {
                // Only the stop interrupts an idle thread, after it ended the loop.
            }
        }
    }

    /** Runs one reserved job and records its outcome. */
    void execute(JobRepository.Claimed job) {
        active.add(job.id());
        MDC.put(MDC_KEY, "job-" + job.id());
        try {
            Target target = targets.get(job.kind());
            if (target == null) {
                record(job, JobFailure.permanent("job.handler_missing", "No handler for the kind " + job.kind(), null), false);
                return;
            }
            Map<String, String> payload = transaction.execute(status -> jobs.payload(job.id()));
            JobExecution execution = new JobExecution(job.id(), job.companyId(), job.kind(), payload, job.attempt(), job.maxAttempts());
            Watchdog watchdog = new Watchdog(Thread.currentThread());
            ScheduledFuture<?> alarm = maintenance.schedule(watchdog::fire, Math.max(1, target.timeout().toMillis()), TimeUnit.MILLISECONDS);
            Throwable failure = null;
            try {
                target.run(execution);
            } catch (Throwable thrown) {
                failure = thrown;
            } finally {
                alarm.cancel(false);
                watchdog.done();
                // An interrupt meant for this attempt must not hit the database calls that record its outcome.
                Thread.interrupted();
            }
            if (failure == null) {
                boolean recorded = retrying(() -> jobs.succeed(job.id(), owner, clock.instant()));
                if (!recorded) {
                    LOG.warn("Job {} of kind {} finished, but the executor {} no longer held it; its outcome is not recorded.", job.id(), job.kind(), owner);
                }
            } else {
                record(job, failure, watchdog.fired());
                if (failure instanceof Error error && !(failure instanceof AssertionError)) {
                    throw error;
                }
            }
        } catch (RuntimeException failure) {
            // The outcome could not be written; the lease expires and the job runs again.
            LOG.warn("The outcome of job {} could not be recorded: {}", job.id(), failure.toString());
        } finally {
            active.remove(job.id());
            MDC.remove(MDC_KEY);
        }
    }

    private void record(JobRepository.Claimed job, Throwable failure, boolean timedOut) {
        String code;
        boolean permanent = false;
        if (failure instanceof JobFailure known) {
            code = known.code();
            permanent = known.permanent();
        } else if (timedOut) {
            code = "job.timeout";
        } else if (stopping) {
            code = "job.interrupted";
        } else {
            code = "job.failed";
        }
        UUID reference = UUID.randomUUID();
        Instant now = clock.instant();
        boolean last = permanent || job.attempt() >= job.maxAttempts();
        Instant retryAt = last ? null : "job.interrupted".equals(code) ? now : now.plus(properties.delayAfter(job.attempt()));
        LOG.warn("Job {} of kind {} failed in attempt {} of {} with {} (reference {}){}.", job.id(), job.kind(), job.attempt(), job.maxAttempts(),
            code, reference, last ? ", it does not run again" : ", next attempt at " + retryAt, failure);
        boolean recorded = retrying(() -> jobs.fail(job.id(), owner, now, retryAt, code, reference));
        if (!recorded) {
            LOG.warn("The executor {} no longer held job {}; its failure is not recorded.", owner, job.id());
        }
    }

    /** Writes an outcome; a short database failure is tried twice more before the lease decides. */
    private boolean retrying(Supplier<Boolean> write) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return Boolean.TRUE.equals(transaction.execute(status -> write.get()));
            } catch (RuntimeException failure) {
                last = failure;
                try {
                    Thread.sleep(200L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw last;
    }

    /** Signs of life, expired leases and recurring tasks; one run per poll interval. */
    void maintain() {
        try {
            Instant now = clock.instant();
            transaction.executeWithoutResult(status -> jobs.seen(owner, startedAt, now));
            Integer released = transaction.execute(status -> jobs.releaseExpired(now));
            if (released != null && released > 0) {
                LOG.warn("{} jobs of executors that ended without reporting were given back.", released);
            }
            enqueueDue(now);
            reportBacklog(now);
        } catch (RuntimeException failure) {
            LOG.warn("The maintenance of the job executor {} failed: {}", owner, failure.toString());
        }
    }

    /** Warns in the log when a due job waits longer than the backlog limit, at most once per limit. */
    private void reportBacklog(Instant now) {
        if (backlogReported.isAfter(now.minus(properties.backlogAfter()))) {
            return;
        }
        Optional<Instant> oldest = transaction.execute(status -> jobs.oldestDue(now));
        if (oldest != null && oldest.isPresent() && oldest.get().isBefore(now.minus(properties.backlogAfter()))) {
            backlogReported = now;
            LOG.warn("Job backlog: the oldest due job waits since {}; the executors do not keep up.", oldest.get());
        }
    }

    private void enqueueDue(Instant now) {
        if (recurring.isEmpty()) {
            return;
        }
        transaction.executeWithoutResult(status -> {
            for (String name : jobs.lockDueSchedules(List.copyOf(recurring.keySet()), now)) {
                String kind = RECURRING + name;
                boolean enqueue = !jobs.pending(kind);
                if (enqueue) {
                    jobs.insert(UUID.randomUUID(), null, kind, Map.of(), null, now, 1, now);
                }
                jobs.advanceSchedule(name, now, enqueue);
            }
        });
    }

    private void renew() {
        try {
            List<UUID> ids = List.copyOf(active);
            if (!ids.isEmpty()) {
                Instant until = clock.instant().plus(properties.lease());
                transaction.execute(status -> jobs.renew(owner, ids, until));
            }
        } catch (RuntimeException failure) {
            LOG.warn("The executor {} could not renew its leases: {}", owner, failure.toString());
        }
    }

    /** Interrupts an attempt that took too long, but never after it ended. */
    private static final class Watchdog {
        private final Thread thread;
        private boolean finished;
        private boolean fired;

        Watchdog(Thread thread) {
            this.thread = thread;
        }

        synchronized void fire() {
            if (!finished) {
                fired = true;
                thread.interrupt();
            }
        }

        synchronized void done() {
            finished = true;
        }

        synchronized boolean fired() {
            return fired;
        }
    }
}
