package taskmanager.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.AdministrationFixture;
import taskmanager.TestDatabase;
import taskmanager.identity.account.AccountService;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.CompanyContext;
import taskmanager.search.internal.SearchProjection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-102: wiki pages in the search through the real HTTP interface with the real permissions, projects and wiki modules and
 * a real PostgreSQL: published revisions for readers and drafts only on request with a draft right (T11), language versions as
 * one hit in the preferred language, archived pages only for who publishes, no hits, totals or projects without a wiki action,
 * the visible text without images and texts of links to wiki pages, the path above a page, the excerpt with its marks, the
 * page found right after its publication (T13) and the same result after a rebuild of the projection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WikiSearchApiTest {
    private static final String DATABASE = freshDatabase();
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static String freshDatabase() {
        try {
            return TestDatabase.createEmptyDatabase();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE);
        registry.add("spring.datasource.username", TestDatabase::username);
        registry.add("spring.datasource.password", TestDatabase::password);
    }

    @Value("${local.server.port}") int port;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyContext companies;
    @Autowired PlatformTransactionManager transactions;
    @Autowired SearchProjection projection;

    // ---------------------------------------------------------------- a minimal browser

    record Response(int status, String body, String etag) {
        JsonNode json() {
            return JSON.readTree(body);
        }

        String code() {
            return json().get("code").asString();
        }
    }

    final class Browser {
        private final HttpClient http = HttpClient.newHttpClient();
        final Map<String, String> cookies = new LinkedHashMap<>();
        UUID id;

        Response send(String method, String path, Object body, String ifMatch) {
            try {
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
                if (body != null) {
                    request.header("Content-Type", "application/json");
                }
                request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body)));
                if (ifMatch != null) {
                    request.header("If-Match", ifMatch);
                }
                if (!cookies.isEmpty()) {
                    List<String> pairs = new ArrayList<>();
                    cookies.forEach((name, value) -> pairs.add(name + "=" + value));
                    request.header("Cookie", String.join("; ", pairs));
                }
                String csrf = cookies.get("__Host-tm-csrf");
                if (csrf != null) {
                    request.header("X-CSRF-Token", csrf);
                }
                HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
                for (String cookie : response.headers().allValues("set-cookie")) {
                    String pair = cookie.substring(0, cookie.indexOf(';') < 0 ? cookie.length() : cookie.indexOf(';'));
                    String name = pair.substring(0, pair.indexOf('='));
                    if (cookie.toLowerCase().contains("max-age=0")) {
                        cookies.remove(name);
                    } else {
                        cookies.put(name, pair.substring(pair.indexOf('=') + 1));
                    }
                }
                return new Response(response.statusCode(), response.body(), response.headers().firstValue("ETag").orElse(null));
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        Response get(String path) {
            return send("GET", path, null, null);
        }

    }

    private Browser person(AdministrationRight... rights) {
        var issued = accounts.create("test", "wikisearch-" + COUNTER.incrementAndGet(), null);
        if (rights.length > 0) {
            AdministrationFixture.grant(jdbc, companies, transactions, issued.account().id(), rights);
        }
        Browser browser = new Browser();
        assertEquals(200, browser.get("/api/v1/session").status());
        Response login = browser.send("POST", "/api/v1/session",
            Map.of("username", issued.account().username(), "password", issued.oneTimePassword()), null);
        assertEquals(200, login.status(), login.body());
        Response changed = browser.send("POST", "/api/v1/session/password",
            Map.of("currentPassword", issued.oneTimePassword(), "newPassword", STRONG), null);
        assertEquals(200, changed.status(), changed.body());
        browser.id = issued.account().id();
        return browser;
    }

    /** Grants actions and ticket field rules in a group of its own for one project; returns the group. */
    private UUID grant(UUID account, UUID project, String... rules) {
        UUID group = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            UUID companyId = companies.find().orElseGet(companies::create);
            jdbc.update("insert into permissions_revision(company_id) values (?) on conflict do nothing", companyId);
            jdbc.update("insert into permissions_group(company_id, id, name) values (?, ?, ?)", companyId, group, "fixture-" + group);
            for (String rule : rules) {
                if (!rule.contains(":")) {
                    jdbc.update("insert into permissions_action(company_id, group_id, project_id, action_key) values (?, ?, ?, ?)",
                        companyId, group, project, rule);
                    continue;
                }
                String[] parts = rule.split(":");
                jdbc.update("""
                        insert into permissions_field(company_id, group_id, project_id, resource_type, field_key, operation, effect)
                        values (?, ?, ?, 'ticket', ?, ?, 'ALLOW')
                        """, companyId, group, project, parts[1], parts[0]);
            }
            jdbc.update("insert into permissions_membership(company_id, group_id, user_id) values (?, ?, ?)", companyId, group, account);
            jdbc.update("update permissions_revision set revision = revision + 1 where company_id = ?", companyId);
        });
        return group;
    }

    private void revoke(UUID group) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("delete from permissions_membership where group_id = ?", group);
            jdbc.update("update permissions_revision set revision = revision + 1");
        });
    }

    // ---------------------------------------------------------------- fixtures

    private record Setup(UUID project, Browser admin) {
    }

    private Setup project() {
        int n = COUNTER.incrementAndGet();
        Browser admin = person(AdministrationRight.PROJECTS_MANAGE);
        Response created = admin.send("POST", "/api/v1/projects", Map.of("key", "WS" + n, "name", "Wiki " + n), null);
        assertEquals(201, created.status(), created.body());
        return new Setup(UUID.fromString(created.json().get("id").asString()), admin);
    }

    private Browser member(Setup setup, String... rules) {
        Browser browser = person();
        add(setup, browser.id);
        if (rules.length > 0) {
            grant(browser.id, setup.project(), rules);
        }
        return browser;
    }

    private void add(Setup setup, UUID account) {
        Response current = setup.admin().get("/api/v1/projects/" + setup.project());
        assertEquals(200, current.status(), current.body());
        Response added = setup.admin().send("PUT", "/api/v1/projects/" + setup.project() + "/members/" + account, null, current.etag());
        assertEquals(200, added.status(), added.body());
    }

    private static void assertInvalid(Response response, String violation) {
        assertEquals(400, response.status(), response.body());
        List<String> violations = new ArrayList<>();
        response.json().get("violations").forEach(v -> violations.add(v.asString()));
        assertTrue(violations.contains(violation), response.body());
    }

    // ---------------------------------------------------------------- documents

    private static Map<String, Object> node(String type, Object... content) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", type);
        if (content.length > 0) {
            node.put("content", List.of(content));
        }
        return node;
    }

    private static Map<String, Object> text(String value, Object... marks) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "text");
        node.put("text", value);
        if (marks.length > 0) {
            node.put("marks", List.of(marks));
        }
        return node;
    }

    private static Map<String, Object> mark(String type) {
        return Map.of("type", type);
    }

    private static Map<String, Object> link(String href) {
        return Map.of("type", "link", "attrs", Map.of("href", href, "target", "_blank", "rel", "noopener"));
    }

    private static Map<String, Object> withAttrs(Map<String, Object> node, Map<String, Object> attrs) {
        Map<String, Object> copy = new LinkedHashMap<>(node);
        copy.put("attrs", attrs);
        return copy;
    }

    private static Map<String, Object> doc(Object... blocks) {
        return node("doc", blocks);
    }

    private static Map<String, Object> paragraph(String value) {
        return node("paragraph", text(value));
    }

    private Response create(Browser who, Setup setup, String title, UUID parent, String language, Object content) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("parentId", parent);
        body.put("content", content);
        body.put("language", language);
        Response created = who.send("POST", "/api/v1/wiki/projects/" + setup.project() + "/pages", body, null);
        assertEquals(201, created.status(), created.body());
        return created;
    }

    private static UUID id(Response page) {
        return UUID.fromString(page.json().get("id").asString());
    }

    private Response save(Browser who, UUID page, String language, int basedOn, String title, Object content) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("content", content);
        Response saved = who.send("PUT", "/api/v1/wiki/pages/" + page + "/draft?language=" + language, body, "\"" + basedOn + "\"");
        assertEquals(200, saved.status(), saved.body());
        return saved;
    }

    private void publish(Browser who, UUID page, String language, int revision) {
        Response published = who.send("POST", "/api/v1/wiki/pages/" + page + "/publish?language=" + language, Map.of("revision", revision),
            "\"" + revision + "\"");
        assertEquals(200, published.status(), published.body());
    }

    /** A word of letters only that no other test uses. */
    private static String word(String stem) {
        int n = COUNTER.incrementAndGet();
        StringBuilder letters = new StringBuilder();
        for (int rest = n; rest > 0; rest /= 26) {
            letters.append((char) ('a' + rest % 26));
        }
        return stem + "q" + letters;
    }

    private Response search(Browser who, String q, String... parameters) {
        StringBuilder path = new StringBuilder("/api/v1/search/wiki?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
        for (String parameter : parameters) {
            path.append('&').append(parameter);
        }
        return who.get(path.toString());
    }

    private static List<String> titles(Response found) {
        List<String> titles = new ArrayList<>();
        found.json().get("items").forEach(hit -> titles.add(hit.get("title").asString()));
        return titles;
    }

    private static long total(Response found) {
        assertEquals(200, found.status(), found.body());
        return found.json().get("total").asLong();
    }

    // ---------------------------------------------------------------- tests

    /** T11: readers find the published revision only; a word of the draft needs drafts=true and a draft right. */
    @Test
    void draftsAreSearchedOnlyOnRequestWithADraftRight() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit");
        Browser publisher = member(setup, "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        String published = word("druckerwarteschlange");
        String drafted = word("zauberwort");
        UUID page = id(create(author, setup, "Drucker einrichten", null, "de", doc(paragraph("Die " + published + " leeren und neu starten."))));
        assertEquals(0, total(search(reader, published)), "an unpublished page is found by nobody without drafts");
        assertEquals(0, total(search(author, published)));
        assertEquals(1, total(search(author, published, "drafts=true")));

        publish(publisher, page, "de", 1);
        Response found = search(reader, published.substring(0, published.length() - 3));
        assertEquals(1, total(found), "T13: found right after the publication, also by a prefix");
        JsonNode hit = found.json().get("items").get(0);
        assertEquals("Drucker einrichten", hit.get("title").asString());
        assertEquals("published", hit.get("stage").asString());
        assertEquals("de", hit.get("language").asString());
        assertEquals(1, hit.get("revision").asInt());
        assertFalse(hit.get("publishedAt").isNull());
        assertEquals(setup.project().toString(), hit.get("projectId").asString());
        JsonNode snippet = hit.get("snippet");
        String excerpt = snippet.get("text").asString();
        JsonNode mark = snippet.get("marks").get(0);
        assertEquals(published, excerpt.substring(mark.get("start").asInt(), mark.get("end").asInt()));

        save(author, page, "de", 1, "Drucker einrichten", doc(paragraph("Mit dem " + drafted + " geht es schneller.")));
        assertEquals(0, total(search(reader, drafted)), "T11: a word of the draft finds nothing for readers");
        assertEquals(0, total(search(author, drafted)), "without drafts=true the published revision is searched for editors too");
        assertEquals(1, total(search(reader, published)), "the published revision stays findable");
        Response draft = search(author, drafted, "drafts=true");
        assertEquals(1, total(draft));
        assertEquals("draft", draft.json().get("items").get(0).get("stage").asString());
        assertEquals(2, draft.json().get("items").get(0).get("revision").asInt());
        assertTrue(draft.json().get("items").get(0).get("publishedAt").isNull());
        assertEquals(1, total(search(publisher, drafted, "drafts=true")), "wiki.publish reads drafts as well");
        assertEquals(0, total(search(author, published, "drafts=true")), "never mixed: the draft no longer holds the old word");
        assertEquals(403, search(reader, drafted, "drafts=true").status());
        assertEquals(403, search(reader, drafted, "archived=true").status());
        assertEquals(403, search(author, drafted, "archived=true").status(), "the archive belongs to wiki.publish");

        publish(publisher, page, "de", 2);
        assertEquals(1, total(search(reader, drafted)));
        assertEquals(0, total(search(reader, published)));
    }

    /** Both language versions are searched; a page is one hit, shown in the preferred language when both match. */
    @Test
    void languageVersionsAreOneHitInThePreferredLanguage() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        String common = word("kalibrierung");
        String english = word("maintenance");
        UUID page = id(create(author, setup, "Wartung", null, "de", doc(paragraph("Regelmäßige " + common + " der Geräte."))));
        publish(author, page, "de", 1);
        Response added = author.send("POST", "/api/v1/wiki/pages/" + page + "/languages", Map.of("language", "en"), null);
        assertEquals(201, added.status(), added.body());
        save(author, page, "en", 1, "Maintenance", doc(paragraph("Regular " + common + " and " + english + " of devices.")));
        assertEquals(0, total(search(reader, english)), "the English draft is not published");
        publish(author, page, "en", 2);

        Response german = search(reader, common, "preferred=de");
        assertEquals(1, total(german), "one hit per page");
        assertEquals("Wartung", german.json().get("items").get(0).get("title").asString());
        Response inEnglish = search(reader, common, "preferred=en");
        assertEquals(1, total(inEnglish));
        assertEquals("Maintenance", inEnglish.json().get("items").get(0).get("title").asString());
        assertEquals("en", inEnglish.json().get("items").get(0).get("language").asString());
        Response onlyEnglish = search(reader, english, "preferred=de");
        assertEquals(List.of("Maintenance"), titles(onlyEnglish), "the matching version is shown when only it matches");
        assertEquals(0, total(search(reader, english, "language=de")));
        assertEquals(1, total(search(reader, common, "language=en")));
        assertEquals("Maintenance", search(reader, common, "language=en", "preferred=de").json().get("items").get(0).get("title").asString());
        // Every version matches on its own: one word of each version finds nothing together.
        String german2 = word("handbuch");
        save(author, page, "de", 1, "Wartung", doc(paragraph("Regelmäßige " + common + " der Geräte. " + german2)));
        publish(author, page, "de", 2);
        assertEquals(1, total(search(reader, german2 + " " + common)));
        assertEquals(0, total(search(reader, german2 + " " + english)));
    }

    /** Rights first: no hits or totals without a wiki action, project filters only for own projects, archive for publishers. */
    @Test
    void rightsDecideAboutHitsTotalsAndTheArchive() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        Browser member = member(setup, "ticket.read");
        Browser outsider = person();
        String secret = word("geheimnis");
        UUID page = id(create(author, setup, "Interna", null, "de", doc(paragraph(secret))));
        publish(author, page, "de", 1);
        assertEquals(1, total(search(reader, secret)));
        assertEquals(0, total(search(member, secret)), "membership without a wiki action finds nothing");
        assertEquals(0, total(search(outsider, secret)));
        assertEquals(400, search(member, secret, "project=" + setup.project()).status());
        assertEquals("project.unknown", search(outsider, secret, "project=" + setup.project()).json().get("violations").get(0).asString());
        assertEquals(1, total(search(reader, secret, "project=" + setup.project())));

        Response archived = author.send("POST", "/api/v1/wiki/pages/" + page + "/archive",
            Map.of("version", author.get("/api/v1/wiki/pages/" + page).json().get("version").asLong()), null);
        assertEquals(200, archived.status(), archived.body());
        assertEquals(0, total(search(reader, secret)), "archived pages are gone for readers");
        assertEquals(0, total(search(author, secret)), "and for publishers without the filter");
        Response withArchive = search(author, secret, "archived=true");
        assertEquals(1, total(withArchive));
        assertTrue(withArchive.json().get("items").get(0).get("archived").asBoolean());
        assertEquals(403, search(reader, secret, "archived=true").status());

        assertEquals(400, search(reader, secret, "language=fr").status());
        assertEquals(400, search(reader, secret, "drafts=yes").status());
        assertEquals(400, search(reader, secret, "preferred=xx").status());
        assertEquals(400, search(reader, "x".repeat(201)).status());
    }

    /** The visible text: external link texts count, images and texts of links to wiki pages do not; the path above a page. */
    @Test
    void theVisibleTextAndThePathAreSearchedAndShown() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        String hidden = word("verborgenseite");
        String external = word("hilfeportal");
        String table = word("tabellenwert");
        String quote = word("zitatwort");
        UUID parent = id(create(author, setup, "Oberseite", null, "de", doc(paragraph("Oben"))));
        publish(author, parent, "de", 1);
        UUID secret = id(create(author, setup, "Unveröffentlicht", null, "de", doc(paragraph("Nichts"))));
        Map<String, Object> content = doc(
            node("paragraph", text(hidden, link("wiki:" + secret)), text(" und "), text(external, link("https://example.org"))),
            node("table", node("tableRow", node("tableCell", paragraph(table)))),
            node("blockquote", paragraph(quote)));
        UUID child = id(create(author, setup, "Unterseite", parent, "de", content));
        publish(author, child, "de", 1);

        assertEquals(0, total(search(reader, hidden)), "the text of a link to a wiki page is not searched");
        assertEquals(0, total(search(author, hidden, "drafts=true")));
        assertEquals(1, total(search(reader, external)));
        assertEquals(1, total(search(reader, table)));
        assertEquals(1, total(search(reader, quote)));
        JsonNode hit = search(reader, quote).json().get("items").get(0);
        assertEquals(1, hit.get("path").size());
        assertEquals("Oberseite", hit.get("path").get(0).get("title").asString());
        assertEquals(parent.toString(), hit.get("path").get(0).get("id").asString());
        assertTrue(hit.get("snippet").get("text").asString().contains(quote));
        assertFalse(hit.get("snippet").get("text").asString().contains("<"), "the excerpt is plain text");

        // The title counts: a word of the title ranks above the same word in the text.
        String ranked = word("rangwort");
        UUID inText = id(create(author, setup, "Andere Seite", null, "de", doc(paragraph(ranked))));
        UUID inTitle = id(create(author, setup, "Seite " + ranked, null, "de", doc(paragraph("Text"))));
        publish(author, inText, "de", 1);
        publish(author, inTitle, "de", 1);
        Response both = search(reader, ranked);
        assertEquals(List.of("Seite " + ranked, "Andere Seite"), titles(both));
        JsonNode titleMark = both.json().get("items").get(0).get("titleMarks").get(0);
        assertEquals(6, titleMark.get("start").asInt());

        // A rebuild of the projection gives the same answers.
        String before = search(reader, quote).json().get("items").toString();
        projection.rebuild(companies.find().orElseThrow());
        assertEquals(before, search(reader, quote).json().get("items").toString());
        assertEquals(0, total(search(reader, hidden)));
    }

    /** Without a text every readable page is listed; paging keeps the order. */
    @Test
    void withoutTextAllReadablePagesOfTheProjectAreListedInOrder() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        for (String title : List.of("Charlie", "alpha", "Bravo")) {
            UUID page = id(create(author, setup, title, null, "de", doc(paragraph(title))));
            publish(author, page, "de", 1);
        }
        create(author, setup, "Delta", null, "de", doc(paragraph("Entwurf")));
        Response all = search(reader, "", "project=" + setup.project());
        assertEquals(List.of("alpha", "Bravo", "Charlie"), titles(all));
        assertEquals(List.of("Bravo"), titles(search(reader, "", "project=" + setup.project(), "limit=1", "offset=1")));
        assertEquals(4, total(search(author, "", "project=" + setup.project(), "drafts=true")));
    }

    // ---------------------------------------------------------------- after the review (docs/REVIEW_TASK-102_PR-299.md)

    /**
     * F2: every level above a hit stays; in the published stage a parent shows its published title and an unpublished parent
     * is "not available" without identifier or title, in the draft stage the draft titles show.
     */
    @Test
    void thePathKeepsEveryLevelInTheStageSearched() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        String found = word("pfadwort");
        UUID top = id(create(author, setup, "Spitze", null, "de", doc(paragraph("Oben"))));
        publish(author, top, "de", 1);
        save(author, top, "de", 1, "Spitze neu", doc(paragraph("Oben")));
        UUID hidden = id(create(author, setup, "Geheime Mitte", top, "de", doc(paragraph("Mitte"))));
        UUID child = id(create(author, setup, "Blatt", hidden, "de", doc(paragraph(found))));
        publish(author, child, "de", 1);

        for (Browser who : List.of(reader, author)) {
            JsonNode path = search(who, found).json().get("items").get(0).get("path");
            assertEquals(2, path.size(), path.toString());
            assertEquals("Spitze", path.get(0).get("title").asString(), "the published title, not the newer draft title");
            assertEquals(top.toString(), path.get(0).get("id").asString());
            assertTrue(path.get(0).get("available").asBoolean());
            assertFalse(path.get(1).get("available").asBoolean());
            assertTrue(path.get(1).get("id").isNull());
            assertTrue(path.get(1).get("title").isNull());
            assertFalse(path.toString().contains("Geheime"), path.toString());
            assertFalse(path.toString().contains(hidden.toString()), path.toString());
        }
        JsonNode drafts = search(author, found, "drafts=true").json().get("items").get(0).get("path");
        assertEquals("Spitze neu", drafts.get(0).get("title").asString());
        assertEquals("Geheime Mitte", drafts.get(1).get("title").asString());
        assertTrue(drafts.get(1).get("available").asBoolean());
    }

    /**
     * R2 of the follow up review: an archived parent is a placeholder and the path goes on to the root. The child is archived
     * first and keeps its parent, the parent is archived after it; the archive hit of the child shows the readable root.
     */
    @Test
    void anArchivedParentKeepsThePathUpToTheRoot() {
        Setup setup = project();
        Browser publisher = member(setup, "wiki.edit", "wiki.publish");
        String found = word("archivpfad");
        UUID root = id(create(publisher, setup, "Wurzel", null, "de", doc(paragraph("Oben"))));
        publish(publisher, root, "de", 1);
        UUID parent = id(create(publisher, setup, "Eltern", root, "de", doc(paragraph("Mitte"))));
        publish(publisher, parent, "de", 1);
        UUID child = id(create(publisher, setup, "Kind", parent, "de", doc(paragraph(found))));
        publish(publisher, child, "de", 1);
        archive(publisher, child);
        archive(publisher, parent);

        for (String[] stage : List.of(new String[] {"archived=true"}, new String[] {"archived=true", "drafts=true"})) {
            Response result = search(publisher, found, stage);
            assertEquals(1, total(result), result.body());
            JsonNode path = result.json().get("items").get(0).get("path");
            assertEquals(2, path.size(), path.toString());
            assertEquals("Wurzel", path.get(0).get("title").asString());
            assertEquals(root.toString(), path.get(0).get("id").asString());
            assertFalse(path.get(1).get("available").asBoolean());
            assertTrue(path.get(1).get("id").isNull());
            assertTrue(path.get(1).get("title").isNull());
        }
    }

    private void archive(Browser who, UUID page) {
        long version = who.get("/api/v1/wiki/pages/" + page + "?language=de").json().get("version").asLong();
        Response archived = who.send("POST", "/api/v1/wiki/pages/" + page + "/archive?language=de", Map.of("version", version), null);
        assertEquals(200, archived.status(), archived.body());
    }

    /** H6: withdrawing, taking an old revision over, archiving and restoring reach the search in the same transaction. */
    @Test
    void everyChangeOfAPageReachesTheSearch() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        String first = word("ersterstand");
        String second = word("zweiterstand");
        UUID page = id(create(author, setup, "Wechsel", null, "de", doc(paragraph(first))));
        publish(author, page, "de", 1);
        save(author, page, "de", 1, "Wechsel", doc(paragraph(second)));
        publish(author, page, "de", 2);
        assertEquals(0, total(search(reader, first)));
        assertEquals(1, total(search(reader, second)));

        Response withdrawn = author.send("POST", "/api/v1/wiki/pages/" + page + "/withdraw?language=de", null, null);
        assertEquals(200, withdrawn.status(), withdrawn.body());
        assertEquals(0, total(search(reader, second)), "a withdrawn page is not found by readers");
        assertEquals(1, total(search(author, second, "drafts=true")));

        Response restored = author.send("POST", "/api/v1/wiki/pages/" + page + "/revisions/1/restore?language=de", null, "\"2\"");
        assertEquals(200, restored.status(), restored.body());
        assertEquals(1, total(search(author, first, "drafts=true")), "the old revision taken over is the new draft");
        assertEquals(0, total(search(author, second, "drafts=true")));
        publish(author, page, "de", 3);
        assertEquals(1, total(search(reader, first)));

        long version = author.get("/api/v1/wiki/pages/" + page + "?language=de").json().get("version").asLong();
        Response archived = author.send("POST", "/api/v1/wiki/pages/" + page + "/archive?language=de", Map.of("version", version), null);
        assertEquals(200, archived.status(), archived.body());
        assertEquals(0, total(search(reader, first)));
        assertEquals(1, total(search(author, first, "archived=true")));
        Response unarchived = author.send("POST", "/api/v1/wiki/pages/" + page + "/unarchive?language=de",
            Map.of("version", archived.json().get("version").asLong()), null);
        assertEquals(200, unarchived.status(), unarchived.body());
        assertEquals(1, total(search(reader, first)));
        JsonNode hit = search(reader, first).json().get("items").get(0);
        assertFalse(hit.get("archived").asBoolean());
        assertEquals(3, hit.get("revision").asInt());
    }

    /**
     * F4: row, revision, title and excerpt of a hit come from one snapshot. While a page is published again and again, every
     * hit shows the excerpt of exactly the revision it names.
     */
    @Test
    void aHitNeverMixesRevisionsWhileThePageIsPublished() throws Exception {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        String common = word("gleichzeitig");
        UUID page = id(create(author, setup, "Stand 1", null, "de", doc(paragraph(common + " inhalt1"))));
        publish(author, page, "de", 1);
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<Throwable> failed = new java.util.concurrent.atomic.AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                for (int revision = 2; revision <= 25; revision++) {
                    save(author, page, "de", revision - 1, "Stand " + revision, doc(paragraph(common + " inhalt" + revision)));
                    publish(author, page, "de", revision);
                }
            } catch (Throwable e) {
                failed.set(e);
            } finally {
                done.set(true);
            }
        });
        writer.start();
        int checked = 0;
        while (!done.get() || checked == 0) {
            Response found = search(reader, common);
            assertEquals(200, found.status(), found.body());
            JsonNode hit = found.json().get("items").get(0);
            int revision = hit.get("revision").asInt();
            assertEquals(1, found.json().get("total").asInt());
            assertEquals("Stand " + revision, hit.get("title").asString());
            assertEquals(common + " inhalt" + revision, hit.get("snippet").get("text").asString());
            checked++;
        }
        writer.join();
        if (failed.get() != null) {
            throw new AssertionError(failed.get());
        }
        assertTrue(checked > 0);
    }

    /** H2: words spread over title and text rank by where each one is found, a word in the title above the same in the text. */
    @Test
    void wordsSpreadOverTitleAndTextRankAboveTheTextAlone() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        String a = word("verteilta");
        String b = word("verteiltb");
        UUID textOnly = id(create(author, setup, "Nur Text", null, "de", doc(paragraph(a + " " + b))));
        UUID spread = id(create(author, setup, "Titel " + a, null, "de", doc(paragraph(b))));
        publish(author, textOnly, "de", 1);
        publish(author, spread, "de", 1);
        assertEquals(List.of("Titel " + a, "Nur Text"), titles(search(reader, a + " " + b)));
    }

    /** Past the last page the answer still names the total, without hits. */
    @Test
    void pastTheLastPageTheTotalStays() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        String common = word("seitenende");
        for (int n = 1; n <= 3; n++) {
            UUID page = id(create(author, setup, "Ende " + n, null, "de", doc(paragraph(common))));
            publish(author, page, "de", 1);
        }
        Response past = search(reader, common, "offset=20");
        assertEquals(3, total(past));
        assertEquals(0, past.json().get("items").size());
        Response second = search(reader, common, "limit=2", "offset=2");
        assertEquals(3, total(second));
        assertEquals(List.of("Ende 3"), titles(second));
    }

    /**
     * R4 of the follow up review: total and page come from one snapshot also past the last page. While a second page is
     * published and withdrawn again and again, the second page of one hit each holds a hit exactly when the total says two.
     */
    @Test
    void pastTheLastPageTheTotalComesFromTheSameSnapshot() throws Exception {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        String common = word("wechselende");
        UUID first = id(create(author, setup, "Erste", null, "de", doc(paragraph(common))));
        publish(author, first, "de", 1);
        UUID second = id(create(author, setup, "Zweite", null, "de", doc(paragraph(common))));
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<Throwable> failed = new java.util.concurrent.atomic.AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                for (int round = 0; round < 15; round++) {
                    publish(author, second, "de", 1);
                    Response withdrawn = author.send("POST", "/api/v1/wiki/pages/" + second + "/withdraw?language=de", null, null);
                    assertEquals(200, withdrawn.status(), withdrawn.body());
                }
            } catch (Throwable e) {
                failed.set(e);
            } finally {
                done.set(true);
            }
        });
        writer.start();
        int checked = 0;
        while (!done.get() || checked == 0) {
            Response found = search(reader, common, "limit=1", "offset=1");
            long total = total(found);
            assertTrue(total == 1 || total == 2, found.body());
            assertEquals(total == 2 ? 1 : 0, found.json().get("items").size(), found.body());
            checked++;
        }
        writer.join();
        if (failed.get() != null) {
            throw new AssertionError(failed.get());
        }
    }

    /** F3: a decomposed accent stays inside its word; the search for the composed form marks it in title and excerpt. */
    @Test
    void decomposedAccentsAreMarkedLikeTheIndexFindsThem() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        String stem = word("cafe");
        String decomposed = stem + "e\u0301x";
        String filler = "Wort ".repeat(80);
        UUID page = id(create(author, setup, "Titel " + decomposed, null, "de", doc(paragraph(filler + decomposed + " Ende"))));
        publish(author, page, "de", 1);
        Response found = search(reader, stem + "\u00e9x");
        assertEquals(1, total(found));
        JsonNode hit = found.json().get("items").get(0);
        assertEquals(1, hit.get("titleMarks").size());
        assertEquals(6, hit.get("titleMarks").get(0).get("start").asInt());
        assertEquals(6 + decomposed.length(), hit.get("titleMarks").get(0).get("end").asInt());
        JsonNode snippet = hit.get("snippet");
        assertTrue(snippet.get("cutStart").asBoolean(), "the excerpt starts near the word, not at the text start");
        String text = snippet.get("text").asString();
        int start = snippet.get("marks").get(0).get("start").asInt();
        assertEquals(decomposed, text.substring(start, snippet.get("marks").get(0).get("end").asInt()));
    }

    /** R3 of the follow up review: a compatibility symbol at the start of a word is marked in the title and leads the excerpt. */
    @Test
    void compatibilitySymbolsAreMarkedLikeTheIndexFindsThem() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        String stem = word("lpha");
        String symbol = "\u24B6" + stem;
        UUID page = id(create(author, setup, "Titel " + symbol, null, "de", doc(paragraph("Vorwort ".repeat(40) + symbol + " Ende"))));
        publish(author, page, "de", 1);
        Response found = search(reader, "a" + stem);
        assertEquals(1, total(found));
        JsonNode hit = found.json().get("items").get(0);
        assertEquals(1, hit.get("titleMarks").size(), hit.toString());
        assertEquals(6, hit.get("titleMarks").get(0).get("start").asInt());
        assertEquals(6 + symbol.length(), hit.get("titleMarks").get(0).get("end").asInt());
        JsonNode snippet = hit.get("snippet");
        assertTrue(snippet.get("cutStart").asBoolean(), "the excerpt starts near the word, not at the text start");
        String text = snippet.get("text").asString();
        JsonNode mark = snippet.get("marks").get(0);
        assertEquals(symbol, text.substring(mark.get("start").asInt(), mark.get("end").asInt()));
    }

    /**
     * N1 of the follow up review, the same cases as the browser stand in: the limits of the search text count code points after
     * NFKC, and a word after the limit of the indexed terms is not found.
     */
    @Test
    void theLimitsCountCodePointsAfterNormalizationAndTheIndexStops() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        // 26 ligatures are 52 letters after NFKC, more than 50 for one word.
        assertInvalid(search(reader, "\uFB01".repeat(26)), "q.invalid");
        // 150 emoji are 300 UTF-16 units but 150 code points: valid, and without words they find nothing.
        Response emoji = search(reader, "\uD83D\uDE00".repeat(150));
        assertEquals(0, total(emoji));
        String early = word("anfang");
        String late = word("spaet");
        UUID page = id(create(author, setup, "Lang", null, "de", doc(paragraph(early + " " + "fueller ".repeat(30_000) + late))));
        publish(author, page, "de", 1);
        assertEquals(1, total(search(reader, early)));
        assertEquals(0, total(search(reader, late)), "a word after the limit of the indexed terms is not found");
    }

    /**
     * N3 of the follow up review: the four reasons of the wiki search the contract names are answered with 400, and every level
     * of a real path is one of the two forms of the contract.
     */
    @Test
    void theDocumentedReasonsAreAnsweredAndThePathHasTheFormsOfTheContract() throws Exception {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        String contract = java.nio.file.Files.readString(java.nio.file.Path.of("..", "contracts", "search.openapi.yaml"));
        Map<String, String> reasons = new LinkedHashMap<>();
        reasons.put("language=fr", "language.invalid");
        reasons.put("preferred=fr", "preferred.invalid");
        reasons.put("drafts=ja", "drafts.invalid");
        reasons.put("archived=ja", "archived.invalid");
        reasons.forEach((parameter, reason) -> {
            assertTrue(contract.contains("`" + reason + "`"), reason + " is named in the contract");
            assertInvalid(search(reader, "wort", parameter), reason);
        });

        String found = word("formwort");
        UUID top = id(create(author, setup, "Oben", null, "de", doc(paragraph("Oben"))));
        publish(author, top, "de", 1);
        UUID hidden = id(create(author, setup, "Mitte", top, "de", doc(paragraph("Mitte"))));
        UUID leaf = id(create(author, setup, "Blatt", hidden, "de", doc(paragraph(found))));
        publish(author, leaf, "de", 1);
        Response result = search(reader, found);
        assertEquals(200, result.status(), result.body());
        JsonNode path = result.json().get("items").get(0).get("path");
        assertEquals(2, path.size(), path.toString());
        for (JsonNode level : path) {
            if (level.get("available").asBoolean()) {
                assertTrue(level.get("id").isString() && level.get("title").isString(), level.toString());
            } else {
                assertTrue(level.get("id").isNull() && level.get("title").isNull(), level.toString());
            }
        }
        assertFalse(path.get(1).get("available").asBoolean(), "the unpublished parent is the placeholder");
    }

    /** H1: the text of a link to a wiki page is left out, but the words before and after it stay apart. */
    @Test
    void theWordBoundaryOfALeftOutLinkTextIsKept() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        String before = word("vorher");
        String after = word("nachher");
        UUID target = id(create(author, setup, "Ziel", null, "de", doc(paragraph("Ziel"))));
        UUID page = id(create(author, setup, "Verweis", null, "de",
            doc(node("paragraph", text(before), text("Ziel", link("wiki:" + target)), text(after)))));
        publish(author, page, "de", 1);
        assertEquals(1, total(search(reader, after)), "the word after a left out link text stays a word of its own");
        assertEquals(1, total(search(reader, before)));
    }
}
