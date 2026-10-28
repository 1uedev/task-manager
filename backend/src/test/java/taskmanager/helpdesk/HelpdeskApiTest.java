package taskmanager.helpdesk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.FlagTerm;
import java.io.IOException;
import java.net.ServerSocket;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterAll;
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
import taskmanager.helpdesk.internal.MailIntake;
import taskmanager.identity.account.AccountService;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.CompanyContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-057: the helpdesk intake through the real HTTP interface, a real IMAP server in the test process (GreenMail), the
 * real permissions, projects, tickets and files modules and a real PostgreSQL. A fetch is started directly instead of by
 * the job executor, so each test decides when mails arrive. Business actions and field rules are granted in the tables.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HelpdeskApiTest {
    private static final String DATABASE = freshDatabase();
    private static final Path STORE = newStore();
    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII));
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final String MAIL_PASSWORD = "Mailbox-Secret-4711";
    private static final String AUTHSERV = "mx.firma.example";
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final GreenMail MAIL = startMailServer();
    private static final ExecutorService POOL = Executors.newCachedThreadPool();

    private static String freshDatabase() {
        try {
            return TestDatabase.createEmptyDatabase();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path newStore() {
        try {
            return Files.createTempDirectory("taskmanager-helpdesk-api");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static GreenMail startMailServer() {
        GreenMail server = new GreenMail(ServerSetupTest.IMAP.dynamicPort());
        server.start();
        return server;
    }

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE);
        registry.add("spring.datasource.username", TestDatabase::username);
        registry.add("spring.datasource.password", TestDatabase::password);
        registry.add("taskmanager.files.directory", STORE::toString);
        registry.add("taskmanager.helpdesk.secret-key", () -> KEY);
        registry.add("taskmanager.helpdesk.timeout", () -> "5s");
    }

    @AfterAll
    static void stop() {
        MAIL.stop();
        POOL.shutdownNow();
    }

    @Value("${local.server.port}") int port;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyContext companies;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MailIntake intake;

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
        var issued = accounts.create("test", "helpdesk-" + COUNTER.incrementAndGet(), null);
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

    /** Grants actions and field rules of the resource type {@code ticket} in a group of its own, for one project. */
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

    /** A project with the given members, a mailbox account on the mail server and an administrator of the helpdesk. */
    private record Setup(UUID project, Browser admin, GreenMailUser account, String address, UUID mailbox, UUID customer, UUID contact,
            String contactAddress) {
    }

    private Setup setup(Browser... members) {
        int n = COUNTER.incrementAndGet();
        Browser admin = person(AdministrationRight.HELPDESK_MANAGE, AdministrationRight.PROJECTS_MANAGE);
        Response created = admin.send("POST", "/api/v1/projects", Map.of("key", "HD" + n, "name", "Support " + n), null);
        assertEquals(201, created.status(), created.body());
        UUID project = UUID.fromString(created.json().get("id").asString());
        String version = created.etag();
        for (Browser member : members) {
            Response added = admin.send("PUT", "/api/v1/projects/" + project + "/members/" + member.id, null, version);
            assertEquals(200, added.status(), added.body());
            version = added.etag();
        }
        String address = "support" + n + "@firma.example";
        GreenMailUser account = MAIL.setUser(address, "support" + n, MAIL_PASSWORD);
        Response customer = admin.send("POST", "/api/v1/helpdesk/customers", Map.of("name", "Nordwerk " + n, "projects", List.of(project)), null);
        assertEquals(201, customer.status(), customer.body());
        UUID customerId = UUID.fromString(customer.json().get("id").asString());
        String contactAddress = "lea" + n + "@nordwerk.example";
        Response contact = admin.send("POST", "/api/v1/helpdesk/contacts",
            Map.of("customerId", customerId, "name", "Lea Lang", "addresses", List.of(contactAddress.toUpperCase())), null);
        assertEquals(201, contact.status(), contact.body());
        assertEquals(contactAddress, contact.json().get("addresses").get(0).asString(), "addresses are stored in lower case");
        Response mailbox = admin.send("POST", "/api/v1/helpdesk/mailboxes", mailboxRequest("Support " + n, project, "support" + n,
            MAIL_PASSWORD, MAIL.getImap().getPort(), AUTHSERV), null);
        assertEquals(201, mailbox.status(), mailbox.body());
        return new Setup(project, admin, account, address, UUID.fromString(mailbox.json().get("id").asString()), customerId,
            UUID.fromString(contact.json().get("id").asString()), contactAddress);
    }

    private static Map<String, Object> mailboxRequest(String name, UUID project, String username, String password, int imapPort, String authserv) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("projectId", project);
        body.put("ticketType", "task");
        body.put("imapHost", "127.0.0.1");
        body.put("imapPort", imapPort);
        body.put("imapSecurity", "none");
        body.put("username", username);
        if (password != null) {
            body.put("password", password);
        }
        body.put("folder", "INBOX");
        if (authserv != null) {
            body.put("authservId", authserv);
        }
        return body;
    }

    // ---------------------------------------------------------------- mails

    private static String confirmed(String fromDomain) {
        return "Authentication-Results: " + AUTHSERV + "; spf=pass smtp.mailfrom=" + fromDomain + "; dkim=pass header.d=" + fromDomain
            + "; dmarc=pass (p=reject) header.from=" + fromDomain + "\r\n";
    }

    private static void deliver(Setup setup, String headers, String subject, String body) {
        String raw = headers + "From: Lea Lang <" + setup.contactAddress() + ">\r\nTo: " + setup.address() + "\r\nSubject: " + subject
            + "\r\nMessage-ID: <" + UUID.randomUUID() + "@nordwerk.example>\r\nDate: Wed, 28 Oct 2026 10:00:00 +0100\r\n"
            + "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n" + body + "\r\n";
        setup.account().deliver(GreenMailUtil.newMimeMessage(raw));
    }

    private static void deliverRaw(Setup setup, String raw) {
        setup.account().deliver(GreenMailUtil.newMimeMessage(raw));
    }

    private static String withFiles(String headers, String from, String to, String subject) {
        String pdf = Base64.getMimeEncoder().encodeToString("%PDF-1.4 Angebot".getBytes(StandardCharsets.US_ASCII));
        String exe = Base64.getMimeEncoder().encodeToString(new byte[] {'M', 'Z', 1, 2, 3});
        return headers + "From: " + from + "\r\nTo: " + to + "\r\nSubject: " + subject + "\r\nMessage-ID: <" + UUID.randomUUID()
            + "@nordwerk.example>\r\nMIME-Version: 1.0\r\nContent-Type: multipart/mixed; boundary=\"b1\"\r\n\r\n"
            + "--b1\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n<p>Hallo <b>Support</b>,</p><script>alert(1)</script><p>bitte pr&uuml;fen.</p>\r\n"
            + "--b1\r\nContent-Type: application/pdf\r\nContent-Disposition: attachment; filename=\"angebot.pdf\"\r\nContent-Transfer-Encoding: base64\r\n\r\n" + pdf + "\r\n"
            + "--b1\r\nContent-Type: application/octet-stream\r\nContent-Disposition: attachment; filename=\"tool.exe\"\r\nContent-Transfer-Encoding: base64\r\n\r\n" + exe + "\r\n"
            + "--b1--\r\n";
    }

    private List<Map<String, Object>> messages(Setup setup) {
        return jdbc.queryForList("select * from helpdesk_message where mailbox_id = ? order by uid", setup.mailbox());
    }

    private long tickets(Setup setup) {
        return jdbc.queryForObject("select count(*) from ticket where project_id = ?", Long.class, setup.project());
    }

    private int unseen(Setup setup) throws Exception {
        Properties settings = new Properties();
        Store store = Session.getInstance(settings).getStore("imap");
        store.connect("127.0.0.1", MAIL.getImap().getPort(), setup.account().getLogin(), MAIL_PASSWORD);
        try {
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            int count = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false)).length;
            folder.close(false);
            return count;
        } finally {
            store.close();
        }
    }

    private Browser reviewer(Setup setup) {
        Browser reviewer = person();
        Response added = setup.admin().send("PUT", "/api/v1/projects/" + setup.project() + "/members/" + reviewer.id, null,
            setup.admin().get("/api/v1/projects/" + setup.project()).etag());
        assertEquals(200, added.status(), added.body());
        grant(reviewer.id, setup.project(), "helpdesk.review", "ticket.read", "ticket.edit", "READ:*", "WRITE:customer", "WRITE:contact");
        return reviewer;
    }

    // ---------------------------------------------------------------- the intake

    @Test
    void aConfirmedContactGetsExactlyOneTicketWithCustomerTextAndAttachments() throws Exception {
        Setup setup = setup();
        deliverRaw(setup, withFiles(confirmed("nordwerk.example"), "Lea Lang <" + setup.contactAddress() + ">", setup.address(), "Drucker defekt"));
        intake.fetch(setup.mailbox());

        List<Map<String, Object>> stored = messages(setup);
        assertEquals(1, stored.size());
        assertEquals("ticket", stored.get(0).get("state"));
        assertNull(stored.get(0).get("raw"), "the raw mail is dropped once it became a ticket");
        assertEquals(1, tickets(setup));
        Map<String, Object> ticket = jdbc.queryForMap("select * from ticket where project_id = ?", setup.project());
        assertEquals("Drucker defekt", ticket.get("title"));
        String description = (String) ticket.get("description");
        assertTrue(description.contains("Hallo Support,") && description.contains("bitte prüfen."), description);
        assertFalse(description.contains("alert") || description.contains("<"), "no markup and no script: " + description);
        assertEquals(new UUID(0, 0x4d41494cL), ticket.get("created_by"), "the intake is the actor");
        assertEquals(List.of("angebot.pdf"), jdbc.queryForList("select name from file_attachment where ticket_id = ?", String.class, ticket.get("id")));
        String notes = jdbc.queryForObject("select attachments::text from helpdesk_message where id = ?", String.class, stored.get(0).get("id"));
        assertTrue(notes.contains("attachment.type_blocked") && notes.contains("tool.exe"), notes);
        assertEquals(0, unseen(setup), "the mail is marked as seen");

        // A second fetch and a fetch that lost its position add nothing.
        intake.fetch(setup.mailbox());
        jdbc.update("update helpdesk_mailbox set last_uid = 0 where id = ?", setup.mailbox());
        intake.fetch(setup.mailbox());
        assertEquals(1, messages(setup).size());
        assertEquals(1, tickets(setup));

        // Customer, contact and mail are in the ticket and its history for a reader of these fields.
        Browser reader = reviewer(setup);
        long number = (Long) ticket.get("number");
        Response view = reader.get("/api/v1/projects/" + setup.project() + "/tickets/" + number + "/helpdesk");
        assertEquals(200, view.status(), view.body());
        assertEquals(setup.customer().toString(), view.json().get("customer").get("id").asString());
        assertEquals("Lea Lang", view.json().get("contact").get("name").asString());
        assertEquals("Drucker defekt", view.json().get("mails").get(0).get("subject").asString());
        Response history = reader.get("/api/v1/projects/" + setup.project() + "/tickets/" + number + "/history");
        List<String> fields = new ArrayList<>();
        history.json().forEach(entry -> entry.get("changes").forEach(change -> fields.add(change.get("field").asString())));
        assertTrue(fields.containsAll(List.of("customer", "contact", "mail", "attachments")), fields.toString());
    }

    @Test
    void twoFetchesAtTheSameTimeCreateOneTicketPerMail() throws Exception {
        Setup setup = setup();
        for (int i = 0; i < 3; i++) {
            deliver(setup, confirmed("nordwerk.example"), "Anfrage " + i, "Text " + i);
        }
        List<Future<Object>> runs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            runs.add(POOL.submit((Callable<Object>) () -> {
                intake.fetch(setup.mailbox());
                return null;
            }));
        }
        for (Future<Object> run : runs) {
            run.get();
        }
        assertEquals(3, messages(setup).size());
        assertEquals(3, tickets(setup));
    }

    @Test
    void whatIsNotAConfirmedKnownContactWaitsForAPerson() {
        Setup setup = setup();
        // unknown sender
        deliverRaw(setup, confirmed("fremd.example") + "From: Max <max@fremd.example>\r\nTo: " + setup.address()
            + "\r\nSubject: Unbekannt\r\nMessage-ID: <u1@fremd.example>\r\n\r\nHallo\r\n");
        // known address, no confirmation at all: a forged From
        deliver(setup, "", "Gefälscht", "Bitte überweisen");
        // the own server says fail on top, a forged pass with the same id below
        deliver(setup, "Authentication-Results: " + AUTHSERV + "; dmarc=fail header.from=nordwerk.example\r\n" + confirmed("nordwerk.example"),
            "Doppelt", "Text");
        // pass for another domain than the From address
        deliver(setup, confirmed("andere.example"), "Andere Domain", "Text");
        // a contact of another customer in copy
        Response other = setup.admin().send("POST", "/api/v1/helpdesk/customers", Map.of("name", "Südtechnik " + COUNTER.incrementAndGet()), null);
        String mia = "mia" + COUNTER.incrementAndGet() + "@suedtechnik.example";
        assertEquals(201, setup.admin().send("POST", "/api/v1/helpdesk/contacts",
            Map.of("customerId", other.json().get("id").asString(), "name", "Mia", "addresses", List.of(mia)), null).status());
        deliver(setup, confirmed("nordwerk.example") + "Cc: " + mia + "\r\n", "Mit Kopie", "Text");
        intake.fetch(setup.mailbox());

        List<Map<String, Object>> stored = messages(setup);
        assertEquals(List.of("sender_unknown", "sender_unconfirmed", "sender_unconfirmed", "sender_unconfirmed", "foreign_customer"),
            stored.stream().map(row -> row.get("reason")).toList());
        assertTrue(stored.stream().allMatch(row -> row.get("state").equals("review") && row.get("raw") != null));
        assertEquals(0, tickets(setup));
    }

    @Test
    void automaticMailsGetNoTicketAndALargeMailWaitsWithoutContent() {
        Setup setup = setup();
        deliver(setup, confirmed("nordwerk.example") + "Auto-Submitted: auto-replied\r\n", "Abwesend", "Ich bin im Urlaub");
        deliver(setup, confirmed("nordwerk.example") + "Precedence: bulk\r\n", "Newsletter", "Angebote");
        jdbc.update("update helpdesk_mailbox set max_bytes = 600 where id = ?", setup.mailbox());
        deliver(setup, confirmed("nordwerk.example"), "=?UTF-8?Q?Gro=C3=9F?=", "x".repeat(2000));
        intake.fetch(setup.mailbox());

        List<Map<String, Object>> stored = messages(setup);
        assertEquals(List.of("automatic", "automatic", "review"), stored.stream().map(row -> row.get("state")).toList());
        assertEquals("too_large", stored.get(2).get("reason"));
        assertNull(stored.get(2).get("raw"));
        assertNull(stored.get(2).get("body"));
        assertEquals("Groß", stored.get(2).get("subject"));
        assertEquals(0, tickets(setup));
    }

    @Test
    void aNewUidValidityAddsNoDuplicatesAndLeavesANotice() throws Exception {
        Setup setup = setup();
        deliver(setup, confirmed("nordwerk.example"), "Erste", "Text");
        intake.fetch(setup.mailbox());
        assertEquals(1, tickets(setup));
        // The server reports another UIDVALIDITY than the stored one; the same mail must not arrive again.
        jdbc.update("update helpdesk_mailbox set uid_validity = uid_validity + 1 where id = ?", setup.mailbox());
        jdbc.update("update helpdesk_message set uid_validity = uid_validity + 1 where mailbox_id = ?", setup.mailbox());
        // The mail counts as unread again, as after a rebuild of the folder on the server.
        Store store = Session.getInstance(new Properties()).getStore("imap");
        store.connect("127.0.0.1", MAIL.getImap().getPort(), setup.account().getLogin(), MAIL_PASSWORD);
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        folder.setFlags(1, folder.getMessageCount(), new Flags(Flags.Flag.SEEN), false);
        folder.close(false);
        store.close();
        deliver(setup, confirmed("nordwerk.example"), "Zweite", "Text");
        intake.fetch(setup.mailbox());
        assertEquals(2, tickets(setup), "only the new mail became a ticket");
        Response list = setup.admin().get("/api/v1/helpdesk/mailboxes");
        JsonNode mailbox = find(list.json(), setup.mailbox());
        assertEquals("helpdesk.uidvalidity_changed", mailbox.get("status").get("notice").asString());
    }

    // ---------------------------------------------------------------- mailboxes and secrets

    @Test
    void thePasswordNeverLeavesTheServerAndFailuresShowInTheList() throws Exception {
        Setup setup = setup();
        Response list = setup.admin().get("/api/v1/helpdesk/mailboxes");
        assertEquals(200, list.status(), list.body());
        assertFalse(list.body().contains(MAIL_PASSWORD));
        assertFalse(list.body().contains("\"password\""), list.body());
        assertTrue(find(list.json(), setup.mailbox()).get("passwordSet").asBoolean());
        byte[] cipher = jdbc.queryForObject("select password_cipher from helpdesk_mailbox where id = ?", byte[].class, setup.mailbox());
        assertFalse(new String(cipher, StandardCharsets.ISO_8859_1).contains(MAIL_PASSWORD), "stored encrypted");
        String audit = String.join(" ", jdbc.queryForList("select coalesce(detail, '') from helpdesk_audit_event where target_id = ?", String.class, setup.mailbox()));
        assertFalse(audit.contains(MAIL_PASSWORD), audit);

        Response test = setup.admin().send("POST", "/api/v1/helpdesk/mailboxes/" + setup.mailbox() + "/test", null, null);
        assertEquals(200, test.status(), test.body());
        assertTrue(test.json().get("ok").asBoolean(), test.body());

        // A wrong password and an unreachable server are visible with their code.
        Map<String, Object> wrong = mailboxRequest("Falsch " + COUNTER.incrementAndGet(), setup.project(), setup.account().getLogin(), "nicht-richtig",
            MAIL.getImap().getPort(), AUTHSERV);
        UUID wrongId = UUID.fromString(setup.admin().send("POST", "/api/v1/helpdesk/mailboxes", wrong, null).json().get("id").asString());
        intake.fetch(wrongId);
        Map<String, Object> closed = mailboxRequest("Geschlossen " + COUNTER.incrementAndGet(), setup.project(), "x", "y", freePort(), null);
        UUID closedId = UUID.fromString(setup.admin().send("POST", "/api/v1/helpdesk/mailboxes", closed, null).json().get("id").asString());
        intake.fetch(closedId);
        JsonNode mailboxes = setup.admin().get("/api/v1/helpdesk/mailboxes").json();
        assertEquals("helpdesk.authentication_failed", find(mailboxes, wrongId).get("status").get("errorCode").asString());
        assertEquals("helpdesk.connection_failed", find(mailboxes, closedId).get("status").get("errorCode").asString());
        Response failed = setup.admin().send("POST", "/api/v1/helpdesk/mailboxes/" + wrongId + "/test", null, null);
        assertEquals("helpdesk.authentication_failed", failed.json().get("code").asString());
    }

    @Test
    void theAdministrationNeedsItsRightAndChecksItsInput() {
        Setup setup = setup();
        Browser stranger = person(AdministrationRight.PROJECTS_MANAGE);
        for (String path : List.of("/api/v1/helpdesk/customers", "/api/v1/helpdesk/contacts", "/api/v1/helpdesk/mailboxes", "/api/v1/helpdesk/status")) {
            Response refused = stranger.get(path);
            assertEquals(403, refused.status(), path);
            assertEquals("auth.forbidden", refused.code());
        }
        assertEquals(403, stranger.send("POST", "/api/v1/helpdesk/customers", Map.of("name", "X"), null).status());
        // An address belongs to one contact only.
        Response taken = setup.admin().send("POST", "/api/v1/helpdesk/contacts",
            Map.of("customerId", setup.customer(), "name", "Zweite Lea", "addresses", List.of(setup.contactAddress())), null);
        assertEquals(409, taken.status(), taken.body());
        assertEquals("contact.address_taken", taken.code());
        Response invalid = setup.admin().send("POST", "/api/v1/helpdesk/contacts",
            Map.of("customerId", setup.customer(), "name", "Ohne", "addresses", List.of("keine adresse")), null);
        assertEquals(400, invalid.status());
        Response type = setup.admin().send("POST", "/api/v1/helpdesk/mailboxes",
            Map.of("name", "Typ", "projectId", setup.project(), "ticketType", "unbekannt", "imapHost", "imap.example", "imapPort", 993,
                "imapSecurity", "tls", "username", "u"), null);
        assertEquals(400, type.status());
        assertTrue(type.body().contains("ticketType.unknown"), type.body());
        // A stale version is refused.
        Response customers = setup.admin().get("/api/v1/helpdesk/customers");
        Response stale = setup.admin().send("PUT", "/api/v1/helpdesk/customers/" + setup.customer(), Map.of("name", "Neu", "projects", List.of()), "\"99\"");
        assertEquals(412, stale.status(), customers.body());
    }

    // ---------------------------------------------------------------- the review queue

    @Test
    void theReviewQueueBelongsToTheReviewersOfTheProject() {
        Browser outsider = person();
        Setup setup = setup(outsider);
        grant(outsider.id, setup.project(), "ticket.read", "READ:*");
        Browser reviewer = reviewer(setup);
        deliverRaw(setup, withFiles("", "Max <max@unbekannt.example>", setup.address(), "Bitte helfen"));
        deliver(setup, "", "Zweite", "Text");
        intake.fetch(setup.mailbox());
        List<Map<String, Object>> stored = messages(setup);
        UUID first = (UUID) stored.get(0).get("id");
        UUID second = (UUID) stored.get(1).get("id");

        assertEquals(0, outsider.get("/api/v1/helpdesk/inbox").json().size(), "a member without helpdesk.review sees nothing");
        assertEquals(404, outsider.get("/api/v1/helpdesk/inbox/" + first).status());
        assertEquals(404, outsider.send("POST", "/api/v1/helpdesk/inbox/" + first + "/discard", Map.of("reason", "x"), "\"1\"").status());

        Response list = reviewer.get("/api/v1/helpdesk/inbox");
        assertEquals(2, list.json().size(), list.body());
        assertTrue(list.json().get(0).get("body").isNull(), "the list carries no text");
        assertEquals(2, reviewer.get("/api/v1/helpdesk/inbox/summary").json().get("review").asLong());
        Response detail = reviewer.get("/api/v1/helpdesk/inbox/" + first);
        assertEquals("sender_unknown", detail.json().get("reason").asString());
        assertTrue(detail.json().get("body").asString().contains("Hallo Support"));
        Response candidates = reviewer.get("/api/v1/helpdesk/inbox/" + first + "/candidates");
        assertEquals(setup.contact().toString(), candidates.json().get(0).get("contactId").asString());

        // A ticket with the contact; the second attempt and a stale version create nothing.
        Response ticket = reviewer.send("POST", "/api/v1/helpdesk/inbox/" + first + "/ticket", Map.of("contactId", setup.contact()), detail.etag());
        assertEquals(200, ticket.status(), ticket.body());
        long number = ticket.json().get("number").asLong();
        assertEquals(1, StreamSupport.stream(ticket.json().get("attachments").spliterator(), false).filter(a -> !a.get("attachmentId").isNull()).count());
        Response again = reviewer.send("POST", "/api/v1/helpdesk/inbox/" + first + "/ticket", Map.of(), detail.etag());
        assertEquals(409, again.status());
        assertEquals("helpdesk.message_processed", again.code());
        assertEquals(1, tickets(setup));
        Response view = reviewer.get("/api/v1/projects/" + setup.project() + "/tickets/" + number + "/helpdesk");
        assertEquals("Lea Lang", view.json().get("contact").get("name").asString());
        Map<String, Object> created = jdbc.queryForMap("select created_by from ticket where project_id = ? and number = ?", setup.project(), number);
        assertEquals(reviewer.id, created.get("created_by"), "the person who processed the mail is the actor");

        Response stale = reviewer.send("POST", "/api/v1/helpdesk/inbox/" + second + "/discard", Map.of("reason", "Spam"), "\"7\"");
        assertEquals(412, stale.status());
        Response noReason = reviewer.send("POST", "/api/v1/helpdesk/inbox/" + second + "/discard", Map.of("reason", " "), "\"1\"");
        assertEquals(400, noReason.status());
        Response discarded = reviewer.send("POST", "/api/v1/helpdesk/inbox/" + second + "/discard", Map.of("reason", "Spam"), "\"1\"");
        assertEquals(200, discarded.status(), discarded.body());
        assertEquals("discarded", discarded.json().get("state").asString());
        assertEquals(0, reviewer.get("/api/v1/helpdesk/inbox").json().size());
        assertEquals(1, reviewer.get("/api/v1/helpdesk/inbox?state=discarded").json().size());
        assertNull(jdbc.queryForObject("select raw from helpdesk_message where id = ?", byte[].class, second));
        assertEquals(List.of("message.ticket_created", "message.discarded"),
            jdbc.queryForList("select operation from helpdesk_audit_event where target_id in (?, ?) order by id", String.class, first, second));
    }

    @Test
    void customerContactAndMailFollowTheFieldRules() {
        Setup setup = setup();
        deliver(setup, confirmed("nordwerk.example"), "Feldrechte", "Text");
        intake.fetch(setup.mailbox());
        long number = jdbc.queryForObject("select number from ticket where project_id = ?", Long.class, setup.project());
        Browser limited = person();
        assertEquals(200, setup.admin().send("PUT", "/api/v1/projects/" + setup.project() + "/members/" + limited.id, null,
            setup.admin().get("/api/v1/projects/" + setup.project()).etag()).status());
        grant(limited.id, setup.project(), "ticket.read", "ticket.edit", "READ:title", "READ:customer");
        String base = "/api/v1/projects/" + setup.project() + "/tickets/" + number;
        Response view = limited.get(base + "/helpdesk");
        assertEquals(200, view.status(), view.body());
        assertEquals("Nordwerk", view.json().get("customer").get("name").asString().substring(0, 8));
        assertTrue(view.json().get("contact").isNull());
        assertTrue(view.json().get("mails").isNull());
        assertEquals(List.of("customer"), StreamSupport.stream(view.json().get("readable").spliterator(), false).map(JsonNode::asString).toList());
        Response history = limited.get(base + "/history");
        assertFalse(history.body().contains("\"contact\"") || history.body().contains("\"mail\""), history.body());
        assertTrue(history.body().contains("\"customer\""), history.body());
        // Without a write rule nothing changes.
        Response change = limited.send("PUT", base + "/helpdesk/customer", Map.of(), null);
        assertEquals(403, change.status());
        // Someone outside the project does not see the ticket at all.
        assertEquals(404, person().get(base + "/helpdesk").status());

        // An editor clears the contact and gets an entry in the history.
        Browser editor = reviewer(setup);
        Response cleared = editor.send("PUT", base + "/helpdesk/customer", Map.of("customerId", setup.customer()), null);
        assertEquals(200, cleared.status(), cleared.body());
        assertTrue(cleared.json().get("contact").isNull());
        Response foreign = editor.send("PUT", base + "/helpdesk/customer", Map.of("customerId", setup.customer(), "contactId", UUID.randomUUID()), null);
        assertEquals(400, foreign.status());
        assertNotNull(jdbc.queryForObject("select count(*) from ticket_change where field = 'contact' and old_value is not null and new_value is null",
            Long.class));
    }

    // ---------------------------------------------------------------- helpers

    private static JsonNode find(JsonNode list, UUID id) {
        for (JsonNode item : list) {
            if (item.get("id").asString().equals(id.toString())) {
                return item;
            }
        }
        throw new AssertionError("not in the list: " + id);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
