package taskmanager.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.util.ssl.KeyStoreKeyManager;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import taskmanager.AdministrationFixture;
import taskmanager.TestDatabase;
import taskmanager.identity.ApiClient.Response;
import taskmanager.identity.account.AccountService;
import taskmanager.identity.account.AccountService.Issued;
import taskmanager.platform.CompanyContext;

/**
 * The encrypted connections to the directory (TASK-046): LDAPS and StartTLS against a directory with a self signed
 * certificate for {@code localhost}, trusted through the trust store of the operation. A connection to the same server under a
 * name the certificate does not cover must fail at the certificate check.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = { "taskmanager.identity.source-failure-threshold=100000", "taskmanager.identity.ldap.bind-password=" + LdapTlsTest.SEARCH_SECRET,
        "taskmanager.identity.ldap.timeout=3s" })
class LdapTlsTest {
    static final String SEARCH_SECRET = "search-account-secret-tls";
    private static final String STORE_PASSWORD = "changeit-store";
    private static final String BASE = "dc=example,dc=test";
    private static final String PEOPLE = "ou=people," + BASE;
    private static final String SEARCH_DN = "cn=search," + BASE;
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final String DATABASE = freshDatabase();
    private static final Path DIR = tempDirectory();
    private static final InMemoryDirectoryServer DIRECTORY = directory();

    private static String freshDatabase() {
        try {
            return TestDatabase.createEmptyDatabase();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path tempDirectory() {
        try {
            return Files.createTempDirectory("tm-ldap-tls");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void keytool(String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IllegalStateException(output);
        }
    }

    private static InMemoryDirectoryServer directory() {
        try {
            Path server = DIR.resolve("server.p12");
            Path certificate = DIR.resolve("server.crt");
            keytool("-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=localhost",
                "-ext", "SAN=dns:localhost", "-storetype", "PKCS12", "-keystore", server.toString(), "-storepass", STORE_PASSWORD);
            keytool("-exportcert", "-alias", "server", "-keystore", server.toString(), "-storepass", STORE_PASSWORD, "-file", certificate.toString());
            keytool("-importcert", "-noprompt", "-alias", "server", "-file", certificate.toString(), "-storetype", "PKCS12",
                "-keystore", DIR.resolve("trust.p12").toString(), "-storepass", STORE_PASSWORD);
            SSLUtil ssl = new SSLUtil(new KeyStoreKeyManager(server.toString(), STORE_PASSWORD.toCharArray(), "PKCS12", "server"), new TrustAllTrustManager());
            InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(BASE);
            config.addAdditionalBindCredentials(SEARCH_DN, SEARCH_SECRET);
            config.setSchema(null);
            config.setGenerateOperationalAttributes(true);
            config.setListenerConfigs(
                InMemoryListenerConfig.createLDAPSConfig("ldaps", 0, ssl.createSSLServerSocketFactory()),
                InMemoryListenerConfig.createLDAPConfig("starttls", null, 0, ssl.createSSLSocketFactory()));
            InMemoryDirectoryServer result = new InMemoryDirectoryServer(config);
            result.add("dn: " + BASE, "objectClass: top", "objectClass: domain", "dc: example");
            result.add("dn: " + PEOPLE, "objectClass: top", "objectClass: organizationalUnit", "ou: people");
            result.add("dn: uid=tina," + PEOPLE, "objectClass: top", "objectClass: inetOrgPerson", "uid: tina", "cn: Tina Tls", "sn: T", "userPassword: tina-pass-123");
            result.startListening();
            return result;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void stopDirectory() {
        DIRECTORY.shutDown(true);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE);
        registry.add("spring.datasource.username", TestDatabase::username);
        registry.add("spring.datasource.password", TestDatabase::password);
        registry.add("taskmanager.identity.ldap.trust-store", () -> DIR.resolve("trust.p12").toString());
        registry.add("taskmanager.identity.ldap.trust-store-password", () -> STORE_PASSWORD);
    }

    @Value("${local.server.port}") int port;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyContext companies;
    @Autowired PlatformTransactionManager transactions;

    private ApiClient admin;
    private int counter;

    @BeforeEach
    void setUp() {
        Issued issued = accounts.create("test", "tls-admin-" + (++counter) + "-" + System.nanoTime() % 100000, null);
        AdministrationFixture.grantAdministration(jdbc, companies, transactions, issued.account().id());
        admin = new ApiClient(port);
        admin.get("/api/v1/session");
        assertEquals(200, admin.post("/api/v1/session", Map.of("username", issued.account().username(), "password", issued.oneTimePassword())).status());
        assertEquals(200, admin.post("/api/v1/session/password", Map.of("currentPassword", issued.oneTimePassword(), "newPassword", STRONG)).status());
    }

    private static Map<String, Object> settings(String url, String security) {
        Map<String, Object> body = new HashMap<>();
        body.put("enabled", true);
        body.put("url", url);
        body.put("security", security);
        body.put("bindDn", SEARCH_DN);
        body.put("searchBase", PEOPLE);
        body.put("userFilter", "(uid={0})");
        body.put("idAttribute", "entryUUID");
        body.put("nameAttribute", "cn");
        return body;
    }

    private Response check(Map<String, Object> settings, String username) {
        Response response = admin.post("/api/v1/settings/ldap/test", Map.of("settings", settings, "username", username));
        assertEquals(200, response.status(), response.body());
        return response;
    }

    private String ldaps(String host) {
        return "ldaps://" + host + ":" + DIRECTORY.getListenPort("ldaps");
    }

    private String starttls(String host) {
        return "ldap://" + host + ":" + DIRECTORY.getListenPort("starttls");
    }

    @Test
    void ldapsAndStartTlsReachTheDirectoryWhenTheCertificateIsTrusted() {
        assertTrue(check(settings(ldaps("localhost"), "ldaps"), "tina").json().get("ok").asBoolean());
        assertTrue(check(settings(starttls("localhost"), "starttls"), "tina").json().get("ok").asBoolean());
    }

    @Test
    void aCertificateForAnotherNameIsRefused() {
        Response ldaps = check(settings(ldaps("127.0.0.1"), "ldaps"), "tina");
        assertFalse(ldaps.json().get("ok").asBoolean(), ldaps.body());
        assertEquals("directory.tls_failed", ldaps.json().get("reason").asString());
        Response starttls = check(settings(starttls("127.0.0.1"), "starttls"), "tina");
        assertFalse(starttls.json().get("ok").asBoolean(), starttls.body());
        assertEquals("directory.tls_failed", starttls.json().get("reason").asString());
    }

    @Test
    void aPlainConnectionToAnEncryptedPortOrTheOtherWayRoundFails() {
        Response plainToLdaps = check(settings("ldap://localhost:" + DIRECTORY.getListenPort("ldaps"), "none"), "tina");
        assertFalse(plainToLdaps.json().get("ok").asBoolean(), plainToLdaps.body());
        Response ldapsToPlain = check(settings("ldaps://localhost:" + DIRECTORY.getListenPort("starttls"), "ldaps"), "tina");
        assertFalse(ldapsToPlain.json().get("ok").asBoolean(), ldapsToPlain.body());
    }

    @Test
    void aPersonSignsInThroughBothEncryptedConnections() {
        for (Map<String, Object> encrypted : java.util.List.of(settings(ldaps("localhost"), "ldaps"), settings(starttls("localhost"), "starttls"))) {
            assertEquals(200, admin.put("/api/v1/settings/ldap", encrypted).status());
            ApiClient tina = new ApiClient(port);
            tina.get("/api/v1/session");
            Response login = tina.post("/api/v1/session", Map.of("username", "tina", "password", "tina-pass-123"));
            assertEquals(200, login.status(), login.body());
            assertEquals("ldap", login.json().get("authKind").asString());
            assertEquals(401, tina.post("/api/v1/session", Map.of("username", "tina", "password", "wrong-pass-1")).status());
        }
    }
}
