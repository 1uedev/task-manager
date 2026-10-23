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

    SearchService(JdbcClient jdbc, SearchRepository search, ProjectAccess projects, Permissions permissions) {
        this.jdbc = jdbc;
        this.search = search;
        this.projects = projects;
        this.permissions = permissions;
    }

    /** A required custom value: the ticket holds exactly this canonical text in the custom field with this key (TASK-044). */
    public record CustomFilter(String key, String value) {
    }

    public record TicketQuery(String text, List<UUID> projects, List<String> statuses, List<String> types, List<UUID> assignees,
            List<UUID> tags, List<CustomFilter> custom, int limit, int offset) {
        public TicketQuery(String text, List<UUID> projects, List<String> statuses, List<String> types, List<UUID> assignees,
                List<UUID> tags, int limit, int offset) {
            this(text, projects, statuses, types, assignees, tags, List.of(), limit, offset);
        }
    }

    public record ProjectQuery(String text, List<UUID> tags, int limit, int offset) {
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
                || (!query.tags().isEmpty() && !fields.contains(TicketAccess.TAGS_FIELD))
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
        for (UUID tag : query.tags()) {
            where.append(" and exists (select 1 from search_ticket_tag a where a.ticket_id = s.ticket_id and a.tag_id = ?)");
            local.add(tag);
        }
        for (CustomFilter filter : query.custom()) {
            where.append(" and exists (select 1 from search_ticket_custom c where c.ticket_id = s.ticket_id and c.field_key = ? and c.value = ?)");
            local.add(filter.key());
            local.add(filter.value());
        }
        selects.add("select s.ticket_id as id, s.project_id, p.key as project_key, s.number, " + rank + " as rank "
            + "from search_ticket s join search_project p on p.project_id = s.project_id where " + where);
        params.addAll(local);
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
            params.add(text.tsquery());
        }
        params.add(actor.companyId());
        if (!companyWide) {
            where.append(" and s.project_id in (").append(placeholders(visible.size())).append(")");
            params.addAll(visible);
        }
        if (text.present()) {
            where.append(" and s.document @@ to_tsquery('simple', ?)");
            params.add(text.tsquery());
        }
        for (UUID tag : query.tags()) {
            where.append(" and exists (select 1 from search_project_tag a where a.project_id = s.project_id and a.tag_id = ?)");
            params.add(tag);
        }
        String select = "select s.project_id, s.key, s.name, s.description, " + rank + " as rank from search_project s where " + where;
        List<Object> countParams = text.present() ? params.subList(1, params.size()) : params;
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
