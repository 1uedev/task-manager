package taskmanager.ai.internal;

import java.time.LocalDate;

/** Count of executions and reported tokens per day (UTC) and use case (TASK-125, rule 11). */
public record Usage(LocalDate day, String useCase, long runs, long succeeded, long inputTokens, long outputTokens) {
}
