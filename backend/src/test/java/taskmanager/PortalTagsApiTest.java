package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.identity.account.AccountService;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.CompanyContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-077 (DEC-108): tags in the customer portal through the real HTTP interface with the real identity, permissions,
 * projects, tickets, tags and helpdesk modules and a real PostgreSQL: the visibility of tags, cases T04, T10, T18 and T19 of
 * docs/TAGS_SEARCH.md, and internal tags that changes in the portal leave untouched, also under a concurrent internal change.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PortalTagsApiTest {
    private static final String DATABASE = freshDatabase();
    private static final Path STORE = newStore();
    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
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

    private static Path newStore() {
        try {
            return Files.createTempDirectory("taskmanager-portal-tags");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE);
        registry.add("spring.datasource.username", TestDatabase::username);
        registry.add("spring.datasource.password", TestDatabase::password);
        registry.add("taskmanager.files.directory", STORE::toString);
        registry.add("taskmanager.helpdesk.secret-key", () -> KEY);
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

        List<String> violations() {
            JsonNode list = json().get("violations");
            return list == null ? List.of() : StreamSupport.stream(list.spliterator(), false).map(JsonNode::asString).toList();
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

        Response upload(String path, String name, byte[] content) {
            try {
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path + "?name="
                        + java.net.URLEncoder.encode(name, StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/octet-stream").POST(HttpRequest.BodyPublishers.ofByteArray(content));
                List<String> pairs = new ArrayList<>();
                cookies.forEach((key, value) -> pairs.add(key + "=" + value));
                request.header("Cookie", String.join("; ", pairs));
                request.header("X-CSRF-Token", cookies.get("__Host-tm-csrf"));
                HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
                return new Response(response.statusCode(), response.body(), null);
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private Browser login(UUID id, String username, String oneTimePassword, boolean change) {
        Browser browser = new Browser();
        assertEquals(200, browser.get("/api/v1/session").status());
        Response login = browser.send("POST", "/api/v1/session", Map.of("username", username, "password", oneTimePassword), null);
        assertEquals(200, login.status(), login.body());
        if (change) {
            Response changed = browser.send("POST", "/api/v1/session/password",
                Map.of("currentPassword", oneTimePassword, "newPassword", STRONG), null);
            assertEquals(200, changed.status(), changed.body());
        }
        browser.id = id;
        return browser;
    }

    private Browser person(AdministrationRight... rights) {
        var issued = accounts.create("test", "intern-" + COUNTER.incrementAndGet(), null);
        if (rights.length > 0) {
            AdministrationFixture.grant(jdbc, companies, transactions, issued.account().id(), rights);
        }
        return login(issued.account().id(), issued.account().username(), issued.oneTimePassword(), true);
    }

    /** Grants project actions and ticket field rules in a group of its own. */
    private void grant(UUID account, UUID project, String... rules) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            UUID companyId = companies.find().orElseGet(companies::create);
            jdbc.update("insert into permissions_revision(company_id) values (?) on conflict do nothing", companyId);
            UUID group = UUID.randomUUID();
            jdbc.update("insert into permissions_group(company_id, id, name) values (?, ?, ?)", companyId, group, "fixture-" + group);
            for (String rule : rules) {
                if (!rule.contains(":")) {
                    jdbc.update("insert into permissions_action(company_id, group_id, project_id, action_key) values (?, ?, ?, ?)",
                        companyId, group, project, rule);
                    continue;
                }
                String[] parts = rule.split(":");
                List<String> fields = parts[1].equals("*")
                    ? List.of("title", "description", "type", "status", "assignee", "attachments", "customer", "contact", "mail")
                    : List.of(parts[1]);
                for (String field : fields) {
                    jdbc.update("""
                            insert into permissions_field(company_id, group_id, project_id, resource_type, field_key, operation, effect)
                            values (?, ?, ?, 'ticket', ?, ?, 'ALLOW')
                            """, companyId, group, project, field, parts[0]);
                }
            }
            jdbc.update("insert into permissions_membership(company_id, group_id, user_id) values (?, ?, ?)", companyId, group, account);
            jdbc.update("update permissions_revision set revision = revision + 1 where company_id = ?", companyId);
        });
    }

    // ---------------------------------------------------------------- the setting

    /**
     * Two projects released to Nordwerk (one of them also to Südtechnik), one project of nobody, a contact of each organization
     * and an agent who creates requests and sets their customer.
     */
    private record Setup(Browser admin, Browser agent, UUID shared, UUID second, UUID internal, UUID nordwerk, UUID nordwerkContact,
            UUID suedtechnik, UUID suedtechnikContact) {
    }

    private Setup setup() {
        int n = COUNTER.incrementAndGet();
        Browser admin = person(AdministrationRight.ACCOUNTS_MANAGE, AdministrationRight.PROJECTS_MANAGE, AdministrationRight.HELPDESK_MANAGE);
        Browser agent = person();
        UUID shared = project(admin, "PA" + n, agent);
        UUID second = project(admin, "PB" + n, agent);
        UUID internal = project(admin, "PI" + n, agent);
        for (UUID project : List.of(shared, second, internal)) {
            grant(agent.id, project, "ticket.read", "ticket.create", "ticket.edit", "READ:*", "WRITE:*");
        }
        UUID nordwerk = customer(admin, "Nordwerk " + n, List.of(shared, second));
        UUID suedtechnik = customer(admin, "Südtechnik " + n, List.of(shared));
        return new Setup(admin, agent, shared, second, internal, nordwerk, contact(admin, nordwerk, "lea" + n + "@nordwerk.example"),
            suedtechnik, contact(admin, suedtechnik, "tom" + n + "@suedtechnik.example"));
    }

    private UUID project(Browser admin, String key, Browser... members) {
        Response created = admin.send("POST", "/api/v1/projects", Map.of("key", key, "name", "Projekt " + key), null);
        assertEquals(201, created.status(), created.body());
        UUID project = UUID.fromString(created.json().get("id").asString());
        String version = created.etag();
        for (Browser member : members) {
            Response added = admin.send("PUT", "/api/v1/projects/" + project + "/members/" + member.id, null, version);
            assertEquals(200, added.status(), added.body());
            version = added.etag();
        }
        return project;
    }

    private static UUID customer(Browser admin, String name, List<UUID> projects) {
        Response customer = admin.send("POST", "/api/v1/helpdesk/customers", Map.of("name", name, "projects", projects), null);
        assertEquals(201, customer.status(), customer.body());
        return UUID.fromString(customer.json().get("id").asString());
    }

    private static UUID contact(Browser admin, UUID customer, String address) {
        Response contact = admin.send("POST", "/api/v1/helpdesk/contacts",
            Map.of("customerId", customer, "name", "Kontakt " + address, "addresses", List.of(address)), null);
        assertEquals(201, contact.status(), contact.body());
        return UUID.fromString(contact.json().get("id").asString());
    }

    /** A request of the agent with the given customer (or none), as the mail intake or the agent would set it. */
    private long request(Setup setup, UUID project, String title, UUID customer) {
        Response created = setup.agent().send("POST", "/api/v1/projects/" + project + "/tickets", Map.of("type", "task", "title", title), null);
        assertEquals(201, created.status(), created.body());
        UUID ticket = UUID.fromString(created.json().get("id").asString());
        if (customer != null) {
            jdbc.update("""
                    insert into helpdesk_ticket_customer(ticket_id, company_id, project_id, customer_id)
                    select id, company_id, project_id, ? from ticket where id = ?
                    """, customer, ticket);
        }
        return created.json().get("number").asLong();
    }

    private record Portal(UUID id, String username, String oneTimePassword) {
    }

    private Portal createPortal(Setup setup, UUID organization, UUID contact, List<UUID> projects) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", "kunde-" + COUNTER.incrementAndGet());
        body.put("displayName", "Kunde");
        body.put("organizationId", organization);
        body.put("contactId", contact);
        body.put("projectIds", projects);
        Response created = setup.admin().send("POST", "/api/v1/portal-accounts", body, null);
        assertEquals(201, created.status(), created.body());
        JsonNode account = created.json().get("account");
        assertEquals(organization.toString(), account.get("organizationId").asString());
        assertNotNull(created.json().get("oneTimePassword").asString());
        return new Portal(UUID.fromString(account.get("id").asString()), account.get("username").asString(),
            created.json().get("oneTimePassword").asString());
    }

    private Browser portalSession(Portal portal) {
        return login(portal.id(), portal.username(), portal.oneTimePassword(), true);
    }

    private static List<Long> numbers(Response list) {
        assertEquals(200, list.status(), list.body());
        return StreamSupport.stream(list.json().spliterator(), false).map(node -> node.get("number").asLong()).sorted().toList();
    }


    // ---------------------------------------------------------------- helpers of the tags

    /** An agent who may read, write and care for the tags of the project, besides the rights of the setting. */
    private void tagAgent(Setup setup, UUID project) {
        grant(setup.agent().id, project, "tag.create", "tags.manage", "READ:tags", "WRITE:tags");
    }

    /** A project tag of the agent with the given visibility. */
    private UUID projectTag(Setup setup, UUID project, String name, String visibility) {
        String path = "/api/v1/projects/" + project + "/catalog/tags";
        Response created = setup.agent().send("POST", path, Map.of("name", name), null);
        assertEquals(201, created.status(), created.body());
        assertEquals("internal", created.json().get("visibility").asString());
        UUID id = UUID.fromString(created.json().get("id").asString());
        if (!visibility.equals("internal")) {
            Response changed = setup.agent().send("PATCH", path + "/" + id, Map.of("visibility", visibility), created.etag());
            assertEquals(200, changed.status(), changed.body());
            assertEquals(visibility, changed.json().get("visibility").asString());
        }
        return id;
    }

    private void assignInternally(Setup setup, UUID project, long number, UUID tag) {
        Response assigned = setup.agent().send("POST", "/api/v1/projects/" + project + "/tickets/" + number + "/tags", Map.of("tagId", tag), null);
        assertEquals(204, assigned.status(), assigned.body());
    }

    private List<String> internalTagNames(Setup setup, UUID project, long number) {
        Response tags = setup.agent().get("/api/v1/projects/" + project + "/tickets/" + number + "/tags");
        assertEquals(200, tags.status(), tags.body());
        return StreamSupport.stream(tags.json().spliterator(), false).map(tag -> tag.get("name").asString()).sorted().toList();
    }

    private static List<String> names(JsonNode items) {
        return StreamSupport.stream(items.spliterator(), false).map(tag -> tag.get("name").asString()).sorted().toList();
    }

    private static String tags(UUID project, long number) {
        return "/api/v1/portal/projects/" + project + "/tickets/" + number + "/tags";
    }

    /** A portal account of Nordwerk in the shared project that may also set tags and, when asked, create them. */
    private Browser writer(Setup setup, UUID organization, UUID contact, boolean create) {
        Portal portal = createPortal(setup, organization, contact, List.of(setup.shared()));
        if (create) {
            grant(portal.id(), setup.shared(), "ticket.edit", "WRITE:tags", "tag.create");
        } else {
            grant(portal.id(), setup.shared(), "ticket.edit", "WRITE:tags");
        }
        return portalSession(portal);
    }

    // ---------------------------------------------------------------- tests

    /** T04 and rule 1: the portal shows only tags visible to customers, in the request, in suggestions and in answers. */
    @Test
    void internalTagsStayHiddenInThePortal() {
        Setup setup = setup();
        tagAgent(setup, setup.shared());
        UUID hidden = projectTag(setup, setup.shared(), "Eskalation intern", "internal");
        UUID shown = projectTag(setup, setup.shared(), "Drucker", "customer");
        long number = request(setup, setup.shared(), "Drucker defekt", setup.nordwerk());
        assignInternally(setup, setup.shared(), number, hidden);
        assignInternally(setup, setup.shared(), number, shown);

        Browser lea = portalSession(createPortal(setup, setup.nordwerk(), setup.nordwerkContact(), List.of(setup.shared())));
        Response own = lea.get(tags(setup.shared(), number));
        assertEquals(200, own.status(), own.body());
        assertEquals(List.of("Drucker"), names(own.json().get("items")));
        assertFalse(own.json().get("writable").asBoolean());
        assertFalse(own.body().contains(hidden.toString()), own.body());

        String suggestions = "/api/v1/portal/projects/" + setup.shared() + "/tags";
        Response all = lea.get(suggestions);
        assertEquals(200, all.status(), all.body());
        assertEquals(List.of("Drucker"), names(all.json().get("items")));
        assertFalse(all.json().get("create").asBoolean());
        Response searched = lea.get(suggestions + "?q=eskal");
        assertEquals(200, searched.status(), searched.body());
        assertEquals(0, searched.json().get("items").size(), searched.body());

        // The default group writes no tags; the internal catalog stays closed.
        assertEquals(403, lea.send("POST", tags(setup.shared(), number), Map.of("tagId", shown), null).status());
        assertEquals(403, lea.send("DELETE", tags(setup.shared(), number) + "/" + shown, null, null).status());
        assertEquals(404, lea.get("/api/v1/projects/" + setup.shared() + "/catalog/tags").status());
        // An internal account has no portal path.
        assertEquals(404, setup.agent().get(suggestions).status());
        assertEquals(404, setup.agent().get(tags(setup.shared(), number)).status());
    }

    /** T10: a filter by a tag that both organizations use lists only the own requests; a hidden tag finds nothing. */
    @Test
    void theTagFilterListsOnlyOwnRequests() {
        Setup setup = setup();
        tagAgent(setup, setup.shared());
        UUID shown = projectTag(setup, setup.shared(), "Wartung", "customer");
        UUID hidden = projectTag(setup, setup.shared(), "Kulanz", "internal");
        long nordwerk = request(setup, setup.shared(), "Wartung Nordwerk", setup.nordwerk());
        long untagged = request(setup, setup.shared(), "Ohne Tag", setup.nordwerk());
        long suedtechnik = request(setup, setup.shared(), "Wartung Südtechnik", setup.suedtechnik());
        for (long number : List.of(nordwerk, suedtechnik)) {
            assignInternally(setup, setup.shared(), number, shown);
            assignInternally(setup, setup.shared(), number, hidden);
        }
        Browser lea = portalSession(createPortal(setup, setup.nordwerk(), setup.nordwerkContact(), List.of(setup.shared())));
        String list = "/api/v1/portal/projects/" + setup.shared() + "/tickets";
        assertEquals(List.of(nordwerk, untagged), numbers(lea.get(list)));
        assertEquals(List.of(nordwerk), numbers(lea.get(list + "?tag=" + shown)));
        assertEquals(List.of(), numbers(lea.get(list + "?tag=" + hidden)));
        assertEquals(List.of(), numbers(lea.get(list + "?tag=" + UUID.randomUUID())));

        Browser tom = portalSession(createPortal(setup, setup.suedtechnik(), setup.suedtechnikContact(), List.of(setup.shared())));
        assertEquals(List.of(suedtechnik), numbers(tom.get(list + "?tag=" + shown)));
        // The request of the other organization answers like an unknown one, also for its tags.
        assertEquals(404, lea.get(tags(setup.shared(), suedtechnik)).status());
    }

    /** T18 and rule 2: an authorized portal account creates a tag of its organization and sets it; the support releases it. */
    @Test
    void anAuthorizedPortalAccountCreatesAndAssignsTags() {
        Setup setup = setup();
        tagAgent(setup, setup.shared());
        UUID internal = projectTag(setup, setup.shared(), "Rückruf", "internal");
        long nordwerk = request(setup, setup.shared(), "Bitte zurückrufen", setup.nordwerk());
        long suedtechnik = request(setup, setup.shared(), "Auch zurückrufen", setup.suedtechnik());
        Browser lea = writer(setup, setup.nordwerk(), setup.nordwerkContact(), true);
        assertTrue(lea.get("/api/v1/portal/projects/" + setup.shared() + "/tags").json().get("create").asBoolean());

        // The internal tag of the same name stays hidden: the portal gets a tag of its own organization.
        Response created = lea.send("POST", tags(setup.shared(), nordwerk), Map.of("name", "rückruf"), null);
        assertEquals(200, created.status(), created.body());
        UUID own = UUID.fromString(created.json().get("id").asString());
        assertFalse(own.equals(internal));
        assertTrue(created.json().get("own").asBoolean());
        Response listed = lea.get(tags(setup.shared(), nordwerk));
        assertEquals(List.of("rückruf"), names(listed.json().get("items")));
        assertTrue(listed.json().get("writable").asBoolean());
        // Setting it again by name finds the same tag and changes nothing.
        assertEquals(own.toString(), lea.send("POST", tags(setup.shared(), nordwerk), Map.of("name", "RÜCKRUF"), null).json().get("id").asString());
        assertEquals(List.of("rückruf"), internalTagNames(setup, setup.shared(), nordwerk));

        // Internally the tag is visible to customers and bound to Nordwerk; Südtechnik does not see it.
        Response tag = setup.agent().get("/api/v1/projects/" + setup.shared() + "/catalog/tags/" + own);
        assertEquals(200, tag.status(), tag.body());
        assertEquals("customer", tag.json().get("visibility").asString());
        assertEquals(setup.nordwerk().toString(), tag.json().get("organizationId").asString());
        Browser tom = writer(setup, setup.suedtechnik(), setup.suedtechnikContact(), true);
        assertEquals(0, tom.get("/api/v1/portal/projects/" + setup.shared() + "/tags?q=r").json().get("items").size());
        assertEquals(404, tom.send("POST", tags(setup.shared(), suedtechnik), Map.of("tagId", own), null).status());
        Response theirs = tom.send("POST", tags(setup.shared(), suedtechnik), Map.of("name", "Rückruf"), null);
        assertEquals(200, theirs.status(), theirs.body());
        UUID foreign = UUID.fromString(theirs.json().get("id").asString());
        assertFalse(foreign.equals(own));

        // Releasing the tag of Nordwerk for every customer clashes with the internal name; renamed first, it works.
        String catalog = "/api/v1/projects/" + setup.shared() + "/catalog/tags/";
        Response clash = setup.agent().send("PATCH", catalog + own, Map.of("forAllCustomers", true), tag.etag());
        assertEquals(409, clash.status(), clash.body());
        assertEquals("tag.name_taken", clash.code());
        Response released = setup.agent().send("PATCH", catalog + own, Map.of("forAllCustomers", true, "name", "Rückruf erbeten"), tag.etag());
        assertEquals(200, released.status(), released.body());
        assertTrue(released.json().get("organizationId").isNull());
        assertEquals(List.of("Rückruf", "Rückruf erbeten"),
            names(tom.get("/api/v1/portal/projects/" + setup.shared() + "/tags?q=r").json().get("items")));
        // Made internal, it leaves the portal of Nordwerk too.
        Response hidden = setup.agent().send("PATCH", catalog + own, Map.of("visibility", "internal"), released.etag());
        assertEquals(200, hidden.status(), hidden.body());
        assertEquals(List.of(), names(lea.get(tags(setup.shared(), nordwerk)).json().get("items")));
        assertEquals(List.of("Rückruf erbeten"), internalTagNames(setup, setup.shared(), nordwerk));
        Response invalid = setup.agent().send("PATCH", catalog + own, Map.of("visibility", "public"), hidden.etag());
        assertEquals(400, invalid.status(), invalid.body());
        assertTrue(invalid.violations().contains("visibility.invalid"), invalid.body());
    }

    /** T19: without the right, without membership or with a manipulated organization nothing is created or changed. */
    @Test
    void creatingAndAssigningIsRefusedWithoutRightMembershipOrOwnOrganization() {
        Setup setup = setup();
        tagAgent(setup, setup.shared());
        UUID shown = projectTag(setup, setup.shared(), "Lieferung", "customer");
        UUID hidden = projectTag(setup, setup.shared(), "Intern geprüft", "internal");
        long nordwerk = request(setup, setup.shared(), "Lieferung fehlt", setup.nordwerk());
        long suedtechnik = request(setup, setup.shared(), "Fremde Anfrage", setup.suedtechnik());
        long before = jdbc.queryForObject("select count(*) from tag", Long.class);

        // Without tag.create a new name is refused and no tag is left behind.
        Browser writer = writer(setup, setup.nordwerk(), setup.nordwerkContact(), false);
        Response refused = writer.send("POST", tags(setup.shared(), nordwerk), Map.of("name", "Neu im Portal"), null);
        assertEquals(403, refused.status(), refused.body());
        assertEquals(before, jdbc.queryForObject("select count(*) from tag", Long.class));
        assertFalse(writer.get("/api/v1/portal/projects/" + setup.shared() + "/tags").json().get("create").asBoolean());
        // A visible tag it may set, an internal one answers like an unknown one, also on removal.
        assertEquals(200, writer.send("POST", tags(setup.shared(), nordwerk), Map.of("tagId", shown), null).status());
        assertEquals(404, writer.send("POST", tags(setup.shared(), nordwerk), Map.of("tagId", hidden), null).status());
        assertEquals(404, writer.send("DELETE", tags(setup.shared(), nordwerk) + "/" + hidden, null, null).status());
        // A request of another organization and a manipulated organization in the body.
        assertEquals(404, writer.send("POST", tags(setup.shared(), suedtechnik), Map.of("tagId", shown), null).status());
        Map<String, Object> manipulated = new HashMap<>();
        manipulated.put("name", "Fremd");
        manipulated.put("organizationId", setup.suedtechnik());
        Response unknown = writer.send("POST", tags(setup.shared(), nordwerk), manipulated, null);
        assertEquals(400, unknown.status(), unknown.body());
        assertTrue(unknown.violations().contains("fields.unknown"), unknown.body());

        // With tag.create, but after the membership ended, the project answers like an unknown one.
        Portal creator = createPortal(setup, setup.nordwerk(), setup.nordwerkContact(), List.of(setup.shared()));
        grant(creator.id(), setup.shared(), "ticket.edit", "WRITE:tags", "tag.create");
        Browser lea = portalSession(creator);
        Map<String, Object> change = new HashMap<>();
        change.put("contactId", setup.nordwerkContact());
        change.put("projectIds", List.of(setup.second()));
        assertEquals(200, setup.admin().send("PUT", "/api/v1/portal-accounts/" + creator.id(), change, null).status());
        assertEquals(404, lea.send("POST", tags(setup.shared(), nordwerk), Map.of("name", "Nach dem Entzug"), null).status());
        assertEquals(404, lea.get("/api/v1/portal/projects/" + setup.shared() + "/tags").status());
        assertEquals(before, jdbc.queryForObject("select count(*) from tag", Long.class));
        assertEquals(List.of("Lieferung"), internalTagNames(setup, setup.shared(), nordwerk));
    }

    /** Acceptance 2: changes in the portal leave the internal tags of the request as they are, also under a concurrent change. */
    @Test
    void portalChangesLeaveInternalTagsUntouched() throws Exception {
        Setup setup = setup();
        tagAgent(setup, setup.shared());
        UUID internal = projectTag(setup, setup.shared(), "Vertrag geprüft", "internal");
        UUID shown = projectTag(setup, setup.shared(), "Rechnung", "customer");
        List<UUID> others = new ArrayList<>();
        List<UUID> visible = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            others.add(projectTag(setup, setup.shared(), "Intern " + i, "internal"));
            visible.add(projectTag(setup, setup.shared(), "Kunde " + i, "customer"));
        }
        long number = request(setup, setup.shared(), "Rechnung falsch", setup.nordwerk());
        assignInternally(setup, setup.shared(), number, internal);
        assignInternally(setup, setup.shared(), number, shown);
        Browser lea = writer(setup, setup.nordwerk(), setup.nordwerkContact(), false);

        Response removed = lea.send("DELETE", tags(setup.shared(), number) + "/" + shown, null, null);
        assertEquals(204, removed.status(), removed.body());
        assertEquals(List.of("Vertrag geprüft"), internalTagNames(setup, setup.shared(), number));

        // The portal sets customer tags while the support sets internal ones and changes the visibility of another tag.
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var portal = pool.submit(() -> {
                for (UUID tag : visible) {
                    Response set = lea.send("POST", tags(setup.shared(), number), Map.of("tagId", tag), null);
                    if (set.status() != 200 && set.status() != 503) {
                        return set.status() + " " + set.body();
                    }
                }
                return "ok";
            });
            var support = pool.submit(() -> {
                for (UUID tag : others) {
                    Response set = setup.agent().send("POST", "/api/v1/projects/" + setup.shared() + "/tickets/" + number + "/tags",
                        Map.of("tagId", tag), null);
                    if (set.status() != 204 && set.status() != 503) {
                        return set.status() + " " + set.body();
                    }
                }
                return "ok";
            });
            assertEquals("ok", portal.get());
            assertEquals("ok", support.get());
        } finally {
            pool.shutdown();
        }
        for (UUID tag : visible) {
            lea.send("POST", tags(setup.shared(), number), Map.of("tagId", tag), null);
        }
        for (UUID tag : others) {
            assignInternally(setup, setup.shared(), number, tag);
        }
        List<String> all = internalTagNames(setup, setup.shared(), number);
        assertEquals(List.of("Intern 0", "Intern 1", "Intern 2", "Intern 3", "Kunde 0", "Kunde 1", "Kunde 2", "Kunde 3", "Vertrag geprüft"), all);
        assertEquals(List.of("Kunde 0", "Kunde 1", "Kunde 2", "Kunde 3"), names(lea.get(tags(setup.shared(), number)).json().get("items")));
        assertEquals(1L, jdbc.queryForObject("select count(*) from tag_ticket where tag_id = ?", Long.class, internal));
    }
}
