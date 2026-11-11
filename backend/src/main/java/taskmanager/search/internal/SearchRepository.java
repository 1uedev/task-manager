package taskmanager.search.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import taskmanager.projects.ProjectChanged;
import taskmanager.tags.TagChanged;
import taskmanager.tickets.TicketChanged;
import taskmanager.requirements.RequirementChanged;
import taskmanager.wiki.WikiPageChanged;

/**
 * The projection tables of the search module. Nobody else reads them. Rows that a write changes are locked before the
 * names of their tags are written again, so the statement that writes them reads every assignment committed before.
 *
 * <p>Searchable text is stored a second time as terms: the words of {@link SearchText#terms}, which also splits the search
 * text, so a search and the index agree on every word (finding F1 of the review of TASK-029).
 *
 * <p>Lock order: every write first takes {@code search_state} in {@code ROW SHARE} mode ({@link #enterWrite}), a rebuild
 * takes it first in {@code EXCLUSIVE} mode. Writes do not wait for each other there, but a rebuild waits for every write
 * that already entered, and a later write waits before it touches any other projection table, so a write and a rebuild
 * never hold tables the other one needs (finding F2).
 */
@Repository
class SearchRepository {
    private static final String TAG_NAMES = "coalesce((select string_agg(g.terms, ' ' order by g.name, g.tag_id) from %s a "
        + "join search_tag g on g.tag_id = a.tag_id where a.%s = s.%s), '')";

    private final JdbcClient jdbc;

    SearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The first statement of every write of the projection; see the lock order above. */
    void enterWrite() {
        jdbc.sql("lock table search_state in row share mode").update();
    }

    void upsertProject(ProjectChanged project) {
        String terms = SearchText.terms(project.key() + " " + project.name() + " " + (project.description() == null ? "" : project.description()));
        jdbc.sql("""
                insert into search_project (project_id, company_id, key, name, description, terms) values (?, ?, ?, ?, ?, ?)
                on conflict (project_id) do update set key = excluded.key, name = excluded.name, description = excluded.description,
                    terms = excluded.terms
                """).params(project.projectId(), project.companyId(), project.key(), project.name(), project.description(), terms)
            .update();
    }

    void upsertTicket(TicketChanged ticket) {
        jdbc.sql("""
                insert into search_ticket (ticket_id, company_id, project_id, number, type, status, assignee, title, description,
                    title_terms, description_terms)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (ticket_id) do update set type = excluded.type, status = excluded.status, assignee = excluded.assignee,
                    title = excluded.title, description = excluded.description, title_terms = excluded.title_terms,
                    description_terms = excluded.description_terms
                """).params(ticket.ticketId(), ticket.companyId(), ticket.projectId(), ticket.number(), ticket.type(), ticket.status(),
                ticket.assignee(), ticket.title(), ticket.description(), SearchText.terms(ticket.title()),
                SearchText.terms(ticket.description())).update();
        // The custom values are written again as a whole: a field that lost its value must lose its row (TASK-044).
        jdbc.sql("delete from search_ticket_custom where ticket_id = ?").param(ticket.ticketId()).update();
        for (TicketChanged.CustomValue value : ticket.custom()) {
            boolean text = "text".equals(value.type()) || "longtext".equals(value.type());
            jdbc.sql("insert into search_ticket_custom (ticket_id, field_key, value, terms) values (?, ?, ?, ?)")
                .params(ticket.ticketId(), value.key(), value.value(), text ? SearchText.terms(value.value()) : "").update();
        }
    }

    /**
     * Writes a wiki page with the published revision and the draft of each language version as rows of their own (TASK-102).
     * The page row is locked first, so two changes of one page write one after the other.
     */
    void upsertWikiPage(WikiPageChanged page) {
        jdbc.sql("""
                insert into search_wiki_page (page_id, company_id, project_id, source_language, archived) values (?, ?, ?, ?, ?)
                on conflict (page_id) do update set project_id = excluded.project_id, source_language = excluded.source_language,
                    archived = excluded.archived
                """).params(page.pageId(), page.companyId(), page.projectId(), page.sourceLanguage(), page.archived()).update();
        jdbc.sql("delete from search_wiki_text where page_id = ?").param(page.pageId()).update();
        for (WikiPageChanged.Version version : page.versions()) {
            if (version.publishedRevision() != null) {
                insertWikiText(page.pageId(), version.language(), "published", version.publishedRevision(), version.publishedTitle(),
                    version.publishedText(), version.publishedAt());
            }
            insertWikiText(page.pageId(), version.language(), "draft", version.draftRevision(), version.draftTitle(), version.draftText(), null);
        }
    }

    private void insertWikiText(UUID pageId, String language, String stage, int revision, String title, String body, java.time.Instant publishedAt) {
        String text = body == null ? "" : body;
        jdbc.sql("""
                insert into search_wiki_text (page_id, language, stage, revision, title, body, published_at, title_terms, body_terms)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(pageId, language, stage, revision, title, text, publishedAt == null ? null : java.sql.Timestamp.from(publishedAt),
                SearchText.terms(title), SearchText.indexTerms(text)).update();
    }

    /**
     * Writes a requirement with its current revision and its newest approved revision as stages of their own (TASK-103). The
     * requirement row is locked first, so two changes of one requirement write one after the other.
     */
    void upsertRequirement(RequirementChanged requirement) {
        jdbc.sql("""
                insert into search_requirement (requirement_id, company_id, project_id, number, status, owner_id, current_revision,
                    approved_revision)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (requirement_id) do update set project_id = excluded.project_id, status = excluded.status,
                    owner_id = excluded.owner_id, current_revision = excluded.current_revision, approved_revision = excluded.approved_revision
                """).params(requirement.requirementId(), requirement.companyId(), requirement.projectId(), requirement.number(),
                requirement.status(), requirement.ownerId(), requirement.currentRevision(), requirement.approvedRevision()).update();
        UUID id = requirement.requirementId();
        jdbc.sql("delete from search_requirement_custom where requirement_id = ?").param(id).update();
        jdbc.sql("delete from search_requirement_criterion where requirement_id = ?").param(id).update();
        jdbc.sql("delete from search_requirement_text where requirement_id = ?").param(id).update();
        for (RequirementChanged.Stage stage : requirement.stages()) {
            String description = stage.description() == null ? "" : stage.description();
            StringBuilder criteria = new StringBuilder();
            stage.criteria().forEach(criterion -> criteria.append(criteria.isEmpty() ? "" : "\n").append(criterion.text()));
            jdbc.sql("""
                    insert into search_requirement_text (requirement_id, stage, revision, title, description, priority, title_terms,
                        description_terms, criteria_terms)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """).params(id, stage.stage(), stage.revision(), stage.title(), description, stage.priority(),
                    SearchText.terms(stage.title()), SearchText.indexTerms(description), SearchText.indexTerms(criteria.toString())).update();
            int position = 0;
            for (RequirementChanged.Criterion criterion : stage.criteria()) {
                jdbc.sql("insert into search_requirement_criterion (requirement_id, stage, criterion_id, position, text) values (?, ?, ?, ?, ?)")
                    .params(id, stage.stage(), criterion.id(), position++, criterion.text()).update();
            }
            for (RequirementChanged.CustomValue value : stage.custom()) {
                boolean text = "text".equals(value.type()) || "longtext".equals(value.type());
                jdbc.sql("insert into search_requirement_custom (requirement_id, stage, field_key, value, terms) values (?, ?, ?, ?, ?)")
                    .params(id, stage.stage(), value.key(), value.value(), text ? SearchText.indexTerms(value.value()) : "").update();
            }
        }
    }

    /** Stores the tag and writes the tag names of every ticket and project that carries it again. */
    void upsertTag(TagChanged tag) {
        jdbc.sql("""
                insert into search_tag (tag_id, company_id, project_id, name, terms) values (?, ?, ?, ?, ?)
                on conflict (tag_id) do update set name = excluded.name, terms = excluded.terms
                """).params(tag.tagId(), tag.companyId(), tag.projectId(), tag.name(), SearchText.terms(tag.name())).update();
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

    /**
     * The tag {@code source} was merged into {@code target} (TASK-072): its assignments move to the target, duplicates go, the
     * source leaves the projection, and the tag names of every ticket and project that carried the source are written again.
     */
    void mergeTag(UUID source, UUID target) {
        List<UUID> tickets = jdbc.sql("select ticket_id from search_ticket_tag where tag_id = ?").param(source).query(UUID.class).list();
        List<UUID> projects = jdbc.sql("select project_id from search_project_tag where tag_id = ?").param(source).query(UUID.class).list();
        jdbc.sql("insert into search_ticket_tag (ticket_id, tag_id) select ticket_id, ? from search_ticket_tag where tag_id = ? on conflict do nothing")
            .params(target, source).update();
        jdbc.sql("delete from search_ticket_tag where tag_id = ?").param(source).update();
        jdbc.sql("insert into search_project_tag (project_id, tag_id) select project_id, ? from search_project_tag where tag_id = ? on conflict do nothing")
            .params(target, source).update();
        jdbc.sql("delete from search_project_tag where tag_id = ?").param(source).update();
        jdbc.sql("delete from search_tag where tag_id = ?").param(source).update();
        refreshTickets(tickets);
        refreshProjects(projects);
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
     * Locks the projection against every write until the commit, {@code search_state} first: a write that already entered
     * finishes before the rebuild starts, one that enters later waits for its end.
     */
    void lockForRebuild() {
        jdbc.sql("lock table search_state, search_project, search_ticket, search_ticket_custom, search_tag, search_ticket_tag, "
            + "search_project_tag, search_wiki_page, search_wiki_text, search_requirement, search_requirement_text, search_requirement_criterion, "
            + "search_requirement_custom in exclusive mode").update();
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
        jdbc.sql("delete from search_ticket_custom a using search_ticket s where s.ticket_id = a.ticket_id and s.company_id = ?").param(companyId).update();
        jdbc.sql("delete from search_ticket where company_id = ?").param(companyId).update();
        jdbc.sql("delete from search_wiki_text a using search_wiki_page s where s.page_id = a.page_id and s.company_id = ?").param(companyId).update();
        jdbc.sql("delete from search_wiki_page where company_id = ?").param(companyId).update();
        for (String table : List.of("search_requirement_custom", "search_requirement_criterion", "search_requirement_text")) {
            jdbc.sql("delete from " + table + " a using search_requirement s where s.requirement_id = a.requirement_id and s.company_id = ?")
                .param(companyId).update();
        }
        jdbc.sql("delete from search_requirement where company_id = ?").param(companyId).update();
        jdbc.sql("delete from search_project where company_id = ?").param(companyId).update();
        jdbc.sql("delete from search_tag where company_id = ?").param(companyId).update();
    }

    /**
     * Fills the tag terms of every ticket and project of the company in one statement each. Only a rebuild calls this,
     * with all projection tables already locked; a list of identifiers would exceed the 65,535 parameters PostgreSQL
     * accepts in one statement for a stock such as the 90,000 tickets of the reference profile.
     */
    void refreshCompany(UUID companyId) {
        jdbc.sql("update search_ticket s set tags_text = " + TAG_NAMES.formatted("search_ticket_tag", "ticket_id", "ticket_id")
            + " where s.company_id = ?").param(companyId).update();
        jdbc.sql("update search_project s set tags_text = " + TAG_NAMES.formatted("search_project_tag", "project_id", "project_id")
            + " where s.company_id = ?").param(companyId).update();
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
