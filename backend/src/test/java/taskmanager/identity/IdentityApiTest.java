package taskmanager.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import taskmanager.TestDatabase;
import taskmanager.identity.ApiClient.Response;
import taskmanager.identity.account.AccountService;
import taskmanager.identity.account.AccountService.Issued;

/**
 * The identity module through its real HTTP interface against a real PostgreSQL, with a clock the test
 * moves by hand. Each test creates its own accounts, so the tests do not depend on each other.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "taskmanager.identity.source-failure-threshold=100000")
@Import(IdentityApiTest.Clocks.class)
@ExtendWith(OutputCaptureExtension.class)
class IdentityApiTest {
    @TestConfiguration
    static class Clocks {
        @Bean
        @Primary
        MutableClock testClock() {
            return CLOCK;
        }
    }

    static final MutableClock CLOCK = new MutableClock();
    private static final String DATABASE = freshDatabase();
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private static String freshDatabase() {
        try {
            return TestDatabase.createEmptyDatabase();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE);
        registry.add("spring.datasource.username", TestDatabase::username);
        registry.add("spring.datasource.password", TestDatabase::password);
    }

    @Value("${local.server.port}") int port;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate jdbc;

    // ---------------------------------------------------------------- helpers

    /** A person at a browser: its cookies, name and the password it currently has. */
    private record Person(ApiClient browser, String username, String password) {
    }

    private static String name(String prefix) {
        return prefix + "-" + COUNTER.incrementAndGet();
    }

    private ApiClient browser() {
        ApiClient client = new ApiClient(port);
        Response state = client.get("/api/v1/session");
        assertEquals(200, state.status());
        assertNotNull(client.cookies.get("__Host-tm-csrf"), "the first visit hands out a CSRF token");
        return client;
    }

    private Response login(ApiClient browser, String username, String password) {
        return browser.post("/api/v1/session", Map.of("username", username, "password", password));
    }

    /** Creates an account with a one time password, completes the forced change and returns the logged in person. */
    private Person person(boolean administrator) {
        Issued issued = accounts.create("test", name(administrator ? "admin" : "user"), null, administrator);
        ApiClient browser = browser();
        assertEquals(200, login(browser, issued.account().username(), issued.oneTimePassword()).status());
        Response changed = browser.post("/api/v1/session/password", Map.of("currentPassword", issued.oneTimePassword(), "newPassword", STRONG));
        assertEquals(200, changed.status(), changed.body());
        assertEquals("full", changed.json().get("level").asString());
        return new Person(browser, issued.account().username(), STRONG);
    }

    private static void assertProblem(Response response, int status, String code) {
        assertEquals(status, response.status(), response.body());
        assertEquals("application/problem+json", response.header("Content-Type"));
        assertEquals(code, response.code());
        assertEquals(status, response.json().get("status").asInt());
        String correlation = response.header("X-Correlation-Id");
        assertEquals(correlation, response.json().get("correlationId").asString());
        assertEquals("urn:uuid:" + correlation, response.json().get("instance").asString());
        assertFalse(response.json().has("detail"));
    }

    // ---------------------------------------------------------------- default deny, CSRF

    @Test
    void everyApiPathWithoutLoginIsRefusedEvenWhenItDoesNotExist() {
        ApiClient anonymous = browser();
        for (String path : List.of("/api/v1/accounts", "/api/v1/settings/session-duration", "/api/v1/unknown", "/api/v1/tickets/1", "/api")) {
            Response response = anonymous.get(path);
            assertProblem(response, 401, "auth.required");
            assertEquals("Session realm=\"task-manager\"", response.header("WWW-Authenticate"), path);
            assertEquals("no-store", response.header("Cache-Control"), path);
        }
        assertProblem(anonymous.post("/api/v1/accounts", Map.of("username", "someone-x")), 401, "auth.required");
    }

    @Test
    void healthEndpointsStayReachableWithoutLogin() {
        assertEquals(200, new ApiClient(port).get("/actuator/health/readiness").status());
    }

    @Test
    void loginNeedsTheCsrfTokenFromTheCookie() {
        Issued issued = accounts.create("test", name("csrf"), null, false);
        ApiClient noToken = browser();
        noToken.sendCsrf = false;
        assertProblem(login(noToken, issued.account().username(), issued.oneTimePassword()), 403, "auth.csrf_invalid");

        ApiClient wrongToken = browser();
        wrongToken.csrfHeaderOverride = "not-the-cookie-value";
        assertProblem(login(wrongToken, issued.account().username(), issued.oneTimePassword()), 403, "auth.csrf_invalid");

        ApiClient noCookie = new ApiClient(port);
        noCookie.csrfHeaderOverride = "some-value";
        assertProblem(login(noCookie, issued.account().username(), issued.oneTimePassword()), 403, "auth.csrf_invalid");

        ApiClient foreignOrigin = browser();
        foreignOrigin.headers.put("Origin", "https://evil.example");
        assertProblem(login(foreignOrigin, issued.account().username(), issued.oneTimePassword()), 403, "auth.csrf_invalid");
    }

    @Test
    void anOriginOfThisHostIsAccepted() {
        Issued issued = accounts.create("test", name("origin"), null, false);
        ApiClient own = browser();
        own.headers.put("Origin", "http://localhost:" + port);
        assertEquals(200, login(own, issued.account().username(), issued.oneTimePassword()).status());
    }

    @Test
    void theCsrfTokenIsBoundToTheSession() {
        Person admin = person(true);
        ApiClient attacker = new ApiClient(port);
        attacker.cookies.putAll(admin.browser().cookies);
        attacker.csrfHeaderOverride = "attacker-controlled";
        attacker.cookies.put("__Host-tm-csrf", "attacker-controlled");
        assertProblem(attacker.post("/api/v1/accounts", Map.of("username", name("evil"))), 403, "auth.csrf_invalid");
        // The legitimate browser still works.
        assertEquals(201, admin.browser().post("/api/v1/accounts", Map.of("username", name("fine"))).status());
    }

    // ---------------------------------------------------------------- cookies

    @Test
    void cookiesCarryTheDocumentedAttributes() {
        Issued issued = accounts.create("test", name("cookie"), null, false);
        ApiClient browser = browser();
        Response response = login(browser, issued.account().username(), issued.oneTimePassword());
        List<String> cookies = response.setCookies();
        String session = cookies.stream().filter(c -> c.startsWith("__Host-tm-session=")).findFirst().orElseThrow();
        String csrf = cookies.stream().filter(c -> c.startsWith("__Host-tm-csrf=")).findFirst().orElseThrow();
        for (String cookie : List.of(session, csrf)) {
            assertTrue(cookie.contains("Secure"), cookie);
            assertTrue(cookie.contains("SameSite=Lax"), cookie);
            assertTrue(cookie.contains("Path=/"), cookie);
            assertFalse(cookie.contains("Domain"), cookie);
            assertTrue(cookie.contains("Max-Age="), cookie);
        }
        assertTrue(session.contains("HttpOnly"), session);
        assertFalse(csrf.contains("HttpOnly"), "the interface must read the CSRF token");
        assertEquals(response.json().get("csrfToken").asString(), browser.cookies.get("__Host-tm-csrf"));
    }

    // ---------------------------------------------------------------- forced password change

    @Test
    void theOneTimePasswordOnlyOpensTheForcedPasswordChange() {
        Issued issued = accounts.create("test", name("forced"), null, true);
        String username = issued.account().username();
        ApiClient browser = browser();
        Response login = login(browser, username, issued.oneTimePassword());
        assertEquals(200, login.status(), login.body());
        assertEquals("password_change_required", login.json().get("level").asString());
        assertFalse(login.json().get("passwordChangeBy").isNull());

        // No regular access over the API, whatever the endpoint or method.
        assertProblem(browser.get("/api/v1/accounts"), 403, "auth.password_change_required");
        assertProblem(browser.post("/api/v1/accounts", Map.of("username", name("nope"))), 403, "auth.password_change_required");
        assertProblem(browser.get("/api/v1/settings/session-duration"), 403, "auth.password_change_required");
        assertProblem(browser.get("/api/v1/anything"), 403, "auth.password_change_required");
        assertEquals("password_change_required", browser.get("/api/v1/session").json().get("level").asString());

        // Rules for the new password.
        Response tooShort = browser.post("/api/v1/session/password", Map.of("currentPassword", issued.oneTimePassword(), "newPassword", "short"));
        assertProblem(tooShort, 400, "password.policy_violation");
        assertTrue(tooShort.json().get("violations").toString().contains("password.too_short"));
        Response common = browser.post("/api/v1/session/password", Map.of("currentPassword", issued.oneTimePassword(), "newPassword", "Password123456"));
        assertTrue(common.json().get("violations").toString().contains("password.too_common"), common.body());
        Response sameAsName = browser.post("/api/v1/session/password", Map.of("currentPassword", issued.oneTimePassword(), "newPassword", username));
        assertTrue(sameAsName.json().get("violations").toString().contains("password.equals_username"), sameAsName.body());
        Response unchanged = browser.post("/api/v1/session/password", Map.of("currentPassword", issued.oneTimePassword(), "newPassword", issued.oneTimePassword()));
        assertTrue(unchanged.json().get("violations").toString().contains("password.unchanged"), unchanged.body());
        assertProblem(browser.post("/api/v1/session/password", Map.of("currentPassword", "wrong-one-time-password", "newPassword", STRONG)),
            400, "password.current_incorrect");

        // Still restricted after all those refusals, and the one time password still works for this single purpose.
        assertProblem(browser.get("/api/v1/accounts"), 403, "auth.password_change_required");
        Instant expiresBefore = Instant.parse(browser.get("/api/v1/session").json().get("expiresAt").asString());

        Response changed = browser.post("/api/v1/session/password", Map.of("currentPassword", issued.oneTimePassword(), "newPassword", STRONG));
        assertEquals(200, changed.status(), changed.body());
        assertEquals("full", changed.json().get("level").asString());
        assertEquals(200, browser.get("/api/v1/accounts").status());
        // The change does not extend the fixed period.
        assertEquals(expiresBefore, Instant.parse(changed.json().get("expiresAt").asString()));
    }

    @Test
    void aConsumedOneTimePasswordCannotBeUsedAgain() {
        Issued issued = accounts.create("test", name("consumed"), null, false);
        String username = issued.account().username();
        ApiClient first = browser();
        assertEquals(200, login(first, username, issued.oneTimePassword()).status());
        ApiClient secondBrowser = browser();
        assertEquals(200, login(secondBrowser, username, issued.oneTimePassword()).status(), "valid until it is used");
        assertEquals(200, first.post("/api/v1/session/password", Map.of("currentPassword", issued.oneTimePassword(), "newPassword", STRONG)).status());

        // The other half finished session of the same account ended with the change.
        assertProblem(secondBrowser.post("/api/v1/session/password", Map.of("currentPassword", issued.oneTimePassword(), "newPassword", "Another-Strong-Pass-7")),
            401, "auth.session_expired");
        assertProblem(login(browser(), username, issued.oneTimePassword()), 401, "auth.invalid_credentials");
        assertEquals(200, login(browser(), username, STRONG).status());
    }

    @Test
    void twoSimultaneousChangesWithTheSameOneTimePasswordCannotBothSucceed() throws Exception {
        Issued issued = accounts.create("test", name("race"), null, false);
        ApiClient first = browser();
        assertEquals(200, login(first, issued.account().username(), issued.oneTimePassword()).status());
        ApiClient second = new ApiClient(port);
        second.cookies.putAll(first.cookies);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            tasks.add(() -> first.post("/api/v1/session/password", Map.of("currentPassword", issued.oneTimePassword(), "newPassword", "First-Winner-Pass-1")).status());
            tasks.add(() -> second.post("/api/v1/session/password", Map.of("currentPassword", issued.oneTimePassword(), "newPassword", "Second-Winner-Pass-2")).status());
            List<Integer> statuses = pool.invokeAll(tasks).stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).sorted().toList();
            assertEquals(1, statuses.stream().filter(s -> s == 200).count(), statuses.toString());
            assertTrue(statuses.stream().filter(s -> s != 200).allMatch(s -> s == 401 || s == 400), statuses.toString());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void anExpiredOneTimePasswordIsRefusedAndAnAdministratorCanIssueANewOne() {
        Person admin = person(true);
        Response created = admin.browser().post("/api/v1/accounts", Map.of("username", name("late")));
        assertEquals(201, created.status(), created.body());
        String username = created.json().get("account").get("username").asString();
        String oneTime = created.json().get("oneTimePassword").asString();
        String id = created.json().get("account").get("id").asString();

        CLOCK.advance(Duration.ofHours(72).plusSeconds(1));
        assertProblem(login(browser(), username, oneTime), 401, "auth.one_time_password_expired");
        // The administrator's own 24 hour session ended long ago; a new login is needed.
        ApiClient adminAgain = browser();
        assertEquals(200, login(adminAgain, admin.username(), admin.password()).status());
        Response reset = adminAgain.post("/api/v1/accounts/" + id + "/password-reset", Map.of());
        assertEquals(200, reset.status(), reset.body());
        String fresh = reset.json().get("oneTimePassword").asString();
        assertNotEquals(oneTime, fresh);
        assertProblem(login(browser(), username, oneTime), 401, "auth.invalid_credentials");
        assertEquals(200, login(browser(), username, fresh).status());
    }

    @Test
    void theRestrictedSessionEndsAfterThirtyMinutes() {
        Issued issued = accounts.create("test", name("slow"), null, false);
        ApiClient browser = browser();
        assertEquals(200, login(browser, issued.account().username(), issued.oneTimePassword()).status());
        CLOCK.advance(Duration.ofMinutes(31));
        assertProblem(browser.post("/api/v1/session/password", Map.of("currentPassword", issued.oneTimePassword(), "newPassword", STRONG)),
            401, "auth.session_expired");
        // The one time password is still valid, so a new login starts the change again.
        ApiClient again = browser();
        assertEquals(200, login(again, issued.account().username(), issued.oneTimePassword()).status());
    }

    // ---------------------------------------------------------------- fixed session period

    @Test
    void theSessionPeriodIsFixedAndActivityNeverExtendsIt() {
        Person person = person(false);
        Instant expires = Instant.parse(person.browser().get("/api/v1/session").json().get("expiresAt").asString());
        assertEquals(Duration.ofHours(24), Duration.between(CLOCK.instant(), expires).plusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(0).compareTo(Duration.ofHours(23)) >= 0
            ? Duration.ofHours(24) : Duration.ZERO);
        for (int hour = 0; hour < 23; hour++) {
            CLOCK.advance(Duration.ofHours(1));
            Response state = person.browser().get("/api/v1/session");
            assertEquals(expires, Instant.parse(state.json().get("expiresAt").asString()), "activity at hour " + hour);
            assertProblem(person.browser().get("/api/v1/accounts"), 403, "auth.forbidden");
        }
        CLOCK.advance(Duration.ofMinutes(59));
        assertProblem(person.browser().get("/api/v1/accounts"), 403, "auth.forbidden");
        CLOCK.advance(Duration.ofMinutes(2));
        // Expired on the server: a direct API call with the cookie is refused and the stale cookie is cleared.
        Response refused = person.browser().get("/api/v1/accounts");
        assertProblem(refused, 401, "auth.session_expired");
        assertTrue(refused.setCookies().stream().anyMatch(c -> c.startsWith("__Host-tm-session=") && c.contains("Max-Age=0")));
        assertFalse(person.browser().get("/api/v1/session").json().get("authenticated").asBoolean());
        // A new login starts a new period.
        assertEquals(200, login(browser(), person.username(), person.password()).status());
    }

    @Test
    void theNewInstallationDefaultIsTwentyFourHours() {
        assertEquals("24", jdbc.queryForObject("select value from identity_setting where name = 'session_duration_hours'", String.class));
        Person admin = person(true);
        Response setting = admin.browser().get("/api/v1/settings/session-duration");
        assertEquals(200, setting.status());
        assertEquals(24, setting.json().get("hours").asInt());
        assertEquals(1, setting.json().get("minimumHours").asInt());
        assertEquals(720, setting.json().get("maximumHours").asInt());
    }

    @Test
    void shorteningTheDurationReachesExistingSessionsButExtendingDoesNot() {
        Person admin = person(true);
        Person colleague = person(false);
        try {
            assertEquals(200, admin.browser().put("/api/v1/settings/session-duration", Map.of("hours", 2)).status());
            // The existing 24 hour session is now limited to 2 hours since its start.
            CLOCK.advance(Duration.ofHours(3));
            assertProblem(colleague.browser().get("/api/v1/accounts"), 401, "auth.session_expired");
            // Administrator too: log in again with the 2 hour rule in force.
            ApiClient fresh = browser();
            Response login = login(fresh, admin.username(), admin.password());
            Instant start = CLOCK.instant();
            assertEquals(Duration.ofHours(2), Duration.between(start, Instant.parse(login.json().get("expiresAt").asString())).plusMillis(500).truncatedTo(java.time.temporal.ChronoUnit.HOURS));
            // Extending only applies to later logins.
            assertEquals(200, fresh.put("/api/v1/settings/session-duration", Map.of("hours", 48)).status());
            CLOCK.advance(Duration.ofHours(3));
            assertProblem(fresh.get("/api/v1/accounts"), 401, "auth.session_expired");
            ApiClient later = browser();
            Response laterLogin = login(later, admin.username(), admin.password());
            assertEquals(Duration.ofHours(48), Duration.between(CLOCK.instant(), Instant.parse(laterLogin.json().get("expiresAt").asString())).plusMillis(500).truncatedTo(java.time.temporal.ChronoUnit.HOURS));
            assertEquals(200, later.put("/api/v1/settings/session-duration", Map.of("hours", 24)).status());
        } finally {
            jdbc.update("update identity_setting set value = '24' where name = 'session_duration_hours'");
        }
    }

    @Test
    void theDurationSettingIsLimitedAndAdministratorsOnly() {
        Person admin = person(true);
        Person user = person(false);
        for (int hours : List.of(0, -1, 721, 100000)) {
            Response refused = admin.browser().put("/api/v1/settings/session-duration", Map.of("hours", hours));
            assertProblem(refused, 400, "request.invalid");
            assertTrue(refused.json().get("violations").toString().contains("hours.out_of_range"));
        }
        assertProblem(admin.browser().put("/api/v1/settings/session-duration", "{}"), 400, "request.invalid");
        assertProblem(admin.browser().put("/api/v1/settings/session-duration", Map.of("hours", "soon")), 400, "request.invalid");
        assertProblem(user.browser().put("/api/v1/settings/session-duration", Map.of("hours", 5)), 403, "auth.forbidden");
        assertProblem(user.browser().get("/api/v1/settings/session-duration"), 403, "auth.forbidden");
        assertEquals(200, admin.browser().put("/api/v1/settings/session-duration", Map.of("hours", 720)).status());
        assertEquals(200, admin.browser().put("/api/v1/settings/session-duration", Map.of("hours", 1)).status());
        assertEquals(200, admin.browser().put("/api/v1/settings/session-duration", Map.of("hours", 24)).status());
    }

    @Test
    void logoutEndsTheSessionOnTheServerAndIsRepeatable() {
        Person person = person(false);
        ApiClient copy = new ApiClient(port);
        copy.cookies.putAll(person.browser().cookies);
        assertEquals(204, person.browser().delete("/api/v1/session").status());
        assertProblem(copy.get("/api/v1/accounts"), 401, "auth.session_expired");
        // Logout cleared the cookies; the interface asks for the state again, which hands out a new CSRF token.
        assertFalse(person.browser().get("/api/v1/session").json().get("authenticated").asBoolean());
        assertEquals(204, person.browser().delete("/api/v1/session").status());
    }

    // ---------------------------------------------------------------- accounts

    @Test
    void anAdministratorCreatesAccountsAndTheOneTimePasswordIsShownOnce() {
        Person admin = person(true);
        String username = name("Created");
        Response created = admin.browser().post("/api/v1/accounts", Map.of("username", "  " + username + " ", "displayName", "Test Person"));
        assertEquals(201, created.status(), created.body());
        assertEquals(username.toLowerCase(), created.json().get("account").get("username").asString());
        assertTrue(created.json().get("account").get("passwordChangeRequired").asBoolean());
        assertEquals(20, created.json().get("oneTimePassword").asString().length());
        assertNotNull(created.json().get("oneTimePasswordExpiresAt"));

        Response list = admin.browser().get("/api/v1/accounts");
        assertEquals(200, list.status());
        assertTrue(list.body().contains(username.toLowerCase()));
        assertFalse(list.body().contains(created.json().get("oneTimePassword").asString()), "listing never repeats the one time password");
        assertFalse(list.body().toLowerCase().contains("argon2"), "no hash leaves the server");
        assertFalse(list.body().contains("password_hash"));

        // Case insensitive uniqueness, format and administrator only.
        assertProblem(admin.browser().post("/api/v1/accounts", Map.of("username", username.toUpperCase())), 409, "account.username_taken");
        for (String bad : List.of("ab", "has space", "ünicode-name", "-leading", "x".repeat(65), "semi;colon")) {
            Response refused = admin.browser().post("/api/v1/accounts", Map.of("username", bad));
            assertProblem(refused, 400, "request.invalid");
            assertTrue(refused.json().get("violations").toString().contains("username.invalid"), bad);
        }
        assertProblem(admin.browser().post("/api/v1/accounts", "{}"), 400, "request.invalid");
        assertProblem(person(false).browser().post("/api/v1/accounts", Map.of("username", name("sneaky"))), 403, "auth.forbidden");
        assertProblem(person(false).browser().get("/api/v1/accounts"), 403, "auth.forbidden");
    }

    @Test
    void secretsAreStoredOnlyAsHashes() {
        Issued issued = accounts.create("test", name("hash"), null, false);
        String hash = jdbc.queryForObject("select password_hash from identity_account where username = ?", String.class, issued.account().username());
        assertTrue(hash.startsWith("$argon2id$"), hash);
        assertFalse(hash.contains(issued.oneTimePassword()));
        ApiClient browser = browser();
        assertEquals(200, login(browser, issued.account().username(), issued.oneTimePassword()).status());
        String session = browser.cookies.get("__Host-tm-session");
        Integer matching = jdbc.queryForObject("select count(*) from identity_session where token_hash = decode(?, 'hex')", Integer.class,
            java.util.HexFormat.of().formatHex(taskmanager.identity.session.Tokens.hash(session)));
        assertEquals(1, matching);
        assertEquals(0, jdbc.queryForObject("select count(*) from identity_session where encode(token_hash, 'escape') = ?", Integer.class, session));
    }

    @Test
    void deactivationEndsSessionsAtOnceAndBlocksLoginsUntilReactivation() {
        Person admin = person(true);
        Person colleague = person(false);
        Response accountList = admin.browser().get("/api/v1/accounts");
        String id = idOf(accountList, colleague.username());
        Response deactivated = admin.browser().post("/api/v1/accounts/" + id + "/deactivate", Map.of());
        assertEquals(200, deactivated.status(), deactivated.body());
        assertFalse(deactivated.json().get("active").asBoolean());

        assertProblem(colleague.browser().get("/api/v1/accounts"), 401, "auth.session_expired");
        assertFalse(colleague.browser().get("/api/v1/session").json().get("authenticated").asBoolean());
        // Same answer as for a wrong password or an unknown name: nothing tells that the account exists.
        Response blocked = login(browser(), colleague.username(), colleague.password());
        assertProblem(blocked, 401, "auth.invalid_credentials");
        assertEquals(blocked.json().get("title").asString(), login(browser(), name("ghost"), STRONG).json().get("title").asString());

        assertEquals(200, admin.browser().post("/api/v1/accounts/" + id + "/reactivate", Map.of()).status());
        assertEquals(200, login(browser(), colleague.username(), colleague.password()).status());
        assertProblem(admin.browser().post("/api/v1/accounts/" + java.util.UUID.randomUUID() + "/deactivate", Map.of()), 404, "account.not_found");
    }

    @Test
    void theLastActiveAdministratorCannotBeDeactivated() {
        Person admin = person(true);
        jdbc.update("update identity_account set administrator = false where username <> ?", admin.username());
        String id = idOf(admin.browser().get("/api/v1/accounts"), admin.username());
        assertProblem(admin.browser().post("/api/v1/accounts/" + id + "/deactivate", Map.of()), 409, "account.last_administrator");
        assertEquals(200, admin.browser().get("/api/v1/accounts").status());
    }

    @Test
    void anAdministratorResetEndsSessionsAndForcesANewChange() {
        Person admin = person(true);
        Person colleague = person(false);
        String id = idOf(admin.browser().get("/api/v1/accounts"), colleague.username());
        Response reset = admin.browser().post("/api/v1/accounts/" + id + "/password-reset", Map.of());
        assertEquals(200, reset.status(), reset.body());
        assertProblem(colleague.browser().get("/api/v1/accounts"), 401, "auth.session_expired");
        assertProblem(login(browser(), colleague.username(), colleague.password()), 401, "auth.invalid_credentials");
        ApiClient again = browser();
        Response login = login(again, colleague.username(), reset.json().get("oneTimePassword").asString());
        assertEquals("password_change_required", login.json().get("level").asString());
    }

    @Test
    void aPasswordChangeByTheOwnerEndsTheirOtherSessions() {
        Person person = person(false);
        ApiClient second = browser();
        assertEquals(200, login(second, person.username(), person.password()).status());
        Response changed = person.browser().post("/api/v1/session/password", Map.of("currentPassword", person.password(), "newPassword", "Brand-New-Passphrase-3"));
        assertEquals(200, changed.status(), changed.body());
        assertProblem(second.get("/api/v1/accounts"), 401, "auth.session_expired");
        assertEquals(403, person.browser().get("/api/v1/accounts").status(), "the session that changed it keeps working");
        assertProblem(login(browser(), person.username(), person.password()), 401, "auth.invalid_credentials");
    }

    // ---------------------------------------------------------------- throttling

    @Test
    void repeatedFailuresCauseAGrowingWaitingTimeWithoutLockingTheAccount() {
        Person person = person(false);
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertProblem(login(browser(), person.username(), "wrong-" + attempt), 401, "auth.invalid_credentials");
        }
        // The correct password is refused while the waiting time runs, and the refusal names the wait.
        Response waiting = login(browser(), person.username(), person.password());
        assertProblem(waiting, 429, "auth.too_many_attempts");
        assertEquals("5", waiting.header("Retry-After"));
        CLOCK.advance(Duration.ofSeconds(4));
        assertEquals(429, login(browser(), person.username(), person.password()).status());
        CLOCK.advance(Duration.ofSeconds(2));
        assertEquals(200, login(browser(), person.username(), person.password()).status(), "no permanent lock");
        // A success clears the counter of the name.
        assertProblem(login(browser(), person.username(), "wrong-again"), 401, "auth.invalid_credentials");
    }

    @Test
    void theWaitingTimeDoublesUpToTheLimit() {
        Person person = person(false);
        long[] expected = {5, 10, 20, 40, 80, 160, 320, 640, 900, 900};
        for (int i = 0; i < 5; i++) {
            login(browser(), person.username(), "wrong");
        }
        for (long delay : expected) {
            Response waiting = login(browser(), person.username(), person.password());
            assertEquals(Long.toString(delay), waiting.header("Retry-After"));
            CLOCK.advance(Duration.ofSeconds(delay));
            Response again = login(browser(), person.username(), "wrong");
            assertEquals(401, again.status(), "after waiting " + delay + " seconds: " + again.body() + " retry " + again.header("Retry-After"));
        }
    }

    @Test
    void unknownNamesAreThrottledLikeKnownOnes() {
        String ghost = name("ghost");
        for (int attempt = 0; attempt < 5; attempt++) {
            assertProblem(login(browser(), ghost, "whatever-" + attempt), 401, "auth.invalid_credentials");
        }
        assertProblem(login(browser(), ghost, "whatever"), 429, "auth.too_many_attempts");
    }

    @Test
    void aWrongCurrentPasswordIsCountedForPasswordChangesToo() {
        Person person = person(false);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertProblem(person.browser().post("/api/v1/session/password", Map.of("currentPassword", "guess-" + attempt, "newPassword", "Some-Other-Passphrase-1")),
                400, "password.current_incorrect");
        }
        assertProblem(person.browser().post("/api/v1/session/password", Map.of("currentPassword", person.password(), "newPassword", "Some-Other-Passphrase-1")),
            429, "auth.too_many_attempts");
    }

    // ---------------------------------------------------------------- logging and audit

    @Test
    void passwordsAndTokensNeverReachTheLog(CapturedOutput output) {
        Person person = person(false);
        Issued issued = accounts.create("test", name("log"), null, false);
        login(browser(), issued.account().username(), "a-wrong-password-for-the-log-test");
        assertFalse(output.getAll().contains(STRONG));
        assertFalse(output.getAll().contains(issued.oneTimePassword()));
        assertFalse(output.getAll().contains("a-wrong-password-for-the-log-test"));
        assertFalse(output.getAll().contains(person.browser().cookies.get("__Host-tm-session")));
    }

    @Test
    void administrativeActionsAreAuditedWithoutSecrets() {
        Person admin = person(true);
        String username = name("audited");
        Response created = admin.browser().post("/api/v1/accounts", Map.of("username", username));
        String id = created.json().get("account").get("id").asString();
        admin.browser().post("/api/v1/accounts/" + id + "/password-reset", Map.of());
        admin.browser().post("/api/v1/accounts/" + id + "/deactivate", Map.of());
        List<String> events = jdbc.queryForList("select event from identity_audit_event where account_id = ?::uuid order by id", String.class, id);
        assertEquals(List.of("account.created", "password.reset", "account.deactivated"), events);
        assertEquals(admin.username(), jdbc.queryForObject("select actor from identity_audit_event where account_id = ?::uuid and event = 'account.created'", String.class, id));
        String secret = created.json().get("oneTimePassword").asString();
        assertEquals(0, jdbc.queryForObject("select count(*) from identity_audit_event where detail like ?", Integer.class, "%" + secret + "%"));
    }

    // ---------------------------------------------------------------- the contract for other modules

    @Test
    void protectedHandlersReceiveTheAccessContext() {
        Person admin = person(true);
        Response state = admin.browser().get("/api/v1/session");
        assertEquals(admin.username(), state.json().get("username").asString());
        assertTrue(state.json().get("administrator").asBoolean());
        assertNull(state.json().get("csrfToken").isNull() ? null : state.json().get("csrfToken"));
    }

    private static String idOf(Response accountList, String username) {
        for (var node : accountList.json()) {
            if (username.equals(node.get("username").asString())) {
                return node.get("id").asString();
            }
        }
        throw new AssertionError("account not listed: " + username);
    }
}
