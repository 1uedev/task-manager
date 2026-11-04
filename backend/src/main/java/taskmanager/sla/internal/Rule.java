package taskmanager.sla.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * An SLA rule of a project (TASK-068): for every ticket with a customer ({@code customerId} {@code null}), for one customer,
 * or for one customer and ticket type; with its calendar and the budgets per priority in minutes. A removed rule is
 * inactive and stays for the cycles that use it. {@code escalations} are the stages per clock (TASK-070), part of each version.
 */
public record Rule(UUID id, UUID companyId, String name, UUID projectId, UUID customerId, String ticketType, UUID calendarId,
        Budgets budgets, List<Stage> escalations, boolean active, long version, Instant createdAt, UUID createdBy, Instant updatedAt, UUID updatedBy) {

    public static final List<String> PRIORITIES = List.of("low", "normal", "high", "critical");
    public static final String DEFAULT_PRIORITY = "normal";
    /** One year in minutes; a larger budget is surely a mistake. */
    static final int MAX_MINUTES = 525_600;
    static final int MAX_STAGES = 10;
    /** Ten times the budget; a stage beyond that is surely a mistake. */
    static final int MAX_PERCENT = 1000;

    public Rule {
        escalations = escalations == null ? List.of() : List.copyOf(escalations);
    }

    /**
     * An escalation stage (TASK-070, rule 1): when the clock {@code clock} has used {@code percent} of its budget, the group
     * {@code groupId} sees the ticket in its list. The position of a stage in the list of its rule version identifies it.
     */
    public record Stage(String clock, Integer percent, UUID groupId) {
        /** The open time after which the stage is due, rounded up to whole seconds. */
        long thresholdSeconds(long budgetSeconds) {
            return (budgetSeconds * percent + 99) / 100;
        }

        /** Why the stages cannot be stored; {@code groups} are the groups of the company. */
        public static List<String> violations(List<Stage> stages, Set<UUID> groups) {
            List<String> violations = new ArrayList<>();
            if (stages == null) {
                return violations;
            }
            Set<String> seen = new HashSet<>();
            for (String clock : List.of(SlaClock.FIRST_RESPONSE, SlaClock.RESOLUTION)) {
                if (stages.stream().filter(stage -> stage != null && clock.equals(stage.clock())).count() > MAX_STAGES) {
                    violations.add("escalations.too_many");
                }
            }
            for (Stage stage : stages) {
                // List.contains throws on null (TASK-071, F3), so a missing clock is checked first.
                if (stage == null || stage.clock() == null || !List.of(SlaClock.FIRST_RESPONSE, SlaClock.RESOLUTION).contains(stage.clock())
                        || stage.percent() == null
                        || stage.percent() < 1 || stage.percent() > MAX_PERCENT) {
                    add(violations, "escalations.invalid");
                } else if (stage.groupId() == null || !groups.contains(stage.groupId())) {
                    add(violations, "escalations.group_unknown");
                } else if (!seen.add(stage.clock() + "/" + stage.percent() + "/" + stage.groupId())) {
                    add(violations, "escalations.duplicate");
                }
            }
            return violations;
        }

        private static void add(List<String> violations, String code) {
            if (!violations.contains(code)) {
                violations.add(code);
            }
        }
    }

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
