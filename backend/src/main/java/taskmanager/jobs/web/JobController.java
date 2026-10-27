package taskmanager.jobs.web;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.identity.AccessContext;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.jobs.internal.Job;
import taskmanager.jobs.internal.JobAdministration;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.AdministrationRights;
import taskmanager.platform.ApiProblemException;

/**
 * The background jobs for the operation (TASK-054, REQ-OPS-006). Every endpoint needs the right {@code jobs.manage}; the
 * service checks it in its transaction, the endpoint refuses a request that clearly lacks it first. A job is shown without
 * its payload, and a failure only with its code and the reference of the log entry.
 */
@RestController
@RequestMapping("/api/v1/jobs")
class JobController {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final JobAdministration administration;
    private final AccessContexts contexts;
    private final ObjectProvider<AdministrationRights> rights;

    JobController(JobAdministration administration, AccessContexts contexts, ObjectProvider<AdministrationRights> rights) {
        this.administration = administration;
        this.contexts = contexts;
        this.rights = rights;
    }

    record JobBody(UUID id, long position, String kind, String state, int attempts, int maxAttempts, Instant runAt, Instant createdAt,
            Instant startedAt, Instant finishedAt, String errorCode, UUID errorReference, boolean installation) {
        static JobBody of(Job job) {
            return new JobBody(job.id(), job.seq(), job.kind(), job.state(), job.attempts(), job.maxAttempts(), job.runAt(), job.createdAt(),
                job.startedAt(), job.finishedAt(), job.errorCode(), job.errorReference(), job.companyId() == null);
        }
    }

    record SummaryBody(Map<String, Long> states, Instant oldestDueAt, Long oldestDueSeconds, long activeWorkers, boolean backlog,
            Instant checkedAt) {
    }

    @GetMapping("/summary")
    SummaryBody summary() {
        administrator();
        JobAdministration.Overview overview = administration.overview();
        Instant oldest = overview.summary().oldestDue();
        Long age = oldest == null ? null : Math.max(0, Duration.between(oldest, overview.now()).toSeconds());
        return new SummaryBody(overview.summary().states(), oldest, age, overview.summary().activeWorkers(), overview.backlog(), overview.now());
    }

    /** Newest first; the next page starts below the smallest position of the last. */
    @GetMapping
    List<JobBody> list(@RequestParam(required = false) String state, @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer limit) {
        administrator();
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw ApiProblemException.of(400, "job.limit_invalid");
        }
        return administration.list(state, before, size).stream().map(JobBody::of).toList();
    }

    @PostMapping("/{id}/retry")
    JobBody retry(@PathVariable UUID id) {
        return JobBody.of(administration.retry(administrator(), id));
    }

    @PostMapping("/{id}/cancel")
    JobBody cancel(@PathVariable UUID id) {
        return JobBody.of(administration.cancel(administrator(), id));
    }

    /** A snapshot of the right: it spares an unauthorized request the work, the service decides. */
    private AccessContext administrator() {
        AccessContext context = contexts.require();
        if (context.level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        var port = AdministrationRights.unique(rights);
        if (port == null || !port.held().contains(AdministrationRight.JOBS_MANAGE)) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        return context;
    }
}
