package taskmanager.requirements;

import java.util.UUID;
import java.util.function.Consumer;

/** What the search module reads of requirements to build its projection again (TASK-103, DEC-133). */
public interface RequirementSources {
    /** Every requirement of the company as {@link RequirementChanged} publishes it, one after the other. */
    void searchSources(UUID companyId, Consumer<RequirementChanged> sink);
}
