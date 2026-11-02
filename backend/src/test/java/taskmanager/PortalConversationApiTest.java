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
 * TASK-076 (DEC-106): the conversation of the customer portal through the real HTTP interface with the real identity,
 * permissions, projects, tickets, files and helpdesk modules and a real PostgreSQL: creating a request with an offered type,
 * the conversation that shows only what the customer may see, posts with attachments, answers of the support through the
 * portal, the visibility of attachments (case 8 of docs/PERMISSIONS.md), the filters and the marker of new answers, and the
 * events through which posts and answers act on the service deadlines like the mail paths.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PortalConversationApiTest {
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
            return Files.createTempDirectory("taskmanager-portal-conversation");
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

    // ---------------------------------------------------------------- the events of the service deadlines

    /** Collects the helpdesk events the SLA module listens to, so a test sees that the portal paths publish them like the mail paths. */
    @org.springframework.boot.test.context.TestConfiguration
    static class Events {
        static final List<Object> SEEN = new java.util.concurrent.CopyOnWriteArrayList<>();

        @org.springframework.context.event.EventListener
        void customerAnswered(taskmanager.helpdesk.CustomerAnswered event) {
            SEEN.add(event);
        }

        @org.springframework.context.event.EventListener
        void replySent(taskmanager.helpdesk.ReplySent event) {
            SEEN.add(event);
        }

        @org.springframework.context.event.EventListener
        void customerChanged(taskmanager.helpdesk.TicketCustomerChanged event) {
            SEEN.add(event);
        }

        static <T> List<T> of(Class<T> type, UUID ticket) {
            return SEEN.stream().filter(type::isInstance).map(type::cast).filter(event -> switch (event) {
                case taskmanager.helpdesk.CustomerAnswered e -> e.ticketId().equals(ticket);
                case taskmanager.helpdesk.ReplySent e -> e.ticketId().equals(ticket);
                case taskmanager.helpdesk.TicketCustomerChanged e -> e.ticketId().equals(ticket);
                default -> false;
            }).toList();
        }
    }

    // ---------------------------------------------------------------- helpers of the conversation

    private String offerFirstType(UUID project) {
        String type = jdbc.queryForObject("""
                select c.document -> 'ticketTypes' -> 0 ->> 'key' from project_configuration c join project p
                on p.id = c.project_id and p.configuration_version = c.version where p.id = ?
                """, String.class, project);
        jdbc.update("""
                update project_configuration c set document = jsonb_set(document, '{ticketTypes,0,portal}', 'true')
                from project p where p.id = c.project_id and p.configuration_version = c.version and p.id = ?
                """, project);
        return type;
    }

    private UUID ticketId(UUID project, long number) {
        return jdbc.queryForObject("select id from ticket where project_id = ? and number = ?", UUID.class, project, number);
    }

    private static String base(UUID project, long number) {
        return "/api/v1/portal/projects/" + project + "/tickets/" + number;
    }

    private static List<String> kinds(Response conversation) {
        assertEquals(200, conversation.status(), conversation.body());
        return StreamSupport.stream(conversation.json().spliterator(), false)
            .map(entry -> entry.get("kind").asString() + ":" + entry.get("author").asString()).toList();
    }

    private UUID mailbox(Setup setup, UUID project) {
        UUID id = UUID.randomUUID();
        UUID company = companies.find().orElseThrow();
        jdbc.update("""
                insert into helpdesk_mailbox (id, company_id, name, enabled, project_id, ticket_type, imap_host, imap_port, imap_security,
                    username, folder, smtp_host, smtp_port, smtp_security, address, max_bytes, version, created_at, created_by, updated_at, updated_by)
                values (?, ?, ?, false, ?, 'task', 'imap.example', 993, 'tls', 'support', 'INBOX', 'smtp.example', 465, 'tls',
                    'support@firma.example', 1000000, 1, now(), ?, now(), ?)
                """, id, company, "Postfach " + id, project, setup.agent().id, setup.agent().id);
        return id;
    }

    private void mail(UUID mailbox, UUID project, UUID ticket, UUID customer, String subject, int uid) {
        jdbc.update("""
                insert into helpdesk_message (id, company_id, mailbox_id, project_id, uid_validity, uid, received_at, from_address, subject, body,
                    size_bytes, state, ticket_id, customer_id, version)
                values (?, ?, ?, ?, 1, ?, now() - interval '1 hour', 'lea@nordwerk.example', ?, 'Text der Mail', 10, 'ticket', ?, ?, 1)
                """, UUID.randomUUID(), companies.find().orElseThrow(), mailbox, project, uid, subject, ticket, customer);
    }

    private void reply(UUID mailbox, UUID project, UUID ticket, UUID contact, String subject, String state, UUID by) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into helpdesk_reply (id, company_id, project_id, ticket_id, mailbox_id, contact_id, from_address, to_address, subject, body,
                    message_id, state, job_id, sends, sent_at, created_at, created_by, updated_at, updated_by, version)
                values (?, ?, ?, ?, ?, ?, 'support@firma.example', 'lea@nordwerk.example', ?, 'Antwort', ?, ?, ?, 1,
                    case when ? = 'sent' then now() - interval '30 minutes' end, now() - interval '40 minutes', ?, now(), ?, 1)
                """, id, companies.find().orElseThrow(), project, ticket, mailbox, contact, subject, "<" + id + "@firma.example>", state,
                UUID.randomUUID(), state, by, by);
    }

    // ---------------------------------------------------------------- tests

    /** Rule 1: a request needs a type the project offers; customer and contact come from the account, not the form. */
    @Test
    void aPortalAccountCreatesRequestsOnlyWithOfferedTypes() {
        Setup setup = setup();
        Portal lea = createPortal(setup, setup.nordwerk(), setup.nordwerkContact(), List.of(setup.shared()));
        Browser portal = portalSession(lea);
        String create = "/api/v1/portal/projects/" + setup.shared() + "/tickets";
        Response refused = portal.send("POST", create, Map.of("type", "task", "title", "Drucker defekt"), null);
        assertEquals(400, refused.status(), refused.body());
        assertTrue(refused.violations().contains("type.not_offered"), refused.body());

        String type = offerFirstType(setup.shared());
        Response projects = portal.get("/api/v1/portal/projects");
        assertEquals(200, projects.status(), projects.body());
        JsonNode project = projects.json().get(0);
        assertEquals(type, project.get("ticketTypes").get(0).get("key").asString());
        assertEquals(1, project.get("ticketTypes").size());
        assertTrue(StreamSupport.stream(project.get("statuses").spliterator(), false)
            .anyMatch(status -> status.get("category").asString().equals("done")), projects.body());

        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("title", "Drucker defekt");
        body.put("description", "Seit heute Morgen");
        body.put("customerId", setup.suedtechnik());
        Response created = portal.send("POST", create, body, null);
        assertEquals(201, created.status(), created.body());
        long number = created.json().get("number").asLong();
        UUID ticket = ticketId(setup.shared(), number);
        Map<String, Object> link = jdbc.queryForMap("select customer_id, contact_id from helpdesk_ticket_customer where ticket_id = ?", ticket);
        assertEquals(setup.nordwerk(), link.get("customer_id"), "the organization of the account, whatever the form says");
        assertEquals(setup.nordwerkContact(), link.get("contact_id"));
        assertEquals(1, Events.of(taskmanager.helpdesk.TicketCustomerChanged.class, ticket).size(), "the deadlines start");
        assertEquals(List.of(number), numbers(portal.get("/api/v1/portal/projects/" + setup.shared() + "/tickets")));

        // Invalid input, the staff and another organization.
        Response empty = portal.send("POST", create, Map.of("type", type, "title", " "), null);
        assertEquals(400, empty.status(), empty.body());
        assertTrue(empty.violations().contains("title.invalid"), empty.body());
        assertEquals(404, setup.agent().send("POST", create, Map.of("type", type, "title", "Intern"), null).status());
        Portal tom = createPortal(setup, setup.suedtechnik(), null, List.of(setup.shared()));
        Browser other = portalSession(tom);
        assertEquals(404, other.get(base(setup.shared(), number) + "/conversation").status());
        assertEquals(404, other.send("POST", base(setup.shared(), number) + "/conversation", Map.of("body", "Hallo"), null).status());
        assertEquals(404, other.send("POST", "/api/v1/portal/projects/" + setup.second() + "/tickets", Map.of("type", type, "title", "x"), null).status(),
            "a project the account is not a member of");
    }

    /** Rule 2 and case 8: the conversation shows mails of the organization, sent mail answers and posts, nothing internal. */
    @Test
    void theConversationShowsOnlyWhatTheCustomerMaySee() {
        Setup setup = setup();
        grant(setup.agent().id, setup.shared(), "helpdesk.reply");
        Portal lea = createPortal(setup, setup.nordwerk(), setup.nordwerkContact(), List.of(setup.shared()));
        Browser portal = portalSession(lea);
        long number = request(setup, setup.shared(), "Anmeldung prüfen", setup.nordwerk());
        UUID ticket = ticketId(setup.shared(), number);
        UUID mailbox = mailbox(setup, setup.shared());
        mail(mailbox, setup.shared(), ticket, setup.nordwerk(), "Erste Mail", 1);
        mail(mailbox, setup.shared(), ticket, setup.suedtechnik(), "Mail einer anderen Organisation", 2);
        reply(mailbox, setup.shared(), ticket, setup.nordwerkContact(), "Gesendet", "sent", setup.agent().id);
        reply(mailbox, setup.shared(), ticket, setup.nordwerkContact(), "Gescheitert", "failed", setup.agent().id);
        reply(mailbox, setup.shared(), ticket, setup.nordwerkContact(), "Wartet", "pending", setup.agent().id);

        String internal = "/api/v1/projects/" + setup.shared() + "/tickets/" + number + "/helpdesk/portal-posts";
        Response answer = setup.agent().send("POST", internal, Map.of("body", "Wir prüfen das."), null);
        assertEquals(201, answer.status(), answer.body());
        UUID post = UUID.fromString(answer.json().get("id").asString());
        List<taskmanager.helpdesk.ReplySent> sent = Events.of(taskmanager.helpdesk.ReplySent.class, ticket);
        assertEquals(1, sent.size(), "a portal answer counts as first response");
        assertEquals(post, sent.get(0).replyId());

        Response conversation = portal.get(base(setup.shared(), number) + "/conversation");
        assertEquals(List.of("mail:customer", "reply:support", "post:support"), kinds(conversation));
        assertFalse(conversation.body().contains("Gescheitert"), conversation.body());
        assertFalse(conversation.body().contains("Wartet"), conversation.body());
        assertFalse(conversation.body().contains("anderen Organisation"), conversation.body());
        assertFalse(conversation.body().contains(setup.agent().id.toString()), "no account of the staff");
        // The internal routes stay closed to the portal account, the portal routes to the staff.
        assertEquals(404, portal.get(internal).status());
        assertEquals(404, setup.agent().get(base(setup.shared(), number) + "/conversation").status());
        Response posts = setup.agent().get(internal);
        assertEquals(200, posts.status(), posts.body());
        assertEquals("support", posts.json().get(0).get("authorKind").asString());

        // No portal answer without customer or without helpdesk.reply.
        long without = request(setup, setup.shared(), "Ohne Kunde", null);
        Response noCustomer = setup.agent().send("POST", "/api/v1/projects/" + setup.shared() + "/tickets/" + without + "/helpdesk/portal-posts",
            Map.of("body", "Hallo"), null);
        assertEquals(409, noCustomer.status(), noCustomer.body());
        assertEquals("helpdesk.portal_no_customer", noCustomer.code());
        Browser colleague = person();
        jdbc.update("insert into project_member (project_id, account_id, added_at, added_by) values (?, ?, now(), ?)", setup.shared(), colleague.id, colleague.id);
        grant(colleague.id, setup.shared(), "ticket.read", "READ:*");
        assertEquals(403, colleague.send("POST", internal, Map.of("body", "Hallo"), null).status());
    }

    /** Rules 3 and 7: posts carry own customer visible uploads; an internal attachment answers 404 in the portal until it is switched. */
    @Test
    void postsCarryOwnUploadsAndInternalAttachmentsStayHidden() {
        Setup setup = setup();
        Portal lea = createPortal(setup, setup.nordwerk(), setup.nordwerkContact(), List.of(setup.shared()));
        Portal ben = createPortal(setup, setup.nordwerk(), null, List.of(setup.shared()));
        Browser portal = portalSession(lea);
        Browser colleague = portalSession(ben);
        long number = request(setup, setup.shared(), "Rechnung fehlt", setup.nordwerk());
        UUID ticket = ticketId(setup.shared(), number);
        String files = base(setup.shared(), number) + "/attachments";

        Response own = portal.upload(files, "rechnung.pdf", "%PDF-1.4 Rechnung".getBytes(StandardCharsets.UTF_8));
        assertEquals(201, own.status(), own.body());
        UUID ownId = UUID.fromString(own.json().get("id").asString());
        String internalFiles = "/api/v1/projects/" + setup.shared() + "/tickets/" + number + "/attachments";
        Response hidden = setup.agent().upload(internalFiles, "notiz.txt", "intern".getBytes(StandardCharsets.UTF_8));
        assertEquals(201, hidden.status(), hidden.body());
        assertEquals("internal", hidden.json().get("visibility").asString());
        UUID hiddenId = UUID.fromString(hidden.json().get("id").asString());

        Response listed = portal.get(files);
        assertEquals(200, listed.status(), listed.body());
        assertEquals(1, listed.json().size());
        assertEquals(ownId.toString(), listed.json().get(0).get("id").asString());
        assertEquals(404, portal.get(files + "/" + hiddenId).status(), "an internal attachment does not exist in the portal");
        assertEquals(200, portal.get(files + "/" + ownId).status());

        String conversation = base(setup.shared(), number) + "/conversation";
        Response foreign = portal.send("POST", conversation, Map.of("body", "Anbei", "attachmentIds", List.of(hiddenId)), null);
        assertEquals(400, foreign.status(), foreign.body());
        assertTrue(foreign.violations().contains("attachmentIds.invalid"));
        Response notHers = colleague.send("POST", conversation, Map.of("body", "Anbei", "attachmentIds", List.of(ownId)), null);
        assertEquals(400, notHers.status(), "only the files the account uploaded itself");
        Response posted = portal.send("POST", conversation, Map.of("body", "Anbei die Rechnung", "attachmentIds", List.of(ownId)), null);
        assertEquals(201, posted.status(), posted.body());
        assertEquals("rechnung.pdf", posted.json().get("attachments").get(0).get("name").asString());
        assertEquals(1, Events.of(taskmanager.helpdesk.CustomerAnswered.class, ticket).size(), "a post counts as answer of the customer");
        Response again = portal.send("POST", conversation, Map.of("body", "Nochmal", "attachmentIds", List.of(ownId)), null);
        assertEquals(400, again.status(), "a file belongs to one post");
        Response tooLong = portal.send("POST", conversation, Map.of("body", "x".repeat(100_001)), null);
        assertEquals(400, tooLong.status());

        // The colleague of the same organization sees the post, not as its own.
        Response seen = colleague.get(conversation);
        assertEquals(List.of("post:customer"), kinds(seen));
        assertFalse(seen.json().get(0).get("own").asBoolean());
        assertTrue(portal.get(conversation).json().get(0).get("own").asBoolean());

        // Switching the internal attachment makes it reachable; a wrong value is refused.
        Response wrong = setup.agent().send("PATCH", internalFiles + "/" + hiddenId, Map.of("visibility", "public"), null);
        assertEquals(400, wrong.status(), wrong.body());
        Response switched = setup.agent().send("PATCH", internalFiles + "/" + hiddenId, Map.of("visibility", "customer"), null);
        assertEquals(200, switched.status(), switched.body());
        assertEquals("customer", switched.json().get("visibility").asString());
        assertEquals(200, portal.get(files + "/" + hiddenId).status());
        assertEquals(2, portal.get(files).json().size());
        assertEquals(404, portal.send("PATCH", internalFiles + "/" + hiddenId, Map.of("visibility", "internal"), null).status());
    }

    /** Rule 6: filters by status, open or done and text, and the marker of new answers of the support until the request is opened. */
    @Test
    void theListFiltersAndMarksNewAnswers() {
        Setup setup = setup();
        grant(setup.agent().id, setup.shared(), "helpdesk.reply");
        Portal lea = createPortal(setup, setup.nordwerk(), setup.nordwerkContact(), List.of(setup.shared()));
        Browser portal = portalSession(lea);
        long open = request(setup, setup.shared(), "Drucker defekt", setup.nordwerk());
        long done = request(setup, setup.shared(), "Zugang gesperrt", setup.nordwerk());
        jdbc.update("update ticket set status = 'done' where id = ?", ticketId(setup.shared(), done));
        String list = "/api/v1/portal/projects/" + setup.shared() + "/tickets";
        assertEquals(List.of(done), numbers(portal.get(list + "?state=done")));
        assertEquals(List.of(open), numbers(portal.get(list + "?state=open")));
        assertEquals(List.of(open), numbers(portal.get(list + "?status=new")));
        assertEquals(List.of(done), numbers(portal.get(list + "?q=ZUGANG")));
        assertEquals(400, portal.get(list + "?state=closed").status());

        String activity = "/api/v1/portal/projects/" + setup.shared() + "/activity";
        assertTrue(StreamSupport.stream(portal.get(activity).json().spliterator(), false).noneMatch(row -> row.get("unread").asBoolean()));
        assertEquals(201, setup.agent().send("POST", "/api/v1/projects/" + setup.shared() + "/tickets/" + open + "/helpdesk/portal-posts",
            Map.of("body", "Techniker kommt morgen."), null).status());
        JsonNode rows = portal.get(activity).json();
        JsonNode row = StreamSupport.stream(rows.spliterator(), false).filter(r -> r.get("number").asLong() == open).findFirst().orElseThrow();
        assertTrue(row.get("unread").asBoolean(), rows.toString());
        assertNotNull(row.get("lastActivityAt").asString());
        assertEquals(204, portal.send("PUT", base(setup.shared(), open) + "/seen", null, null).status());
        row = StreamSupport.stream(portal.get(activity).json().spliterator(), false).filter(r -> r.get("number").asLong() == open).findFirst().orElseThrow();
        assertFalse(row.get("unread").asBoolean());
    }

    /** Two posts with the same file at the same time: exactly one carries it. */
    @Test
    void concurrentPostsWithTheSameFileLinkItOnce() throws Exception {
        Setup setup = setup();
        Portal lea = createPortal(setup, setup.nordwerk(), setup.nordwerkContact(), List.of(setup.shared()));
        Browser portal = portalSession(lea);
        long number = request(setup, setup.shared(), "Gleichzeitig", setup.nordwerk());
        Response own = portal.upload(base(setup.shared(), number) + "/attachments", "bild.png", new byte[] { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3 });
        assertEquals(201, own.status(), own.body());
        UUID file = UUID.fromString(own.json().get("id").asString());
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            List<java.util.concurrent.Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(pool.submit(() -> portal.send("POST", base(setup.shared(), number) + "/conversation",
                    Map.of("body", "Bild", "attachmentIds", List.of(file)), null).status()));
            }
            List<Integer> statuses = new ArrayList<>();
            for (var result : results) {
                statuses.add(result.get());
            }
            assertEquals(1, statuses.stream().filter(status -> status == 201).count(), statuses.toString());
            assertTrue(statuses.stream().allMatch(status -> status == 201 || status == 400 || status == 503), statuses.toString());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from helpdesk_portal_post_attachment where attachment_id = ?", Integer.class, file));
    }
}
