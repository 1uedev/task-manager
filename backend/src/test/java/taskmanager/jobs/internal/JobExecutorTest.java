package taskmanager.jobs.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.TestDatabase;
import taskmanager.jobs.JobExecution;
import taskmanager.jobs.JobFailure;
import taskmanager.jobs.JobHandler;
import taskmanager.jobs.Jobs;
import taskmanager.jobs.NewJob;
import taskmanager.jobs.RecurringTask;

/**
 * The executor against a real PostgreSQL (TASK-054). The executor of the application is off in tests; every test starts the
 * executors it needs, often two at once, the way a web process and a worker process share the table.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "taskmanager.jobs.poll-interval=100ms", "taskmanager.jobs.lease=3s", "taskmanager.jobs.retry-delay=100ms",
    "taskmanager.jobs.retry-max-delay=400ms", "taskmanager.jobs.worker-threads=3" })
class JobExecutorTest {
    private static final String DATABASE = freshDatabase();

    private static String freshDatabase() {
        try {
            return TestDatabase.createEmptyDatabase();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE);
        registry.add("spring.datasource.username", TestDatabase::username);
        registry.add("spring.datasource.password", TestDatabase::password);
    }

    /** What the test handlers did, per job. */
    static final Map<UUID, AtomicInteger> RUNS = new ConcurrentHashMap<>();
    static final Set<UUID> RUNNING = ConcurrentHashMap.newKeySet();
    static final AtomicInteger OVERLAPS = new AtomicInteger();
    static final AtomicInteger TICKS = new AtomicInteger();
    /** What the handler of {@code test.resume} saw per run: attempt and {@code repeated()}. */
    static final List<String> SEEN = java.util.Collections.synchronizedList(new ArrayList<>());
    static volatile JdbcTemplate effects;
    /** Holds the clean up of {@code test.cleanup} after the stop interrupted it (TASK-056, R3). */
    static volatile CountDownLatch cleanUp = new CountDownLatch(1);
    /** The runs of {@code test.gate} in their order: each signals its start and waits until the test lets it end (TASK-065). */
    static final List<Gate> GATES = java.util.Collections.synchronizedList(new ArrayList<>());
    static final AtomicInteger GATE_RUNS = new AtomicInteger();

    /** One run of {@code test.gate}: how it ends ({@code ok}, {@code fail} or {@code permanent}) once the test released it. */
    record Gate(CountDownLatch entered, CountDownLatch release, java.util.concurrent.atomic.AtomicReference<String> outcome) {
        Gate() {
            this(new CountDownLatch(1), new CountDownLatch(1), new java.util.concurrent.atomic.AtomicReference<>("ok"));
        }

        void end(String how) {
            outcome.set(how);
            release.countDown();
        }
    }

    @TestConfiguration
    static class Handlers {
        @Bean
        JobHandler countingHandler() {
            return handler("test.count", Duration.ofMinutes(1), execution -> {
            });
        }

        @Bean
        JobHandler failingHandler() {
            return handler("test.fail", Duration.ofMinutes(1), execution -> {
                throw new IllegalStateException("secret value that must not reach the table");
            });
        }

        @Bean
        JobHandler permanentHandler() {
            return handler("test.permanent", Duration.ofMinutes(1), execution -> {
                throw JobFailure.permanent("test.refused", "refused", null);
            });
        }

        @Bean
        JobHandler slowHandler() {
            return handler("test.slow", Duration.ofMillis(300), execution -> Thread.sleep(10_000));
        }

        @Bean
        JobHandler longHandler() {
            // Longer than the lease of three seconds: only the renewal keeps a second executor from taking it.
            return handler("test.long", Duration.ofMinutes(1), execution -> Thread.sleep(4_500));
        }

        @Bean
        JobHandler effectHandler() {
            // Records its effect under the job identifier, so a repeated attempt finds it and does not add a second one.
            return handler("test.effect", Duration.ofMinutes(1), execution -> effects.update(
                "insert into test_job_effect (job_id, attempt) values (?, ?) on conflict (job_id) do nothing", execution.id(), execution.attempt()));
        }

        @Bean
        JobHandler overflowHandler() {
            // A stack overflow is unwound and leaves the virtual machine sound: the worker thread goes on (TASK-055, F1).
            return handler("test.overflow", Duration.ofMinutes(1), execution -> {
                throw new StackOverflowError("test");
            });
        }

        @Bean
        JobHandler outOfMemoryHandler() {
            return handler("test.oom", Duration.ofMinutes(1), execution -> {
                throw new OutOfMemoryError("test");
            });
        }

        @Bean
        JobHandler resumeHandler() {
            // The first run waits until the stop interrupts it or fails; every run records what it saw (TASK-055, F2 and F3).
            return handler("test.resume", Duration.ofMinutes(1), execution -> {
                SEEN.add(execution.attempt() + "/" + execution.repeated());
                if (!execution.repeated()) {
                    if ("fail".equals(execution.payload().get("first"))) {
                        throw new IllegalStateException("first run fails after its effect");
                    }
                    Thread.sleep(60_000);
                }
            });
        }

        @Bean
        JobHandler refusingHandler() {
            // Reports a permanent failure when the stop interrupts it (TASK-056, R2).
            return handler("test.refuse", Duration.ofMinutes(1), execution -> {
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException interrupted) {
                    throw JobFailure.permanent("test.refused_on_stop", "refused while stopping", interrupted);
                }
            });
        }

        @Bean
        JobHandler cleaningHandler() {
            // Cleans up after the stop interrupted it and passes the interrupt on only when the test lets it (TASK-056, R3).
            return handler("test.cleanup", Duration.ofMinutes(1), execution -> {
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException interrupted) {
                    cleanUp.await();
                    throw interrupted;
                }
            });
        }

        @Bean
        JobHandler gateHandler() {
            // Runs that overlap after a lost lease; the test decides when and how each one ends (TASK-065, F1).
            return handler("test.gate", Duration.ofMinutes(1), execution -> {
                Gate gate = GATES.get(GATE_RUNS.getAndIncrement());
                gate.entered().countDown();
                gate.release().await();
                switch (gate.outcome().get()) {
                    case "fail" -> throw new IllegalStateException("late failure");
                    case "permanent" -> throw JobFailure.permanent("test.gate_refused", "refused", null);
                    default -> {
                    }
                }
            });
        }

        @Bean
        RecurringTask tick() {
            return new RecurringTask() {
                public String name() {
                    return "test.tick";
                }

                public Duration interval() {
                    return Duration.ofHours(1);
                }

                public void run() {
                    TICKS.incrementAndGet();
                }
            };
        }

        interface Body {
            void run(JobExecution execution) throws Exception;
        }

        static JobHandler handler(String kind, Duration timeout, Body body) {
            return new JobHandler() {
                public String kind() {
                    return kind;
                }

                public Duration timeout() {
                    return timeout;
                }

                public void handle(JobExecution execution) throws Exception {
                    if (!RUNNING.add(execution.id())) {
                        OVERLAPS.incrementAndGet();
                    }
                    try {
                        RUNS.computeIfAbsent(execution.id(), id -> new AtomicInteger()).incrementAndGet();
                        body.run(execution);
                    } finally {
                        RUNNING.remove(execution.id());
                    }
                }
            };
        }
    }

    @Autowired Jobs jobs;
    @Autowired JobRepository repository;
    @Autowired JobsProperties properties;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext context;

    private final List<JobExecutor> executors = new ArrayList<>();
    private TransactionTemplate transaction;

    @BeforeEach
    void clean() {
        effects = jdbc;
        transaction = new TransactionTemplate(transactions);
        jdbc.execute("create table if not exists test_job_effect (job_id uuid primary key, attempt integer not null)");
        jdbc.update("delete from test_job_effect");
        jdbc.update("delete from job");
        jdbc.update("delete from job_schedule");
        jdbc.update("delete from job_worker");
        RUNS.clear();
        RUNNING.clear();
        OVERLAPS.set(0);
        TICKS.set(0);
        SEEN.clear();
        cleanUp = new CountDownLatch(1);
        GATES.clear();
        GATE_RUNS.set(0);
        for (int i = 0; i < 3; i++) {
            GATES.add(new Gate());
        }
    }

    @AfterEach
    void stop() {
        executors.forEach(JobExecutor::stop);
        executors.clear();
    }

    private JobExecutor executor() {
        return executor(repository);
    }

    private JobExecutor executor(JobRepository repository) {
        JobExecutor executor = new JobExecutor(repository, properties, clock, transactions, context.getBeanProvider(JobHandler.class),
            context.getBeanProvider(RecurringTask.class));
        executor.stopGrace = Duration.ofSeconds(1);
        executors.add(executor);
        return executor;
    }

    /** Stops an executor that a test started, and waits until the stop returned. */
    private void stop(JobExecutor executor) {
        executor.stop();
        executors.remove(executor);
    }

    private JobExecutor started() {
        JobExecutor executor = executor();
        executor.start();
        return executor;
    }

    private UUID enqueue(NewJob job) {
        return transaction.execute(status -> jobs.enqueue(job));
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("select state, attempts, max_attempts, error_code, error_reference, lease_owner from job where id = ?", id);
    }

    private static void await(String what, BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting until " + what);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private String state(UUID id) {
        return jdbc.queryForObject("select state from job where id = ?", String.class, id);
    }

    @Test
    void aJobOfARolledBackChangeDoesNotExistAndACommittedOneRunsOnce() {
        UUID company = UUID.randomUUID();
        UUID[] rolledBack = new UUID[1];
        transaction.executeWithoutResult(status -> {
            rolledBack[0] = jobs.enqueue(NewJob.of(company, "test.count"));
            status.setRollbackOnly();
        });
        assertEquals(0, jdbc.queryForObject("select count(*) from job where id = ?", Integer.class, rolledBack[0]));

        UUID committed = enqueue(NewJob.of(company, "test.count").withPayload(Map.of("ticket", "42")));
        started();
        await("the job is done", () -> "succeeded".equals(state(committed)));
        assertEquals(1, RUNS.get(committed).get());
        assertNull(RUNS.get(rolledBack[0]));
        Map<String, Object> row = row(committed);
        assertEquals(1, row.get("attempts"));
        assertNull(row.get("lease_owner"));
        assertNull(row.get("error_code"));
    }

    @Test
    void enqueuingNeedsATransactionAndRefusesWhatIsNotAReference() {
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class, () -> jobs.enqueue(NewJob.of(null, "test.count")));
        assertThrows(IllegalArgumentException.class, () -> enqueue(NewJob.of(null, "Test Count")));
        assertThrows(IllegalArgumentException.class, () -> enqueue(NewJob.of(null, "test.count").withPayload(Map.of("text", "x".repeat(201)))));
        assertThrows(IllegalArgumentException.class, () -> enqueue(NewJob.of(null, "test.count").withMaxAttempts(0)));
        assertEquals(0, jdbc.queryForObject("select count(*) from job", Integer.class));
    }

    @Test
    void aSecondJobWithTheSameKeyIsTheFirstOne() {
        UUID company = UUID.randomUUID();
        UUID first = enqueue(NewJob.of(company, "test.count").withDedupKey("mail-17"));
        UUID second = enqueue(NewJob.of(company, "test.count").withDedupKey("mail-17").withPayload(Map.of("other", "1")));
        assertEquals(first, second);
        UUID otherCompany = enqueue(NewJob.of(UUID.randomUUID(), "test.count").withDedupKey("mail-17"));
        UUID installation = enqueue(NewJob.of(null, "test.count").withDedupKey("mail-17"));
        assertEquals(installation, enqueue(NewJob.of(null, "test.count").withDedupKey("mail-17")));
        assertEquals(3, Set.of(first, otherCompany, installation).size());
        assertEquals("{}", jdbc.queryForObject("select payload::text from job where id = ?", String.class, first));
    }

    @Test
    void twoExecutorsRunEveryJobExactlyOnce() {
        List<UUID> ids = new ArrayList<>();
        transaction.executeWithoutResult(status -> {
            for (int i = 0; i < 150; i++) {
                ids.add(jobs.enqueue(NewJob.of(null, "test.count")));
            }
        });
        started();
        started();
        await("all jobs are done", () -> jdbc.queryForObject("select count(*) from job where kind = 'test.count' and state = 'succeeded'", Integer.class) == 150);
        for (UUID id : ids) {
            assertEquals(1, RUNS.get(id).get(), "runs of " + id);
        }
        assertEquals(0, OVERLAPS.get());
        assertEquals(150, jdbc.queryForObject("select count(*) from job where kind = 'test.count' and attempts = 1", Integer.class));
    }

    @Test
    void aJobThatAlwaysFailsEndsVisiblyWithACodeAndAReferenceButNoMessage() {
        UUID id = enqueue(NewJob.of(UUID.randomUUID(), "test.fail").withMaxAttempts(3));
        started();
        await("the job failed for good", () -> "failed".equals(state(id)));
        Map<String, Object> row = row(id);
        assertEquals(3, row.get("attempts"));
        assertEquals(3, RUNS.get(id).get());
        assertEquals("job.failed", row.get("error_code"));
        assertNotNull(row.get("error_reference"));
        String everything = jdbc.queryForObject("select row_to_json(job)::text from job where id = ?", String.class, id);
        assertFalse(everything.contains("secret"), everything);
    }

    @Test
    void aPermanentFailureEndsTheJobAtOnce() {
        UUID id = enqueue(NewJob.of(null, "test.permanent"));
        started();
        await("the job failed", () -> "failed".equals(state(id)));
        assertEquals(1, row(id).get("attempts"));
        assertEquals("test.refused", row(id).get("error_code"));
    }

    @Test
    void anAttemptThatTakesTooLongIsInterruptedAndCountsAsTimeout() {
        UUID id = enqueue(NewJob.of(null, "test.slow").withMaxAttempts(1));
        long start = System.nanoTime();
        started();
        await("the job failed", () -> "failed".equals(state(id)));
        assertEquals("job.timeout", row(id).get("error_code"));
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 8, "the handler was not interrupted");
    }

    @Test
    void aJobWithoutHandlerFailsForGood() {
        UUID id = enqueue(NewJob.of(null, "test.unknown"));
        started();
        await("the job failed", () -> "failed".equals(state(id)));
        assertEquals("job.handler_missing", row(id).get("error_code"));
        assertEquals(1, row(id).get("attempts"));
    }

    @Test
    void aJobOfAnExecutorThatEndedRunsOnceMoreAndSeesItsEarlierEffect() {
        UUID id = enqueue(NewJob.of(UUID.randomUUID(), "test.effect"));
        // An executor of another process reserved the job, produced the effect and ended before it reported the outcome.
        Instant now = clock.instant();
        transaction.executeWithoutResult(status -> repository.claim("ended-process", now, now.minusSeconds(1)));
        jdbc.update("insert into test_job_effect (job_id, attempt) values (?, 1)", id);
        assertEquals("running", state(id));

        started();
        await("the job is done", () -> "succeeded".equals(state(id)));
        assertEquals(2, row(id).get("attempts"));
        assertEquals(1, jdbc.queryForObject("select count(*) from test_job_effect where job_id = ?", Integer.class, id));
        assertEquals(1, jdbc.queryForObject("select attempt from test_job_effect where job_id = ?", Integer.class, id));
        assertNull(row(id).get("error_code"));
    }

    @Test
    void anExpiredLeaseOnTheLastAttemptFailsTheJob() {
        UUID id = enqueue(NewJob.of(null, "test.count").withMaxAttempts(1));
        Instant now = clock.instant();
        transaction.executeWithoutResult(status -> repository.claim("ended-process", now, now.minusSeconds(1)));
        executor().maintain();
        assertEquals("failed", state(id));
        assertEquals("job.lease_expired", row(id).get("error_code"));
        assertNull(RUNS.get(id));
    }

    @Test
    void aRunningExecutorRenewsItsLeaseSoALongJobIsNotTakenOver() {
        UUID id = enqueue(NewJob.of(null, "test.long"));
        started();
        await("the job runs", () -> RUNS.containsKey(id));
        started();
        await("the job is done", () -> "succeeded".equals(state(id)));
        assertEquals(1, RUNS.get(id).get());
        assertEquals(1, row(id).get("attempts"));
        assertEquals(0, OVERLAPS.get());
    }

    @Test
    void aDueRecurringTaskIsEnqueuedOnceEvenWithTwoExecutorsAtTheSameTime() throws Exception {
        JobExecutor first = executor();
        JobExecutor second = executor();
        Instant now = clock.instant();
        transaction.executeWithoutResult(status -> repository.schedule("test.tick", 3600, now));
        CountDownLatch go = new CountDownLatch(1);
        Thread a = new Thread(() -> {
            await(go);
            first.maintain();
        });
        Thread b = new Thread(() -> {
            await(go);
            second.maintain();
        });
        a.start();
        b.start();
        go.countDown();
        a.join();
        b.join();
        assertEquals(1, jdbc.queryForObject("select count(*) from job where kind = 'recurring.test.tick'", Integer.class));
        Map<String, Object> schedule = jdbc.queryForMap("select next_run_at > now() + interval '50 minutes' as moved, last_enqueued_at from job_schedule where name = 'test.tick'");
        assertEquals(true, schedule.get("moved"));
        assertNotNull(schedule.get("last_enqueued_at"));

        // Due again while the first run still waits: no second run.
        jdbc.update("update job_schedule set next_run_at = now() - interval '1 second' where name = 'test.tick'");
        first.maintain();
        assertEquals(1, jdbc.queryForObject("select count(*) from job where kind = 'recurring.test.tick'", Integer.class));

        first.start();
        await("the run is done", () -> jdbc.queryForObject("select count(*) from job where kind = 'recurring.test.tick' and state = 'succeeded'", Integer.class) == 1);
        assertEquals(1, TICKS.get());
        assertEquals(1, jdbc.queryForObject("select max_attempts from job where kind = 'recurring.test.tick'", Integer.class));
    }

    @Test
    void theFileCleanUpIsARecurringTaskOfTheExecutor() {
        started();
        await("the schedule exists", () -> jdbc.queryForObject("select count(*) from job_schedule where name = 'files.cleanup'", Integer.class) == 1);
        await("the clean up ran", () -> jdbc.queryForObject(
            "select count(*) from job where kind = 'recurring.files.cleanup' and state = 'succeeded'", Integer.class) == 1);
        assertEquals(3600L, jdbc.queryForObject("select interval_seconds from job_schedule where name = 'files.cleanup'", Long.class));
    }

    @Test
    void theRetentionDeletesOldFinishedJobsOnly() {
        UUID old = enqueue(NewJob.of(null, "test.count"));
        UUID oldFailed = enqueue(NewJob.of(null, "test.count"));
        UUID recentFailed = enqueue(NewJob.of(null, "test.count"));
        UUID waiting = enqueue(NewJob.of(null, "test.count").withRunAt(clock.instant().plusSeconds(3600)));
        jdbc.update("update job set state = 'succeeded', finished_at = now() - interval '8 days' where id = ?", old);
        jdbc.update("update job set state = 'failed', finished_at = now() - interval '31 days' where id = ?", oldFailed);
        jdbc.update("update job set state = 'failed', finished_at = now() - interval '8 days' where id = ?", recentFailed);
        context.getBean(JobsRetention.class).run();
        List<UUID> left = jdbc.queryForList("select id from job", UUID.class);
        assertEquals(Set.of(recentFailed, waiting), Set.copyOf(left));
    }

    @Test
    void theDelayGrowsUpToTheLongestOne() {
        JobsProperties defaults = new JobsProperties(true, 2, Duration.ofSeconds(2), Duration.ofMinutes(2), 5, Duration.ofSeconds(30),
            Duration.ofHours(1), Duration.ofDays(7), Duration.ofDays(30), Duration.ofMinutes(15), Duration.ofHours(1));
        assertEquals(Duration.ofSeconds(30), defaults.delayAfter(1));
        assertEquals(Duration.ofSeconds(60), defaults.delayAfter(2));
        assertEquals(Duration.ofMinutes(4), defaults.delayAfter(4));
        assertEquals(Duration.ofHours(1), defaults.delayAfter(30));
    }

    @Test
    void anErrorOfAHandlerIsRecordedAndTheWorkerThreadGoesOn() {
        // Three threads, three overflowing jobs: before TASK-055 each one ended a thread and nothing ran any more.
        List<UUID> overflowing = new ArrayList<>();
        transaction.executeWithoutResult(status -> {
            for (int i = 0; i < 3; i++) {
                overflowing.add(jobs.enqueue(NewJob.of(null, "test.overflow").withMaxAttempts(1)));
            }
        });
        started();
        await("the overflowing jobs failed", () -> overflowing.stream().allMatch(id -> "failed".equals(state(id))));
        assertEquals("job.failed", row(overflowing.get(0)).get("error_code"));
        UUID next = enqueue(NewJob.of(null, "test.count"));
        await("the next job is done", () -> "succeeded".equals(state(next)));
    }

    @Test
    void anErrorThatLeavesTheVirtualMachineInDoubtEndsTheProcessAfterRecordingTheJob() {
        UUID id = enqueue(NewJob.of(null, "test.oom").withMaxAttempts(1));
        JobExecutor executor = executor();
        List<Error> ended = java.util.Collections.synchronizedList(new ArrayList<>());
        executor.fatal = ended::add;
        executor.start();
        await("the process is ended", () -> !ended.isEmpty());
        assertTrue(ended.get(0) instanceof OutOfMemoryError);
        assertEquals("failed", state(id));
        assertTrue(JobExecutor.fatal(new OutOfMemoryError()));
        assertFalse(JobExecutor.fatal(new StackOverflowError()));
        assertFalse(JobExecutor.fatal(new NoClassDefFoundError()));
    }

    @Test
    void aStopDuringTheLastAttemptGivesTheJobBackWithoutUsingTheAttempt() {
        UUID id = enqueue(NewJob.of(null, "test.resume").withMaxAttempts(1));
        JobExecutor executor = started();
        await("the job runs", () -> RUNS.containsKey(id));
        stop(executor);
        Map<String, Object> row = row(id);
        assertEquals("queued", row.get("state"));
        assertEquals(0, row.get("attempts"));
        assertEquals("job.interrupted", row.get("error_code"));
        assertNotNull(row.get("error_reference"));

        // After the restart it runs once more, knows that a run began before and ends.
        started();
        await("the job is done", () -> "succeeded".equals(state(id)));
        assertEquals(List.of("1/false", "1/true"), List.copyOf(SEEN));
        assertEquals(1, row(id).get("attempts"));
    }

    @Test
    void aFatalErrorEndsTheProcessEvenWhenItsOutcomeCannotBeWritten() {
        // TASK-056, R1: before, the failed write swallowed the error and the process went on.
        JobRepository failing = mock(JobRepository.class, AdditionalAnswers.delegatesTo(repository));
        doThrow(new DataAccessResourceFailureException("database gone")).when(failing).fail(any(), any(), any(), any(), any(), any());
        UUID id = enqueue(NewJob.of(null, "test.oom").withMaxAttempts(1));
        JobExecutor executor = executor(failing);
        List<Error> ended = java.util.Collections.synchronizedList(new ArrayList<>());
        executor.fatal = ended::add;
        executor.start();
        await("the process is ended", () -> !ended.isEmpty());
        assertTrue(ended.get(0) instanceof OutOfMemoryError);
        verify(failing, times(3)).fail(eq(id), any(), any(), any(), any(), any());
        // The outcome is missing; the lease decides later.
        assertEquals("running", state(id));
    }

    @Test
    void aPermanentFailureReportedAtTheStopKeepsItsCodeAndEndsTheJob() {
        // TASK-056, R2: before, the stop turned it into job.interrupted and the job waited again.
        UUID id = enqueue(NewJob.of(null, "test.refuse").withMaxAttempts(1));
        JobExecutor executor = started();
        await("the job runs", () -> RUNS.containsKey(id));
        stop(executor);
        Map<String, Object> row = row(id);
        assertEquals("failed", row.get("state"));
        assertEquals(1, row.get("attempts"));
        assertEquals("test.refused_on_stop", row.get("error_code"));
    }

    @Test
    void anAttemptThatEndsAtTheStopInterruptOnlyAfterTheStopReturnedIsGivenBackWithoutCounting() {
        // TASK-056, R3: before, the stop forgot its interrupt after waiting and the last attempt failed with job.failed.
        UUID id = enqueue(NewJob.of(null, "test.cleanup").withMaxAttempts(1));
        JobExecutor executor = started();
        await("the job runs", () -> RUNS.containsKey(id));
        stop(executor);
        assertEquals("running", state(id));
        cleanUp.countDown();
        await("the job is given back", () -> "queued".equals(state(id)));
        Map<String, Object> row = row(id);
        assertEquals(0, row.get("attempts"));
        assertEquals("job.interrupted", row.get("error_code"));
    }

    @Test
    void aJobPutInTheQueueAgainByTheAdministrationKnowsThatItRanBefore() {
        UUID id = enqueue(NewJob.of(null, "test.resume").withPayload(Map.of("first", "fail")).withMaxAttempts(1));
        started();
        await("the first run failed", () -> "failed".equals(state(id)));
        // What JobAdministration.retry does in its transaction.
        transaction.executeWithoutResult(status -> repository.requeue(id, clock.instant()));
        await("the retried job is done", () -> "succeeded".equals(state(id)));
        assertEquals(List.of("1/false", "1/true"), List.copyOf(SEEN));
        assertEquals(1, row(id).get("attempts"));
    }

    @Test
    void theFirstRunOfAJobIsNotRepeatedAndALaterAttemptIs() {
        UUID id = enqueue(NewJob.of(null, "test.fail").withMaxAttempts(2));
        Instant now = clock.instant();
        JobRepository.Claimed first = transaction.execute(status -> repository.claim("probe", now, now.plusSeconds(60))).orElseThrow();
        assertEquals(1, first.attempt());
        assertFalse(first.repeated());
        transaction.executeWithoutResult(status -> repository.fail(id, first.lease(), now, now, "job.failed", UUID.randomUUID()));
        JobRepository.Claimed second = transaction.execute(status -> repository.claim("probe", now, now.plusSeconds(60))).orElseThrow();
        assertEquals(2, second.attempt());
        assertTrue(second.repeated());
    }

    /** The lease of the running attempt expires and the maintenance gives the job back, while that attempt goes on. */
    private void loseLease(UUID id) {
        transaction.executeWithoutResult(status -> {
            jdbc.update("update job set lease_until = now() - interval '1 minute' where id = ?", id);
            repository.releaseExpired(clock.instant());
        });
    }

    private String lease(UUID id) {
        return jdbc.queryForObject("select lease_owner from job where id = ?", String.class, id);
    }

    @Test
    void anOldAttemptThatSucceedsLateDoesNotEndTheNewerAttemptOfTheSameExecutor() {
        // TASK-065, F1: before, the old success marked the job done while the newer attempt still ran, and its failure was lost.
        UUID id = enqueue(NewJob.of(null, "test.gate").withMaxAttempts(3));
        JobExecutor executor = started();
        await(GATES.get(0).entered());
        String first = lease(id);
        loseLease(id);
        await(GATES.get(1).entered());
        String second = lease(id);

        GATES.get(0).end("ok");
        await("the old attempt ended", () -> !executor.runs(first));
        assertEquals("running", state(id));
        assertEquals(second, lease(id));
        // The newer attempt keeps its renewal after the old one ended: it outlives its lease of three seconds.
        sleep(Duration.ofSeconds(4));
        assertEquals("running", state(id));
        assertEquals(second, lease(id));
        assertEquals(2, row(id).get("attempts"));

        GATES.get(1).end("permanent");
        await("the newer attempt failed", () -> "failed".equals(state(id)));
        assertEquals("test.gate_refused", row(id).get("error_code"));
        assertEquals(2, GATE_RUNS.get());
        // Each reservation had its own lease of the same executor.
        assertTrue(first.startsWith(executor.owner() + "#") && second.startsWith(executor.owner() + "#"));
        assertFalse(second.equals(first));
    }

    @Test
    void anOldAttemptThatFailsLateDoesNotResetTheNewerAttemptOfTheSameExecutor() {
        UUID id = enqueue(NewJob.of(null, "test.gate").withMaxAttempts(3));
        JobExecutor executor = started();
        await(GATES.get(0).entered());
        String first = lease(id);
        loseLease(id);
        await(GATES.get(1).entered());
        String second = lease(id);

        GATES.get(0).end("fail");
        await("the old attempt ended", () -> !executor.runs(first));
        assertEquals("running", state(id));
        assertEquals(second, lease(id));
        assertEquals("job.lease_expired", row(id).get("error_code"));

        GATES.get(1).end("ok");
        await("the newer attempt is done", () -> "succeeded".equals(state(id)));
        assertEquals(2, row(id).get("attempts"));
        assertNull(row(id).get("error_code"));
        assertEquals(2, GATE_RUNS.get());
    }

    @Test
    void anOldAttemptDoesNotEndTheRunThatTheAdministrationStartedAgain() {
        // The last attempt loses its lease and fails; the administration queues the job again, which starts at attempt 1 once more.
        UUID id = enqueue(NewJob.of(null, "test.gate").withMaxAttempts(1));
        JobExecutor executor = started();
        await(GATES.get(0).entered());
        String first = lease(id);
        loseLease(id);
        assertEquals("failed", state(id));
        transaction.executeWithoutResult(status -> repository.requeue(id, clock.instant()));
        await(GATES.get(1).entered());
        String retried = lease(id);
        assertEquals(1, row(id).get("attempts"));

        GATES.get(0).end("permanent");
        await("the old attempt ended", () -> !executor.runs(first));
        assertEquals("running", state(id));
        assertEquals(retried, lease(id));

        GATES.get(1).end("ok");
        await("the retried run is done", () -> "succeeded".equals(state(id)));
        assertNull(row(id).get("error_code"));
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
