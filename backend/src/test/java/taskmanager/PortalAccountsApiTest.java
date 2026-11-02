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
 * TASK-075 (DEC-105): portal accounts through the real HTTP interface with the real identity, permissions, projects,
 * tickets and helpdesk modules and a real PostgreSQL. The sight follows the test cases 1 to 8 of docs/PERMISSIONS.md: a portal
 * account sees the requests of its organization in its projects, those of colleagues included, and nothing else.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PortalAccountsApiTest {
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
            return Files.createTempDirectory("taskmanager-portal-api");
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

    // ---------------------------------------------------------------- tests

    /** Cases 1 to 4: own requests and those of colleagues are visible, others answer 404, writable fields stay empty. */
    @Test
    void aPortalAccountSeesTheRequestsOfItsOrganizationInItsProjects() {
        Setup setup = setup();
        long own = request(setup, setup.shared(), "Drucker defekt", setup.nordwerk());
        long colleague = request(setup, setup.shared(), "Rechnung fehlt", setup.nordwerk());
        long foreign = request(setup, setup.shared(), "Südtechnik Anfrage", setup.suedtechnik());
        long none = request(setup, setup.shared(), "Interne Aufgabe", null);
        long elsewhere = request(setup, setup.second(), "Zweites Projekt", setup.nordwerk());

        Browser lea = portalSession(createPortal(setup, setup.nordwerk(), setup.nordwerkContact(), List.of(setup.shared())));
        Response session = lea.get("/api/v1/session");
        assertEquals("portal", session.json().get("kind").asString(), session.body());

        Response projects = lea.get("/api/v1/portal/projects");
        assertEquals(200, projects.status(), projects.body());
        assertEquals(1, projects.json().size(), projects.body());
        assertEquals(setup.shared().toString(), projects.json().get(0).get("id").asString());

        String base = "/api/v1/portal/projects/" + setup.shared() + "/tickets";
        assertEquals(List.of(own, colleague), numbers(lea.get(base)));
        Response view = lea.get(base + "/" + own);
        assertEquals(200, view.status(), view.body());
        JsonNode fields = view.json().get("fields");
        assertEquals("Drucker defekt", fields.get("title").asString());
        assertFalse(fields.has("assignee"), view.body());
        assertEquals(0, view.json().get("writableFields").size(), view.body());
        assertEquals(200, lea.get(base + "/" + colleague).status());

        // Another organization, no organization, an unknown number and a project without membership: all alike.
        Response unknown = lea.get(base + "/999999");
        assertEquals(404, unknown.status());
        for (long number : List.of(foreign, none)) {
            Response hidden = lea.get(base + "/" + number);
            assertEquals(404, hidden.status(), hidden.body());
            assertEquals(unknown.code(), hidden.code());
        }
        Response other = lea.get("/api/v1/portal/projects/" + setup.second() + "/tickets/" + elsewhere);
        assertEquals(404, other.status(), other.body());
        assertEquals(404, lea.get("/api/v1/portal/projects/" + setup.second() + "/tickets").status());

        // The second organization sees only its own request in the shared project.
        Browser tom = portalSession(createPortal(setup, setup.suedtechnik(), null, List.of(setup.shared())));
        assertEquals(List.of(foreign), numbers(tom.get(base)));
        assertEquals(404, tom.get(base + "/" + own).status());
    }

    /** Case 5: the internal endpoints, history, search and the account directory stay closed to portal accounts. */
    @Test
    void theInternalEndpointsStayClosed() {
        Setup setup = setup();
        long own = request(setup, setup.shared(), "Drucker defekt", setup.nordwerk());
        Browser lea = portalSession(createPortal(setup, setup.nordwerk(), null, List.of(setup.shared())));
        String ticket = "/api/v1/projects/" + setup.shared() + "/tickets/" + own;
        for (String path : List.of(ticket, ticket + "/history", "/api/v1/projects/" + setup.shared() + "/tickets",
                "/api/v1/projects/" + setup.shared())) {
            Response closed = lea.get(path);
            assertEquals(404, closed.status(), path + " " + closed.body());
        }
        Response attachments = lea.get(ticket + "/attachments");
        assertEquals(404, attachments.status(), attachments.body());
        Response search = lea.get("/api/v1/search/tickets?q=Drucker");
        // The search is closed as a whole for portal accounts; nothing of a request shows.
        assertEquals(403, search.status(), search.body());
        assertFalse(search.body().contains("Drucker"));
        Response projects = lea.get("/api/v1/projects");
        assertTrue(projects.status() != 200 || projects.json().isEmpty(), projects.body());
        Response directory = lea.get("/api/v1/directory/accounts");
        assertEquals(403, directory.status(), directory.body());
        assertEquals(403, lea.get("/api/v1/portal-accounts").status());
        assertEquals(403, lea.get("/api/v1/accounts").status());
        // The directory of the staff marks portal accounts, so the interface leaves them out of the choice of assignees.
        Response staff = setup.agent().get("/api/v1/directory/accounts");
        assertEquals(200, staff.status(), staff.body());
        JsonNode entry = StreamSupport.stream(staff.json().spliterator(), false)
            .filter(node -> node.get("id").asString().equals(lea.id.toString())).findFirst().orElseThrow();
        assertEquals("portal", entry.get("kind").asString());
        // A portal account is a member of the project, but never an assignee (DEC-105, proposal 6).
        Response created = setup.agent().send("POST", "/api/v1/projects/" + setup.shared() + "/tickets",
            Map.of("type", "task", "title", "Zuweisung", "assignee", lea.id.toString()), null);
        assertEquals(400, created.status(), created.body());
        assertEquals(List.of("assignee.not_member"), created.violations());
        // An internal account has no portal.
        assertEquals(404, setup.agent().get("/api/v1/portal/projects").status());
        assertEquals(404, setup.agent().get("/api/v1/portal/projects/" + setup.shared() + "/tickets").status());
    }

    /** The forced change of the one time password comes first, as for every account. */
    @Test
    void thePortalWaitsForThePasswordChange() {
        Setup setup = setup();
        Portal portal = createPortal(setup, setup.nordwerk(), null, List.of(setup.shared()));
        Browser fresh = login(portal.id(), portal.username(), portal.oneTimePassword(), false);
        Response refused = fresh.get("/api/v1/portal/projects");
        assertEquals(403, refused.status(), refused.body());
        assertEquals("auth.password_change_required", refused.code());
    }

    /** The group "Portal" is created once with the default rules, and a company right never reaches a portal account. */
    @Test
    void theGroupPortalHoldsNoCompanyRight() {
        Setup setup = setup();
        Portal first = createPortal(setup, setup.nordwerk(), null, List.of(setup.shared()));
        Portal second = createPortal(setup, setup.suedtechnik(), null, List.of());
        List<Map<String, Object>> groups = jdbc.queryForList("select id, name from permissions_group where purpose = 'portal'");
        assertEquals(1, groups.size(), groups.toString());
        UUID group = (UUID) groups.get(0).get("id");
        assertTrue(((String) groups.get(0).get("name")).startsWith("Portal"));
        assertEquals(Set.of("ticket.read", "ticket.create"),
            Set.copyOf(jdbc.queryForList("select action_key from permissions_action where group_id = ?", String.class, group)));
        assertEquals(Set.of("READ:title", "READ:description", "READ:type", "READ:status", "READ:priority", "READ:tags",
                "READ:attachments", "WRITE:title", "WRITE:description", "WRITE:attachments"),
            Set.copyOf(jdbc.queryForList("select operation || ':' || field_key from permissions_field where group_id = ? and effect = 'ALLOW'",
                String.class, group)));
        for (Portal member : List.of(first, second)) {
            assertEquals(1L, jdbc.queryForObject("select count(*) from permissions_membership where group_id = ? and user_id = ?", Long.class,
                group, member.id()));
        }

        Browser rights = person(AdministrationRight.PERMISSIONS_MANAGE);
        // A company right on the group of the portal accounts is refused.
        Response action = rights.send("PUT", "/api/v1/permissions/groups/" + group + "/actions",
            Map.of("action", "accounts.manage", "enabled", true), null);
        assertEquals(409, action.status(), action.body());
        assertEquals("permissions.portal_company_right", action.code());
        // A portal account cannot join a group that holds a company right.
        Response created = rights.send("POST", "/api/v1/permissions/groups", Map.of("name", "Verwaltung " + COUNTER.incrementAndGet()), null);
        assertEquals(201, created.status(), created.body());
        String admins = created.json().get("id").asString();
        Response enabled = rights.send("PUT", "/api/v1/permissions/groups/" + admins + "/actions",
            Map.of("action", "jobs.manage", "enabled", true), null);
        assertTrue(enabled.status() == 200, enabled.body());
        Response joined = rights.send("PUT", "/api/v1/permissions/groups/" + admins + "/members/" + first.id(), null, null);
        assertEquals(409, joined.status(), joined.body());
        assertEquals("permissions.portal_company_right", joined.code());
    }

    /** Organization, contact and projects are checked against the helpdesk; the organization never changes. */
    @Test
    void creationChecksOrganizationContactAndProjects() {
        Setup setup = setup();
        Browser admin = setup.admin();
        Map<String, Object> body = new HashMap<>();
        body.put("username", "kunde-" + COUNTER.incrementAndGet());
        body.put("organizationId", setup.nordwerk());
        body.put("projectIds", List.of(setup.internal()));
        Response unreleased = admin.send("POST", "/api/v1/portal-accounts", body, null);
        assertEquals(400, unreleased.status(), unreleased.body());
        assertEquals(List.of("projectIds.not_released"), unreleased.violations());

        body.put("projectIds", List.of(setup.shared()));
        body.put("contactId", setup.suedtechnikContact());
        Response foreignContact = admin.send("POST", "/api/v1/portal-accounts", body, null);
        assertEquals(400, foreignContact.status(), foreignContact.body());
        assertEquals(List.of("contactId.not_of_organization"), foreignContact.violations());

        body.remove("contactId");
        body.put("organizationId", UUID.randomUUID());
        Response unknown = admin.send("POST", "/api/v1/portal-accounts", body, null);
        assertEquals(400, unknown.status(), unknown.body());
        assertEquals(List.of("organizationId.unknown"), unknown.violations());

        body.remove("organizationId");
        Response missing = admin.send("POST", "/api/v1/portal-accounts", body, null);
        assertEquals(400, missing.status(), missing.body());

        // Nothing of the refused attempts stays behind.
        assertEquals(0L, jdbc.queryForObject("select count(*) from identity_account where username = ?", Long.class, body.get("username")));

        // Without accounts.manage nothing happens.
        Browser stranger = person(AdministrationRight.HELPDESK_MANAGE);
        body.put("organizationId", setup.nordwerk());
        assertEquals(403, stranger.send("POST", "/api/v1/portal-accounts", body, null).status());
        assertEquals(403, stranger.get("/api/v1/portal-accounts").status());

        // The options list the released projects and the contacts of each organization.
        Response options = admin.get("/api/v1/portal-accounts/options");
        assertEquals(200, options.status(), options.body());
        JsonNode nordwerk = StreamSupport.stream(options.json().spliterator(), false)
            .filter(option -> option.get("id").asString().equals(setup.nordwerk().toString())).findFirst().orElseThrow();
        assertEquals(2, nordwerk.get("projects").size(), nordwerk.toString());
        assertEquals(setup.nordwerkContact().toString(), nordwerk.get("contacts").get(0).get("id").asString());

        // An internal account cannot become a portal account through the database either.
        assertEquals(1, jdbc.queryForList("select 1 from identity_account where id = ? and kind = 'internal'", setup.agent().id).size());
    }

    /** Case 6 and 7: removing a project or deactivating the account takes the sight away at once. */
    @Test
    void removingTheProjectOrDeactivatingTheAccountEndsTheSight() {
        Setup setup = setup();
        long own = request(setup, setup.shared(), "Drucker defekt", setup.nordwerk());
        long second = request(setup, setup.second(), "Zweites Projekt", setup.nordwerk());
        Portal portal = createPortal(setup, setup.nordwerk(), null, List.of(setup.shared()));
        Browser lea = portalSession(portal);
        String shared = "/api/v1/portal/projects/" + setup.shared() + "/tickets/" + own;
        String other = "/api/v1/portal/projects/" + setup.second() + "/tickets/" + second;
        assertEquals(200, lea.get(shared).status());
        assertEquals(404, lea.get(other).status());

        Map<String, Object> change = new HashMap<>();
        change.put("contactId", setup.nordwerkContact());
        change.put("projectIds", List.of(setup.second()));
        Response updated = setup.admin().send("PUT", "/api/v1/portal-accounts/" + portal.id(), change, null);
        assertEquals(200, updated.status(), updated.body());
        assertEquals(setup.nordwerkContact().toString(), updated.json().get("contactId").asString());
        assertEquals(List.of(setup.second().toString()),
            StreamSupport.stream(updated.json().get("projectIds").spliterator(), false).map(JsonNode::asString).toList());
        assertEquals(404, lea.get(shared).status());
        assertEquals(200, lea.get(other).status());

        // A contact of another organization and a project not released are refused.
        change.put("contactId", setup.suedtechnikContact());
        assertEquals(400, setup.admin().send("PUT", "/api/v1/portal-accounts/" + portal.id(), change, null).status());
        change.put("contactId", null);
        change.put("projectIds", List.of(setup.internal()));
        assertEquals(400, setup.admin().send("PUT", "/api/v1/portal-accounts/" + portal.id(), change, null).status());
        // An internal account is not a portal account.
        change.put("projectIds", List.of());
        Response internal = setup.admin().send("PUT", "/api/v1/portal-accounts/" + setup.agent().id, change, null);
        assertEquals(409, internal.status(), internal.body());
        assertEquals("account.not_portal", internal.code());

        // The project administration cannot add the portal account to a project not released to its organization.
        Response project = setup.admin().get("/api/v1/projects/" + setup.internal());
        Response added = setup.admin().send("PUT", "/api/v1/projects/" + setup.internal() + "/members/" + portal.id(), null, project.etag());
        assertEquals(400, added.status(), added.body());

        Response deactivated = setup.admin().send("POST", "/api/v1/accounts/" + portal.id() + "/deactivate", null, null);
        assertEquals(200, deactivated.status(), deactivated.body());
        int status = lea.get(other).status();
        assertTrue(status == 401 || status == 403, String.valueOf(status));
    }

    /** The list of portal accounts shows organization, contact and projects, and no internal accounts. */
    @Test
    void theListShowsOnlyPortalAccounts() {
        Setup setup = setup();
        Portal portal = createPortal(setup, setup.nordwerk(), setup.nordwerkContact(), List.of(setup.shared(), setup.second()));
        Response list = setup.admin().get("/api/v1/portal-accounts");
        assertEquals(200, list.status(), list.body());
        assertFalse(list.body().contains(setup.agent().id.toString()));
        JsonNode entry = StreamSupport.stream(list.json().spliterator(), false)
            .filter(node -> node.get("id").asString().equals(portal.id().toString())).findFirst().orElseThrow();
        assertEquals(setup.nordwerk().toString(), entry.get("organizationId").asString());
        assertEquals(setup.nordwerkContact().toString(), entry.get("contactId").asString());
        assertEquals(2, entry.get("projectIds").size());
        Response all = setup.admin().get("/api/v1/accounts");
        JsonNode account = StreamSupport.stream(all.json().spliterator(), false)
            .filter(node -> node.get("id").asString().equals(portal.id().toString())).findFirst().orElseThrow();
        assertEquals("portal", account.get("kind").asString());
    }
}
