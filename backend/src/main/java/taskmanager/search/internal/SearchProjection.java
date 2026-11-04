package taskmanager.search.internal;

import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.projects.ProjectAccess;
import taskmanager.projects.ProjectChanged;
import taskmanager.tags.ProjectTagChanged;
import taskmanager.tags.TagAccess;
import taskmanager.tags.TagChanged;
import taskmanager.tags.TagMerged;
import taskmanager.tags.TicketTagChanged;
import taskmanager.tickets.TicketAccess;
import taskmanager.tickets.TicketChanged;

/**
 * Keeps the projection of the search module (TASK-029, DEC-069). The listeners run synchronously inside the transaction that
 * published the event, after the locks of the owning module, so the projection commits or rolls back with the change and a
 * search started after the commit sees it. The projection holds no rights; {@link SearchService} checks them at every search.
 */
@Service
public class SearchProjection {
    private final SearchRepository search;
    private final ProjectAccess projects;
    private final TicketAccess tickets;
    private final TagAccess tags;

    SearchProjection(SearchRepository search, ProjectAccess projects, TicketAccess tickets, TagAccess tags) {
        this.search = search;
        this.projects = projects;
        this.tickets = tickets;
        this.tags = tags;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(ProjectChanged project) {
        search.enterWrite();
        search.upsertProject(project);
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(TicketChanged ticket) {
        search.enterWrite();
        search.upsertTicket(ticket);
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(TagChanged tag) {
        search.enterWrite();
        search.upsertTag(tag);
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(TagMerged merged) {
        search.enterWrite();
        search.mergeTag(merged.sourceId(), merged.targetId());
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(TicketTagChanged assignment) {
        search.enterWrite();
        search.ticketTag(assignment.ticketId(), assignment.tagId(), assignment.assigned());
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(ProjectTagChanged assignment) {
        search.enterWrite();
        search.projectTag(assignment.projectId(), assignment.tagId(), assignment.assigned());
    }

    /**
     * Builds the projection of the company again from the owning modules, in one transaction. Writes of projects, tickets
     * and tags wait meanwhile; searches keep reading the old projection until the commit.
     */
    @Transactional
    public void rebuild(UUID companyId) {
        search.lockForRebuild();
        fill(companyId);
    }

    /**
     * Builds the projection once for a company that has none yet, such as a database that held projects, tickets or tags
     * before the search module existed. Returns whether it built.
     */
    @Transactional
    public boolean buildIfMissing(UUID companyId) {
        search.lockForRebuild();
        if (search.built(companyId)) {
            return false;
        }
        fill(companyId);
        return true;
    }

    private void fill(UUID companyId) {
        search.deleteCompany(companyId);
        projects.searchSources(companyId).forEach(search::upsertProject);
        tags.tags(companyId).forEach(search::upsertTag);
        tickets.searchSources(companyId).forEach(search::upsertTicket);
        tags.ticketAssignments(companyId).forEach(assignment -> search.insertTicketTag(assignment.ticketId(), assignment.tagId()));
        tags.projectAssignments(companyId).forEach(assignment -> search.insertProjectTag(assignment.projectId(), assignment.tagId()));
        search.refreshCompany(companyId);
        search.markBuilt(companyId);
    }
}
