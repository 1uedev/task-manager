package taskmanager.jobs.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.AccessContext;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.AdministrationRights;
import taskmanager.platform.ApiProblemException;
import taskmanager.platform.CompanyContext;

/**
 * What the operation sees of the jobs and may do with them (TASK-054, REQ-OPS-006). Every method needs the right
 * {@code jobs.manage}, checked first in its transaction. Only jobs of the company of the installation and tasks of the
 * installation are visible; another identifier answers 404 like an unknown one.
 */
@Service
public class JobAdministration {
    public static final Set<String> STATES = Set.of("queued", "running", "succeeded", "failed", "cancelled");

    /** The state of the queue: counts, the oldest due job and whether anybody works it off. */
    public record Overview(JobRepository.Summary summary, Instant now, boolean backlog) {
    }

    private final JobRepository jobs;
    private final JobsProperties properties;
    private final CompanyContext company;
    private final Clock clock;
    private final ObjectProvider<AdministrationRights> rights;

    JobAdministration(JobRepository jobs, JobsProperties properties, CompanyContext company, Clock clock,
            ObjectProvider<AdministrationRights> rights) {
        this.jobs = jobs;
        this.properties = properties;
        this.company = company;
        this.clock = clock;
        this.rights = rights;
    }

    @Transactional
    public Overview overview() {
        UUID companyId = requireRight();
        Instant now = clock.instant();
        // An executor that was not seen for three polls or a third of a lease is no longer counted.
        Instant since = now.minus(properties.pollInterval().multipliedBy(3).plus(properties.lease().dividedBy(3)));
        JobRepository.Summary summary = jobs.summary(companyId, now, since);
        boolean stale = summary.oldestDue() != null && summary.oldestDue().isBefore(now.minus(properties.backlogAfter()));
        boolean unattended = summary.activeWorkers() == 0 && summary.states().get("queued") > 0;
        return new Overview(summary, now, stale || unattended);
    }

    @Transactional
    public List<Job> list(String state, Long before, int limit) {
        UUID companyId = requireRight();
        if (state != null && !STATES.contains(state)) {
            throw ApiProblemException.of(400, "job.state_invalid");
        }
        return jobs.list(companyId, state, before, limit);
    }

    /** Puts a failed or cancelled job back in the queue with all its attempts, to run now. */
    @Transactional
    public Job retry(AccessContext actor, UUID id) {
        UUID companyId = requireRight();
        Job job = jobs.lock(companyId, id).orElseThrow(() -> ApiProblemException.of(404, "job.not_found"));
        if (!job.state().equals("failed") && !job.state().equals("cancelled")) {
            throw ApiProblemException.of(409, "job.state_conflict");
        }
        Instant now = clock.instant();
        jobs.requeue(id, now);
        jobs.audit(job.companyId(), id, "job.retried", actor.accountId(), actor.username(), now);
        return jobs.find(id).orElseThrow();
    }

    /** Cancels a job that waits; a running job cannot be cancelled. */
    @Transactional
    public Job cancel(AccessContext actor, UUID id) {
        UUID companyId = requireRight();
        Job job = jobs.lock(companyId, id).orElseThrow(() -> ApiProblemException.of(404, "job.not_found"));
        if (!job.state().equals("queued")) {
            throw ApiProblemException.of(409, "job.state_conflict");
        }
        Instant now = clock.instant();
        jobs.cancel(id, now);
        jobs.audit(job.companyId(), id, "job.cancelled", actor.accountId(), actor.username(), now);
        return jobs.find(id).orElseThrow();
    }

    /** The authoritative check before anything else of the transaction; returns the company. */
    private UUID requireRight() {
        var port = AdministrationRights.unique(rights);
        if (port == null) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        port.require(AdministrationRight.JOBS_MANAGE);
        return company.find().orElseThrow(() -> ApiProblemException.of(403, "auth.forbidden"));
    }
}
