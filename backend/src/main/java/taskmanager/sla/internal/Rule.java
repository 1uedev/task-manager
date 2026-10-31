package taskmanager.sla.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An SLA rule of a project (TASK-068): for every ticket with a customer ({@code customerId} {@code null}), for one customer,
 * or for one customer and ticket type; with its calendar and the budgets per priority in minutes. A removed rule is
 * inactive and stays for the cycles that use it.
 */
public record Rule(UUID id, UUID companyId, String name, UUID projectId, UUID customerId, String ticketType, UUID calendarId,
        Budgets budgets, boolean active, long version, Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy) {

    public static final List<String> PRIORITIES = List.of("low", "normal", "high", "critical");
    public static final String DEFAULT_PRIORITY = "normal";
    /** One year in minutes; a larger budget is surely a mistake. */
    static final int MAX_MINUTES = 525_600;

    /** First response and resolution in minutes of open time. */
    public record Minutes(Integer firstResponse, Integer resolution) {
    }

    public record Budgets(Minutes low, Minutes normal, Minutes high, Minutes critical) {
        public Minutes of(String priority) {
            return switch (priority) {
                case "low" -> low;
                case "high" -> high;
                case "critical" -> critical;
                default -> normal;
            };
        }

        public SlaCycle.Budget seconds(String priority) {
            Minutes minutes = of(priority);
            return new SlaCycle.Budget(minutes.firstResponse() * 60L, minutes.resolution() * 60L);
        }

        /** Why the budgets cannot be stored; every priority needs both values between one minute and one year. */
        public static List<String> violations(Budgets budgets) {
            if (budgets == null) {
                return List.of("budgets.required");
            }
            for (String priority : PRIORITIES) {
                Minutes minutes = budgets.of(priority);
                if (minutes == null || !valid(minutes.firstResponse()) || !valid(minutes.resolution())) {
                    return List.of("budgets.invalid");
                }
            }
            return List.of();
        }

        private static boolean valid(Integer value) {
            return value != null && value >= 1 && value <= MAX_MINUTES;
        }
    }
}
