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
import taskmanager.helpdesk.internal.ScriptedMailbox;
import taskmanager.identity.account.AccountService;
import taskmanager.jobs.JobExecution;
import taskmanager.jobs.JobFailure;
import taskmanager.jobs.JobHandler;
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
        GreenMail server = new GreenMail(new com.icegreen.greenmail.util.ServerSetup[] {ServerSetupTest.IMAP.dynamicPort(),
            ServerSetupTest.SMTP.dynamicPort()});
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
        // Small batches, so that the tests see what lasts from one fetch to the next (TASK-059, F2).
        registry.add("taskmanager.helpdesk.batch", () -> "5");
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
    @Autowired org.springframework.context.ApplicationContext context;

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

    // ---------------------------------------------------------------- corrections after the review (TASK-059)

    /** F2: the comparison after a new UIDVALIDITY lasts across batches; with batches of five, the sixth mail is compared too. */
    @Test
    void theComparisonAfterANewUidValidityLastsAcrossBatches() throws Exception {
        Setup setup = setup();
        for (int i = 0; i < 6; i++) {
            deliver(setup, confirmed("nordwerk.example"), "Mail " + i, "Text");
        }
        intake.fetch(setup.mailbox());
        intake.fetch(setup.mailbox());
        assertEquals(6, tickets(setup));
        jdbc.update("update helpdesk_mailbox set uid_validity = uid_validity + 1 where id = ?", setup.mailbox());
        jdbc.update("update helpdesk_message set uid_validity = uid_validity + 1 where mailbox_id = ?", setup.mailbox());
        markAllUnseen(setup);
        intake.fetch(setup.mailbox());
        assertEquals(6, tickets(setup));
        // The second batch is still compared, also after a restart of the process in between.
        intake.fetch(setup.mailbox());
        assertEquals(6, tickets(setup), "no mail became a ticket twice");
        deliver(setup, confirmed("nordwerk.example"), "Neu", "Text");
        intake.fetch(setup.mailbox());
        assertEquals(7, tickets(setup), "a new mail after the comparison becomes a ticket");
        assertNull(jdbc.queryForObject("select compare_until from helpdesk_mailbox where id = ?", Long.class, setup.mailbox()));
    }

    /** F3: after a change of the account, a mail with the same UIDVALIDITY and UID as an old one is still taken. */
    @Test
    void aChangedAccountIsANewSourceWhoseMailsAreNotTakenForOldOnes() throws Exception {
        Setup setup = setup();
        Setup other = setup();
        deliver(setup, confirmed("nordwerk.example"), "Altes Konto", "Text");
        intake.fetch(setup.mailbox());
        // The new account holds a mail of the contact of the first setup.
        deliver(other, confirmed("nordwerk.example"), "Neues Konto", "Text");
        long[] origin = firstOrigin(other);
        // Two accounts may report the same UIDVALIDITY and UID; the fixture makes it certain.
        jdbc.update("update helpdesk_message set uid_validity = ?, uid = ? where mailbox_id = ?", origin[0], origin[1], setup.mailbox());
        Response change = setup.admin().send("PUT", "/api/v1/helpdesk/mailboxes/" + setup.mailbox(), mailboxRequest("Gewechselt "
            + COUNTER.incrementAndGet(), setup.project(), other.account().getLogin(), MAIL_PASSWORD, MAIL.getImap().getPort(), AUTHSERV), "\"1\"");
        assertEquals(200, change.status(), change.body());
        intake.fetch(setup.mailbox());
        List<Map<String, Object>> messages = messages(setup);
        assertEquals(2, messages.size(), "the mail of the new account arrived");
        assertEquals(List.of(1, 2), messages.stream().map(row -> (Integer) row.get("source")).sorted().toList());
        // The sender of the new mail is a contact of a customer not released for this project; it waits for a person.
        assertEquals("customer_not_released", messages.stream().filter(row -> row.get("source").equals(2)).findFirst().orElseThrow().get("reason"));
    }

    /** F1: a mail server that repeats the login in its answer does not bring the password into the log. */
    @Test
    void aMailServerThatRepeatsThePasswordDoesNotBringItIntoTheLog() throws Exception {
        Setup setup = setup();
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start();
        var log = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("taskmanager.helpdesk");
        log.addAppender(capture);
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(15_000);
            Future<Object> peer = POOL.submit((Callable<Object>) () -> {
                for (int n = 0; n < 2; n++) {
                    try (var socket = server.accept()) {
                        socket.setSoTimeout(10_000);
                        var reader = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                        var writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), true);
                        writer.print("* OK synthetic IMAP\r\n");
                        writer.flush();
                        for (String line; (line = reader.readLine()) != null;) {
                            String tag = line.split(" ")[0];
                            if (line.toUpperCase(java.util.Locale.ROOT).contains("CAPABILITY")) {
                                writer.print("* CAPABILITY IMAP4rev1\r\n" + tag + " OK done\r\n");
                            } else if (line.toUpperCase(java.util.Locale.ROOT).contains("LOGIN")) {
                                writer.print(tag + " NO rejected " + line + "\r\n");
                                writer.flush();
                                break;
                            } else {
                                writer.print(tag + " OK\r\n");
                            }
                            writer.flush();
                        }
                    }
                }
                return null;
            });
            jdbc.update("update helpdesk_mailbox set imap_port = ? where id = ?", server.getLocalPort(), setup.mailbox());
            Response test = setup.admin().send("POST", "/api/v1/helpdesk/mailboxes/" + setup.mailbox() + "/test", null, null);
            assertEquals("helpdesk.authentication_failed", test.json().get("code").asString(), test.body());
            intake.fetch(setup.mailbox());
            peer.get(20, java.util.concurrent.TimeUnit.SECONDS);
            List<String> lines = capture.list.stream().map(event -> event.getFormattedMessage()
                + (event.getThrowableProxy() == null ? "" : event.getThrowableProxy().getMessage())).toList();
            assertTrue(lines.stream().filter(line -> line.contains("helpdesk.authentication_failed")).count() >= 2, lines.toString());
            assertTrue(lines.stream().noneMatch(line -> line.contains(MAIL_PASSWORD)), lines.toString());
        } finally {
            log.detachAppender(capture);
            capture.stop();
        }
    }

    /** F4: the choice of customers carries contacts only for whoever may read and write the contact. */
    @Test
    void theChoiceOfCustomersShowsContactsOnlyWithTheirRules() {
        Setup setup = setup();
        deliver(setup, confirmed("nordwerk.example"), "Auswahl", "Text");
        intake.fetch(setup.mailbox());
        long number = jdbc.queryForObject("select number from ticket where project_id = ?", Long.class, setup.project());
        String base = "/api/v1/projects/" + setup.project() + "/tickets/" + number + "/helpdesk";
        Browser limited = member(setup);
        grant(limited.id, setup.project(), "ticket.read", "ticket.edit", "READ:title", "READ:customer", "WRITE:customer");
        assertTrue(limited.get(base).json().get("contact").isNull());
        Response options = limited.get(base + "/options");
        assertEquals(200, options.status(), options.body());
        assertFalse(options.body().contains(setup.contactAddress()), options.body());
        assertEquals(0, options.json().get(0).get("contacts").size());
        // Reading without writing the contact does not list them either.
        Browser reader = member(setup);
        grant(reader.id, setup.project(), "ticket.read", "ticket.edit", "READ:title", "READ:customer", "READ:contact", "WRITE:customer");
        assertFalse(reader.get(base + "/options").body().contains(setup.contactAddress()));
        // Writing the customer without reading it is refused.
        Browser blind = member(setup);
        grant(blind.id, setup.project(), "ticket.read", "ticket.edit", "READ:title", "WRITE:customer");
        assertEquals(403, blind.get(base + "/options").status());
        // With both rules for the contact the contacts are there.
        Response full = reviewer(setup).get(base + "/options");
        assertTrue(full.body().contains(setup.contactAddress()), full.body());
    }

    /** F5: a contact of another customer counts at any position among the recipients, and too many recipients wait for a person. */
    @Test
    void everyRecipientCountsForTheCheckOfOtherCustomers() {
        Setup setup = setup();
        Setup other = setup();
        String fifty = java.util.stream.IntStream.range(0, 50).mapToObj(i -> "n" + i + "@unbekannt.example")
            .collect(java.util.stream.Collectors.joining(", "));
        deliver(setup, confirmed("nordwerk.example") + "Cc: " + fifty + ", " + other.contactAddress() + "\r\n", "Empfaenger 51", "Text");
        String thousand = java.util.stream.IntStream.range(0, 1_001).mapToObj(i -> "m" + i + "@unbekannt.example")
            .collect(java.util.stream.Collectors.joining(", "));
        deliver(setup, confirmed("nordwerk.example") + "Cc: " + thousand + "\r\n", "Zu viele", "Text");
        intake.fetch(setup.mailbox());
        List<Map<String, Object>> messages = messages(setup);
        assertEquals(List.of("foreign_customer", "recipients_incomplete"), messages.stream().map(row -> row.get("reason")).toList());
        assertTrue(messages.get(0).get("cc_addresses").toString().contains(other.contactAddress()));
        assertEquals(0, tickets(setup));
    }

    /**
     * R1 of the review of TASK-059: a fetch that still reads the old UIDVALIDITY when another fetch starts the folder over, and
     * its release after a third fetch moved on, leave the position of the new UIDVALIDITY where it is; the next mail is taken.
     */
    @Test
    void aFetchOfTheOldUidValidityDoesNotMoveThePositionOfTheNewOne() throws Exception {
        Setup setup = setup();
        jdbc.update("update helpdesk_mailbox set uid_validity = 100, last_uid = 0 where id = ?", setup.mailbox());
        var oldRead = new java.util.concurrent.CountDownLatch(1);
        var oldGo = new java.util.concurrent.CountDownLatch(1);
        ScriptedMailbox old = new ScriptedMailbox(100).put(100, scripted(setup, "alt-100", ""))
            .beforeFetch(uid -> { oldRead.countDown(); hold(oldGo); });
        var newRead = new java.util.concurrent.CountDownLatch(1);
        var newGo = new java.util.concurrent.CountDownLatch(1);
        ScriptedMailbox current = new ScriptedMailbox(200).put(1, scripted(setup, "neu-1", "")).put(2, scripted(setup, "neu-2", ""));
        Future<?> first = POOL.submit(() -> old.intake(context, 1).fetch(setup.mailbox()));
        Future<?> third = null;
        try {
            hold(oldRead);
            current.intake(context, 1).fetch(setup.mailbox());
            current.beforeFetch(uid -> { newRead.countDown(); hold(newGo); });
            third = POOL.submit(() -> current.intake(context, 1).fetch(setup.mailbox()));
            hold(newRead);
            oldGo.countDown();
            first.get(20, java.util.concurrent.TimeUnit.SECONDS);
            newGo.countDown();
            third.get(20, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            oldGo.countDown();
            newGo.countDown();
        }
        Map<String, Object> state = jdbc.queryForMap("select uid_validity, last_uid from helpdesk_mailbox where id = ?", setup.mailbox());
        assertEquals(200L, state.get("uid_validity"));
        assertEquals(2L, state.get("last_uid"), "the old fetch did not move the position of the new UIDVALIDITY");
        current.beforeFetch(uid -> { }).put(3, scripted(setup, "neu-3", ""));
        current.intake(context, 5).fetch(setup.mailbox());
        assertTrue(current.seen(3), "the next mail is taken");
        // The overtaken fetch of the old UIDVALIDITY recorded nothing (TASK-061, S1); the folder holds the mail again under the new one.
        assertEquals(3, messages(setup).size());
        assertFalse(old.seen(100));
        assertEquals(3L, jdbc.queryForObject("select last_uid from helpdesk_mailbox where id = ?", Long.class, setup.mailbox()));
    }

    /** R1: two fetches that see the same new UIDVALIDITY start the folder over once; the second ends without writing. */
    @Test
    void twoFetchesThatSeeTheSameNewUidValidityStartTheFolderOverOnce() throws Exception {
        Setup setup = setup();
        jdbc.update("update helpdesk_mailbox set uid_validity = 100, last_uid = 5 where id = ?", setup.mailbox());
        // Both fetches read the old state before either starts the folder over.
        var both = new java.util.concurrent.CountDownLatch(2);
        ScriptedMailbox current = new ScriptedMailbox(200).put(1, scripted(setup, "gleich-1", "")).put(2, scripted(setup, "gleich-2", ""))
            .beforeOpen(() -> { both.countDown(); hold(both); });
        Future<?> a = POOL.submit(() -> current.intake(context, 5).fetch(setup.mailbox()));
        Future<?> b = POOL.submit(() -> current.intake(context, 5).fetch(setup.mailbox()));
        a.get(20, java.util.concurrent.TimeUnit.SECONDS);
        b.get(20, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(2, messages(setup).size());
        assertEquals(2, tickets(setup));
        Map<String, Object> state = jdbc.queryForMap("select source, uid_validity, last_uid from helpdesk_mailbox where id = ?", setup.mailbox());
        assertEquals(2, state.get("source"), "one start over, one new source");
        assertEquals(200L, state.get("uid_validity"));
        assertEquals(2L, state.get("last_uid"));
        assertEquals(1L, jdbc.queryForObject("select count(*) from helpdesk_audit_event where target_id = ? and operation = 'mailbox.uidvalidity_changed'",
            Long.class, setup.mailbox()));
    }

    /**
     * S1 of the review of TASK-060: the same mail, read by a fetch of the old UIDVALIDITY that is overtaken by a start over of
     * the folder, becomes one ticket. The overtaken fetch writes neither mail nor ticket; in the other order the new fetch
     * recognizes the mail by its Message-ID.
     */
    @Test
    void theSameMailInTheOldAndTheNewUidValidityBecomesOneTicket() throws Exception {
        Setup setup = setup();
        jdbc.update("update helpdesk_mailbox set uid_validity = 100, last_uid = 0 where id = ?", setup.mailbox());
        byte[] mail = scripted(setup, "dieselbe", "");
        var oldRead = new java.util.concurrent.CountDownLatch(1);
        var oldGo = new java.util.concurrent.CountDownLatch(1);
        ScriptedMailbox old = new ScriptedMailbox(100).put(100, mail).beforeFetch(uid -> { oldRead.countDown(); hold(oldGo); });
        Future<?> overtaken = POOL.submit(() -> old.intake(context, 5).fetch(setup.mailbox()));
        try {
            hold(oldRead);
            new ScriptedMailbox(200).put(1, mail).intake(context, 5).fetch(setup.mailbox());
            assertEquals(1, tickets(setup));
        } finally {
            oldGo.countDown();
        }
        overtaken.get(20, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(1, tickets(setup), "the overtaken fetch wrote no second ticket");
        assertEquals(1, messages(setup).size(), "nor a second mail");
        assertFalse(old.seen(100), "and did not mark the mail as seen");

        // The other order: the old fetch records first, the new one recognizes the mail.
        Setup other = setup();
        jdbc.update("update helpdesk_mailbox set uid_validity = 100, last_uid = 0 where id = ?", other.mailbox());
        byte[] again = scripted(other, "zuerst-alt", "");
        new ScriptedMailbox(100).put(100, again).intake(context, 5).fetch(other.mailbox());
        new ScriptedMailbox(200).put(1, again).intake(context, 5).fetch(other.mailbox());
        assertEquals(1, tickets(other));
        assertEquals(1, messages(other).size());
    }

    /** H1 of the review of TASK-060: a mail too large to fetch is read only by its header and waits with its reason. */
    @Test
    void aMailTooLargeToFetchIsReadByItsHeader() {
        Setup setup = setup();
        Setup other = setup();
        jdbc.update("update helpdesk_mailbox set max_bytes = 600 where id = ?", setup.mailbox());
        String text = "x".repeat(1_000);
        byte[] large = (new String(scripted(setup, "gross", "Cc: Outer: Inner: " + other.contactAddress() + ";;\r\n"), StandardCharsets.UTF_8)
            + text).getBytes(StandardCharsets.UTF_8);
        ScriptedMailbox folder = new ScriptedMailbox(100).put(1, large);
        folder.intake(context, 5).fetch(setup.mailbox());
        List<Map<String, Object>> messages = messages(setup);
        assertEquals(1, messages.size());
        assertEquals("too_large", messages.get(0).get("reason"));
        assertNull(messages.get(0).get("body"), "only the header was read");
        assertTrue(folder.seen(1));
        assertEquals(0, tickets(setup));
    }

    /** S2 of the review of TASK-060: a broken group that could hide a contact of another customer keeps the mail in the queue. */
    @Test
    void aBrokenGroupKeepsTheMailInTheQueue() {
        Setup setup = setup();
        Setup other = setup();
        ScriptedMailbox folder = new ScriptedMailbox(100)
            .put(1, scripted(setup, "verschachtelt", "Cc: Outer: Inner: " + other.contactAddress() + ";;\r\n"))
            .put(2, scripted(setup, "ohne-ende", "Cc: MissingEnd: " + other.contactAddress() + "\r\n"))
            .put(3, scripted(setup, "offener-name", "To: Broken: \"unterminated <" + other.contactAddress() + ">;\r\n"));
        folder.intake(context, 5).fetch(setup.mailbox());
        assertEquals(List.of("recipients_incomplete", "recipients_incomplete", "recipients_incomplete"),
            messages(setup).stream().map(row -> row.get("reason")).toList());
        assertEquals(0, tickets(setup));
    }

    /** R2 of the review of TASK-059: a contact of another customer inside a group of To or Cc keeps the mail in the queue. */
    @Test
    void aContactOfAnotherCustomerInsideAGroupCounts() {
        Setup setup = setup();
        Setup other = setup();
        ScriptedMailbox folder = new ScriptedMailbox(100)
            .put(1, scripted(setup, "gruppe-cc", "Cc: Support-Team: " + other.contactAddress() + ";\r\n"))
            .put(2, scripted(setup, "gruppe-to", "To: Alle: kollege@unbekannt.example, Lea <" + other.contactAddress().toUpperCase() + ">;\r\n"))
            .put(3, scripted(setup, "leere-gruppe", "Cc: Niemand:;, kollege@unbekannt.example\r\n"));
        folder.intake(context, 5).fetch(setup.mailbox());
        List<Map<String, Object>> messages = messages(setup);
        assertEquals(java.util.Arrays.asList("foreign_customer", "foreign_customer", null), messages.stream().map(row -> row.get("reason")).toList());
        assertEquals(other.contactAddress(), messages.get(0).get("cc_addresses"), "the members of a group are its addresses");
        assertEquals(1, tickets(setup), "only the mail without a foreign contact gets a ticket");
    }

    /** R3 of the review of TASK-059: the limit of recipients holds for To and Cc of a mail together. */
    @Test
    void theLimitOfRecipientsHoldsForToAndCcTogether() {
        Setup setup = setup();
        // The fixture writes one To address of its own.
        deliver(setup, confirmed("nordwerk.example") + "Cc: " + unknown("a", 1_000) + "\r\n", "1001 gesamt", "Text");
        deliver(setup, confirmed("nordwerk.example") + "Cc: " + unknown("b", 999) + "\r\n", "1000 gesamt", "Text");
        deliver(setup, confirmed("nordwerk.example") + "To: " + unknown("c", 500) + "\r\nCc: " + unknown("d", 500) + "\r\n", "Geteilt", "Text");
        intake.fetch(setup.mailbox());
        List<Map<String, Object>> messages = messages(setup);
        assertEquals(java.util.Arrays.asList("recipients_incomplete", null, "recipients_incomplete"),
            messages.stream().map(row -> row.get("reason")).toList());
        assertEquals(1, tickets(setup));
    }

    // ---------------------------------------------------------------- replies (TASK-058)

    /** A member who may send replies in the project of the setup: helpdesk.reply and the read rules of every field. */
    private Browser replier(Setup setup) {
        Browser replier = member(setup);
        grant(replier.id, setup.project(), "helpdesk.reply", "ticket.read", "READ:*");
        return replier;
    }

    /** Gives the mailbox of the setup an SMTP server, here GreenMail or a scripted one, and its address. */
    private void smtp(Setup setup, int port) {
        jdbc.update("update helpdesk_mailbox set smtp_host = '127.0.0.1', smtp_port = ?, smtp_security = 'none', address = ? where id = ?",
            port, setup.address(), setup.mailbox());
    }

    /** A ticket from a mail of the confirmed contact; returns its number and the Message-ID of the mail. */
    private Object[] ticketFromMail(Setup setup, String subject) {
        String messageId = "<" + UUID.randomUUID() + "@nordwerk.example>";
        deliverRaw(setup, confirmed("nordwerk.example") + "From: Lea Lang <" + setup.contactAddress() + ">\r\nTo: " + setup.address()
            + "\r\nSubject: " + subject + "\r\nMessage-ID: " + messageId + "\r\nDate: Wed, 28 Oct 2026 10:00:00 +0100\r\n"
            + "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\nBitte helfen.\r\n");
        intake.fetch(setup.mailbox());
        long number = jdbc.queryForObject("select number from ticket where project_id = ? order by number desc limit 1", Long.class, setup.project());
        return new Object[] {number, messageId};
    }

    private String ticketPath(Setup setup, long number) {
        return "/api/v1/projects/" + setup.project() + "/tickets/" + number + "/helpdesk";
    }

    private Response reply(Browser browser, Setup setup, long number, String body) {
        return browser.send("POST", ticketPath(setup, number) + "/replies",
            Map.of("mailboxId", setup.mailbox(), "subject", "Re: Anfrage", "body", body), null);
    }

    private JobHandler replyHandler() {
        return context.getBeansOfType(JobHandler.class).values().stream().filter(h -> h.kind().equals("helpdesk.reply")).findFirst().orElseThrow();
    }

    /** Runs the current job of the reply as the executor would, in the given attempt. */
    private RuntimeException runReplyJob(UUID reply, int attempt) {
        Map<String, Object> job = jdbc.queryForMap("select j.id, j.company_id, j.max_attempts from job j join helpdesk_reply r on r.job_id = j.id where r.id = ?",
            reply);
        try {
            replyHandler().handle(new JobExecution((UUID) job.get("id"), (UUID) job.get("company_id"), "helpdesk.reply",
                Map.of("reply", reply.toString()), attempt, (Integer) job.get("max_attempts"), attempt > 1));
            return null;
        } catch (RuntimeException failure) {
            return failure;
        } catch (Exception unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    private String replyState(UUID reply) {
        return jdbc.queryForObject("select state from helpdesk_reply where id = ?", String.class, reply);
    }

    private List<jakarta.mail.internet.MimeMessage> receivedBy(String address) {
        List<jakarta.mail.internet.MimeMessage> result = new ArrayList<>();
        for (jakarta.mail.internet.MimeMessage message : MAIL.getReceivedMessages()) {
            try {
                if (message.getAllRecipients() != null && java.util.Arrays.stream(message.getAllRecipients())
                        .anyMatch(a -> ((jakarta.mail.internet.InternetAddress) a).getAddress().equalsIgnoreCase(address))) {
                    result.add(message);
                }
            } catch (jakarta.mail.MessagingException unreadable) {
                throw new AssertionError(unreadable);
            }
        }
        return result;
    }

    @Test
    void aConfirmedReplyIsSentOnceWithTheHeadersOfItsThread() throws Exception {
        Setup setup = setup();
        smtp(setup, MAIL.getSmtp().getPort());
        Object[] ticket = ticketFromMail(setup, "Drucker defekt");
        long number = (Long) ticket[0];
        Browser replier = replier(setup);

        Response view = replier.get(ticketPath(setup, number));
        assertTrue(view.json().get("replyAllowed").asBoolean(), view.body());
        Response form = replier.get(ticketPath(setup, number) + "/reply");
        assertEquals(200, form.status(), form.body());
        assertEquals("Re: Drucker defekt", form.json().get("subject").asString());
        assertEquals(setup.mailbox().toString(), form.json().get("suggestedMailbox").asString());
        assertEquals(setup.contactAddress(), form.json().get("recipients").get(0).asString());

        Response sent = reply(replier, setup, number, "Wir kümmern uns darum.");
        assertEquals(201, sent.status(), sent.body());
        assertEquals("pending", sent.json().get("state").asString());
        UUID reply = UUID.fromString(sent.json().get("id").asString());
        assertEquals(1, jdbc.queryForObject("select count(*) from job where kind = 'helpdesk.reply' and payload->>'reply' = ?", Long.class,
            reply.toString()), "exactly one send job, written with the reply");
        assertEquals(0, receivedBy(setup.contactAddress()).size(), "nothing is sent before the job runs");

        assertNull(runReplyJob(reply, 1));
        assertEquals("sent", replyState(reply));
        List<jakarta.mail.internet.MimeMessage> received = receivedBy(setup.contactAddress());
        assertEquals(1, received.size());
        jakarta.mail.internet.MimeMessage mail = received.get(0);
        assertEquals("Re: Anfrage", mail.getSubject());
        assertEquals(setup.address(), ((jakarta.mail.internet.InternetAddress) mail.getFrom()[0]).getAddress());
        assertEquals(ticket[1], mail.getHeader("In-Reply-To")[0]);
        assertEquals("<" + reply + "@firma.example>", mail.getMessageID());
        assertNull(mail.getHeader("Auto-Submitted"), "a reply of a person is no automatic mail");
        assertTrue(GreenMailUtil.getBody(mail).contains("Wir k"), GreenMailUtil.getBody(mail));

        // The job running again, after a lost outcome or a retry by the administration, sends nothing.
        assertNull(runReplyJob(reply, 2));
        assertEquals(1, receivedBy(setup.contactAddress()).size());

        // The reply is in the ticket and in its history.
        Response after = replier.get(ticketPath(setup, number));
        assertEquals("sent", after.json().get("replies").get(0).get("state").asString());
        Response history = replier.get("/api/v1/projects/" + setup.project() + "/tickets/" + number + "/history");
        assertTrue(history.body().contains(reply.toString()), history.body());
        assertEquals(List.of("reply.confirmed"), jdbc.queryForList("select operation from helpdesk_audit_event where target_id = ?", String.class, reply));
    }

    @Test
    void theAnswerOfTheCustomerJoinsTheTicketOfTheReply() throws Exception {
        Setup setup = setup();
        smtp(setup, MAIL.getSmtp().getPort());
        long number = (Long) ticketFromMail(setup, "Netzwerk")[0];
        Browser replier = replier(setup);
        UUID reply = UUID.fromString(reply(replier, setup, number, "Bitte neu starten.").json().get("id").asString());
        assertNull(runReplyJob(reply, 1));
        String replyId = "<" + reply + "@firma.example>";
        long ticketsBefore = tickets(setup);

        // A confirmed answer of the contact joins the ticket.
        deliver(setup, confirmed("nordwerk.example") + "In-Reply-To: " + replyId + "\r\nReferences: <alt@nordwerk.example> " + replyId + "\r\n",
            "Re: Re: Netzwerk", "Hat geholfen.");
        intake.fetch(setup.mailbox());
        assertEquals(ticketsBefore, tickets(setup), "no new ticket");
        Map<String, Object> answer = jdbc.queryForMap("select * from helpdesk_message where mailbox_id = ? order by uid desc limit 1", setup.mailbox());
        assertEquals("ticket", answer.get("state"));
        UUID ticketId = jdbc.queryForObject("select id from ticket where project_id = ? and number = ?", UUID.class, setup.project(), number);
        assertEquals(ticketId, answer.get("ticket_id"));
        assertEquals(2, replier.get(ticketPath(setup, number)).json().get("mails").size());

        // Unconfirmed, it waits with the ticket as a hint, and a reviewer adds it there.
        deliver(setup, "In-Reply-To: " + replyId + "\r\n", "Re: Netzwerk", "Noch eine Frage.");
        intake.fetch(setup.mailbox());
        Map<String, Object> waiting = jdbc.queryForMap("select * from helpdesk_message where mailbox_id = ? order by uid desc limit 1", setup.mailbox());
        assertEquals("review", waiting.get("state"));
        assertEquals("sender_unconfirmed", waiting.get("reason"));
        assertEquals(ticketId, waiting.get("related_ticket_id"));
        Browser reviewer = reviewer(setup);
        UUID waitingId = (UUID) waiting.get("id");
        Response shown = reviewer.get("/api/v1/helpdesk/inbox/" + waitingId);
        assertEquals(number, shown.json().get("relatedTicket").get("number").asLong());
        Response attached = reviewer.send("POST", "/api/v1/helpdesk/inbox/" + waitingId + "/attach", null, shown.etag());
        assertEquals(200, attached.status(), attached.body());
        assertEquals(number, attached.json().get("number").asLong());
        assertEquals(ticketsBefore, tickets(setup));
        assertEquals(3, replier.get(ticketPath(setup, number)).json().get("mails").size());
        assertEquals(409, reviewer.send("POST", "/api/v1/helpdesk/inbox/" + waitingId + "/attach", null, "\"2\"").status());

        // A confirmed contact of another customer answering the reply does not join the ticket.
        Response other = setup.admin().send("POST", "/api/v1/helpdesk/customers", Map.of("name", "Südtechnik " + COUNTER.incrementAndGet(),
            "projects", List.of(setup.project())), null);
        String foreign = "mia" + COUNTER.incrementAndGet() + "@suedtechnik.example";
        assertEquals(201, setup.admin().send("POST", "/api/v1/helpdesk/contacts", Map.of("customerId", other.json().get("id").asString(),
            "name", "Mia", "addresses", List.of(foreign)), null).status());
        deliverRaw(setup, confirmed("suedtechnik.example") + "In-Reply-To: " + replyId + "\r\nFrom: Mia <" + foreign + ">\r\nTo: "
            + setup.address() + "\r\nSubject: Re: Netzwerk\r\nMessage-ID: <" + UUID.randomUUID() + "@suedtechnik.example>\r\n\r\nHallo\r\n");
        intake.fetch(setup.mailbox());
        Map<String, Object> mismatch = jdbc.queryForMap("select * from helpdesk_message where mailbox_id = ? order by uid desc limit 1", setup.mailbox());
        assertEquals("reply_customer_mismatch", mismatch.get("reason"));
        assertEquals(ticketsBefore, tickets(setup));

        // An auto responder answering the reply and the reply itself coming back get no ticket and no answer.
        long replies = jdbc.queryForObject("select count(*) from helpdesk_reply", Long.class);
        deliver(setup, confirmed("nordwerk.example") + "Auto-Submitted: auto-replied\r\nIn-Reply-To: " + replyId + "\r\n", "Abwesend", "Bin weg.");
        deliverRaw(setup, "From: Support <" + setup.address() + ">\r\nTo: " + setup.contactAddress() + "\r\nSubject: Re: Anfrage\r\nMessage-ID: "
            + replyId + "\r\n\r\nKopie\r\n");
        intake.fetch(setup.mailbox());
        List<Map<String, Object>> last = jdbc.queryForList("select state, reason from helpdesk_message where mailbox_id = ? order by uid desc limit 2",
            setup.mailbox());
        assertEquals("own_reply", last.get(0).get("reason"));
        assertEquals("automatic", last.get(1).get("reason"));
        assertTrue(last.stream().allMatch(m -> m.get("state").equals("automatic")));
        assertEquals(ticketsBefore, tickets(setup));
        assertEquals(replies, jdbc.queryForObject("select count(*) from helpdesk_reply", Long.class), "nothing answers by itself");
    }

    @Test
    void repliesNeedTheirRightAndTheContactOfTheTicket() throws Exception {
        Setup setup = setup();
        smtp(setup, MAIL.getSmtp().getPort());
        long number = (Long) ticketFromMail(setup, "Rechte")[0];
        String path = ticketPath(setup, number);

        // Reading every field is not enough without helpdesk.reply.
        Browser reader = member(setup);
        grant(reader.id, setup.project(), "ticket.read", "READ:*");
        assertFalse(reader.get(path).json().get("replyAllowed").asBoolean());
        assertEquals(403, reader.get(path + "/reply").status());
        assertEquals(403, reply(reader, setup, number, "Text").status());
        // helpdesk.reply without the read rule for the mails is not enough either.
        Browser blind = member(setup);
        grant(blind.id, setup.project(), "helpdesk.reply", "ticket.read", "READ:title", "READ:contact");
        assertEquals(403, reply(blind, setup, number, "Text").status());
        // Someone outside the project does not see the ticket.
        assertEquals(404, reply(person(), setup, number, "Text").status());

        Browser replier = replier(setup);
        Response foreign = replier.send("POST", path + "/replies", Map.of("mailboxId", setup.mailbox(), "to", "jemand@fremd.example",
            "subject", "Re", "body", "Text"), null);
        assertEquals(400, foreign.status());
        assertTrue(foreign.body().contains("to.not_of_contact"), foreign.body());
        Response empty = replier.send("POST", path + "/replies", Map.of("mailboxId", setup.mailbox(), "subject", "Zwei\nZeilen", "body", " "), null);
        assertTrue(empty.body().contains("subject.invalid") && empty.body().contains("body.invalid"), empty.body());
        jdbc.update("update helpdesk_mailbox set smtp_host = null where id = ?", setup.mailbox());
        Response unavailable = reply(replier, setup, number, "Text");
        assertTrue(unavailable.body().contains("mailboxId.unavailable"), unavailable.body());
        smtp(setup, MAIL.getSmtp().getPort());

        // Without a contact on the ticket there is nobody to answer.
        Browser editor = reviewer(setup);
        assertEquals(200, editor.send("PUT", path + "/customer", Map.of("customerId", setup.customer()), null).status());
        assertEquals(409, reply(replier, setup, number, "Text").status());
        assertEquals(0, jdbc.queryForObject("select count(*) from helpdesk_reply where project_id = ?", Long.class, setup.project()));
    }

    @Test
    void aMissingAnswerOfTheServerLeavesTheReplyUncertainAndNothingRepeatsIt() throws Exception {
        Setup setup = setup();
        try (ScriptedSmtp server = new ScriptedSmtp(ScriptedSmtp.Mode.DROP_AFTER_DATA)) {
            smtp(setup, server.port());
            long number = (Long) ticketFromMail(setup, "Unklar")[0];
            Browser replier = replier(setup);
            UUID reply = UUID.fromString(reply(replier, setup, number, "Antwort").json().get("id").asString());

            RuntimeException failure = runReplyJob(reply, 1);
            assertTrue(failure instanceof JobFailure known && known.permanent() && known.code().equals("helpdesk.send_uncertain"), String.valueOf(failure));
            assertEquals("uncertain", replyState(reply));
            assertEquals(1, server.handedOver().size());
            // A later run of the same job does not send again.
            assertNull(runReplyJob(reply, 2));
            assertEquals(1, server.connections());

            // The open sends show it to whoever may reply.
            Response open = replier.get("/api/v1/helpdesk/replies");
            assertEquals("uncertain", find(open.json(), reply).get("state").asString());
            assertEquals(0, person().get("/api/v1/helpdesk/replies").json().size());

            // A person sends again only with the risk of a duplicate confirmed.
            Response view = replier.get(ticketPath(setup, number));
            String etag = "\"" + view.json().get("replies").get(0).get("version").asLong() + "\"";
            Response refused = replier.send("POST", ticketPath(setup, number) + "/replies/" + reply + "/send", Map.of(), etag);
            assertEquals(409, refused.status());
            assertEquals("helpdesk.reply_uncertain", refused.code());
            server.mode(ScriptedSmtp.Mode.ACCEPT);
            Response again = replier.send("POST", ticketPath(setup, number) + "/replies/" + reply + "/send", Map.of("duplicateAccepted", true), etag);
            assertEquals(200, again.status(), again.body());
            assertEquals(2, again.json().get("sends").asInt());
            assertNull(runReplyJob(reply, 1));
            assertEquals("sent", replyState(reply));
            assertEquals(2, server.handedOver().size());
        }
    }

    @Test
    void aRefusalFailsTheReplyAndATemporaryOneWaitsForTheNextAttempt() throws Exception {
        Setup setup = setup();
        try (ScriptedSmtp server = new ScriptedSmtp(ScriptedSmtp.Mode.REFUSE_RECIPIENT)) {
            smtp(setup, server.port());
            long number = (Long) ticketFromMail(setup, "Abgelehnt")[0];
            Browser replier = replier(setup);
            UUID refused = UUID.fromString(reply(replier, setup, number, "Eins").json().get("id").asString());
            RuntimeException failure = runReplyJob(refused, 1);
            assertTrue(failure instanceof JobFailure known && known.permanent(), String.valueOf(failure));
            assertEquals("failed", replyState(refused));
            assertEquals("helpdesk.recipient_refused", jdbc.queryForObject("select error_code from helpdesk_reply where id = ?", String.class, refused));

            server.mode(ScriptedSmtp.Mode.DEFER_RECIPIENT);
            UUID deferred = UUID.fromString(reply(replier, setup, number, "Zwei").json().get("id").asString());
            failure = runReplyJob(deferred, 1);
            assertTrue(failure instanceof JobFailure known && !known.permanent(), String.valueOf(failure));
            assertEquals("pending", replyState(deferred));
            failure = runReplyJob(deferred, 5);
            assertTrue(failure instanceof JobFailure known && known.permanent(), String.valueOf(failure));
            assertEquals("failed", replyState(deferred));
            assertEquals(0, server.handedOver().size(), "the server never took a mail");
        }
        // A server that is not reachable sent nothing for sure: the reply waits for the next attempt.
        UUID unreachable;
        try (ServerSocket closed = new ServerSocket(0)) {
            smtp(setup, closed.getLocalPort());
        }
        long number = jdbc.queryForObject("select number from ticket where project_id = ?", Long.class, setup.project());
        unreachable = UUID.fromString(reply(replier(setup), setup, number, "Drei").json().get("id").asString());
        RuntimeException failure = runReplyJob(unreachable, 1);
        assertTrue(failure instanceof JobFailure known && !known.permanent() && known.code().equals("helpdesk.connection_failed"), String.valueOf(failure));
        assertEquals("pending", replyState(unreachable));
    }

    @Test
    void anAttemptWhoseOutcomeWasLostIsNotRepeatedAndAChangedRecipientIsNotWritten() throws Exception {
        Setup setup = setup();
        try (ScriptedSmtp server = new ScriptedSmtp(ScriptedSmtp.Mode.ACCEPT)) {
            smtp(setup, server.port());
            long number = (Long) ticketFromMail(setup, "Abbruch")[0];
            Browser replier = replier(setup);
            // The process ended after the attempt began: the reply is still marked as being sent.
            UUID lost = UUID.fromString(reply(replier, setup, number, "Eins").json().get("id").asString());
            jdbc.update("update helpdesk_reply set state = 'sending' where id = ?", lost);
            assertNull(runReplyJob(lost, 2));
            assertEquals("uncertain", replyState(lost));
            assertEquals("helpdesk.send_interrupted", jdbc.queryForObject("select error_code from helpdesk_reply where id = ?", String.class, lost));
            assertEquals(0, server.connections());

            // A person who learned that it arrived records it as sent.
            Response view = replier.get(ticketPath(setup, number));
            String etag = "\"" + find(view.json().get("replies"), lost).get("version").asLong() + "\"";
            Response settled = replier.send("POST", ticketPath(setup, number) + "/replies/" + lost + "/settle", null, etag);
            assertEquals(200, settled.status(), settled.body());
            assertEquals("sent", settled.json().get("state").asString());
            assertEquals(0, server.connections());

            // The contact lost the address between the confirmation and the send: nothing is sent.
            UUID changed = UUID.fromString(reply(replier, setup, number, "Zwei").json().get("id").asString());
            jdbc.update("delete from helpdesk_contact_address where contact_id = ?", setup.contact());
            RuntimeException ignored = runReplyJob(changed, 1);
            assertNull(ignored);
            assertEquals("failed", replyState(changed));
            assertEquals("helpdesk.recipient_changed", jdbc.queryForObject("select error_code from helpdesk_reply where id = ?", String.class, changed));
            assertEquals(0, server.connections());
        }
    }

    private static String unknown(String prefix, int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> prefix + i + "@unbekannt.example")
            .collect(java.util.stream.Collectors.joining(", "));
    }

    /** A confirmed mail of the contact of the setup for a scripted folder; {@code headers} may add recipients. */
    private static byte[] scripted(Setup setup, String id, String headers) {
        return (confirmed("nordwerk.example") + "From: Lea Lang <" + setup.contactAddress() + ">\r\nTo: " + setup.address() + "\r\n" + headers
            + "Subject: " + id + "\r\nMessage-ID: <" + id + "-" + UUID.randomUUID() + "@nordwerk.example>\r\nMIME-Version: 1.0\r\n"
            + "Content-Type: text/plain; charset=UTF-8\r\n\r\nText\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static void hold(java.util.concurrent.CountDownLatch gate) {
        try {
            assertTrue(gate.await(20, java.util.concurrent.TimeUnit.SECONDS), "the fetch did not arrive");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private Browser member(Setup setup) {
        Browser member = person();
        Response added = setup.admin().send("PUT", "/api/v1/projects/" + setup.project() + "/members/" + member.id, null,
            setup.admin().get("/api/v1/projects/" + setup.project()).etag());
        assertEquals(200, added.status(), added.body());
        return member;
    }

    private void markAllUnseen(Setup setup) throws Exception {
        Store store = Session.getInstance(new Properties()).getStore("imap");
        store.connect("127.0.0.1", MAIL.getImap().getPort(), setup.account().getLogin(), MAIL_PASSWORD);
        try {
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            folder.setFlags(1, folder.getMessageCount(), new Flags(Flags.Flag.SEEN), false);
            folder.close(false);
        } finally {
            store.close();
        }
    }

    /** UIDVALIDITY and UID of the first mail in the folder of the setup. */
    private long[] firstOrigin(Setup setup) throws Exception {
        Store store = Session.getInstance(new Properties()).getStore("imap");
        store.connect("127.0.0.1", MAIL.getImap().getPort(), setup.account().getLogin(), MAIL_PASSWORD);
        try {
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            jakarta.mail.UIDFolder uids = (jakarta.mail.UIDFolder) folder;
            long[] origin = {uids.getUIDValidity(), uids.getUID(folder.getMessage(1))};
            folder.close(false);
            return origin;
        } finally {
            store.close();
        }
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
