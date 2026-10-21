package taskmanager.tags;

import java.util.List;
import java.util.UUID;

/**
 * What other modules (search) read of the tags to rebuild what they derive from them (TASK-029). They never read the tables
 * of this module. Each call needs a transaction and reads the committed state of the company.
 */
public interface TagAccess {
    /** Every tag of the company, archived ones included. */
    List<TagChanged> tags(UUID companyId);

    /** Every assignment of a tag to a ticket of the company. */
    List<TicketTagChanged> ticketAssignments(UUID companyId);

    /** Every assignment of a tag to a project of the company. */
    List<ProjectTagChanged> projectAssignments(UUID companyId);
}
