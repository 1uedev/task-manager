package taskmanager.search.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import taskmanager.projects.ProjectChanged;
import taskmanager.tags.TagChanged;
import taskmanager.tickets.TicketChanged;

/**
 * The projection tables of the search module. Nobody else reads them. Rows that a write changes are locked before the
 * names of their tags are written again, so the statement that writes them reads every assignment committed before.
 */
@Repository
class SearchRepository {
    private static final String TAG_NAMES = "coalesce((select string_agg(g.name, ' ' order by g.name) from %s a "
        + "join search_tag g on g.tag_id = a.tag_id where a.%s = s.%s), '')";

    private final JdbcClient jdbc;

    SearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void upsertProject(ProjectChanged project) {
        jdbc.sql("""
                insert into search_project (project_id, company_id, key, name, description) values (?, ?, ?, ?, ?)
                on conflict (project_id) do update set key = excluded.key, name = excluded.name, description = excluded.description
                """).params(project.projectId(), project.companyId(), project.key(), project.name(), project.description()).update();
    }

    void upsertTicket(TicketChanged ticket) {
        jdbc.sql("""
                insert into search_ticket (ticket_id, company_id, project_id, number, type, status, assignee, title, description)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (ticket_id) do update set type = excluded.type, status = excluded.status, assignee = excluded.assignee,
                    title = excluded.title, description = excluded.description
                """).params(ticket.ticketId(), ticket.companyId(), ticket.projectId(), ticket.number(), ticket.type(), ticket.status(),
                ticket.assignee(), ticket.title(), ticket.description()).update();
    }

    /** Stores the tag and writes the tag names of every ticket and project that carries it again. */
    void upsertTag(TagChanged tag) {
        jdbc.sql("""
                insert into search_tag (tag_id, company_id, project_id, name) values (?, ?, ?, ?)
                on conflict (tag_id) do update set name = excluded.name
                """).params(tag.tagId(), tag.companyId(), tag.projectId(), tag.name()).update();
        refreshTickets(jdbc.sql("select ticket_id from search_ticket_tag where tag_id = ?").param(tag.tagId()).query(UUID.class).list());
        refreshProjects(jdbc.sql("select project_id from search_project_tag where tag_id = ?").param(tag.tagId()).query(UUID.class).list());
    }

    /** An assignment without writing the tag names again, for a rebuild that writes them once at its end. */
    void insertTicketTag(UUID ticketId, UUID tagId) {
        jdbc.sql("insert into search_ticket_tag (ticket_id, tag_id) values (?, ?) on conflict do nothing").params(ticketId, tagId).update();
    }

    void insertProjectTag(UUID projectId, UUID tagId) {
        jdbc.sql("insert into search_project_tag (project_id, tag_id) values (?, ?) on conflict do nothing").params(projectId, tagId).update();
    }

    void ticketTag(UUID ticketId, UUID tagId, boolean assigned) {
        if (assigned) {
            jdbc.sql("insert into search_ticket_tag (ticket_id, tag_id) values (?, ?) on conflict do nothing").params(ticketId, tagId).update();
        } else {
            jdbc.sql("delete from search_ticket_tag where ticket_id = ? and tag_id = ?").params(ticketId, tagId).update();
        }
        refreshTickets(List.of(ticketId));
    }

    void projectTag(UUID projectId, UUID tagId, boolean assigned) {
        if (assigned) {
            jdbc.sql("insert into search_project_tag (project_id, tag_id) values (?, ?) on conflict do nothing").params(projectId, tagId).update();
        } else {
            jdbc.sql("delete from search_project_tag where project_id = ? and tag_id = ?").params(projectId, tagId).update();
        }
        refreshProjects(List.of(projectId));
    }

    private void refreshTickets(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        // In the order of the identifiers, so two writes that share tickets wait for each other instead of deadlocking.
        jdbc.sql("select ticket_id from search_ticket where ticket_id in (:ids) order by ticket_id for update").param("ids", ids)
            .query(UUID.class).list();
        jdbc.sql("update search_ticket s set tags_text = " + TAG_NAMES.formatted("search_ticket_tag", "ticket_id", "ticket_id")
            + " where s.ticket_id in (:ids)").param("ids", ids).update();
    }

    private void refreshProjects(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.sql("select project_id from search_project where project_id in (:ids) order by project_id for update").param("ids", ids)
            .query(UUID.class).list();
        jdbc.sql("update search_project s set tags_text = " + TAG_NAMES.formatted("search_project_tag", "project_id", "project_id")
            + " where s.project_id in (:ids)").param("ids", ids).update();
    }

    /**
     * Locks the projection against every write until the commit; a write that already wrote waits for the rebuild to start,
     * one that writes later waits for its end.
     */
    void lockForRebuild() {
        jdbc.sql("lock table search_state, search_project, search_ticket, search_tag, search_ticket_tag, search_project_tag "
            + "in exclusive mode").update();
    }

    boolean built(UUID companyId) {
        return jdbc.sql("select count(*) from search_state where company_id = ?").param(companyId).query(Long.class).single() > 0;
    }

    void markBuilt(UUID companyId) {
        jdbc.sql("insert into search_state (company_id, built_at) values (?, now()) "
                + "on conflict (company_id) do update set built_at = excluded.built_at")
            .param(companyId).update();
    }

    void deleteCompany(UUID companyId) {
        jdbc.sql("delete from search_ticket_tag a using search_ticket s where s.ticket_id = a.ticket_id and s.company_id = ?").param(companyId).update();
        jdbc.sql("delete from search_project_tag a using search_project s where s.project_id = a.project_id and s.company_id = ?").param(companyId).update();
        jdbc.sql("delete from search_ticket where company_id = ?").param(companyId).update();
        jdbc.sql("delete from search_project where company_id = ?").param(companyId).update();
        jdbc.sql("delete from search_tag where company_id = ?").param(companyId).update();
    }

    void refreshCompany(UUID companyId) {
        refreshTickets(jdbc.sql("select ticket_id from search_ticket where company_id = ?").param(companyId).query(UUID.class).list());
        refreshProjects(jdbc.sql("select project_id from search_project where company_id = ?").param(companyId).query(UUID.class).list());
    }

    /** The identifiers of the tags among {@code ids} in the company catalog or in one of the given project catalogs. */
    List<UUID> visibleTags(UUID companyId, Collection<UUID> ids, Collection<UUID> projects, boolean companyCatalogOnly) {
        String catalogs = companyCatalogOnly || projects.isEmpty() ? "project_id is null" : "(project_id is null or project_id in (:projects))";
        var statement = jdbc.sql("select tag_id from search_tag where company_id = :company and tag_id in (:ids) and " + catalogs)
            .param("company", companyId).param("ids", ids);
        if (!companyCatalogOnly && !projects.isEmpty()) {
            statement = statement.param("projects", projects);
        }
        return statement.query(UUID.class).list();
    }
}
