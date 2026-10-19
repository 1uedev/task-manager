package taskmanager.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
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
import taskmanager.identity.ApiClient.Response;
import taskmanager.identity.account.AccountService;
import taskmanager.identity.session.SessionDuration;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.CompanyContext;

/**
 * TASK-040: the administrative endpoints of the identity and appearance modules ask the permissions module for their own
 * company right, through the real adapter against a real PostgreSQL. No identity marker exists any more; a person gets
 * exactly the rights that were granted to a group of theirs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "taskmanager.identity.source-failure-threshold=100000")
class AdministrationRightsApiTest {
    private static final String DATABASE = freshDatabase();
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ExecutorService POOL = Executors.newCachedThreadPool();

    private static String freshDatabase() {
        try {
            return TestDatabase.createEmptyDatabase();
        } catch (SQLException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE);
        registry.add("spring.datasource.username", TestDatabase::username);
        registry.add("spring.datasource.password", TestDatabase::password);
    }

    @AfterAll
    static void stopThreads() {
        POOL.shutdownNow();
    }

    @Value("${local.server.port}") int port;
    @Autowired AccountService accounts;
    @Autowired SessionDuration duration;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyContext companies;
    @Autowired PlatformTransactionManager transactions;

    /** A person with a complete login. */
    private record Person(ApiClient browser, UUID id, String username) {
    }

    private Person person(boolean manage, AdministrationRight... rights) {
        var issued = accounts.create("test", "rights-" + COUNTER.incrementAndGet(), null);
        if (manage || rights.length > 0) {
            AdministrationFixture.grant(jdbc, companies, transactions, issued.account().id(), manage, rights);
        }
        return login(issued);
    }

    private Person login(AccountService.Issued issued) {
        ApiClient browser = new ApiClient(port);
        assertEquals(200, browser.get("/api/v1/session").status());
        Response login = browser.post("/api/v1/session", Map.of("username", issued.account().username(), "password", issued.oneTimePassword()));
        assertEquals(200, login.status(), login.body());
        Response changed = browser.post("/api/v1/session/password", Map.of("currentPassword", issued.oneTimePassword(), "newPassword", STRONG));
        assertEquals(200, changed.status(), changed.body());
        return new Person(browser, issued.account().id(), issued.account().username());
    }

    private static void assertForbidden(Response response, String what) {
        assertEquals(403, response.status(), what + ": " + response.body());
        assertEquals("auth.forbidden", response.code(), what);
    }

    private static List<String> rightsOf(Response session) {
        return StreamSupport.stream(session.json().get("rights").spliterator(), false).map(node -> node.asString()).toList();
    }

    private String versionOfAppearance(Person reader) {
        return reader.browser().get("/api/v1/appearance").header("ETag");
    }

    // ---------------------------------------------------------------------------------------------- the matrix

    /** One administrative call, the right it needs and the status of its success. */
    private record Call(String name, AdministrationRight right, int success, Function<Person, Response> run) {
    }

    @Test
    void eachAdministrativeEndpointNeedsItsOwnRightAndNoOtherRightOrMarkerHelps() {
        Person admin = person(true, AdministrationRight.values());
        UUID target = accounts.create("test", "rights-target-" + COUNTER.incrementAndGet(), null).account().id();
        Person reader = person(false);

        List<Call> calls = List.of(
            new Call("list accounts", AdministrationRight.ACCOUNTS_MANAGE, 200, who -> who.browser().get("/api/v1/accounts")),
            new Call("create account", AdministrationRight.ACCOUNTS_MANAGE, 201,
                who -> who.browser().post("/api/v1/accounts", Map.of("username", "rights-new-" + COUNTER.incrementAndGet()))),
            new Call("reactivate account", AdministrationRight.ACCOUNTS_MANAGE, 200,
                who -> who.browser().post("/api/v1/accounts/" + target + "/reactivate", Map.of())),
            new Call("reset password", AdministrationRight.ACCOUNTS_MANAGE, 200,
                who -> who.browser().post("/api/v1/accounts/" + target + "/password-reset", Map.of())),
            new Call("deactivate account", AdministrationRight.ACCOUNTS_MANAGE, 200,
                who -> who.browser().post("/api/v1/accounts/" + target + "/deactivate", Map.of())),
            new Call("read session duration", AdministrationRight.SESSION_DURATION_MANAGE, 200,
                who -> who.browser().get("/api/v1/settings/session-duration")),
            new Call("change session duration", AdministrationRight.SESSION_DURATION_MANAGE, 200,
                who -> who.browser().put("/api/v1/settings/session-duration", Map.of("hours", 24))),
            new Call("change accent color", AdministrationRight.APPEARANCE_MANAGE, 200, who -> withVersion(who, reader,
                () -> who.browser().put("/api/v1/appearance", Map.of("accentColor", "#1d4ed8")))),
            new Call("remove logo", AdministrationRight.APPEARANCE_MANAGE, 200, who -> withVersion(who, reader,
                () -> who.browser().delete("/api/v1/appearance/logo"))));

        // The right to manage rights is no administrative right of the products, and a person with no right has none either.
        List<Person> holders = new ArrayList<>();
        for (AdministrationRight right : AdministrationRight.values()) {
            holders.add(person(false, right));
        }
        Person managesRightsOnly = person(true);
        Person nothing = person(false);

        for (Call call : calls) {
            for (Person who : holders) {
                int own = holders.indexOf(who);
                boolean allowed = AdministrationRight.values()[own] == call.right();
                if (allowed) {
                    assertEquals(call.success(), call.run().apply(who).status(), call.name() + " with its right");
                } else {
                    assertForbidden(call.run().apply(who), call.name() + " with " + AdministrationRight.values()[own]);
                }
            }
            assertForbidden(call.run().apply(managesRightsOnly), call.name() + " with permissions.manage only");
            assertForbidden(call.run().apply(nothing), call.name() + " without any right");
        }
        assertEquals(call(admin, "list accounts", calls).status(), 200, "the full administration can do everything");
    }

    private static Response call(Person who, String name, List<Call> calls) {
        return calls.stream().filter(call -> call.name().equals(name)).findFirst().orElseThrow().run().apply(who);
    }

    /** Sends the change with the version of the setting that any logged in person reads; a refused call changes nothing. */
    private Response withVersion(Person who, Person reader, java.util.function.Supplier<Response> change) {
        who.browser().headers.put("If-Match", versionOfAppearance(reader));
        try {
            return change.get();
        } finally {
            who.browser().headers.remove("If-Match");
        }
    }

    // ---------------------------------------------------------------------------------------------- what the session says

    @Test
    void theSessionListsExactlyTheHeldRightsSortedAndOnlyForAnAuthenticatedState() {
        Person some = person(false, AdministrationRight.SESSION_DURATION_MANAGE, AdministrationRight.ACCOUNTS_MANAGE);
        assertEquals(List.of("accounts.manage", "session_duration.manage"), rightsOf(some.browser().get("/api/v1/session")));
        Person all = person(true, AdministrationRight.values());
        assertEquals(List.of("accounts.manage", "appearance.manage", "session_duration.manage"), rightsOf(all.browser().get("/api/v1/session")),
            "permissions.manage is not an administrative right of the products and is not listed");
        assertEquals(List.of(), rightsOf(person(false).browser().get("/api/v1/session")));
        assertNull(new ApiClient(port).get("/api/v1/session").json().get("rights"), "an anonymous visitor gets no list");
        assertFalse(some.browser().get("/api/v1/session").json().has("administrator"), "the marker is gone");
    }

    @Test
    void loginAndPasswordChangeDoNotClaimRightsAndTheBodyOfAnAccountHasNoMarker() {
        Person admin = person(false, AdministrationRight.ACCOUNTS_MANAGE);
        Response created = admin.browser().post("/api/v1/accounts", Map.of("username", "rights-fresh-" + COUNTER.incrementAndGet()));
        assertEquals(201, created.status(), created.body());
        assertFalse(created.json().get("account").has("administrator"));
        ApiClient browser = new ApiClient(port);
        browser.get("/api/v1/session");
        Response login = browser.post("/api/v1/session", Map.of("username", created.json().get("account").get("username").asString(),
            "password", created.json().get("oneTimePassword").asString()));
        assertEquals(200, login.status(), login.body());
        assertFalse(login.json().has("rights"), "the interface reads the state after a login");
        assertEquals(List.of(), rightsOf(browser.get("/api/v1/session")), "a login that is not complete holds nothing");
        assertForbidden403PasswordChange(browser.get("/api/v1/accounts"));
    }

    private static void assertForbidden403PasswordChange(Response response) {
        assertEquals(403, response.status(), response.body());
        assertEquals("auth.password_change_required", response.code());
    }

    @Test
    void theOldMarkerInARequestGrantsNothing() {
        Person admin = person(false, AdministrationRight.ACCOUNTS_MANAGE);
        Response created = admin.browser().post("/api/v1/accounts", Map.of("username", "rights-marked-" + COUNTER.incrementAndGet(), "administrator", true));
        assertEquals(201, created.status(), created.body());
        ApiClient browser = new ApiClient(port);
        browser.get("/api/v1/session");
        String username = created.json().get("account").get("username").asString();
        String oneTime = created.json().get("oneTimePassword").asString();
        assertEquals(200, browser.post("/api/v1/session", Map.of("username", username, "password", oneTime)).status());
        assertEquals(200, browser.post("/api/v1/session/password", Map.of("currentPassword", oneTime, "newPassword", STRONG)).status());
        assertEquals(List.of(), rightsOf(browser.get("/api/v1/session")));
        assertForbidden(browser.get("/api/v1/accounts"), "the new account");
        assertEquals(0, jdbc.queryForObject("select count(*) from permissions_membership m join identity_account a on a.id = m.user_id where a.username = ?",
            Integer.class, username));
    }

    // ---------------------------------------------------------------------------------------------- withdrawal

    @Test
    void aWithdrawnRightIsRefusedWithTheNextRequestAndTheListShrinks() {
        Person person = person(false, AdministrationRight.APPEARANCE_MANAGE, AdministrationRight.ACCOUNTS_MANAGE);
        Person reader = person(false);
        person.browser().headers.put("If-Match", versionOfAppearance(reader));
        assertEquals(200, person.browser().put("/api/v1/appearance", Map.of("accentColor", "#0f766e")).status());
        AdministrationFixture.withdrawAll(jdbc, companies, transactions, person.id());
        person.browser().headers.put("If-Match", versionOfAppearance(reader));
        assertForbidden(person.browser().put("/api/v1/appearance", Map.of("accentColor", "#9333ea")), "accent after the withdrawal");
        assertForbidden(person.browser().get("/api/v1/accounts"), "accounts after the withdrawal");
        person.browser().headers.remove("If-Match");
        assertEquals(List.of(), rightsOf(person.browser().get("/api/v1/session")));
        assertEquals("#0f766e", jdbc.queryForObject("select accent_color from appearance_setting", String.class));
        assertEquals(200, person.browser().get("/api/v1/appearance").status(), "reading stays open to every complete login");
    }

    @Test
    void aRightWithdrawnWhileTheChangeWaitsForTheRightsLockIsSeenAndNothingIsWritten() throws Exception {
        Person person = person(false, AdministrationRight.APPEARANCE_MANAGE);
        Person reader = person(false);
        String version = versionOfAppearance(reader);
        String before = jdbc.queryForObject("select accent_color from appearance_setting", String.class);

        // The withdrawal is open: it holds the rights revision of the company exclusively until it commits.
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> withdrawal = POOL.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("delete from permissions_membership where user_id = ?", person.id());
            jdbc.update("update permissions_revision set revision = revision + 1 where company_id = ?", companies.find().orElseThrow());
            locked.countDown();
            try {
                assertTrue(release.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(locked.await(15, TimeUnit.SECONDS));

        // The request built its context from the committed state, which still contains the right, and now waits for the lock.
        ApiClient waiting = person.browser();
        waiting.headers.put("If-Match", version);
        Future<Response> change = POOL.submit(() -> waiting.put("/api/v1/appearance", Map.of("accentColor", "#be123c")));
        assertTrue(waitsAt("permissions_revision where company_id%for share"), "the change must wait for the open withdrawal at the rights revision");
        release.countDown();
        withdrawal.get(15, TimeUnit.SECONDS);
        assertForbidden(change.get(15, TimeUnit.SECONDS), "the change that waited");
        assertEquals(before, jdbc.queryForObject("select accent_color from appearance_setting", String.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from appearance_audit_event where event = 'appearance.accent_changed' and detail like '%#be123c%'", Integer.class));
    }

    // ---------------------------------------------------------------------------------------------- lock order

    /**
     * A login (and a password change) read the setting row shared before they lock the account for update. The change of the
     * duration must therefore lock the setting row before the rights and identity locks of its requester, which include a
     * shared lock on the requester's own account. In the other order the two would wait for each other.
     */
    @Test
    void changingTheSessionDurationWhileTheSamePersonLogsInElsewhereNeverEndsInADeadlock() throws Exception {
        Person admin = person(false, AdministrationRight.SESSION_DURATION_MANAGE);
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        // What a login does: the setting row shared first, the account row for update second.
        Future<?> login = POOL.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            duration.hoursForShare();
            holding.countDown();
            try {
                assertTrue(proceed.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            jdbc.queryForObject("select id from identity_account where id = ? for update", UUID.class, admin.id());
        }));
        assertTrue(holding.await(15, TimeUnit.SECONDS));
        Future<Response> change = POOL.submit(() -> admin.browser().put("/api/v1/settings/session-duration", Map.of("hours", 24)));
        assertTrue(waitsAt("identity_setting"), "the change must be waiting for the login at the setting row");
        proceed.countDown();
        login.get(15, TimeUnit.SECONDS);
        Response changed = change.get(15, TimeUnit.SECONDS);
        assertEquals(200, changed.status(), changed.body());
    }

    /** True once a backend runs a statement on the text and PostgreSQL reports it as blocked. */
    private boolean waitsAt(String statementPart) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    select exists(select 1 from pg_stat_activity
                    where datname = current_database() and pid <> pg_backend_pid()
                    and query ilike ? and cardinality(pg_blocking_pids(pid)) > 0)
                    """, Boolean.class, "%" + statementPart + "%"))) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }
}
