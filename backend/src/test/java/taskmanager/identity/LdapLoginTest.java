package taskmanager.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.LDAPException;
import java.net.ServerSocket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import taskmanager.AdministrationFixture;
import taskmanager.TestDatabase;
import taskmanager.identity.ApiClient.Response;
import taskmanager.identity.account.AccountService;
import taskmanager.identity.account.AccountService.Issued;
import taskmanager.identity.directory.DirectoryClient;
import taskmanager.identity.directory.LdapSettings;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.CompanyContext;

/**
 * Sign in with the LDAP directory (TASK-046, DEC-075) against a real PostgreSQL and a directory that runs inside the test.
 * The directory holds a few people and a search account; the application knows the search account's password only from its
 * environment property.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = { "taskmanager.identity.source-failure-threshold=100000", "taskmanager.identity.ldap.bind-password=" + LdapLoginTest.SEARCH_SECRET,
        "taskmanager.identity.ldap.timeout=2s" })
@ExtendWith(OutputCaptureExtension.class)
class LdapLoginTest {
    static final String SEARCH_SECRET = "search-account-secret-7";
    private static final String BASE = "dc=example,dc=test";
    private static final String PEOPLE = "ou=people," + BASE;
    private static final String SEARCH_DN = "cn=search," + BASE;
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final String DATABASE = freshDatabase();
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final InMemoryDirectoryServer DIRECTORY = directory();

    private static String freshDatabase() {
        try {
            return TestDatabase.createEmptyDatabase();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static InMemoryDirectoryServer directory() {
        try {
            InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE);
            config.addAdditionalBindCredentials(SEARCH_DN, SEARCH_SECRET);
            config.setSchema(null);
            config.setGenerateOperationalAttributes(true);
            config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("plain", 0));
            InMemoryDirectoryServer server = new InMemoryDirectoryServer(config);
            server.add("dn: " + BASE, "objectClass: top", "objectClass: domain", "dc: example");
            server.add("dn: " + PEOPLE, "objectClass: top", "objectClass: organizationalUnit", "ou: people");
            server.startListening();
            return server;
        } catch (LDAPException | com.unboundid.ldif.LDIFException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stopDirectory() {
        DIRECTORY.shutDown(true);
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
    @Autowired CompanyContext companies;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DirectoryClient client;

    private ApiClient admin;
    private UUID adminId;

    // ---------------------------------------------------------------- helpers

    private static String name(String prefix) {
        return prefix + "-" + COUNTER.incrementAndGet();
    }

    private ApiClient browser() {
        ApiClient client = new ApiClient(port);
        assertEquals(200, client.get("/api/v1/session").status());
        return client;
    }

    private Response login(ApiClient browser, String username, String password) {
        return browser.post("/api/v1/session", Map.of("username", username, "password", password));
    }

    /** A logged in administrator of the company. */
    private ApiClient administrator() {
        Issued issued = accounts.create("test", name("admin"), null);
        adminId = issued.account().id();
        AdministrationFixture.grantAdministration(jdbc, companies, transactions, issued.account().id());
        ApiClient browser = browser();
        assertEquals(200, login(browser, issued.account().username(), issued.oneTimePassword()).status());
        assertEquals(200, browser.post("/api/v1/session/password", Map.of("currentPassword", issued.oneTimePassword(), "newPassword", STRONG)).status());
        return browser;
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> settings(String url, boolean enabled) {
        Map<String, Object> body = new HashMap<>();
        body.put("enabled", enabled);
        body.put("url", url);
        body.put("security", "none");
        body.put("bindDn", SEARCH_DN);
        body.put("searchBase", PEOPLE);
        body.put("userFilter", "(uid={0})");
        body.put("idAttribute", "entryUUID");
        body.put("nameAttribute", "cn");
        return body;
    }

    private static String directoryUrl() {
        return "ldap://localhost:" + DIRECTORY.getListenPort();
    }

    private void configure(boolean enabled) {
        Response saved = admin.put("/api/v1/settings/ldap", settings(directoryUrl(), enabled));
        assertEquals(200, saved.status(), saved.body());
    }

    /** Adds a person to the directory and returns the login name. */
    private String person(String prefix, String password, String displayName) throws Exception {
        String uid = name(prefix);
        DIRECTORY.add("dn: uid=" + uid + "," + PEOPLE, "objectClass: top", "objectClass: inetOrgPerson", "uid: " + uid, "cn: " + displayName,
            "sn: Test", "userPassword: " + password);
        return uid;
    }

    private JsonAccount accountOf(String username) {
        Response list = admin.get("/api/v1/accounts");
        assertEquals(200, list.status());
        for (var node : list.json()) {
            if (username.equals(node.get("username").asString())) {
                return new JsonAccount(node.get("id").asString(), node.get("authKind").asString(), node.get("active").asBoolean(),
                    node.get("displayName").isNull() ? null : node.get("displayName").asString());
            }
        }
        throw new AssertionError("no account " + username);
    }

    private record JsonAccount(String id, String authKind, boolean active, String displayName) {
    }

    @BeforeEach
    void setUp() {
        admin = administrator();
        configure(true);
    }

    // ---------------------------------------------------------------- the setting

    @Test
    void theSettingIsStoredWithoutTheSecretAndLogged() {
        Response read = admin.get("/api/v1/settings/ldap");
        assertEquals(200, read.status());
        assertTrue(read.json().get("configured").asBoolean());
        assertTrue(read.json().get("enabled").asBoolean());
        assertTrue(read.json().get("bindPasswordSet").asBoolean());
        assertEquals(SEARCH_DN, read.json().get("bindDn").asString());
        assertFalse(read.body().contains(SEARCH_SECRET));
        Response audit = admin.get("/api/v1/accounts/audit?limit=100");
        assertTrue(audit.body().contains("ldap.configured"), audit.body());
        assertFalse(audit.body().contains(SEARCH_SECRET));
    }

    @Test
    void theSettingNeedsTheRightToManageAccountsAndValidInput() {
        Issued plain = accounts.create("test", name("plain"), null);
        ApiClient nobody = browser();
        assertEquals(200, login(nobody, plain.account().username(), plain.oneTimePassword()).status());
        assertEquals(200, nobody.post("/api/v1/session/password", Map.of("currentPassword", plain.oneTimePassword(), "newPassword", STRONG)).status());
        assertEquals(403, nobody.get("/api/v1/settings/ldap").status());
        assertEquals(403, nobody.put("/api/v1/settings/ldap", settings(directoryUrl(), true)).status());
        assertEquals(403, nobody.post("/api/v1/settings/ldap/test", Map.of()).status());

        Map<String, Object> broken = settings("http://example.test", true);
        broken.put("security", "ldaps");
        broken.put("bindDn", "not a dn");
        broken.put("searchBase", "");
        broken.put("userFilter", "(uid=fixed)");
        broken.put("idAttribute", "bad attribute");
        Response refused = admin.put("/api/v1/settings/ldap", broken);
        assertEquals(400, refused.status());
        String body = refused.body();
        for (String violation : List.of("ldap.url.invalid", "ldap.bindDn.invalid", "ldap.searchBase.invalid", "ldap.userFilter.invalid", "ldap.idAttribute.invalid")) {
            assertTrue(body.contains(violation), violation + " in " + body);
        }
        Map<String, Object> mismatch = settings("ldap://localhost:389", true);
        mismatch.put("security", "ldaps");
        assertTrue(admin.put("/api/v1/settings/ldap", mismatch).body().contains("ldap.security.mismatch"));
        for (String filter : List.of("(uid={0})(cn=x)", "uid={0}", "(uid={1})", "(|(uid={0})", "(uid={0}){")) {
            Map<String, Object> wrongFilter = settings(directoryUrl(), true);
            wrongFilter.put("userFilter", filter);
            assertEquals(400, admin.put("/api/v1/settings/ldap", wrongFilter).status(), filter);
        }
        Map<String, Object> userInfo = settings("ldap://user:secret@localhost:389", true);
        assertEquals(400, admin.put("/api/v1/settings/ldap", userInfo).status());
        // Nothing of the refused requests was stored.
        assertEquals(directoryUrl(), admin.get("/api/v1/settings/ldap").json().get("url").asString());
    }

    @Test
    void theConnectionCheckNamesTheFailingStep() throws Exception {
        String known = person("check", "check-pass-1", "Check Person");
        assertEquals(Map.of("ok", true), Map.of("ok", admin.post("/api/v1/settings/ldap/test", Map.of()).json().get("ok").asBoolean()));
        Response withUser = admin.post("/api/v1/settings/ldap/test", Map.of("username", known));
        assertTrue(withUser.json().get("ok").asBoolean(), withUser.body());

        assertEquals("directory.user_not_found", reason(Map.of("username", "nobody-here")));
        DIRECTORY.add("dn: uid=twin-a," + PEOPLE, "objectClass: top", "objectClass: inetOrgPerson", "uid: twin-a", "mail: twin@example.test", "cn: A", "sn: A");
        DIRECTORY.add("dn: uid=twin-b," + PEOPLE, "objectClass: top", "objectClass: inetOrgPerson", "uid: twin-b", "mail: twin@example.test", "cn: B", "sn: B");
        Map<String, Object> byMail = settings(directoryUrl(), true);
        byMail.put("userFilter", "(mail={0})");
        assertEquals("directory.user_ambiguous", reason(Map.of("settings", byMail, "username", "twin@example.test")));

        Map<String, Object> closed = settings("ldap://localhost:" + freePort(), true);
        assertEquals("directory.unreachable", reason(Map.of("settings", closed)));
        Map<String, Object> wrongBase = settings(directoryUrl(), true);
        wrongBase.put("searchBase", "ou=missing," + BASE);
        assertEquals("directory.search_failed", reason(Map.of("settings", wrongBase)));
        Map<String, Object> wrongAccount = settings(directoryUrl(), true);
        wrongAccount.put("bindDn", "cn=other," + BASE);
        assertEquals("directory.bind_failed", reason(Map.of("settings", wrongAccount)));
        Map<String, Object> noId = settings(directoryUrl(), true);
        noId.put("idAttribute", "employeeNumber");
        assertEquals("directory.id_attribute_missing", reason(Map.of("settings", noId, "username", known)));
        // The failed checks stored nothing.
        assertEquals(directoryUrl(), admin.get("/api/v1/settings/ldap").json().get("url").asString());
    }

    private String reason(Map<String, Object> body) {
        Response response = admin.post("/api/v1/settings/ldap/test", body);
        assertEquals(200, response.status(), response.body());
        assertFalse(response.json().get("ok").asBoolean(), response.body());
        assertFalse(response.body().contains(SEARCH_SECRET));
        return response.json().get("reason").asString();
    }

    // ---------------------------------------------------------------- first login and assignment

    @Test
    void theAccountIsCreatedAtTheFirstLoginWithoutRightsAndFoundAgainByTheIdentifier() throws Exception {
        String uid = person("anna", "anna-pass-123", "Anna Beispiel");
        ApiClient anna = browser();
        Response first = login(anna, uid, "anna-pass-123");
        assertEquals(200, first.status(), first.body());
        assertEquals("full", first.json().get("level").asString());
        assertEquals("ldap", first.json().get("authKind").asString());
        assertEquals("Anna Beispiel", first.json().get("displayName").asString());
        Response state = anna.get("/api/v1/session");
        assertEquals(List.of(), toList(state.json().get("rights")));
        assertEquals(403, anna.get("/api/v1/accounts").status());

        JsonAccount account = accountOf(uid);
        assertEquals("ldap", account.authKind());
        assertTrue(account.active());
        assertEquals(1, jdbc.queryForObject("select count(*) from identity_account where external_id is not null and username = ?", Integer.class, uid));
        assertEquals(0, jdbc.queryForObject("select count(*) from permissions_membership where user_id = ?::uuid", Integer.class, account.id()));

        ApiClient again = browser();
        assertEquals(200, login(again, uid, "anna-pass-123").status());
        assertEquals(account.id(), accountOf(uid).id());
        assertEquals(1, jdbc.queryForObject("select count(*) from identity_account where username = ?", Integer.class, uid));
    }

    private static List<String> toList(tools.jackson.databind.JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(n -> values.add(n.asString()));
        return values;
    }

    @Test
    void theAssignmentFollowsTheIdentifierAndNotTheName() throws Exception {
        String uid = person("frank", "frank-pass-123", "Frank Erster");
        assertEquals(200, login(browser(), uid, "frank-pass-123").status());
        String id = accountOf(uid).id();

        // The entry is renamed; the same person signs in under the new name and reaches the same account.
        String renamed = name("frank-neu");
        try (com.unboundid.ldap.sdk.LDAPConnection connection = DIRECTORY.getConnection()) {
            connection.modifyDN("uid=" + uid + "," + PEOPLE, "uid=" + renamed, true);
        }
        ApiClient viaNewName = browser();
        Response response = login(viaNewName, renamed, "frank-pass-123");
        assertEquals(200, response.status(), response.body());
        assertEquals(uid, response.json().get("username").asString());
        assertEquals(id, accountOf(uid).id());
        assertEquals(0, jdbc.queryForObject("select count(*) from identity_account where username = ?", Integer.class, renamed));

        // A different person later gets the old name. That person does not take over the account of the first one.
        DIRECTORY.add("dn: uid=" + uid + "," + PEOPLE, "objectClass: top", "objectClass: inetOrgPerson", "uid: " + uid, "cn: Frank Zweiter",
            "sn: Test", "userPassword: second-pass-123");
        Response other = login(browser(), uid, "second-pass-123");
        assertEquals(401, other.status(), other.body());
        assertEquals("auth.invalid_credentials", other.code());
        assertEquals(id, accountOf(uid).id());
        assertEquals("Frank Erster", accountOf(uid).displayName());
    }

    @Test
    void aLocalAccountOfTheSameNameDecidesAloneAndNeverBecomesADirectoryAccount() throws Exception {
        String uid = person("bob", "directory-pass-1", "Bob Directory");
        Issued local = accounts.create("test", uid, "Bob Lokal");
        ApiClient localBrowser = browser();
        // The directory password does not open the local account of that name.
        Response viaDirectory = login(localBrowser, uid, "directory-pass-1");
        assertEquals(401, viaDirectory.status(), viaDirectory.body());
        // Its own one time password does.
        Response viaLocal = login(localBrowser, uid, local.oneTimePassword());
        assertEquals(200, viaLocal.status(), viaLocal.body());
        assertEquals("local", viaLocal.json().get("authKind").asString());
        assertEquals("local", accountOf(uid).authKind());
        assertEquals(0, jdbc.queryForObject("select count(*) from identity_account where external_id is not null and username = ?", Integer.class, uid));
    }

    @Test
    void aDirectoryAccountKeepsItsNameAgainstALocalAccount() throws Exception {
        String uid = person("carla", "carla-pass-123", "Carla");
        assertEquals(200, login(browser(), uid, "carla-pass-123").status());
        Response taken = admin.post("/api/v1/accounts", Map.of("username", uid));
        assertEquals(409, taken.status());
        assertEquals("account.username_taken", taken.code());
        // Not even the one time password of nothing: the name still belongs to the directory account.
        assertEquals("ldap", accountOf(uid).authKind());
    }

    @Test
    void wrongPasswordsUnknownNamesAndEmptyPasswordsAreRefusedAlike() throws Exception {
        String uid = person("dora", "dora-pass-123", "Dora");
        for (Map.Entry<String, String> attempt : List.of(Map.entry(uid, "wrong-password"), Map.entry(uid, ""), Map.entry("no-such-person", "dora-pass-123"),
                Map.entry("ab", "x"), Map.entry(uid + "*", "dora-pass-123"), Map.entry("*", "anything"))) {
            Response response = login(browser(), attempt.getKey(), attempt.getValue());
            assertEquals(401, response.status(), attempt + " " + response.body());
            assertEquals("auth.invalid_credentials", response.code());
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from identity_account where username = ?", Integer.class, uid));
    }

    @Test
    void inputWithSpecialCharactersNeverReachesTheFilter() throws Exception {
        person("eve", "eve-pass-123", "Eve");
        LdapSettings settings = new LdapSettings(true, directoryUrl(), "none", SEARCH_DN, PEOPLE, "(uid={0})", "entryUUID", "cn", null);
        for (String name : List.of("*", "eve*", "*)(uid=*", "x)(|(uid=*", "\\2a", "a\u0000b", "(", ")", "eve)(cn=*")) {
            assertTrue(client.authenticate(settings, name, "eve-pass-123").isEmpty(), name);
            assertTrue(client.authenticate(settings, name, "").isEmpty(), name);
        }
        // The filter is the administrator's and may combine the name with other conditions; the name still stays a value.
        LdapSettings narrowed = new LdapSettings(true, directoryUrl(), "none", SEARCH_DN, PEOPLE, "(&(objectClass=inetOrgPerson)(uid={0}))", "entryUUID", "cn", null);
        assertTrue(client.authenticate(narrowed, "*", "x").isEmpty());
        // A base with a slash in a name is not split up as a composite name.
        DIRECTORY.add("dn: ou=a/b," + BASE, "objectClass: top", "objectClass: organizationalUnit", "ou: a/b");
        DIRECTORY.add("dn: uid=slash-eve,ou=a\\/b," + BASE, "objectClass: top", "objectClass: inetOrgPerson", "uid: slash-eve", "cn: Slash Eve", "sn: S", "userPassword: slash-pass-1");
        LdapSettings slashed = new LdapSettings(true, directoryUrl(), "none", SEARCH_DN, "ou=a\\/b," + BASE, "(uid={0})", "entryUUID", "cn", null);
        assertTrue(client.authenticate(slashed, "slash-eve", "slash-pass-1").isPresent());
    }

    @Test
    void theNameFromTheDirectoryIsRefreshedAtTheNextLogin() throws Exception {
        String uid = person("gina", "gina-pass-123", "Gina Alt");
        assertEquals(200, login(browser(), uid, "gina-pass-123").status());
        assertEquals("Gina Alt", accountOf(uid).displayName());
        DIRECTORY.modify("dn: uid=" + uid + "," + PEOPLE, "changetype: modify", "replace: cn", "cn: Gina Neu");
        assertEquals(200, login(browser(), uid, "gina-pass-123").status());
        assertEquals("Gina Neu", accountOf(uid).displayName());
    }

    // ---------------------------------------------------------------- the account in the application

    @Test
    void aDirectoryAccountCanBeDeactivatedAndHasNoPasswordOfItsOwn() throws Exception {
        String uid = person("hans", "hans-pass-123", "Hans");
        ApiClient hans = browser();
        assertEquals(200, login(hans, uid, "hans-pass-123").status());
        String id = accountOf(uid).id();

        Response reset = admin.post("/api/v1/accounts/" + id + "/password-reset", Map.of());
        assertEquals(409, reset.status());
        assertEquals("account.directory_managed", reset.code());
        assertEquals(409, admin.put("/api/v1/accounts/" + id, Map.of("displayName", "Anders")).status());
        Response change = hans.post("/api/v1/session/password", Map.of("currentPassword", "hans-pass-123", "newPassword", STRONG));
        assertEquals(409, change.status(), change.body());
        assertEquals("account.directory_managed", change.code());
        assertEquals(200, hans.get("/api/v1/session").status());

        assertEquals(200, admin.post("/api/v1/accounts/" + id + "/deactivate", Map.of()).status());
        assertFalse(hans.get("/api/v1/session").json().get("authenticated").asBoolean());
        Response refused = login(browser(), uid, "hans-pass-123");
        assertEquals(401, refused.status());
        assertEquals("auth.invalid_credentials", refused.code());

        assertEquals(200, admin.post("/api/v1/accounts/" + id + "/reactivate", Map.of()).status());
        assertEquals(200, login(browser(), uid, "hans-pass-123").status());
    }

    @Test
    void theDirectoryAccountHasNoUsableLocalPassword() throws Exception {
        String uid = person("ida", "ida-pass-123", "Ida");
        assertEquals(200, login(browser(), uid, "ida-pass-123").status());
        // Directory switched off: the same name and password open nothing, because the account has no password of its own.
        configure(false);
        Response response = login(browser(), uid, "ida-pass-123");
        assertEquals(401, response.status());
        configure(true);
        assertEquals(200, login(browser(), uid, "ida-pass-123").status());
    }

    // ---------------------------------------------------------------- outage and throttling

    @Test
    void anOutageStopsOnlyTheDirectoryLogin() throws Exception {
        String uid = person("jonas", "jonas-pass-123", "Jonas");
        ApiClient jonas = browser();
        assertEquals(200, login(jonas, uid, "jonas-pass-123").status());
        Issued local = accounts.create("test", name("lokal"), null);

        assertEquals(200, admin.put("/api/v1/settings/ldap", settings("ldap://localhost:" + freePort(), true)).status());
        for (int i = 0; i < 8; i++) {
            Response down = login(browser(), uid, "jonas-pass-123");
            assertEquals(503, down.status(), down.body());
            assertEquals("auth.directory_unavailable", down.code());
        }
        // Local accounts and the open session are not affected.
        assertEquals(200, login(browser(), local.account().username(), local.oneTimePassword()).status());
        assertTrue(jonas.get("/api/v1/session").json().get("authenticated").asBoolean());
        assertEquals(200, jonas.get("/api/v1/session").status());
        // The outage did not count as failed attempts: the person signs in right after the directory is back.
        configure(true);
        assertEquals(200, login(browser(), uid, "jonas-pass-123").status());
    }

    @Test
    void wrongDirectoryPasswordsAreSlowedDownLikeLocalOnes() throws Exception {
        String uid = person("karl", "karl-pass-123", "Karl");
        int blocked = 0;
        for (int i = 0; i < 8; i++) {
            Response response = login(browser(), uid, "wrong-" + i);
            if (response.status() == 429) {
                blocked++;
                assertNotNull(response.header("Retry-After"));
            } else {
                assertEquals(401, response.status());
            }
        }
        assertTrue(blocked > 0, "the waiting time starts after repeated failures");
    }

    @Test
    void twoFirstLoginsOfTheSamePersonCreateOneAccount() throws Exception {
        String uid = person("lena", "lena-pass-123", "Lena");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                results.add(pool.submit(() -> login(browser(), uid, "lena-pass-123").status()));
            }
            for (Future<Integer> result : results) {
                assertEquals(200, result.get());
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from identity_account where username = ?", Integer.class, uid));
    }

    // ---------------------------------------------------------------- the local administration (TASK-047)

    /** Removes every other holder of the right to manage rights, so the accounts named are the whole administration. */
    private void isolate(UUID... keep) {
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("""
                delete from permissions_membership m using permissions_action a
                where a.company_id = m.company_id and a.group_id = m.group_id and a.project_id is null and a.action_key = 'permissions.manage'
                  and m.user_id <> all (?)
                """, (Object) keep);
            jdbc.update("update permissions_revision set revision = revision + 1");
        });
    }

    private UUID groupOf(UUID account, String action) {
        return jdbc.queryForObject("""
            select a.group_id from permissions_membership m join permissions_action a on a.company_id = m.company_id and a.group_id = m.group_id
            where m.user_id = ? and a.project_id is null and a.action_key = ?
            """, UUID.class, account, action);
    }

    private Response rightChange(ApiClient by, UUID group, String action, boolean enabled) {
        Map<String, Object> body = new HashMap<>(Map.of("action", action, "enabled", enabled));
        body.put("projectId", null);
        return by.put("/api/v1/permissions/groups/" + group + "/actions", body);
    }

    @Test
    void aDirectoryAdministrationCannotLockOutTheLastLocalOne() throws Exception {
        String uid = person("dirk", "dirk-pass-123", "Dirk Verzeichnis");
        ApiClient dirk = browser();
        assertEquals(200, login(dirk, uid, "dirk-pass-123").status());
        UUID dirkId = UUID.fromString(accountOf(uid).id());
        AdministrationFixture.grantAdministration(jdbc, companies, transactions, dirkId);
        isolate(adminId, dirkId);

        // The directory administration holds both rights too, but only the local account counts as the way out.
        Response deactivation = dirk.post("/api/v1/accounts/" + adminId + "/deactivate", Map.of());
        assertEquals(409, deactivation.status(), deactivation.body());
        assertEquals("account.last_administrator", deactivation.code());
        UUID group = groupOf(adminId, "permissions.manage");
        Response removal = dirk.delete("/api/v1/permissions/groups/" + group + "/members/" + adminId);
        assertEquals(409, removal.status(), removal.body());
        assertEquals("permissions.last_local_administrator", removal.code());
        for (String right : List.of("accounts.manage", "permissions.manage")) {
            Response withdrawal = rightChange(dirk, groupOf(adminId, right), right, false);
            assertEquals(409, withdrawal.status(), right + " " + withdrawal.body());
        }
        // The local account still administers, and a change that does not touch its two rights is fine.
        assertTrue(admin.get("/api/v1/accounts").status() == 200);
        assertEquals(200, rightChange(dirk, groupOf(adminId, "permissions.manage"), "appearance.manage", false).status());
        // The directory administration itself may be deactivated: the local one remains.
        assertEquals(200, admin.post("/api/v1/accounts/" + dirkId + "/deactivate", Map.of()).status());
    }

    @Test
    void theLocalAdministrationConvertsADirectoryAccountWhileTheDirectoryIsDown() throws Exception {
        String uid = person("emil", "emil-pass-123", "Emil Verzeichnis");
        ApiClient emil = browser();
        assertEquals(200, login(emil, uid, "emil-pass-123").status());
        UUID emilId = UUID.fromString(accountOf(uid).id());
        AdministrationFixture.grant(jdbc, companies, transactions, emilId, AdministrationRight.PROJECTS_MANAGE);

        assertEquals(200, admin.put("/api/v1/settings/ldap", settings("ldap://localhost:" + freePort(), true)).status());
        assertEquals(503, login(browser(), uid, "emil-pass-123").status());

        Response converted = admin.post("/api/v1/accounts/" + emilId + "/convert-to-local", Map.of());
        assertEquals(200, converted.status(), converted.body());
        String oneTime = converted.json().get("oneTimePassword").asString();
        assertEquals("local", converted.json().get("account").get("authKind").asString());
        assertEquals("open", converted.json().get("account").get("oneTimePasswordState").asString());
        assertFalse(converted.body().contains("emil-pass-123"));
        // The sessions of the account ended, the link to the entry is gone and the rights stay.
        assertFalse(emil.get("/api/v1/session").json().get("authenticated").asBoolean());
        assertEquals(0, jdbc.queryForObject("select count(*) from identity_account where id = ? and external_id is not null", Integer.class, emilId));
        assertEquals(1, jdbc.queryForObject("select count(*) from permissions_membership where user_id = ?", Integer.class, emilId));
        assertTrue(admin.get("/api/v1/accounts/audit?limit=50").body().contains("account.converted_to_local"));

        // While the directory is down the person signs in with the new one time password and chooses a password.
        ApiClient again = browser();
        Response first = login(again, uid, oneTime);
        assertEquals(200, first.status(), first.body());
        assertEquals("password_change_required", first.json().get("level").asString());
        assertEquals(200, again.post("/api/v1/session/password", Map.of("currentPassword", oneTime, "newPassword", STRONG)).status());
        assertEquals("local", again.get("/api/v1/session").json().get("authKind").asString());

        // Once the directory is back its password no longer opens the account: a local account of the name decides alone.
        configure(true);
        assertEquals(401, login(browser(), uid, "emil-pass-123").status());
        assertEquals(200, login(browser(), uid, STRONG).status());

        Response twice = admin.post("/api/v1/accounts/" + emilId + "/convert-to-local", Map.of());
        assertEquals(409, twice.status());
        assertEquals("account.already_local", twice.code());
    }

    @Test
    void aConvertedAccountCountsAsLocalAdministrationAndOnlyTheRightHoldersConvert() throws Exception {
        String uid = person("fred", "fred-pass-123", "Fred Verzeichnis");
        ApiClient fred = browser();
        assertEquals(200, login(fred, uid, "fred-pass-123").status());
        UUID fredId = UUID.fromString(accountOf(uid).id());
        AdministrationFixture.grantAdministration(jdbc, companies, transactions, fredId);
        isolate(adminId, fredId);
        // A person without the right to manage accounts cannot convert.
        Issued plain = accounts.create("test", name("plain"), null);
        ApiClient nobody = browser();
        assertEquals(200, login(nobody, plain.account().username(), plain.oneTimePassword()).status());
        assertEquals(200, nobody.post("/api/v1/session/password", Map.of("currentPassword", plain.oneTimePassword(), "newPassword", STRONG)).status());
        assertEquals(403, nobody.post("/api/v1/accounts/" + fredId + "/convert-to-local", Map.of()).status());
        assertEquals("ldap", accountOf(uid).authKind());

        // The converted account is a second local administration; now the first may be deactivated.
        assertEquals(409, fred.post("/api/v1/accounts/" + adminId + "/deactivate", Map.of()).status());
        assertEquals(200, admin.post("/api/v1/accounts/" + fredId + "/convert-to-local", Map.of()).status());
        assertEquals(200, admin.post("/api/v1/accounts/" + adminId + "/deactivate", Map.of()).status());
    }

    // ---------------------------------------------------------------- secrets

    @Test
    void noSecretIsStoredOrLogged(CapturedOutput output) throws Exception {
        String uid = person("mira", "mira-pass-123", "Mira");
        assertEquals(200, login(browser(), uid, "mira-pass-123").status());
        assertEquals(401, login(browser(), uid, "mira-wrong-pass").status());
        admin.post("/api/v1/settings/ldap/test", Map.of("username", uid));
        StringBuilder stored = new StringBuilder();
        for (String table : List.of("identity_account", "identity_audit_event", "identity_ldap_setting", "identity_setting", "identity_login_throttle")) {
            jdbc.queryForList("select t::text from " + table + " t", String.class).forEach(stored::append);
        }
        for (String secret : List.of(SEARCH_SECRET, "mira-pass-123", "mira-wrong-pass")) {
            assertFalse(stored.toString().contains(secret), secret);
            assertFalse(output.getAll().contains(secret), secret);
        }
        UUID.fromString(accountOf(uid).id());
    }
}
