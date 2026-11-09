package taskmanager.wiki;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-093: the wiki through the real HTTP interface with the real permissions, projects and files modules and a real
 * PostgreSQL: drafts that leave the publication alone (W03), the publication of exactly the revision shown (W04, W06), the
 * conflict of two saves on the same basis (W05), the example page kept through three saves (W02), refused scripts, foreign
 * elements and external images (W10), the three wiki actions with images and links (W09), the tree with its depth and cycles,
 * moving and renaming that keep links (W11), archiving, withdrawing, taking over an old revision and the history.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WikiApiTest {
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

        Response upload(String path, byte[] content) {
            try {
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/octet-stream").POST(HttpRequest.BodyPublishers.ofByteArray(content));
                List<String> pairs = new ArrayList<>();
                cookies.forEach((name, value) -> pairs.add(name + "=" + value));
                request.header("Cookie", String.join("; ", pairs));
                request.header("X-CSRF-Token", cookies.get("__Host-tm-csrf"));
                HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
                return new Response(response.statusCode(), response.body(), response.headers().firstValue("ETag").orElse(null));
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        /**
         * Announces an upload of {@code declaredLength} bytes and sends none of them, so the refusal of the declared length is
         * read without a client that is still writing a large body while the server already answers and closes.
         */
        Response announce(String path, long declaredLength) {
            try (Socket socket = new Socket("localhost", port)) {
                socket.setSoTimeout(10_000);
                List<String> pairs = new ArrayList<>();
                cookies.forEach((name, value) -> pairs.add(name + "=" + value));
                String head = "POST " + path + " HTTP/1.1\r\nHost: localhost:" + port + "\r\nContent-Type: application/octet-stream\r\n"
                    + "Content-Length: " + declaredLength + "\r\nCookie: " + String.join("; ", pairs) + "\r\nX-CSRF-Token: "
                    + cookies.get("__Host-tm-csrf") + "\r\nConnection: close\r\n\r\n";
                socket.getOutputStream().write(head.getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                int status = Integer.parseInt(reader.readLine().split(" ")[1]);
                boolean chunked = false;
                for (String line = reader.readLine(); line != null && !line.isEmpty(); line = reader.readLine()) {
                    chunked |= line.toLowerCase(Locale.ROOT).startsWith("transfer-encoding:") && line.toLowerCase(Locale.ROOT).contains("chunked");
                }
                StringBuilder body = new StringBuilder();
                if (chunked) {
                    for (String size = reader.readLine(); size != null && !size.isBlank(); size = reader.readLine()) {
                        int remaining = Integer.parseInt(size.trim(), 16);
                        if (remaining == 0) {
                            break;
                        }
                        char[] chunk = new char[remaining];
                        int read = 0;
                        while (read < remaining) {
                            int n = reader.read(chunk, read, remaining - read);
                            if (n < 0) {
                                break;
                            }
                            read += n;
                        }
                        body.append(chunk, 0, read);
                        reader.readLine();
                    }
                } else {
                    for (int c = reader.read(); c >= 0; c = reader.read()) {
                        body.append((char) c);
                    }
                }
                return new Response(status, body.toString(), null);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        HttpResponse<byte[]> raw(String path) {
            try {
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
                List<String> pairs = new ArrayList<>();
                cookies.forEach((name, value) -> pairs.add(name + "=" + value));
                request.header("Cookie", String.join("; ", pairs));
                return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private Browser person(AdministrationRight... rights) {
        var issued = accounts.create("test", "wiki-" + COUNTER.incrementAndGet(), null);
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
        Response created = admin.send("POST", "/api/v1/projects", Map.of("key", "WK" + n, "name", "Wiki " + n), null);
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

    private static final byte[] PNG = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52, 1, 2, 3 };

    /** The synthetic example page "Serviceanfrage ergänzen" of docs/WIKI_I18N_BRANDING.md with every element. */
    private static Map<String, Object> example(String imageId, String pageLink) {
        Map<String, Object> table = node("table",
            node("tableRow", withAttrs(node("tableHeader", paragraph("Angabe")), Map.of("colspan", 1, "rowspan", 1)),
                withAttrs(node("tableHeader", paragraph("Beispiel")), Map.of("colspan", 1, "rowspan", 1))),
            node("tableRow", node("tableCell", paragraph("Zeitpunkt")), node("tableCell", paragraph("15.10.2026, 09:30 Europe/Berlin"))),
            node("tableRow", node("tableCell", paragraph("Funktion")), node("tableCell", paragraph("Berichte"))));
        List<Object> blocks = new ArrayList<>(List.of(
            withAttrs(node("heading", text("Angaben zu einer Störung")), Map.of("level", 2)),
            node("paragraph", text("Keine Passwörter", mark("bold")), text(" in eine "), text("Supportanfrage", mark("italic")),
                text(" eintragen, "), text("DEMO-42", mark("code")), node("hardBreak"), text("Hilfe", link("https://example.org/hilfe"))),
            node("orderedList", node("listItem", paragraph("Anfrage öffnen.")), node("listItem", paragraph("Zeitpunkt ergänzen.")),
                node("listItem", paragraph("Antwort prüfen und senden."))),
            node("bulletList", node("listItem", paragraph("Punkt"))),
            table,
            node("blockquote", paragraph("Zitat")),
            withAttrs(node("codeBlock", text("Fehlercode: DEMO-42")), Map.of("language", "text")),
            node("horizontalRule")));
        if (imageId != null) {
            blocks.add(withAttrs(node("image"), Map.of("src", "/api/v1/wiki/images/" + imageId, "alt", "Formular zur Ergänzung einer Serviceanfrage")));
        }
        if (pageLink != null) {
            blocks.add(node("paragraph", text("Siehe Anleitung", link("wiki:" + pageLink))));
        }
        return doc(blocks.toArray());
    }

    private Response create(Browser who, Setup setup, String title, UUID parent, Object content) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("parentId", parent);
        body.put("content", content);
        return who.send("POST", "/api/v1/wiki/projects/" + setup.project() + "/pages", body, null);
    }

    private static UUID id(Response page) {
        return UUID.fromString(page.json().get("id").asString());
    }

    private Response save(Browser who, UUID page, String etag, String title, Object content) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("content", content);
        return who.send("PUT", "/api/v1/wiki/pages/" + page + "/draft", body, etag);
    }

    private Response publish(Browser who, UUID page, String etag, int revision) {
        return who.send("POST", "/api/v1/wiki/pages/" + page + "/publish", Map.of("revision", revision), etag);
    }

    private static int draft(Response page) {
        return page.json().get("draft").get("revision").asInt();
    }

    private static long version(Response page) {
        return page.json().get("version").asLong();
    }

    // ---------------------------------------------------------------- tests

    /** W03, W04, W06: a draft leaves the readers alone; only the publication of the current draft reaches them. */
    @Test
    void draftsStayInvisibleUntilTheShownRevisionIsPublished() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit");
        Browser editor = member(setup, "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        Response created = create(author, setup, "Anleitung", null, doc(paragraph("Erste Fassung")));
        assertEquals(201, created.status(), created.body());
        UUID page = id(created);
        assertEquals("\"1\"", created.etag());
        assertEquals(404, reader.get("/api/v1/wiki/pages/" + page).status(), "an unpublished page does not exist for readers");
        assertEquals(0, reader.get("/api/v1/wiki/projects/" + setup.project() + "/pages").json().get("pages").size());

        assertEquals(403, publish(author, page, "\"1\"", 1).status(), "wiki.edit does not publish");
        Response published = publish(editor, page, "\"1\"", 1);
        assertEquals(200, published.status(), published.body());
        Response read = reader.get("/api/v1/wiki/pages/" + page);
        assertEquals(200, read.status(), read.body());
        assertEquals("<p>Erste Fassung</p>", read.json().get("published").get("html").asString());
        assertTrue(read.json().get("draft").isNull(), "readers never see the draft");
        assertEquals(null, read.etag());

        Response saved = save(author, page, "\"1\"", "Anleitung neu", doc(paragraph("Zweite Fassung")));
        assertEquals(200, saved.status(), saved.body());
        assertEquals(2, draft(saved));
        assertTrue(saved.json().get("draft").get("changed").asBoolean());
        read = reader.get("/api/v1/wiki/pages/" + page);
        assertEquals("<p>Erste Fassung</p>", read.json().get("published").get("html").asString(), "W03: the saved draft changes nothing for readers");
        assertEquals("Anleitung", read.json().get("published").get("title").asString(), "a new title waits for the publication as well");
        JsonNode tree = reader.get("/api/v1/wiki/projects/" + setup.project() + "/pages").json().get("pages");
        assertEquals("Anleitung", tree.get(0).get("title").asString());
        // F5 of the Codex review, rule 3 as extended by Holger: wiki.publish alone sees the draft it is about to publish.
        Response forPublisher = editor.get("/api/v1/wiki/pages/" + page);
        assertEquals(2, forPublisher.json().get("draft").get("revision").asInt());
        assertEquals("\"2\"", forPublisher.etag());
        assertEquals(200, editor.get("/api/v1/wiki/pages/" + page + "/history").status());

        assertEquals(412, publish(editor, page, "\"1\"", 1).status(), "W06: the revision shown before the save is no longer the draft");
        assertEquals(412, publish(editor, page, "\"2\"", 1).status(), "only the current draft is published");
        assertEquals(428, editor.send("POST", "/api/v1/wiki/pages/" + page + "/publish", Map.of("revision", 2), null).status());
        Response second = publish(editor, page, "\"2\"", 2);
        assertEquals(200, second.status(), second.body());
        read = reader.get("/api/v1/wiki/pages/" + page);
        assertEquals("<p>Zweite Fassung</p>", read.json().get("published").get("html").asString());
        assertEquals("Anleitung neu", read.json().get("published").get("title").asString());
        assertEquals(editor.id.toString(), read.json().get("published").get("publishedBy").asString());

        Response withdrawn = editor.send("POST", "/api/v1/wiki/pages/" + page + "/withdraw", null, null);
        assertEquals(200, withdrawn.status(), withdrawn.body());
        assertEquals(404, reader.get("/api/v1/wiki/pages/" + page).status(), "a withdrawn page is gone for readers");
        Response again = editor.send("POST", "/api/v1/wiki/pages/" + page + "/withdraw", null, null);
        assertEquals(409, again.status(), again.body());
        assertEquals("wiki.not_published", again.code());

        JsonNode history = author.get("/api/v1/wiki/pages/" + page + "/history").json();
        List<String> kinds = new ArrayList<>();
        history.get("events").forEach(event -> kinds.add(event.get("kind").asString()));
        assertEquals(List.of("withdrawn", "published", "saved", "published", "created"), kinds);
        assertEquals(2, history.get("revisions").size());
        assertEquals(404, reader.get("/api/v1/wiki/pages/" + page + "/history").status(), "readers have no history");
    }

    /** W05: two saves on the same basis; the second is refused and nothing is overwritten. */
    @Test
    void aSecondSaveOnTheSameBasisIsRefused() {
        Setup setup = project();
        Browser anna = member(setup, "wiki.edit");
        Browser ben = member(setup, "wiki.edit");
        UUID page = id(create(anna, setup, "Gemeinsam", null, doc(paragraph("Basis"))));
        assertEquals(200, save(anna, page, "\"1\"", "Gemeinsam", doc(paragraph("Von Anna"))).status());
        Response late = save(ben, page, "\"1\"", "Gemeinsam", doc(paragraph("Von Ben")));
        assertEquals(412, late.status(), late.body());
        assertEquals("wiki.version_conflict", late.code());
        Response current = ben.get("/api/v1/wiki/pages/" + page);
        assertEquals("<p>Von Anna</p>", current.json().get("draft").get("html").asString());
        assertEquals("\"2\"", current.etag());
        assertEquals(428, ben.send("PUT", "/api/v1/wiki/pages/" + page + "/draft", Map.of("title", "x"), null).status());
        Response merged = save(ben, page, current.etag(), "Gemeinsam", doc(paragraph("Von Anna"), paragraph("Von Ben")));
        assertEquals(200, merged.status(), merged.body());
        assertEquals(3, draft(merged));
        assertEquals(ben.id.toString(), merged.json().get("draft").get("savedBy").asString());

        // Many saves at once on the same basis: exactly one wins.
        List<CompletableFuture<Integer>> runs = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 6; i++) {
            int n = i;
            Browser who = n % 2 == 0 ? anna : ben;
            runs.add(CompletableFuture.supplyAsync(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return save(who, page, "\"3\"", "Gemeinsam", doc(paragraph("Lauf " + n))).status();
            }));
        }
        start.countDown();
        List<Integer> statuses = runs.stream().map(CompletableFuture::join).toList();
        assertEquals(1, statuses.stream().filter(status -> status == 200).count(), statuses.toString());
        assertTrue(statuses.stream().allMatch(status -> status == 200 || status == 412 || status == 503), statuses.toString());
        assertEquals(4, anna.get("/api/v1/wiki/pages/" + page + "/history").json().get("draftRevision").asInt());
    }

    /** W02: the example page with every element keeps its structure through three saves; the reading view escapes text. */
    @Test
    void theExamplePageKeepsItsStructure() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        UUID target = id(create(author, setup, "Ziel", null, doc(paragraph("Ziel"))));
        UUID page = id(create(author, setup, "Serviceanfrage ergänzen", null, doc(paragraph("Start"))));
        Response image = author.upload("/api/v1/wiki/pages/" + page + "/images", PNG);
        assertEquals(201, image.status(), image.body());
        assertEquals("image/png", image.json().get("mediaType").asString());
        String imageId = image.json().get("id").asString();
        Map<String, Object> content = example(imageId, target.toString());
        Response saved = save(author, page, "\"1\"", "Serviceanfrage ergänzen", content);
        assertEquals(200, saved.status(), saved.body());
        JsonNode first = saved.json().get("draft").get("content");
        String etag = saved.etag();
        for (int round = 0; round < 3; round++) {
            Response again = save(author, page, etag, "Serviceanfrage ergänzen", JSON.convertValue(first, Map.class));
            assertEquals(200, again.status(), again.body());
            assertEquals(first, again.json().get("draft").get("content"), "round " + round);
            etag = again.etag();
        }
        String html = saved.json().get("draft").get("html").asString();
        assertTrue(html.startsWith("<h2>Angaben zu einer Störung</h2><p><strong>Keine Passwörter</strong> in eine <em>Supportanfrage</em>"), html);
        assertTrue(html.contains("<code>DEMO-42</code><br><a href=\"https://example.org/hilfe\" rel=\"noopener noreferrer nofollow\" target=\"_blank\">Hilfe</a>"), html);
        assertTrue(html.contains("<ol><li><p>Anfrage öffnen.</p></li>"), html);
        assertTrue(html.contains("<table><tbody><tr><th><p>Angabe</p></th>"), html);
        assertTrue(html.contains("<pre><code class=\"language-text\">Fehlercode: DEMO-42</code></pre><hr>"), html);
        assertTrue(html.contains("<img src=\"/api/v1/wiki/images/" + imageId + "\" alt=\"Formular zur Ergänzung einer Serviceanfrage\" loading=\"lazy\">"), html);
        assertTrue(html.contains("<a href=\"#/wiki/" + setup.project() + "/" + target + "\" data-wiki-page=\"" + target + "\">Siehe Anleitung</a>"), html);

        Response escaped = save(author, page, etag, "<b>Titel</b>", doc(paragraph("<script>alert(1)</script> & \"x\"")));
        assertEquals(200, escaped.status(), escaped.body());
        assertEquals("<p>&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;x&quot;</p>", escaped.json().get("draft").get("html").asString());
        assertEquals("<b>Titel</b>", escaped.json().get("draft").get("title").asString(), "the title is text; the interface escapes it");
    }

    /** W10: scripts, foreign elements, other links and external images are refused; unknown attributes are dropped. */
    @Test
    void foreignContentIsRefused() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit");
        UUID page = id(create(author, setup, "Prüfung", null, null));
        assertInvalid(save(author, page, "\"1\"", "Prüfung", doc(node("script", text("x")))), "content.unsupported");
        assertInvalid(save(author, page, "\"1\"", "Prüfung", doc(node("iframe"))), "content.unsupported");
        assertInvalid(save(author, page, "\"1\"", "Prüfung", doc(node("paragraph", text("x", mark("strike"))))), "content.unsupported");
        assertInvalid(save(author, page, "\"1\"", "Prüfung", doc(node("paragraph", text("x", link("javascript:alert(1)"))))), "link.invalid");
        // H1 of the Codex review: control characters inside a link target are refused, before any normalization.
        for (char control : new char[] {'\u0000', '\u0001', '\u0008', '\u001B', '\u007F', '\u0085'}) {
            assertInvalid(save(author, page, "\"1\"", "Prüfung", doc(node("paragraph", text("x", link("https://example.test/a" + control + "b"))))),
                "link.invalid");
        }
        assertInvalid(save(author, page, "\"1\"", "Prüfung", doc(withAttrs(node("image"), Map.of("src", "https://example.org/x.png", "alt", "x")))),
            "image.external");
        assertInvalid(save(author, page, "\"1\"", "Prüfung", doc(withAttrs(node("image"), Map.of("src", "/api/v1/wiki/images/" + UUID.randomUUID(),
            "alt", "x")))), "image.unknown");
        assertInvalid(save(author, page, "\"1\"", "Prüfung", doc(withAttrs(node("heading", text("x")), Map.of("level", 4)))), "content.invalid");
        assertInvalid(save(author, page, "\"1\"", "Prüfung", Map.of("type", "html", "content", "<b>x</b>")), "content.invalid");
        assertInvalid(save(author, page, "\"1\"", "Prüfung", doc(node("paragraph", text("x", link("wiki:" + UUID.randomUUID()))))), "link.unknown_page");
        assertInvalid(save(author, page, "\"1\"", " ", doc(paragraph("x"))), "title.required");
        assertInvalid(save(author, page, "\"1\"", "x".repeat(201), doc(paragraph("x"))), "title.too_long");
        String big = "x".repeat(1024 * 1024);
        assertInvalid(save(author, page, "\"1\"", "Prüfung", doc(paragraph(big))), "content.too_large");

        Map<String, Object> decorated = withAttrs(node("paragraph", text("Text")), Map.of("textAlign", "center", "class", "evil", "onclick", "x"));
        Response kept = save(author, page, "\"1\"", "Prüfung", doc(decorated));
        assertEquals(200, kept.status(), kept.body());
        assertEquals("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Text\"}]}]}",
            kept.json().get("draft").get("content").toString(), "unknown attributes are dropped");

        Response text = author.upload("/api/v1/wiki/pages/" + page + "/images", "<svg onload=alert(1)></svg>".getBytes());
        assertEquals(415, text.status(), text.body());
        assertEquals("image.type_unsupported", text.code());
        assertEquals(400, author.upload("/api/v1/wiki/pages/" + page + "/images", new byte[0]).status());
        Response large = author.announce("/api/v1/wiki/pages/" + page + "/images", 5L * 1024 * 1024 + 1);
        assertEquals(413, large.status(), large.body());
        assertEquals("image.too_large", large.code());

        List<Object> many = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            Response image = author.upload("/api/v1/wiki/pages/" + page + "/images", PNG);
            assertEquals(201, image.status(), image.body());
            many.add(withAttrs(node("image"), Map.of("src", image.json().get("src").asString(), "alt", "Bild " + i)));
        }
        assertInvalid(save(author, page, kept.etag(), "Prüfung", doc(many.toArray())), "content.too_many_images");
        assertEquals(200, save(author, page, kept.etag(), "Prüfung", doc(many.subList(0, 50).toArray())).status());
    }

    /** W09: the three actions decide about pages, drafts, history, images and the titles behind links. */
    @Test
    void rightsDecideAboutPagesImagesAndLinks() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        Browser stranger = member(setup);
        UUID secret = id(create(author, setup, "Geheime Planung", null, doc(paragraph("intern"))));
        UUID page = id(create(author, setup, "Öffentlich", null, doc(paragraph("x"))));
        Response draftImage = author.upload("/api/v1/wiki/pages/" + page + "/images", PNG);
        String draftImageId = draftImage.json().get("id").asString();
        Response published = author.upload("/api/v1/wiki/pages/" + page + "/images", PNG);
        String imageId = published.json().get("id").asString();
        Response saved = save(author, page, "\"1\"", "Öffentlich", doc(
            withAttrs(node("image"), Map.of("src", "/api/v1/wiki/images/" + imageId, "alt", "Bild")),
            node("paragraph", text("Planung ansehen", link("wiki:" + secret)))));
        assertEquals(200, saved.status(), saved.body());
        assertEquals(200, publish(author, page, saved.etag(), 2).status());
        Response saved2 = save(author, page, saved.etag(), "Öffentlich", doc(
            withAttrs(node("image"), Map.of("src", "/api/v1/wiki/images/" + imageId, "alt", "Bild")),
            withAttrs(node("image"), Map.of("src", "/api/v1/wiki/images/" + draftImageId, "alt", "Entwurf"))));
        assertEquals(200, saved2.status(), saved2.body());

        String html = reader.get("/api/v1/wiki/pages/" + page).json().get("published").get("html").asString();
        assertTrue(html.contains("<span class=\"wiki-unavailable\" data-wiki-unavailable=\"true\"></span>"), html);
        assertFalse(html.contains("Planung"), "neither the title of the unpublished page nor the text of the link reaches the reader");
        String authorHtml = author.get("/api/v1/wiki/pages/" + page).json().get("published").get("html").asString();
        assertTrue(authorHtml.contains("data-wiki-page=\"" + secret + "\">Planung ansehen</a>"), authorHtml);

        HttpResponse<byte[]> image = reader.raw("/api/v1/wiki/images/" + imageId);
        assertEquals(200, image.statusCode());
        assertEquals("image/png", image.headers().firstValue("Content-Type").orElse(""));
        assertEquals("nosniff", image.headers().firstValue("X-Content-Type-Options").orElse(""));
        assertTrue(image.headers().firstValue("Content-Security-Policy").orElse("").contains("sandbox"));
        assertEquals(PNG.length, image.body().length);
        assertEquals(404, reader.raw("/api/v1/wiki/images/" + draftImageId).statusCode(), "an image only the draft shows stays with the editors");
        assertEquals(200, author.raw("/api/v1/wiki/images/" + draftImageId).statusCode());
        assertEquals(404, stranger.raw("/api/v1/wiki/images/" + imageId).statusCode());
        assertEquals(404, stranger.get("/api/v1/wiki/pages/" + page).status());
        assertEquals(404, stranger.get("/api/v1/wiki/projects/" + setup.project() + "/pages").status());
        assertEquals(0, stranger.get("/api/v1/wiki/projects").json().size());
        assertEquals(403, reader.upload("/api/v1/wiki/pages/" + page + "/images", PNG).status());
        assertEquals(403, save(reader, page, "\"3\"", "x", doc(paragraph("x"))).status());
        assertEquals(403, create(reader, setup, "Neu", null, null).status());

        // A revocation takes effect at once.
        Browser later = member(setup);
        UUID group = grant(later.id, setup.project(), "wiki.read");
        assertEquals(200, later.get("/api/v1/wiki/pages/" + page).status());
        revoke(group);
        assertEquals(404, later.get("/api/v1/wiki/pages/" + page).status());
        assertEquals(404, later.raw("/api/v1/wiki/images/" + imageId).statusCode());

        JsonNode projects = author.get("/api/v1/wiki/projects").json();
        assertEquals(1, projects.size());
        assertTrue(projects.get(0).get("access").get("publish").asBoolean());
    }

    /** Portal accounts never see the wiki, whatever actions they were given. */
    @Test
    void portalAccountsSeeNoWiki() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        UUID page = id(create(author, setup, "Seite", null, doc(paragraph("x"))));
        assertEquals(200, publish(author, page, "\"1\"", 1).status());
        Browser admin = person(AdministrationRight.ACCOUNTS_MANAGE, AdministrationRight.HELPDESK_MANAGE);
        Response customer = admin.send("POST", "/api/v1/helpdesk/customers", Map.of("name", "Kunde " + COUNTER.incrementAndGet(),
            "projects", List.of(setup.project())), null);
        assertEquals(201, customer.status(), customer.body());
        Map<String, Object> body = new HashMap<>();
        body.put("username", "wikikunde-" + COUNTER.incrementAndGet());
        body.put("displayName", "Kunde");
        body.put("organizationId", UUID.fromString(customer.json().get("id").asString()));
        body.put("contactId", null);
        body.put("projectIds", List.of(setup.project()));
        Response created = admin.send("POST", "/api/v1/portal-accounts", body, null);
        assertEquals(201, created.status(), created.body());
        UUID portalId = UUID.fromString(created.json().get("account").get("id").asString());
        grant(portalId, setup.project(), "wiki.read", "wiki.edit", "wiki.publish");
        Browser portal = new Browser();
        assertEquals(200, portal.get("/api/v1/session").status());
        assertEquals(200, portal.send("POST", "/api/v1/session", Map.of("username", created.json().get("account").get("username").asString(),
            "password", created.json().get("oneTimePassword").asString()), null).status());
        assertEquals(200, portal.send("POST", "/api/v1/session/password", Map.of("currentPassword", created.json().get("oneTimePassword").asString(),
            "newPassword", STRONG), null).status());
        assertEquals(0, portal.get("/api/v1/wiki/projects").json().size());
        assertEquals(404, portal.get("/api/v1/wiki/pages/" + page).status());
        assertEquals(404, portal.get("/api/v1/wiki/projects/" + setup.project() + "/pages").status());
        assertEquals(404, create(portal, setup, "Neu", null, null).status());
    }

    /** W11 and the tree: depth, cycles, readers' parents, archiving with its pages below, and links that survive renaming and moving. */
    @Test
    void theTreeKeepsItsRules() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        List<UUID> chain = new ArrayList<>();
        UUID parent = null;
        for (int level = 1; level <= 6; level++) {
            Response created = create(author, setup, "Ebene " + level, parent, null);
            assertEquals(201, created.status(), created.body());
            parent = id(created);
            chain.add(parent);
        }
        assertInvalid(create(author, setup, "Ebene 7", parent, null), "parent.too_deep");
        assertInvalid(create(author, setup, "Fremd", UUID.randomUUID(), null), "parent.unknown");

        UUID top = chain.get(0);
        Response topPage = author.get("/api/v1/wiki/pages/" + top);
        Response cycle = author.send("PUT", "/api/v1/wiki/pages/" + top + "/parent", Map.of("parentId", chain.get(3), "version", version(topPage)), null);
        assertInvalid(cycle, "parent.cycle");
        UUID other = id(create(author, setup, "Andere", null, null));
        Response tooDeep = author.send("PUT", "/api/v1/wiki/pages/" + top + "/parent", Map.of("parentId", other, "version", version(topPage)), null);
        assertInvalid(tooDeep, "parent.too_deep");
        assertEquals(412, author.send("PUT", "/api/v1/wiki/pages/" + top + "/parent", Map.of("parentId", other, "version", 99), null).status());
        Response moved = author.send("PUT", "/api/v1/wiki/pages/" + chain.get(4) + "/parent",
            Map.of("parentId", other, "version", version(author.get("/api/v1/wiki/pages/" + chain.get(4)))), null);
        assertEquals(200, moved.status(), moved.body());
        assertEquals(other.toString(), moved.json().get("parentId").asString());
        assertEquals("Andere", moved.json().get("path").get(0).get("title").asString());

        // A link written before a rename and a move still leads to the page.
        UUID linking = id(create(author, setup, "Verweise", null, doc(node("paragraph", text("Zur Ebene", link("wiki:" + chain.get(4)))))));
        assertEquals(200, publish(author, linking, "\"1\"", 1).status());
        UUID renamed = chain.get(4);
        Response rename = save(author, renamed, "\"1\"", "Ebene fünf umbenannt", doc(paragraph("x")));
        assertEquals(200, rename.status(), rename.body());
        assertEquals(200, publish(author, renamed, rename.etag(), 2).status());
        String html = reader.get("/api/v1/wiki/pages/" + linking).json().get("published").get("html").asString();
        assertTrue(html.contains("href=\"#/wiki/" + setup.project() + "/" + renamed + "\""), html);

        // Readers see the nearest published page above as parent.
        assertEquals(200, publish(author, top, "\"1\"", 1).status());
        JsonNode readerTree = reader.get("/api/v1/wiki/projects/" + setup.project() + "/pages").json().get("pages");
        Map<String, String> parents = new HashMap<>();
        readerTree.forEach(item -> parents.put(item.get("title").asString(), item.get("parentId").isNull() ? null : item.get("parentId").asString()));
        assertEquals(3, readerTree.size(), readerTree.toString());
        assertEquals(null, parents.get("Ebene fünf umbenannt"), "its parent Andere is not published");
        assertEquals(null, parents.get("Ebene 1"));
        assertFalse(parents.containsKey("Ebene 2"));

        // Archiving moves the pages below one level up and hides the page; restoring brings it back.
        UUID second = chain.get(1);
        Response archive = author.send("POST", "/api/v1/wiki/pages/" + second + "/archive",
            Map.of("version", version(author.get("/api/v1/wiki/pages/" + second))), null);
        assertEquals(200, archive.status(), archive.body());
        assertTrue(archive.json().get("archived").asBoolean());
        assertEquals(top.toString(), author.get("/api/v1/wiki/pages/" + chain.get(2)).json().get("parentId").asString());
        JsonNode active = author.get("/api/v1/wiki/projects/" + setup.project() + "/pages").json().get("pages");
        active.forEach(item -> assertFalse(item.get("id").asString().equals(second.toString())));
        JsonNode archived = author.get("/api/v1/wiki/projects/" + setup.project() + "/pages?archived=true").json().get("pages");
        assertEquals(1, archived.size());
        assertEquals(403, reader.get("/api/v1/wiki/projects/" + setup.project() + "/pages?archived=true").status());
        Browser editorOnly = member(setup, "wiki.edit");
        assertEquals(404, editorOnly.get("/api/v1/wiki/pages/" + second).status(), "archived pages stay with the people who publish");
        assertEquals(409, save(author, second, "\"1\"", "x", doc(paragraph("x"))).status());
        Response restored = author.send("POST", "/api/v1/wiki/pages/" + second + "/unarchive", Map.of("version", version(archive)), null);
        assertEquals(200, restored.status(), restored.body());
        assertFalse(restored.json().get("archived").asBoolean());
        assertEquals(top.toString(), restored.json().get("parentId").asString());
    }

    /** An older revision becomes the new draft, which needs a publication of its own. */
    @Test
    void anOldRevisionBecomesANewDraft() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        UUID page = id(create(author, setup, "Alt", null, doc(paragraph("Eins"))));
        assertEquals(200, publish(author, page, "\"1\"", 1).status());
        Response second = save(author, page, "\"1\"", "Neu", doc(paragraph("Zwei")));
        JsonNode old = author.get("/api/v1/wiki/pages/" + page + "/revisions/1").json();
        assertEquals("Alt", old.get("title").asString());
        assertTrue(old.get("published").asBoolean());
        assertEquals("<p>Eins</p>", old.get("html").asString());
        assertEquals(404, author.get("/api/v1/wiki/pages/" + page + "/revisions/9").status());
        assertEquals(412, author.send("POST", "/api/v1/wiki/pages/" + page + "/revisions/1/restore", null, "\"1\"").status());
        Response reverted = author.send("POST", "/api/v1/wiki/pages/" + page + "/revisions/1/restore", null, second.etag());
        assertEquals(200, reverted.status(), reverted.body());
        assertEquals(3, draft(reverted));
        assertEquals("Alt", reverted.json().get("draft").get("title").asString());
        assertTrue(reverted.json().get("draft").get("changed").asBoolean(), "the taken over revision is a new draft");
        assertEquals(1, reverted.json().get("published").get("revision").asInt());
        JsonNode revisions = author.get("/api/v1/wiki/pages/" + page + "/history").json().get("revisions");
        assertEquals(1, revisions.get(0).get("restoredFrom").asInt());
        assertEquals(2, revisions.get(0).get("basedOn").asInt());
    }

    // ---------------------------------------------------------------- language versions (TASK-094)

    private Response saveIn(Browser who, UUID page, String language, String etag, String title, Object content, Integer sourceRevision) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("content", content);
        if (sourceRevision != null) {
            body.put("sourceRevision", sourceRevision);
        }
        return who.send("PUT", "/api/v1/wiki/pages/" + page + "/draft?language=" + language, body, etag);
    }

    private static List<String> languages(Response page) {
        List<String> codes = new ArrayList<>();
        page.json().get("languages").forEach(l -> codes.add(l.get("language").asString()));
        return codes;
    }

    /** W01, W07, W08: a German page is complete alone, a draft is never the fallback, a new source marks the translation. */
    @Test
    void languageVersionsFollowTheConfirmedRules() {
        Setup setup = project();
        Browser author = member(setup, "wiki.edit", "wiki.publish");
        Browser reader = member(setup, "wiki.read");
        UUID page = id(create(author, setup, "Anleitung", null, doc(paragraph("Deutsch eins"))));
        assertEquals(200, publish(author, page, "\"1\"", 1).status());
        String path = "/api/v1/wiki/pages/" + page;

        // W01: only German, every request gets it, a requested English one with the language named.
        Response german = reader.get(path);
        assertEquals("de", german.json().get("language").asString());
        assertEquals("de", german.json().get("sourceLanguage").asString());
        assertFalse(german.json().get("fallback").asBoolean());
        assertTrue(german.json().get("translation").isNull());
        Response wanted = reader.get(path + "?language=en");
        assertEquals("de", wanted.json().get("language").asString());
        assertTrue(wanted.json().get("fallback").asBoolean());
        assertEquals(List.of("de"), languages(wanted));
        assertInvalid(reader.get(path + "?language=fr"), "language.invalid");

        // Adding English copies the published German revision as the first English draft.
        assertInvalid(author.send("POST", path + "/languages", Map.of(), null), "language.required");
        assertInvalid(author.send("POST", path + "/languages", Map.of("language", "fr"), null), "language.invalid");
        assertEquals(409, author.send("POST", path + "/languages", Map.of("language", "de"), null).status());
        assertEquals(403, reader.send("POST", path + "/languages", Map.of("language", "en"), null).status());
        assertEquals(404, saveIn(author, page, "en", "\"1\"", "Guide", doc(paragraph("x")), null).status(), "no English version yet");
        Response added = author.send("POST", path + "/languages", Map.of("language", "en"), null);
        assertEquals(201, added.status(), added.body());
        assertEquals("en", added.json().get("language").asString());
        assertEquals("\"1\"", added.etag());
        assertEquals("Anleitung", added.json().get("draft").get("title").asString());
        assertEquals("<p>Deutsch eins</p>", added.json().get("draft").get("html").asString());
        assertEquals(1, added.json().get("translation").get("sourceRevision").asInt());
        assertFalse(added.json().get("translation").get("outdated").asBoolean());
        assertEquals(409, author.send("POST", path + "/languages", Map.of("language", "en"), null).status());

        // W08: the English draft is neither shown to readers nor named.
        Response stillGerman = reader.get(path + "?language=en");
        assertEquals("de", stillGerman.json().get("language").asString());
        assertEquals(List.of("de"), languages(stillGerman));
        assertFalse(stillGerman.body().contains("\"en\""), "readers learn nothing of the English draft");
        assertEquals("Anleitung", reader.get("/api/v1/wiki/projects/" + setup.project() + "/pages?language=en").json().get("pages").get(0).get("title")
            .asString());
        assertEquals(404, reader.get(path + "/history?language=en").status());

        Response english = saveIn(author, page, "en", "\"1\"", "Guide", doc(paragraph("English one")), null);
        assertEquals(200, english.status(), english.body());
        assertEquals(1, english.json().get("translation").get("sourceRevision").asInt(), "the translated revision stays without a new one");
        assertEquals(200, author.send("POST", path + "/publish?language=en", Map.of("revision", 2), "\"2\"").status());
        Response read = reader.get(path + "?language=en");
        assertEquals("en", read.json().get("language").asString());
        assertFalse(read.json().get("fallback").asBoolean());
        assertEquals("Guide", read.json().get("published").get("title").asString());
        assertEquals(List.of("de", "en"), languages(read));
        assertFalse(read.json().get("translation").get("outdated").asBoolean());
        assertTrue(read.json().get("translation").get("sourceRevision").isNull(), "readers get no revision numbers");
        assertEquals("Guide", reader.get("/api/v1/wiki/projects/" + setup.project() + "/pages?language=en").json().get("pages").get(0).get("title")
            .asString());
        assertEquals("Anleitung", reader.get("/api/v1/wiki/projects/" + setup.project() + "/pages?language=de").json().get("pages").get(0).get("title")
            .asString());

        // W07: German is published again; the translation stays and is marked for both.
        assertEquals(200, save(author, page, "\"1\"", "Anleitung", doc(paragraph("Deutsch zwei"))).status());
        assertEquals(200, publish(author, page, "\"2\"", 2).status());
        assertTrue(reader.get(path + "?language=en").json().get("translation").get("outdated").asBoolean());
        JsonNode marked = author.get(path + "?language=en").json().get("translation");
        assertEquals(1, marked.get("sourceRevision").asInt());
        assertEquals(2, marked.get("sourcePublishedRevision").asInt());
        assertTrue(marked.get("outdated").asBoolean());
        assertEquals("<p>Deutsch eins</p>", author.get(path + "/revisions/1?language=de").json().get("html").asString(), "the comparison reads the source");

        assertInvalid(saveIn(author, page, "en", "\"2\"", "Guide", doc(paragraph("English two")), 5), "source_revision.invalid");
        assertInvalid(saveIn(author, page, "de", "\"2\"", "Anleitung", doc(paragraph("Deutsch")), 1), "source_revision.invalid");
        Response aligned = saveIn(author, page, "en", "\"2\"", "Guide", doc(paragraph("English two")), 2);
        assertEquals(200, aligned.status(), aligned.body());
        assertFalse(aligned.json().get("translation").get("outdated").asBoolean());
        assertTrue(reader.get(path + "?language=en").json().get("translation").get("outdated").asBoolean(), "until the English one is published");
        assertEquals(200, author.send("POST", path + "/publish?language=en", Map.of("revision", 3), "\"3\"").status());
        assertFalse(reader.get(path + "?language=en").json().get("translation").get("outdated").asBoolean());

        JsonNode history = author.get(path + "/history?language=en").json();
        assertEquals("en", history.get("language").asString());
        assertEquals(2, history.get("revisions").get(0).get("sourceRevision").asInt());
        List<String> kinds = new ArrayList<>();
        history.get("events").forEach(e -> kinds.add(e.get("kind").asString() + ":" + e.get("language").asString()));
        assertTrue(kinds.contains("translated:en"), kinds.toString());

        // Without the published German version readers get the English one, with the language named.
        assertEquals(200, author.send("POST", path + "/withdraw?language=de", null, null).status());
        Response fallback = reader.get(path);
        assertEquals("en", fallback.json().get("language").asString());
        assertTrue(fallback.json().get("fallback").asBoolean());
        assertEquals(List.of("en"), languages(fallback));
        assertEquals(200, author.send("POST", path + "/withdraw?language=en", null, null).status());
        assertEquals(404, reader.get(path + "?language=en").status(), "no published version, no page for readers");
    }
}
