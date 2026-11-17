package taskmanager.ai.internal;

import java.time.Instant;
import java.util.UUID;

/** The daily limits of the company (TASK-125, rule 11); {@code version} 0 means the defaults apply. */
public record Limits(int personDaily, int companyDaily, long version, Instant updatedAt, UUID updatedBy) {
    static final int PERSON_DEFAULT = 50;
    static final int COMPANY_DEFAULT = 500;

    static Limits defaults() {
        return new Limits(PERSON_DEFAULT, COMPANY_DEFAULT, 0, null, null);
    }
}
