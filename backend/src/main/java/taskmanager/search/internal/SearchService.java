package taskmanager.search.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.Decision;
import taskmanager.permissions.Permissions;
import taskmanager.permissions.ResourceContext;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import taskmanager.projects.ProjectConfiguration;
import taskmanager.tickets.TicketAccess;
import taskmanager.wiki.WikiPages;

/**
 * Searches tickets and projects (TASK-029, DEC-069) in the projection of this module.
 *
 * <p>Rights first: before any match, the service decides with the current rights which projects the caller may search and,
 * per project, which ticket fields they may read. Words match only in readable fields (title, description, tag names), a
 * filter on a field only where that field is readable, and rank, total and page are computed from those rows alone, so a
 * hidden field changes neither whether a ticket is found nor where it stands. Tickets need {@code ticket.read} in their
 * project, which needs the membership. Projects need {@code projects.manage} for the company, or {@code project.read} or
 * {@code project.manage} in the project. The decisions are snapshots ({@code inspect}) like every read path of the tickets
 * and projects modules; the project rows stay locked {@code FOR SHARE} until the end of the search.
 */
@Service
public class SearchService {
    static final String TICKET_READ = "ticket.read";
    static final List<String> PROJECT_READERS = List.of("project.read", "project.manage");
    /** The ticket fields a search may use, in the order of the answer. */
    static final List<String> TICKET_FIELDS = List.of("title", "description", "type", "status", "assignee", TicketAccess.TAGS_FIELD);
    private static final Map<String, String> TEXT_VECTORS = Map.of(
        "title", "s.title_tsv", "description", "s.description_tsv", TicketAccess.TAGS_FIELD, "s.tags_tsv");

    private final JdbcClient jdbc;
    private final SearchRepository search;
    private final ProjectAccess projects;
    private final Permissions permissions;
    private final WikiPages wiki;

    SearchService(JdbcClient jdbc, SearchRepository search, ProjectAccess projects, Permissions permissions, WikiPages wiki) {
        this.jdbc = jdbc;
        this.search = search;
        this.projects = projects;
        this.permissions = permissions;
        this.wiki = wiki;
    }

    /** A required custom value: the ticket holds exactly this canonical text in the custom field with this key (TASK-044). */
    public record CustomFilter(String key, String value) {
    }

    /**
     * How the given tags filter (TASK-073, DEC-103): every one of them, at least one, none of them, or no visible tag at all
     * (then no tag is given). Who may not read the tags of a project finds none of its tickets with a tag filter.
     */
    public enum TagMatch {
        ALL, ANY, NONE, UNTAGGED;

        /** True when the filter asks something of the tags; {@code ALL} without tags asks nothing. */
        boolean active(List<UUID> tags) {
            return this == UNTAGGED || !tags.isEmpty();
        }
    }

    public record TicketQuery(String text, List<UUID> projects, List<String> statuses, List<String> types, List<UUID> assignees,
            List<UUID> tags, TagMatch tagMatch, List<CustomFilter> custom, int limit, int offset) {
        public TicketQuery(String text, List<UUID> projects, List<String> statuses, List<String> types, List<UUID> assignees,
                List<UUID> tags, List<CustomFilter> custom, int limit, int offset) {
            this(text, projects, statuses, types, assignees, tags, TagMatch.ALL, custom, limit, offset);
        }

        public TicketQuery(String text, List<UUID> projects, List<String> statuses, List<String> types, List<UUID> assignees,
                List<UUID> tags, int limit, int offset) {
            this(text, projects, statuses, types, assignees, tags, TagMatch.ALL, List.of(), limit, offset);
        }
    }

    public record ProjectQuery(String text, List<UUID> tags, TagMatch tagMatch, int limit, int offset) {
        public ProjectQuery(String text, List<UUID> tags, int limit, int offset) {
            this(text, tags, TagMatch.ALL, limit, offset);
        }
    }

    public record TagRef(UUID id, String name) {
    }

    /**
     * One ticket with the fields the caller may read; {@code tags} is {@code null} without the read rule for tags.
     * {@code customFields} holds the custom values the caller may read, each in its canonical text form (TASK-044).
     */
    public record TicketHit(UUID id, UUID projectId, String projectKey, long number, Map<String, String> fields,
            Map<String, String> customFields, List<TagRef> tags) {
    }

    public record ProjectHit(UUID id, String key, String name, String description, List<TagRef> tags) {
    }

    public record Page<T>(long total, List<T> items) {
    }

    private record Hit(UUID id, UUID projectId, String key, long number) {
    }

    // ------------------------------------------------------------------------------------------------ tickets

    @Transactional
    public Page<TicketHit> tickets(AccessContext actor, TicketQuery query) {
        internal(actor);
        SearchText text = text(query.text());
        Map<UUID, Set<String>> readable = readableTicketFields(actor);
        Set<UUID> scope = new LinkedHashSet<>(readable.keySet());
        if (!query.projects().isEmpty()) {
            if (!readable.keySet().containsAll(query.projects())) {
                throw invalid("project.unknown");
            }
            scope.retainAll(query.projects());
        }
        checkTags(actor, query.tags(), readable.keySet(), false);

        Map<Set<String>, List<UUID>> groups = new LinkedHashMap<>();
        for (UUID project : scope) {
            groups.computeIfAbsent(readable.get(project), fields -> new ArrayList<>()).add(project);
        }
        List<String> selects = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        groups.forEach((fields, ids) -> ticketSelect(actor, query, text, fields, ids, selects, params));
        if (selects.isEmpty()) {
            return new Page<>(0, List.of());
        }
        String union = String.join(" union all ", selects);
        long total = jdbc.sql("select count(*) from (" + union + ") h").params(params).query(Long.class).single();
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(query.limit());
        pageParams.add(query.offset());
        List<Hit> hits = jdbc.sql("select id, project_id, project_key, number from (" + union + ") h "
                + "order by rank desc, project_key, number desc limit ? offset ?")
            .params(pageParams)
            .query((rs, row) -> new Hit(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getString("project_key"), rs.getLong("number")))
            .list();
        return new Page<>(total, ticketHits(hits, readable));
    }

    /** One select of the union for the projects that share the same readable fields; none when they cannot match. */
    private void ticketSelect(AccessContext actor, TicketQuery query, SearchText text, Set<String> fields, List<UUID> ids,
            List<String> selects, List<Object> params) {
        // A filter on a field the caller may not read matches nothing in these projects.
        if ((!query.statuses().isEmpty() && !fields.contains("status")) || (!query.types().isEmpty() && !fields.contains("type"))
                || (!query.assignees().isEmpty() && !fields.contains("assignee"))
                || (query.tagMatch().active(query.tags()) && !fields.contains(TicketAccess.TAGS_FIELD))
                || query.custom().stream().anyMatch(filter -> !fields.contains(ProjectConfiguration.CUSTOM_PREFIX + filter.key()))) {
            return;
        }
        List<Object> local = new ArrayList<>();
        List<String> vectors = TICKET_FIELDS.stream().filter(TEXT_VECTORS::containsKey).filter(fields::contains)
            .map(TEXT_VECTORS::get).toList();
        // Custom fields of the kinds text and long text are indexed as terms; the other kinds have none and match no word.
        List<String> customKeys = fields.stream().filter(field -> field.startsWith(ProjectConfiguration.CUSTOM_PREFIX))
            .map(field -> field.substring(ProjectConfiguration.CUSTOM_PREFIX.length())).sorted().toList();
        String vector = String.join(" || ", vectors);
        String rank = "0";
        StringBuilder where = new StringBuilder("s.company_id = ? and s.project_id in (" + placeholders(ids.size()) + ")");
        if (text.present()) {
            List<String> matches = new ArrayList<>();
            List<Object> matchParams = new ArrayList<>();
            List<Object> rankParams = new ArrayList<>();
            if (text.tsquery() != null && (!vector.isEmpty() || !customKeys.isEmpty())) {
                List<String> parts = new ArrayList<>();
                if (!vector.isEmpty()) {
                    parts.add("ts_rank(" + vector + ", to_tsquery('simple', ?))");
                    rankParams.add(text.tsquery());
                }
                if (!customKeys.isEmpty()) {
                    parts.add("coalesce((select max(ts_rank(c.tsv, to_tsquery('simple', ?))) from search_ticket_custom c "
                        + "where c.ticket_id = s.ticket_id and c.field_key in (" + placeholders(customKeys.size()) + ")), 0)");
                    rankParams.add(text.tsquery());
                    rankParams.addAll(customKeys);
                }
                rank = String.join(" + ", parts);
                // Every word in one of the readable fields, each field vector on its own, so the GIN index of each field
                // serves the match (finding F3); the same result as matching the joined vector.
                List<String> words = new ArrayList<>();
                for (String word : text.words()) {
                    List<String> alternatives = new ArrayList<>(vectors.stream().map(v -> v + " @@ to_tsquery('simple', ?)").toList());
                    vectors.forEach(v -> matchParams.add(word));
                    if (!customKeys.isEmpty()) {
                        alternatives.add("exists (select 1 from search_ticket_custom c where c.ticket_id = s.ticket_id and c.field_key in ("
                            + placeholders(customKeys.size()) + ") and c.tsv @@ to_tsquery('simple', ?))");
                        matchParams.addAll(customKeys);
                        matchParams.add(word);
                    }
                    words.add("(" + String.join(" or ", alternatives) + ")");
                }
                matches.add("(" + String.join(" and ", words) + ")");
            }
            if (text.key() != null) {
                // The identifier is not a field: who may read the ticket may find it by its key.
                rank = "case when p.key = ? and s.number = ? then 1000 else 0 end + " + rank;
                matches.add("(p.key = ? and s.number = ?)");
                matchParams.add(text.key());
                matchParams.add(text.number());
            }
            if (matches.isEmpty()) {
                return;
            }
            where.append(" and (").append(String.join(" or ", matches)).append(")");
            // The rank expression comes first in the select, so its parameters lead.
            if (text.key() != null) {
                local.add(text.key());
                local.add(text.number());
            }
            local.addAll(rankParams);
            local.add(actor.companyId());
            local.addAll(ids);
            local.addAll(matchParams);
        } else {
            local.add(actor.companyId());
            local.addAll(ids);
        }
        filter(where, local, "s.status", query.statuses());
        filter(where, local, "s.type", query.types());
        filter(where, local, "s.assignee", query.assignees());
        tagFilter(where, local, "search_ticket_tag", "ticket_id", "s.ticket_id", query.tags(), query.tagMatch());
        for (CustomFilter filter : query.custom()) {
            where.append(" and exists (select 1 from search_ticket_custom c where c.ticket_id = s.ticket_id and c.field_key = ? and c.value = ?)");
            local.add(filter.key());
            local.add(filter.value());
        }
        selects.add("select s.ticket_id as id, s.project_id, p.key as project_key, s.number, " + rank + " as rank "
            + "from search_ticket s join search_project p on p.project_id = s.project_id where " + where);
        params.addAll(local);
    }

    /**
     * The tag filter of one select. Every tag on a ticket comes from the company catalog or the catalog of its project, which
     * who may read the tags of the ticket sees, so "no visible tag" is "no tag" there.
     */
    private static void tagFilter(StringBuilder where, List<Object> params, String table, String column, String owner, List<UUID> tags,
            TagMatch match) {
        String assignments = "select 1 from " + table + " a where a." + column + " = " + owner;
        switch (match) {
            case ALL -> {
                for (UUID tag : tags) {
                    where.append(" and exists (").append(assignments).append(" and a.tag_id = ?)");
                    params.add(tag);
                }
            }
            case ANY, NONE -> {
                where.append(match == TagMatch.ANY ? " and exists (" : " and not exists (").append(assignments)
                    .append(" and a.tag_id in (").append(placeholders(tags.size())).append("))");
                params.addAll(tags);
            }
            case UNTAGGED -> where.append(" and not exists (").append(assignments).append(")");
        }
    }

    private static void filter(StringBuilder where, List<Object> params, String column, List<?> values) {
        if (!values.isEmpty()) {
            where.append(" and ").append(column).append(" in (").append(placeholders(values.size())).append(")");
            params.addAll(values);
        }
    }

    /** The page with the fields of each ticket the caller may read in its project. */
    private List<TicketHit> ticketHits(List<Hit> hits, Map<UUID, Set<String>> readable) {
        if (hits.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = hits.stream().map(Hit::id).toList();
        Map<UUID, Map<String, String>> values = new HashMap<>();
        jdbc.sql("select ticket_id, title, description, type, status, assignee from search_ticket where ticket_id in (:ids)")
            .param("ids", ids)
            .query((ResultSet rs) -> {
                Map<String, String> row = new HashMap<>();
                row.put("title", rs.getString("title"));
                row.put("description", rs.getString("description"));
                row.put("type", rs.getString("type"));
                row.put("status", rs.getString("status"));
                UUID assignee = rs.getObject("assignee", UUID.class);
                row.put("assignee", assignee == null ? null : assignee.toString());
                values.put(rs.getObject("ticket_id", UUID.class), row);
            });
        Map<UUID, Map<String, String>> custom = new HashMap<>();
        jdbc.sql("select ticket_id, field_key, value from search_ticket_custom where ticket_id in (:ids) order by field_key")
            .param("ids", ids)
            .query((ResultSet rs) -> {
                custom.computeIfAbsent(rs.getObject("ticket_id", UUID.class), ticket -> new LinkedHashMap<>())
                    .put(rs.getString("field_key"), rs.getString("value"));
            });
        Map<UUID, List<TagRef>> tags = tags("search_ticket_tag", "ticket_id", ids);
        List<TicketHit> result = new ArrayList<>();
        for (Hit hit : hits) {
            Set<String> fields = readable.get(hit.projectId());
            Map<String, String> shown = new LinkedHashMap<>();
            for (String field : TICKET_FIELDS) {
                if (!field.equals(TicketAccess.TAGS_FIELD) && fields.contains(field)) {
                    shown.put(field, values.get(hit.id()).get(field));
                }
            }
            List<TagRef> shownTags = fields.contains(TicketAccess.TAGS_FIELD) ? tags.getOrDefault(hit.id(), List.of()) : null;
            Map<String, String> shownCustom = new LinkedHashMap<>();
            custom.getOrDefault(hit.id(), Map.of()).forEach((key, value) -> {
                if (fields.contains(ProjectConfiguration.CUSTOM_PREFIX + key)) {
                    shownCustom.put(key, value);
                }
            });
            result.add(new TicketHit(hit.id(), hit.projectId(), hit.key(), hit.number(), shown, shownCustom, shownTags));
        }
        return result;
    }

    /** Per project with {@code ticket.read}: the ticket fields the caller may read there. */
    private Map<UUID, Set<String>> readableTicketFields(AccessContext actor) {
        Map<UUID, Set<String>> readable = new LinkedHashMap<>();
        Set<String> fields = Set.copyOf(TICKET_FIELDS);
        for (UUID project : projects.memberships(actor.companyId(), actor.userId())) {
            Supplier<ResourceContext> resource = resource(actor, project, TicketAccess.RESOURCE_TYPE);
            Decision read = permissions.inspect(actor, resource, TICKET_READ, fields);
            if (read.allowed()) {
                Set<String> shown = new HashSet<>(read.readableFields());
                // The identifiers of the custom fields come from the configuration, which the first check has locked.
                List<String> custom = projects.currentConfigurationVersion(project)
                    .flatMap(version -> projects.configuration(project, version)).map(ProjectConfiguration::customFieldIds).orElse(List.of());
                if (!custom.isEmpty()) {
                    shown.addAll(permissions.inspect(actor, resource, TICKET_READ, Set.copyOf(custom)).readableFields());
                }
                readable.put(project, Set.copyOf(shown));
            }
        }
        return readable;
    }

    // ------------------------------------------------------------------------------------------------ projects

    @Transactional
    public Page<ProjectHit> projects(AccessContext actor, ProjectQuery query) {
        internal(actor);
        SearchText text = text(query.text());
        boolean companyWide = permissions.inspect(actor, () -> ResourceContext.company(actor.companyId()),
            AdministrationRight.PROJECTS_MANAGE.key(), Set.of()).allowed();
        List<UUID> visible = companyWide ? List.of() : readableProjects(actor);
        // Projects take tags of the company catalog only.
        checkTags(actor, query.tags(), List.of(), true);
        if (!companyWide && visible.isEmpty()) {
            return new Page<>(0, List.of());
        }
        List<Object> params = new ArrayList<>();
        String rank = "0";
        StringBuilder where = new StringBuilder("s.company_id = ?");
        if (text.present()) {
            if (text.tsquery() == null) {
                return new Page<>(0, List.of());
            }
            rank = "ts_rank(s.document, to_tsquery('simple', ?))";
            String key = projectKey(query.text());
            if (key != null) {
                // A text that is exactly a project key puts that project first (TASK-104, quick search).
                rank = "case when s.key = ? then 1000 else 0 end + " + rank;
                params.add(key);
            }
            params.add(text.tsquery());
        }
        params.add(actor.companyId());
        if (!companyWide) {
            where.append(" and s.project_id in (").append(placeholders(visible.size())).append(")");
            params.addAll(visible);
        }
        if (text.present()) {
            String key = projectKey(query.text());
            if (key == null) {
                where.append(" and s.document @@ to_tsquery('simple', ?)");
                params.add(text.tsquery());
            } else {
                // The normalized key finds its project also when the words of the text differ from those of the index, as
                // straße names the key STRASSE (TASK-110, F5); count and page share this condition.
                where.append(" and (s.document @@ to_tsquery('simple', ?) or s.key = ?)");
                params.add(text.tsquery());
                params.add(key);
            }
        }
        tagFilter(where, params, "search_project_tag", "project_id", "s.project_id", query.tags(), query.tagMatch());
        String select = "select s.project_id, s.key, s.name, s.description, " + rank + " as rank from search_project s where " + where;
        int rankParams = text.present() ? (projectKey(query.text()) != null ? 2 : 1) : 0;
        List<Object> countParams = params.subList(rankParams, params.size());
        long total = jdbc.sql("select count(*) from search_project s where " + where).params(countParams).query(Long.class).single();
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(query.limit());
        pageParams.add(query.offset());
        List<ProjectHit> hits = jdbc.sql(select + " order by rank desc, s.key limit ? offset ?").params(pageParams)
            .query((rs, row) -> project(rs)).list();
        if (hits.isEmpty()) {
            return new Page<>(total, hits);
        }
        Map<UUID, List<TagRef>> tags = tags("search_project_tag", "project_id", hits.stream().map(ProjectHit::id).toList());
        return new Page<>(total, hits.stream()
            .map(hit -> new ProjectHit(hit.id(), hit.key(), hit.name(), hit.description(), tags.getOrDefault(hit.id(), List.of())))
            .toList());
    }

    private static ProjectHit project(ResultSet rs) throws SQLException {
        return new ProjectHit(rs.getObject("project_id", UUID.class), rs.getString("key"), rs.getString("name"),
            rs.getString("description"), List.of());
    }

    /** The member projects with {@code project.read} or {@code project.manage}. */
    private List<UUID> readableProjects(AccessContext actor) {
        List<UUID> readable = new ArrayList<>();
        for (UUID project : projects.memberships(actor.companyId(), actor.userId())) {
            Supplier<ResourceContext> resource = resource(actor, project, "project");
            if (PROJECT_READERS.stream().anyMatch(action -> permissions.inspect(actor, resource, action, Set.of()).allowed())) {
                readable.add(project);
            }
        }
        return readable;
    }

    // ------------------------------------------------------------------------------------------------ wiki

    static final String WIKI_RESOURCE = "wiki_page";
    static final String WIKI_READ = "wiki.read";
    static final String WIKI_EDIT = "wiki.edit";
    static final String WIKI_PUBLISH = "wiki.publish";

    /**
     * A search of wiki pages (TASK-102, DEC-132). {@code language} limits the language versions searched ({@code null} for
     * both), {@code preferred} is the language of the interface, whose version a page shows when both match. {@code drafts}
     * searches the current drafts instead of the published revisions, {@code archived} adds archived pages.
     */
    public record WikiQuery(String text, List<UUID> projects, String language, String preferred, boolean drafts, boolean archived,
            int limit, int offset) {
    }

    /**
     * One page in the version found: {@code stage} is {@code published} or {@code draft}; {@code publishedAt} is set for a
     * published revision. {@code path} holds the pages above as the page view shows them to the caller.
     */
    public record WikiHit(UUID id, UUID projectId, String projectKey, String title, List<SearchSnippet.Mark> titleMarks, String language,
            String stage, int revision, java.time.Instant publishedAt, boolean archived, List<WikiPages.PathEntry> path, SearchSnippet snippet) {
    }

    private record WikiAccess(boolean read, boolean drafts, boolean publish) {
        boolean any() {
            return read || drafts;
        }
    }

    private record WikiRow(UUID id, UUID projectId, String projectKey, String language, String title, String body, int revision,
            java.time.Instant publishedAt, boolean archived) {
    }

    /**
     * Rights first, as for tickets: the projects with a wiki action of the caller are searched, drafts only in projects with
     * {@code wiki.edit} or {@code wiki.publish}, archived pages only in projects with {@code wiki.publish}, as the wiki shows them
     * (DEC-123). Asking for drafts or archived pages without such a project is refused with 403. Every language version matches
     * on its own: all words in its title or its text; a page is one hit, shown in the preferred language, else its source
     * language, else the other matching version. Rank, total and page come from these rows alone.
     */
    @Transactional
    public Page<WikiHit> wiki(AccessContext actor, WikiQuery query) {
        internal(actor);
        SearchText text = text(query.text());
        Map<UUID, WikiAccess> access = wikiAccess(actor);
        if (!query.projects().isEmpty() && !access.keySet().containsAll(query.projects())) {
            throw invalid("project.unknown");
        }
        List<UUID> scope = access.keySet().stream().filter(project -> query.projects().isEmpty() || query.projects().contains(project))
            .filter(project -> !query.drafts() || access.get(project).drafts()).toList();
        if ((query.drafts() && access.values().stream().noneMatch(WikiAccess::drafts))
                || (query.archived() && access.values().stream().noneMatch(WikiAccess::publish))) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        List<UUID> archivedScope = query.archived() ? scope.stream().filter(project -> access.get(project).publish()).toList() : List.of();
        if (scope.isEmpty() || (text.present() && text.tsquery() == null)) {
            return new Page<>(0, List.of());
        }

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("p.company_id = ? and p.project_id in (" + placeholders(scope.size()) + ") and t.stage = ?");
        params.add(actor.companyId());
        params.addAll(scope);
        params.add(query.drafts() ? "draft" : "published");
        if (archivedScope.isEmpty()) {
            where.append(" and not p.archived");
        } else {
            where.append(" and (not p.archived or p.project_id in (").append(placeholders(archivedScope.size())).append("))");
            params.addAll(archivedScope);
        }
        if (query.language() != null) {
            where.append(" and t.language = ?");
            params.add(query.language());
        }
        String rank = "0";
        List<Object> rankParams = new ArrayList<>();
        if (text.present()) {
            for (String word : text.words()) {
                where.append(" and (t.title_tsv @@ to_tsquery('simple', ?) or t.body_tsv @@ to_tsquery('simple', ?))");
                params.add(word);
                params.add(word);
            }
            // Each word counts where it is found, in the title twice as much as in the text; ranking the title and the text
            // each against all words would push a hit with its words spread over both far behind (H2 of the review).
            String anyWord = String.join(" | ", text.words());
            rank = "2 * ts_rank(t.title_tsv, to_tsquery('simple', ?)) + ts_rank(t.body_tsv, to_tsquery('simple', ?))";
            rankParams.add(anyWord);
            rankParams.add(anyWord);
        }
        String preferred = query.preferred() == null ? "de" : query.preferred();
        String from = " from search_wiki_text t join search_wiki_page p on p.page_id = t.page_id "
            + "join search_project sp on sp.project_id = p.project_id where " + where;
        // One statement, also past the last page: the count row is always there and the page rows join it, so total, row,
        // revision and text come from one snapshot even while a page is published or withdrawn (F4 of the review, R4 of the
        // follow up review).
        List<Object> pageParams = new ArrayList<>(rankParams);
        pageParams.add(preferred);
        pageParams.addAll(params);
        pageParams.add(query.limit());
        pageParams.add(query.offset());
        long[] total = {0};
        List<WikiRow> rows = new ArrayList<>();
        jdbc.sql("with h as (select distinct on (t.page_id) t.page_id, p.project_id, "
                + "sp.key as project_key, t.language, t.title, t.body, t.revision, t.published_at, p.archived, " + rank + " as rank, "
                + "case when t.language = ? then 0 when t.language = p.source_language then 1 else 2 end as preference"
                + from + " order by t.page_id, preference) select c.total, w.* from (select count(*) as total from h) c "
                + "left join lateral (select * from h order by rank desc, lower(title), title, page_id limit ? offset ?) w on true "
                + "order by w.rank desc, lower(w.title), w.title, w.page_id")
            .params(pageParams)
            .query((ResultSet rs) -> {
                total[0] = rs.getLong("total");
                UUID id = rs.getObject("page_id", UUID.class);
                if (id != null) {
                    rows.add(new WikiRow(id, rs.getObject("project_id", UUID.class), rs.getString("project_key"),
                        rs.getString("language"), rs.getString("title"), rs.getString("body"), rs.getInt("revision"),
                        rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(), rs.getBoolean("archived")));
                }
            });
        if (rows.isEmpty()) {
            return new Page<>(total[0], List.of());
        }
        String stage = query.drafts() ? "draft" : "published";
        Map<UUID, List<WikiPages.PathEntry>> paths = wiki.paths(actor, rows.stream().map(WikiRow::id).toList(), preferred, query.drafts());
        List<String> words = text.plainWords();
        return new Page<>(total[0], rows.stream().map(row -> new WikiHit(row.id(), row.projectId(), row.projectKey(), row.title(),
            SearchSnippet.marks(row.title(), words), row.language(), stage, row.revision(), row.publishedAt(), row.archived(),
            paths.getOrDefault(row.id(), List.of()), SearchSnippet.of(row.body(), words))).toList());
    }

    /** Per member project with a wiki action: what the caller may search there. */
    private Map<UUID, WikiAccess> wikiAccess(AccessContext actor) {
        Map<UUID, WikiAccess> access = new LinkedHashMap<>();
        for (UUID project : projects.memberships(actor.companyId(), actor.userId())) {
            Supplier<ResourceContext> resource = resource(actor, project, WIKI_RESOURCE);
            boolean read = permissions.inspect(actor, resource, WIKI_READ, Set.of()).allowed();
            boolean edit = permissions.inspect(actor, resource, WIKI_EDIT, Set.of()).allowed();
            boolean publish = permissions.inspect(actor, resource, WIKI_PUBLISH, Set.of()).allowed();
            WikiAccess rights = new WikiAccess(read, edit || publish, publish);
            if (rights.any()) {
                access.put(project, rights);
            }
        }
        return access;
    }

    // ------------------------------------------------------------------------------------------------ requirements

    static final String REQUIREMENT_RESOURCE = "requirement";
    static final String REQUIREMENT_READ = "requirement.read";
    /** The standard fields of requirements with field rules (DEC-126); key, status and revision are no fields. */
    static final List<String> REQUIREMENT_FIELDS = List.of("title", "description", "criteria", "priority", "owner");
    public static final List<String> REQUIREMENT_STATUSES = List.of("draft", "review", "approved", "discarded");
    public static final List<String> REQUIREMENT_PRIORITIES = List.of("low", "normal", "high", "critical");
    private static final java.util.regex.Pattern REQUIREMENT_KEY =
        java.util.regex.Pattern.compile("(?:([A-Z][A-Z0-9]{1,9})\\s+)?ANF-([0-9]{1,18})");
    private static final Map<String, String> REQUIREMENT_VECTORS = Map.of(
        "title", "t.title_tsv", "description", "t.description_tsv", "criteria", "t.criteria_tsv");

    /**
     * A search of requirements (TASK-103, DEC-133). {@code approvedStage} searches the newest approved revision instead of the
     * current one, never both. {@code everApproved} keeps requirements that were ({@code true}) or never were ({@code false})
     * approved; {@code null} keeps all. Discarded requirements are left out unless a status is given.
     */
    public record RequirementQuery(String text, List<UUID> projects, boolean approvedStage, List<String> statuses, List<String> priorities,
            List<UUID> owners, Boolean everApproved, List<CustomFilter> custom, int limit, int offset) {
    }

    /**
     * One requirement in the revision searched. Fields the caller may not read are {@code null} (title, priority, owner) or
     * missing (custom fields). {@code approved} tells whether this revision is the newest approved one.
     */
    public record RequirementHit(UUID id, UUID projectId, String projectKey, String key, long number, String title,
            List<SearchSnippet.Mark> titleMarks, String status, String stage, int revision, int currentRevision, Integer approvedRevision,
            boolean approved, String priority, UUID owner, Map<String, String> customFields, RequirementSnippet snippet) {
    }

    /**
     * The excerpt of a hit and where it comes from: {@code description}, {@code criteria} with the number of the criterion, or
     * the identifier of a custom field of the kind text or long text.
     */
    public record RequirementSnippet(String field, Integer criterion, SearchSnippet excerpt) {
    }

    private record RequirementRow(UUID id, UUID projectId, String projectKey, long number) {
    }

    /**
     * Rights first, as for tickets: projects with {@code requirement.read} of the caller are searched, words match only in the
     * fields the caller may read there, and a filter on a field they may not read matches nothing in that project. Rank, total,
     * page and excerpt come from readable fields alone; the key {@code ANF-n}, with or without the key of the project in front,
     * finds the requirement for every reader. One statement gives total and page from one snapshot.
     */
    @Transactional
    public Page<RequirementHit> requirements(AccessContext actor, RequirementQuery query) {
        internal(actor);
        SearchText text = text(query.text());
        Map<UUID, Set<String>> readable = readableRequirementFields(actor);
        Set<UUID> scope = new LinkedHashSet<>(readable.keySet());
        if (!query.projects().isEmpty()) {
            if (!readable.keySet().containsAll(query.projects())) {
                throw invalid("project.unknown");
            }
            scope.retainAll(query.projects());
        }
        String[] key = requirementKey(query.text());
        Map<Set<String>, List<UUID>> groups = new LinkedHashMap<>();
        for (UUID project : scope) {
            groups.computeIfAbsent(readable.get(project), fields -> new ArrayList<>()).add(project);
        }
        List<String> selects = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String stage = query.approvedStage() ? "approved" : "current";
        groups.forEach((fields, ids) -> requirementSelect(actor, query, text, key, stage, fields, ids, selects, params));
        if (selects.isEmpty()) {
            return new Page<>(0, List.of());
        }
        String union = String.join(" union all ", selects);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(query.limit());
        pageParams.add(query.offset());
        // One statement, also past the last page: the count row is always there and the page rows join it, so total and page
        // come from one snapshot even while a requirement changes (R4 of the follow up review of TASK-102).
        long[] total = {0};
        List<RequirementRow> rows = new ArrayList<>();
        jdbc.sql("with h as (" + union + ") select c.total, p.id, p.project_id, p.project_key, p.number "
                + "from (select count(*) as total from h) c left join lateral (select * from h "
                + "order by rank desc, project_key, number desc limit ? offset ?) p on true order by p.rank desc, p.project_key, p.number desc")
            .params(pageParams)
            .query((ResultSet rs) -> {
                total[0] = rs.getLong("total");
                UUID id = rs.getObject("id", UUID.class);
                if (id != null) {
                    rows.add(new RequirementRow(id, rs.getObject("project_id", UUID.class), rs.getString("project_key"), rs.getLong("number")));
                }
            });
        if (rows.isEmpty()) {
            return new Page<>(total[0], List.of());
        }
        return new Page<>(total[0], requirementHits(rows, stage, readable, text.plainWords()));
    }

    /** {@code [project key or null, number]} when the text names a requirement by its key; {@code null} otherwise. */
    private static String[] requirementKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = Nfkc.of(raw).strip().toUpperCase(java.util.Locale.ROOT);
        java.util.regex.Matcher matcher = REQUIREMENT_KEY.matcher(text);
        return matcher.matches() ? new String[] {matcher.group(1), matcher.group(2)} : null;
    }

    /** One select of the union for the projects that share the same readable fields; none when they cannot match. */
    private void requirementSelect(AccessContext actor, RequirementQuery query, SearchText text, String[] key, String stage, Set<String> fields,
            List<UUID> ids, List<String> selects, List<Object> params) {
        if ((!query.priorities().isEmpty() && !fields.contains("priority")) || (!query.owners().isEmpty() && !fields.contains("owner"))
                || query.custom().stream().anyMatch(filter -> !fields.contains(ProjectConfiguration.CUSTOM_PREFIX + filter.key()))) {
            return;
        }
        List<String> vectors = REQUIREMENT_FIELDS.stream().filter(REQUIREMENT_VECTORS::containsKey).filter(fields::contains)
            .map(REQUIREMENT_VECTORS::get).toList();
        List<String> customKeys = fields.stream().filter(field -> field.startsWith(ProjectConfiguration.CUSTOM_PREFIX))
            .map(field -> field.substring(ProjectConfiguration.CUSTOM_PREFIX.length())).sorted().toList();
        String custom = "search_requirement_custom c where c.requirement_id = r.requirement_id and c.stage = t.stage and c.field_key in ("
            + placeholders(customKeys.size()) + ")";
        List<Object> rankParams = new ArrayList<>();
        List<Object> whereParams = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        String rank = "0";
        if (text.present()) {
            List<String> matches = new ArrayList<>();
            if (text.tsquery() != null && (!vectors.isEmpty() || !customKeys.isEmpty())) {
                List<String> words = new ArrayList<>();
                for (String word : text.words()) {
                    List<String> alternatives = new ArrayList<>();
                    for (String vector : vectors) {
                        alternatives.add(vector + " @@ to_tsquery('simple', ?)");
                        whereParams.add(word);
                    }
                    if (!customKeys.isEmpty()) {
                        alternatives.add("exists (select 1 from " + custom + " and c.tsv @@ to_tsquery('simple', ?))");
                        whereParams.addAll(customKeys);
                        whereParams.add(word);
                    }
                    words.add("(" + String.join(" or ", alternatives) + ")");
                }
                matches.add("(" + String.join(" and ", words) + ")");
                // Each word counts where it is found, the title twice as much as the other fields (as for wiki pages).
                String anyWord = String.join(" | ", text.words());
                List<String> parts = new ArrayList<>();
                for (String vector : vectors) {
                    parts.add((vector.equals("t.title_tsv") ? "2 * " : "") + "ts_rank(" + vector + ", to_tsquery('simple', ?))");
                    rankParams.add(anyWord);
                }
                if (!customKeys.isEmpty()) {
                    parts.add("coalesce((select max(ts_rank(c.tsv, to_tsquery('simple', ?))) from " + custom + "), 0)");
                    rankParams.add(anyWord);
                    rankParams.addAll(customKeys);
                }
                rank = String.join(" + ", parts);
            }
            if (key != null) {
                // The key is not a field: who may read the requirement may find it by its key.
                String keyMatch = key[0] == null ? "r.number = ?" : "(p.key = ? and r.number = ?)";
                rank = "case when " + keyMatch + " then 1000 else 0 end + " + rank;
                List<Object> keyParams = new ArrayList<>();
                if (key[0] != null) {
                    keyParams.add(key[0]);
                }
                keyParams.add(Long.parseLong(key[1]));
                rankParams.addAll(0, keyParams);
                matches.add(keyMatch);
                whereParams.addAll(keyParams);
            }
            if (matches.isEmpty()) {
                return;
            }
            where.append(" and (").append(String.join(" or ", matches)).append(")");
        }
        if (query.statuses().isEmpty()) {
            where.append(" and r.status <> 'discarded'");
        } else {
            filter(where, whereParams, "r.status", query.statuses());
        }
        filter(where, whereParams, "t.priority", query.priorities());
        filter(where, whereParams, "r.owner_id", query.owners());
        if (query.everApproved() != null) {
            where.append(query.everApproved() ? " and r.approved_revision is not null" : " and r.approved_revision is null");
        }
        for (CustomFilter filter : query.custom()) {
            where.append(" and exists (select 1 from search_requirement_custom c where c.requirement_id = r.requirement_id "
                + "and c.stage = t.stage and c.field_key = ? and c.value = ?)");
            whereParams.add(filter.key());
            whereParams.add(filter.value());
        }
        selects.add("select r.requirement_id as id, r.project_id, p.key as project_key, r.number, " + rank + " as rank "
            + "from search_requirement r join search_requirement_text t on t.requirement_id = r.requirement_id and t.stage = ? "
            + "join search_project p on p.project_id = r.project_id "
            + "where r.company_id = ? and r.project_id in (" + placeholders(ids.size()) + ")" + where);
        params.addAll(rankParams);
        params.add(stage);
        params.add(actor.companyId());
        params.addAll(ids);
        params.addAll(whereParams);
    }

    /** The page with the fields of each requirement the caller may read in its project and the excerpt of a readable field. */
    private List<RequirementHit> requirementHits(List<RequirementRow> rows, String stage, Map<UUID, Set<String>> readable, List<String> words) {
        List<UUID> ids = rows.stream().map(RequirementRow::id).toList();
        record Text(String status, UUID owner, int current, Integer approved, int revision, String title, String description, String priority) {
        }
        Map<UUID, Text> texts = new HashMap<>();
        jdbc.sql("select r.requirement_id, r.status, r.owner_id, r.current_revision, r.approved_revision, t.revision, t.title, "
                + "t.description, t.priority from search_requirement r join search_requirement_text t on t.requirement_id = r.requirement_id "
                + "and t.stage = :stage where r.requirement_id in (:ids)")
            .param("stage", stage).param("ids", ids)
            .query((ResultSet rs) -> {
                texts.put(rs.getObject("requirement_id", UUID.class), new Text(rs.getString("status"), rs.getObject("owner_id", UUID.class),
                    rs.getInt("current_revision"), (Integer) rs.getObject("approved_revision"), rs.getInt("revision"), rs.getString("title"),
                    rs.getString("description"), rs.getString("priority")));
            });
        record Criterion(int id, String text) {
        }
        Map<UUID, List<Criterion>> criteria = new HashMap<>();
        jdbc.sql("select requirement_id, criterion_id, text from search_requirement_criterion where stage = :stage and requirement_id in (:ids) "
                + "order by position").param("stage", stage).param("ids", ids)
            .query((ResultSet rs) -> {
                criteria.computeIfAbsent(rs.getObject("requirement_id", UUID.class), id -> new ArrayList<>())
                    .add(new Criterion(rs.getInt("criterion_id"), rs.getString("text")));
            });
        record Custom(String value, boolean text) {
        }
        Map<UUID, Map<String, Custom>> custom = new HashMap<>();
        jdbc.sql("select requirement_id, field_key, value, terms <> '' as text from search_requirement_custom where stage = :stage "
                + "and requirement_id in (:ids) order by field_key").param("stage", stage).param("ids", ids)
            .query((ResultSet rs) -> {
                custom.computeIfAbsent(rs.getObject("requirement_id", UUID.class), id -> new LinkedHashMap<>())
                    .put(rs.getString("field_key"), new Custom(rs.getString("value"), rs.getBoolean("text")));
            });
        List<RequirementHit> hits = new ArrayList<>();
        for (RequirementRow row : rows) {
            Set<String> fields = readable.get(row.projectId());
            Text text = texts.get(row.id());
            Map<String, String> shownCustom = new LinkedHashMap<>();
            Map<String, Custom> values = custom.getOrDefault(row.id(), Map.of());
            values.forEach((field, value) -> {
                if (fields.contains(ProjectConfiguration.CUSTOM_PREFIX + field)) {
                    shownCustom.put(field, value.value());
                }
            });
            // The excerpt comes from the first readable field that holds a search word: description, criteria, custom texts.
            RequirementSnippet snippet = null;
            if (!words.isEmpty()) {
                if (fields.contains("description") && !SearchSnippet.marks(text.description(), words).isEmpty()) {
                    snippet = new RequirementSnippet("description", null, SearchSnippet.of(text.description(), words));
                }
                if (snippet == null && fields.contains("criteria")) {
                    for (Criterion criterion : criteria.getOrDefault(row.id(), List.of())) {
                        if (!SearchSnippet.marks(criterion.text(), words).isEmpty()) {
                            snippet = new RequirementSnippet("criteria", criterion.id(), SearchSnippet.of(criterion.text(), words));
                            break;
                        }
                    }
                }
                if (snippet == null) {
                    for (Map.Entry<String, Custom> value : values.entrySet()) {
                        String id = ProjectConfiguration.CUSTOM_PREFIX + value.getKey();
                        if (value.getValue().text() && fields.contains(id) && !SearchSnippet.marks(value.getValue().value(), words).isEmpty()) {
                            snippet = new RequirementSnippet(id, null, SearchSnippet.of(value.getValue().value(), words));
                            break;
                        }
                    }
                }
            }
            if (snippet == null && fields.contains("description") && !text.description().isBlank()) {
                snippet = new RequirementSnippet("description", null, SearchSnippet.of(text.description(), words));
            }
            boolean titleShown = fields.contains("title");
            hits.add(new RequirementHit(row.id(), row.projectId(), row.projectKey(), "ANF-" + row.number(), row.number(),
                titleShown ? text.title() : null, titleShown ? SearchSnippet.marks(text.title(), words) : List.of(), text.status(), stage,
                text.revision(), text.current(), text.approved(), text.approved() != null && text.approved() == text.revision(),
                fields.contains("priority") ? text.priority() : null, fields.contains("owner") ? text.owner() : null, shownCustom, snippet));
        }
        return hits;
    }

    /** Per project with {@code requirement.read}: the requirement fields the caller may read there, custom fields included. */
    private Map<UUID, Set<String>> readableRequirementFields(AccessContext actor) {
        Map<UUID, Set<String>> readable = new LinkedHashMap<>();
        for (UUID project : projects.memberships(actor.companyId(), actor.userId())) {
            Supplier<ResourceContext> resource = resource(actor, project, REQUIREMENT_RESOURCE);
            Decision read = permissions.inspect(actor, resource, REQUIREMENT_READ, Set.copyOf(REQUIREMENT_FIELDS));
            if (read.allowed()) {
                Set<String> shown = new HashSet<>(read.readableFields());
                List<String> custom = projects.currentConfigurationVersion(project)
                    .flatMap(version -> projects.configuration(project, version)).map(ProjectConfiguration::requirementFieldIds).orElse(List.of());
                if (!custom.isEmpty()) {
                    shown.addAll(permissions.inspect(actor, resource, REQUIREMENT_READ, Set.copyOf(custom)).readableFields());
                }
                readable.put(project, Set.copyOf(shown));
            }
        }
        return readable;
    }

    // ------------------------------------------------------------------------------------------------ shared

    /**
     * Unknown tags and tags of a catalog the caller may not see get the same answer (TAGS_SEARCH.md). The catalog of a
     * project is visible to who may search its tickets, since {@code ticket.read} reads it (DEC-068).
     */
    private void checkTags(AccessContext actor, List<UUID> tags, java.util.Collection<UUID> projects, boolean companyOnly) {
        if (tags.isEmpty()) {
            return;
        }
        if (!new HashSet<>(search.visibleTags(actor.companyId(), tags, projects, companyOnly)).containsAll(tags)) {
            throw invalid("tag.unknown");
        }
    }

    private Map<UUID, List<TagRef>> tags(String table, String column, List<UUID> ids) {
        Map<UUID, List<TagRef>> tags = new HashMap<>();
        jdbc.sql("select a." + column + " as owner, g.tag_id, g.name from " + table + " a join search_tag g on g.tag_id = a.tag_id "
                + "where a." + column + " in (:ids) order by g.name, g.tag_id")
            .param("ids", ids)
            .query((ResultSet rs) -> {
                tags.computeIfAbsent(rs.getObject("owner", UUID.class), owner -> new ArrayList<>())
                    .add(new TagRef(rs.getObject("tag_id", UUID.class), rs.getString("name")));
            });
        return tags;
    }

    private Supplier<ResourceContext> resource(AccessContext actor, UUID project, String resourceType) {
        return projects.resource(actor.companyId(), project, actor.userId(), resourceType, false);
    }

    // ------------------------------------------------------------------------------------------------ overview

    /** The hits of every area in the overview "Alle" (TASK-104, DEC-134). */
    static final int OVERVIEW_LIMIT = 5;
    private static final java.util.regex.Pattern PROJECT_KEY = java.util.regex.Pattern.compile("[A-Z][A-Z0-9]{1,9}");

    /**
     * The object a text names exactly (TASK-104): {@code ticket} for {@code KEY-12}, {@code requirement} for {@code KEY ANF-17},
     * {@code project} for a project key; {@code id} is that of the ticket, requirement or project.
     */
    public record Direct(String kind, UUID id, UUID projectId, String key, Long number) {
    }

    /**
     * The overview: per area the first {@value #OVERVIEW_LIMIT} hits and the total, {@code null} for an area the caller has no
     * right in, and the object a text names exactly when the caller may read it.
     */
    public record Overview(Page<TicketHit> tickets, Page<ProjectHit> projects, Page<WikiHit> wiki, Page<RequirementHit> requirements,
            Direct direct) {
    }

    /**
     * Every area with the rules of its own search, in one transaction: tickets and projects as TASK-029, wiki pages in their
     * published revision as TASK-102, requirements in their current revision as TASK-103. An area appears only when the caller
     * has its read action in a member project ({@code project.read} or the company right for projects); there is no common
     * ranking over the areas. Only an object the caller may read can be named directly; otherwise the text is an ordinary search.
     */
    @Transactional
    public Overview overview(AccessContext actor, String raw, String preferred) {
        internal(actor);
        SearchText text = text(raw);
        boolean ticketArea = !readableTicketFields(actor).isEmpty();
        boolean projectArea = permissions.inspect(actor, () -> ResourceContext.company(actor.companyId()),
            AdministrationRight.PROJECTS_MANAGE.key(), Set.of()).allowed() || !readableProjects(actor).isEmpty();
        boolean wikiArea = wikiAccess(actor).values().stream().anyMatch(WikiAccess::any);
        boolean requirementArea = !readableRequirementFields(actor).isEmpty();
        Page<TicketHit> tickets = ticketArea
            ? tickets(actor, new TicketQuery(raw, List.of(), List.of(), List.of(), List.of(), List.of(), OVERVIEW_LIMIT, 0)) : null;
        Page<ProjectHit> projectHits = projectArea ? projects(actor, new ProjectQuery(raw, List.of(), OVERVIEW_LIMIT, 0)) : null;
        Page<WikiHit> wikiHits = wikiArea
            ? wiki(actor, new WikiQuery(raw, List.of(), null, preferred, false, false, OVERVIEW_LIMIT, 0)) : null;
        Page<RequirementHit> requirementHits = requirementArea
            ? requirements(actor, new RequirementQuery(raw, List.of(), false, List.of(), List.of(), List.of(), null, List.of(), OVERVIEW_LIMIT, 0))
            : null;
        return new Overview(tickets, projectHits, wikiHits, requirementHits, direct(raw, text, tickets, projectHits, requirementHits));
    }

    private static Direct direct(String raw, SearchText text, Page<TicketHit> tickets, Page<ProjectHit> projectHits,
            Page<RequirementHit> requirementHits) {
        if (text.key() != null && tickets != null) {
            for (TicketHit hit : tickets.items()) {
                if (hit.projectKey().equals(text.key()) && hit.number() == text.number()) {
                    return new Direct("ticket", hit.id(), hit.projectId(), hit.projectKey() + "-" + hit.number(), hit.number());
                }
            }
        }
        String[] requirement = requirementKey(raw);
        if (requirement != null && requirement[0] != null && requirementHits != null) {
            for (RequirementHit hit : requirementHits.items()) {
                if (hit.projectKey().equals(requirement[0]) && hit.number() == Long.parseLong(requirement[1])) {
                    return new Direct("requirement", hit.id(), hit.projectId(), hit.projectKey() + " " + hit.key(), hit.number());
                }
            }
        }
        String key = projectKey(raw);
        if (key != null && projectHits != null) {
            for (ProjectHit hit : projectHits.items()) {
                if (hit.key().equals(key)) {
                    return new Direct("project", hit.id(), hit.id(), hit.key(), null);
                }
            }
        }
        return null;
    }

    /** The text as a project key in upper case, when it has the form of one; {@code null} otherwise. */
    private static String projectKey(String raw) {
        if (raw == null) {
            return null;
        }
        String text = Nfkc.of(raw).strip().toUpperCase(java.util.Locale.ROOT);
        return PROJECT_KEY.matcher(text).matches() ? text : null;
    }


    private static SearchText text(String raw) {
        SearchText text = SearchText.parse(raw);
        if (text == null) {
            throw invalid("q.invalid");
        }
        return text;
    }

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    /** Portal accounts search nothing until the portal decides what they may find. */
    private static void internal(AccessContext actor) {
        if (actor.external()) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
    }

    private static ApiProblemException invalid(String violation) {
        return ApiProblemException.withViolations(400, "request.invalid", List.of(violation));
    }
}
