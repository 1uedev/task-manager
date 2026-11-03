package taskmanager.appearance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import taskmanager.AdministrationFixture;
import taskmanager.TestDatabase;
import taskmanager.identity.account.AccountService;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.CompanyContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The appearance setting through its real HTTP interface against a real PostgreSQL. The setting is a single row,
 * so every test starts by resetting it to the default.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppearanceApiTest {
    private static final String DATABASE = freshDatabase();
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    // Real, complete images (TASK-043): a 3 x 2 PNG and JPEG drawn here, a 1 x 1 lossless and a 2 x 2 lossy WebP and a
    // 2 x 2 animated WebP with two lossless frames.
    private static final byte[] PNG = image("png");
    private static final byte[] JPEG = image("jpeg");
    private static final byte[] WEBP = HexFormat.of().parseHex("524946461a000000574542505650384c0d0000002f00000010071011118888fe0700");
    private static final byte[] WEBP_LOSSY = HexFormat.of().parseHex(
        "524946463c0000005745425056503820300000003002009d012a0200020001402625a00274ba01f801f80004a10000fee246bff6b40706abfd3b3ffb68675425fed00000");
    private static final byte[] WEBP_ANIMATED = HexFormat.of().parseHex(
        "524946468600000057454250565038580a00000002000000010000010000414e494d06000000000000000000414e4d462a0000000000000000"
            + "00010000010000640000025650384c110000002f0140000007d0b9e6d7a2ff8188e87f0000414e4d4628000000000000000000010000010000"
            + "640000005650384c0f0000002f014000000710d1fffe0722a2ff0100");
    private static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);

    private static byte[] image(String format) {
        var picture = new java.awt.image.BufferedImage(3, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        picture.setRGB(0, 0, 0xf97316);
        var out = new java.io.ByteArrayOutputStream();
        try {
            if (!javax.imageio.ImageIO.write(picture, format, out)) throw new IllegalStateException(format);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

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
    @Autowired CompanyContext companies;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void defaults() {
        jdbc.update("update appearance_setting set name = 'Task Manager', accent_color = '#f97316', logo = null, logo_type = null, version = 1 where id = 1");
        jdbc.update("""
                update appearance_mail_template set application_url = null, greeting_de = null, signature_de = null, footer_de = null,
                    greeting_en = null, signature_en = null, footer_en = null, version = 1 where id = 1
                """);
        jdbc.update("delete from appearance_audit_event");
    }

    // ---------------------------------------------------------------- a minimal browser

    record Response(int status, byte[] raw, Map<String, List<String>> headers) {
        String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                .flatMap(e -> e.getValue().stream()).findFirst().orElse(null);
        }

        JsonNode json() {
            return JSON.readTree(new String(raw, StandardCharsets.UTF_8));
        }

        String code() {
            return json().get("code").asString();
        }

        String body() {
            return new String(raw, StandardCharsets.UTF_8);
        }
    }

    final class Browser {
        private final HttpClient http = HttpClient.newHttpClient();
        final Map<String, String> cookies = new LinkedHashMap<>();

        Response send(String method, String path, String contentType, byte[] body, String ifMatch) {
            try {
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
                request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
                if (contentType != null) {
                    request.header("Content-Type", contentType);
                }
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
                HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
                for (String cookie : response.headers().allValues("set-cookie")) {
                    String pair = cookie.substring(0, cookie.indexOf(';') < 0 ? cookie.length() : cookie.indexOf(';'));
                    String name = pair.substring(0, pair.indexOf('='));
                    if (cookie.toLowerCase().contains("max-age=0")) {
                        cookies.remove(name);
                    } else {
                        cookies.put(name, pair.substring(pair.indexOf('=') + 1));
                    }
                }
                return new Response(response.statusCode(), response.body(), response.headers().map());
            } catch (java.io.IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        Response get(String path) {
            return send("GET", path, null, null, null);
        }

        Response json(String method, String path, Object body, String ifMatch) {
            return send(method, path, "application/json", JSON.writeValueAsBytes(body), ifMatch);
        }

        Response accent(String color, String ifMatch) {
            return json("PUT", "/api/v1/appearance", Map.of("accentColor", color), ifMatch);
        }

        Response logo(String contentType, byte[] content, String ifMatch) {
            return send("PUT", "/api/v1/appearance/logo", contentType, content, ifMatch);
        }
    }

    private Browser anonymous() {
        Browser browser = new Browser();
        assertEquals(200, browser.get("/api/v1/session").status());
        assertNotNull(browser.cookies.get("__Host-tm-csrf"));
        return browser;
    }

    /** A person with a complete login who administers the company, or with no right at all. */
    private Browser person(boolean administrator) {
        return person(administrator ? AdministrationRight.values() : new AdministrationRight[0]);
    }

    /** A person with a complete login who holds exactly these administrative rights. */
    private Browser person(AdministrationRight... rights) {
        var issued = accounts.create("test", (rights.length > 0 ? "admin-" : "user-") + COUNTER.incrementAndGet(), null);
        if (rights.length > 0) {
            AdministrationFixture.grant(jdbc, companies, transactions, issued.account().id(), rights);
        }
        Browser browser = anonymous();
        Response login = browser.json("POST", "/api/v1/session",
            Map.of("username", issued.account().username(), "password", issued.oneTimePassword()), null);
        assertEquals(200, login.status(), login.body());
        Response changed = browser.json("POST", "/api/v1/session/password",
            Map.of("currentPassword", issued.oneTimePassword(), "newPassword", STRONG), null);
        assertEquals(200, changed.status(), changed.body());
        return browser;
    }

    private static void assertProblem(Response response, int status, String code) {
        assertEquals(status, response.status(), response.body());
        assertEquals("application/problem+json", response.header("Content-Type"));
        assertEquals(code, response.code());
    }

    private int auditCount() {
        return jdbc.queryForObject("select count(*) from appearance_audit_event", Integer.class);
    }

    // ---------------------------------------------------------------- tests

    @Test
    void withoutLoginNothingIsReadableOrChangeable() {
        Browser anonymous = anonymous();
        assertProblem(anonymous.get("/api/v1/appearance"), 401, "auth.required");
        assertProblem(anonymous.get("/api/v1/appearance/logo"), 401, "auth.required");
        assertProblem(anonymous.accent("#123456", "\"1\""), 401, "auth.required");
        assertProblem(anonymous.logo("image/png", PNG, "\"1\""), 401, "auth.required");
        assertEquals("#f97316", jdbc.queryForObject("select accent_color from appearance_setting", String.class));
    }

    @Test
    void everyLoggedInPersonReadsTheDefaultButOnlyAHolderOfTheRightChangesIt() {
        Browser user = person(false);
        Response read = user.get("/api/v1/appearance");
        assertEquals(200, read.status(), read.body());
        assertEquals("#f97316", read.json().get("accentColor").asString());
        assertEquals("#f97316", read.json().get("defaultAccentColor").asString());
        assertTrue(read.json().get("logo").isNull());
        assertEquals("\"1\"", read.header("ETag"));
        assertEquals("no-store", read.header("Cache-Control"));

        assertProblem(user.accent("#123456", "\"1\""), 403, "auth.forbidden");
        assertProblem(user.logo("image/png", PNG, "\"1\""), 403, "auth.forbidden");
        assertProblem(user.send("DELETE", "/api/v1/appearance/logo", null, null, "\"1\""), 403, "auth.forbidden");
        assertEquals(0, auditCount());
        assertEquals(1L, jdbc.queryForObject("select version from appearance_setting", Long.class));
    }

    @Test
    void aHolderOfTheRightChangesTheColorWhichIsNormalizedVersionedAndAudited() {
        Browser admin = person(true);
        Response changed = admin.accent("#1D4ED8", "\"1\"");
        assertEquals(200, changed.status(), changed.body());
        assertEquals("#1d4ed8", changed.json().get("accentColor").asString());
        assertEquals(2, changed.json().get("version").asInt());
        assertEquals("\"2\"", changed.header("ETag"));

        Browser user = person(false);
        assertEquals("#1d4ed8", user.get("/api/v1/appearance").json().get("accentColor").asString());

        Map<String, Object> audit = jdbc.queryForMap("select actor, event, detail, actor_account_id from appearance_audit_event");
        assertTrue(((String) audit.get("actor")).startsWith("admin-"));
        assertEquals("appearance.accent_changed", audit.get("event"));
        assertEquals("#f97316 -> #1d4ed8", audit.get("detail"));
        assertNotNull(audit.get("actor_account_id"));
    }

    @Test
    void invalidColorsAreRefused() {
        Browser admin = person(true);
        for (String color : List.of("orange", "#fff", "#12345g", "#1234567", "rgb(1,2,3)", "")) {
            assertProblem(admin.accent(color, "\"1\""), 400, "appearance.color_invalid");
        }
        assertProblem(admin.json("PUT", "/api/v1/appearance", Map.of(), "\"1\""), 400, "appearance.change_empty");
        // A valid name does not carry an invalid color through.
        assertProblem(admin.json("PUT", "/api/v1/appearance", Map.of("accentColor", "orange", "name", "Nordlicht"), "\"1\""), 400, "appearance.color_invalid");
        assertEquals(0, auditCount());
    }

    @Test
    void changesNeedTheCurrentVersion() {
        Browser admin = person(true);
        assertProblem(admin.accent("#123456", null), 428, "appearance.precondition_required");
        assertProblem(admin.accent("#123456", "\"7\""), 412, "appearance.version_conflict");
        assertProblem(admin.accent("#123456", "1"), 412, "appearance.version_conflict");
        assertProblem(admin.accent("#123456", "\"x\""), 412, "appearance.version_conflict");
        assertEquals(200, admin.accent("#123456", "\"1\"").status());
        // A second administrator still holding version 1 does not silently overwrite the first.
        Browser other = person(true);
        assertProblem(other.accent("#654321", "\"1\""), 412, "appearance.version_conflict");
        assertEquals("#123456", jdbc.queryForObject("select accent_color from appearance_setting", String.class));
        assertEquals(1, auditCount());
    }

    @Test
    void changesNeedTheCsrfToken() {
        Browser admin = person(true);
        admin.cookies.put("__Host-tm-csrf", "forged");
        Response response = admin.accent("#123456", "\"1\"");
        assertEquals(403, response.status(), response.body());
        assertEquals("#f97316", jdbc.queryForObject("select accent_color from appearance_setting", String.class));
    }

    @Test
    void logosAreAcceptedOnlyAsRealPngJpegOrWebp() {
        Browser admin = person(true);
        Response png = admin.logo("image/png", PNG, "\"1\"");
        assertEquals(200, png.status(), png.body());
        JsonNode logo = png.json().get("logo");
        assertEquals("image/png", logo.get("contentType").asString());
        assertEquals(PNG.length, logo.get("size").asInt());
        assertEquals("/api/v1/appearance/logo?v=2", logo.get("url").asString());

        Response served = admin.get("/api/v1/appearance/logo?v=2");
        assertEquals(200, served.status());
        assertArrayEquals(PNG, served.raw());
        assertEquals("image/png", served.header("Content-Type"));
        assertEquals("nosniff", served.header("X-Content-Type-Options"));
        assertEquals("default-src 'none'; sandbox", served.header("Content-Security-Policy"));

        assertEquals(200, admin.logo("image/jpeg", JPEG, "\"2\"").status());
        assertEquals(200, admin.logo("image/webp", WEBP, "\"3\"").status());

        assertProblem(admin.logo("image/svg+xml", SVG, "\"4\""), 415, "appearance.logo_type_invalid");
        // A PNG that claims to be something else, and something else that claims to be a PNG.
        assertProblem(admin.logo("image/jpeg", PNG, "\"4\""), 415, "appearance.logo_type_invalid");
        assertProblem(admin.logo("image/png", SVG, "\"4\""), 415, "appearance.logo_type_invalid");
        assertProblem(admin.logo("image/png", new byte[0], "\"4\""), 400, "appearance.logo_empty");

        byte[] large = new byte[512 * 1024 + 1];
        System.arraycopy(PNG, 0, large, 0, PNG.length);
        assertProblem(admin.logo("image/png", large, "\"4\""), 413, "request.too_large");

        assertEquals("image/webp", jdbc.queryForObject("select logo_type from appearance_setting", String.class));
        assertEquals(List.of("appearance.logo_replaced", "appearance.logo_replaced", "appearance.logo_replaced"),
            jdbc.queryForList("select event from appearance_audit_event order by id", String.class));
    }

    @Test
    void aRecognizedStartIsNotEnoughTheWholeImageMustBeUsable() {
        Browser admin = person(true);
        assertEquals(200, admin.logo("image/webp", WEBP_ANIMATED, "\"1\"").status());
        assertEquals(200, admin.logo("image/webp", WEBP_LOSSY, "\"2\"").status());
        // Signature only, cut short, a damaged length, a huge declared size and a JPEG without its end.
        byte[] signature = Arrays.copyOf(PNG, 8);
        byte[] truncatedPng = Arrays.copyOf(PNG, PNG.length - 12);
        byte[] truncatedJpeg = Arrays.copyOf(JPEG, JPEG.length / 2);
        byte[] truncatedWebp = Arrays.copyOf(WEBP_LOSSY, WEBP_LOSSY.length - 10);
        byte[] wrongLength = WEBP.clone();
        wrongLength[4] = 0x30;
        byte[] noImageChunk = HexFormat.of().parseHex("524946460c00000057454250414c504800000000");
        noImageChunk[4] = (byte) (noImageChunk.length - 8);
        byte[] hugeWebp = WEBP_LOSSY.clone();
        hugeWebp[26] = (byte) 0xff;
        hugeWebp[27] = 0x3f;
        // Animation frames that hold only their 16 byte header (finding F1 of the TASK-043 review), once without and
        // once with the animation header, and a frame without the animation header before it.
        byte[] emptyFrame = HexFormat.of().parseHex("524946461c00000057454250414e4d461000000000000000000000000000000000000000");
        byte[] emptyAnimatedFrame = HexFormat.of().parseHex(
            "524946463c00000057454250565038580a00000002000000010000010000414e494d06000000000000000000414e4d4610000000000000"
                + "00000000000000000000000000");
        byte[] frameWithoutAnimation = WEBP_ANIMATED.clone();
        frameWithoutAnimation[30] = 'X';
        for (var broken : List.of(java.util.Map.entry("image/png", signature), java.util.Map.entry("image/png", truncatedPng),
                java.util.Map.entry("image/jpeg", truncatedJpeg), java.util.Map.entry("image/webp", truncatedWebp),
                java.util.Map.entry("image/webp", wrongLength), java.util.Map.entry("image/webp", noImageChunk),
                java.util.Map.entry("image/webp", hugeWebp), java.util.Map.entry("image/webp", emptyFrame),
                java.util.Map.entry("image/webp", emptyAnimatedFrame), java.util.Map.entry("image/webp", frameWithoutAnimation))) {
            assertProblem(admin.logo(broken.getKey(), broken.getValue(), "\"3\""), 415, "appearance.logo_unreadable");
        }
        // Nothing of it reached the logo, the version or the log.
        assertArrayEquals(WEBP_LOSSY, admin.get("/api/v1/appearance/logo?v=3").raw());
        assertEquals(3L, jdbc.queryForObject("select version from appearance_setting", Long.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from appearance_audit_event where event = 'appearance.logo_replaced'", Integer.class));
    }

    @Test
    void aHolderOfTheRightRemovesTheLogo() {
        Browser admin = person(true);
        assertProblem(admin.get("/api/v1/appearance/logo"), 404, "appearance.logo_not_found");
        assertEquals(200, admin.logo("image/png", PNG, "\"1\"").status());
        assertProblem(admin.send("DELETE", "/api/v1/appearance/logo", null, null, null), 428, "appearance.precondition_required");
        Response removed = admin.send("DELETE", "/api/v1/appearance/logo", null, null, "\"2\"");
        assertEquals(200, removed.status(), removed.body());
        assertTrue(removed.json().get("logo").isNull());
        assertProblem(admin.get("/api/v1/appearance/logo"), 404, "appearance.logo_not_found");
        assertEquals("appearance.logo_removed",
            jdbc.queryForObject("select event from appearance_audit_event order by id desc limit 1", String.class));
    }

    @Test
    void aPersonWhoMustStillChangeThePasswordCannotReadIt() {
        var issued = accounts.create("test", "pending-" + COUNTER.incrementAndGet(), null);
        AdministrationFixture.grantAdministration(jdbc, companies, transactions, issued.account().id());
        Browser browser = anonymous();
        assertEquals(200, browser.json("POST", "/api/v1/session",
            Map.of("username", issued.account().username(), "password", issued.oneTimePassword()), null).status());
        Response read = browser.get("/api/v1/appearance");
        assertEquals(403, read.status(), read.body());
        assertProblem(browser.accent("#123456", "\"1\""), 403, read.code());
    }

    // ---------------------------------------------------------------- name, reset and public read (TASK-081)

    @Test
    void aHolderOfTheRightChangesTheNameWhichIsTrimmedVersionedAndAudited() {
        Browser admin = person(AdministrationRight.APPEARANCE_MANAGE);
        Response read = admin.get("/api/v1/appearance");
        assertEquals("Task Manager", read.json().get("name").asString());
        assertEquals("Task Manager", read.json().get("defaultName").asString());

        Response named = admin.json("PUT", "/api/v1/appearance", Map.of("name", "  Nordlicht Service GmbH  "), "\"1\"");
        assertEquals(200, named.status(), named.body());
        assertEquals("Nordlicht Service GmbH", named.json().get("name").asString());
        // The color stays as it was when only the name is sent.
        assertEquals("#f97316", named.json().get("accentColor").asString());
        assertEquals("\"2\"", named.header("ETag"));

        Response both = admin.json("PUT", "/api/v1/appearance", Map.of("name", "Nordlicht", "accentColor", "#2563EB"), "\"2\"");
        assertEquals(200, both.status(), both.body());
        assertEquals("Nordlicht", both.json().get("name").asString());
        assertEquals("#2563eb", both.json().get("accentColor").asString());
        assertEquals(3, both.json().get("version").asInt());

        assertEquals(List.of("appearance.name_changed: Task Manager -> Nordlicht Service GmbH",
                "appearance.accent_changed: #f97316 -> #2563eb", "appearance.name_changed: Nordlicht Service GmbH -> Nordlicht"),
            jdbc.queryForList("select event || ': ' || detail from appearance_audit_event order by id", String.class));
        assertEquals("Nordlicht", person(false).get("/api/v1/appearance").json().get("name").asString());
    }

    @Test
    void invalidNamesAreRefused() {
        Browser admin = person(true);
        for (String name : List.of("", "   ", "x".repeat(81), "Zeile\nUmbruch", "Tab\there", "Rechts\u202ELinks", "Null\u0000")) {
            assertProblem(admin.json("PUT", "/api/v1/appearance", Map.of("name", name), "\"1\""), 400, "appearance.name_invalid");
        }
        // 80 characters count as characters, not as bytes or UTF-16 units.
        String longest = "\uD83D\uDE80".repeat(80);
        Response accepted = admin.json("PUT", "/api/v1/appearance", Map.of("name", longest), "\"1\"");
        assertEquals(200, accepted.status(), accepted.body());
        assertEquals(longest, accepted.json().get("name").asString());
        assertProblem(person(false).json("PUT", "/api/v1/appearance", Map.of("name", "Fremd"), "\"2\""), 403, "auth.forbidden");
        assertEquals(1, auditCount());
    }

    @Test
    void resetPutsNameColorAndLogoBackInOneStep() {
        Browser admin = person(true);
        assertEquals(200, admin.json("PUT", "/api/v1/appearance", Map.of("name", "Nordlicht", "accentColor", "#7c3aed"), "\"1\"").status());
        assertEquals(200, admin.logo("image/png", PNG, "\"2\"").status());

        assertProblem(person(false).send("DELETE", "/api/v1/appearance", null, null, "\"3\""), 403, "auth.forbidden");
        assertProblem(admin.send("DELETE", "/api/v1/appearance", null, null, null), 428, "appearance.precondition_required");
        assertProblem(admin.send("DELETE", "/api/v1/appearance", null, null, "\"2\""), 412, "appearance.version_conflict");

        Response reset = admin.send("DELETE", "/api/v1/appearance", null, null, "\"3\"");
        assertEquals(200, reset.status(), reset.body());
        assertEquals("Task Manager", reset.json().get("name").asString());
        assertEquals("#f97316", reset.json().get("accentColor").asString());
        assertTrue(reset.json().get("logo").isNull());
        assertEquals("\"4\"", reset.header("ETag"));
        assertProblem(admin.get("/api/v1/appearance/logo"), 404, "appearance.logo_not_found");
        assertEquals("appearance.reset", jdbc.queryForObject("select event from appearance_audit_event order by id desc limit 1", String.class));
    }

    @Test
    void theLoginPageReadsNameColorAndLogoWithoutASessionAndNothingElse() {
        Browser anonymous = anonymous();
        Response initial = anonymous.get("/api/v1/appearance/public");
        assertEquals(200, initial.status(), initial.body());
        assertEquals("no-store", initial.header("Cache-Control"));
        assertTrue(initial.json().get("logoUrl").isNull());
        assertProblem(anonymous.get("/api/v1/appearance/public/logo"), 404, "appearance.logo_not_found");

        Browser admin = person(true);
        assertEquals(200, admin.json("PUT", "/api/v1/appearance", Map.of("name", "Nordlicht", "accentColor", "#16a34a"), "\"1\"").status());
        assertEquals(200, admin.logo("image/png", PNG, "\"2\"").status());

        // A fresh visitor without any cookie at all.
        Browser stranger = new Browser();
        Response shown = stranger.get("/api/v1/appearance/public");
        assertEquals(200, shown.status(), shown.body());
        JsonNode body = shown.json();
        assertEquals(java.util.Set.of("name", "accentColor", "logoUrl"), new java.util.TreeSet<>(body.propertyNames()));
        assertEquals("Nordlicht", body.get("name").asString());
        assertEquals("#16a34a", body.get("accentColor").asString());
        assertEquals("/api/v1/appearance/public/logo", body.get("logoUrl").asString());
        assertTrue(shown.header("ETag") == null);

        Response logo = stranger.get("/api/v1/appearance/public/logo");
        assertEquals(200, logo.status());
        assertArrayEquals(PNG, logo.raw());
        assertEquals("image/png", logo.header("Content-Type"));
        assertEquals("nosniff", logo.header("X-Content-Type-Options"));
        assertEquals("default-src 'none'; sandbox", logo.header("Content-Security-Policy"));
        assertEquals(200, stranger.send("HEAD", "/api/v1/appearance/public", null, null, null).status());

        // An expired cookie does not keep the login page from its appearance.
        stranger.cookies.put("__Host-tm-session", "expired-or-forged");
        assertEquals(200, stranger.get("/api/v1/appearance/public").status());

        // Nothing else opens up: the full setting, its logo and every change still need a session and the right.
        Browser other = new Browser();
        assertProblem(other.get("/api/v1/appearance"), 401, "auth.required");
        assertProblem(other.get("/api/v1/appearance/logo"), 401, "auth.required");
        assertProblem(anonymous.json("PUT", "/api/v1/appearance/public", Map.of("name", "X"), "\"3\""), 401, "auth.required");
        assertProblem(anonymous.send("DELETE", "/api/v1/appearance/public/logo", null, null, "\"3\""), 401, "auth.required");
        assertProblem(anonymous.send("DELETE", "/api/v1/appearance", null, null, "\"3\""), 401, "auth.required");
        assertEquals("Nordlicht", jdbc.queryForObject("select name from appearance_setting", String.class));
    }

    @Test
    void aPersonWhoMustStillChangeThePasswordSeesTheAppearanceOfTheLoginPage() {
        assertEquals(200, person(true).json("PUT", "/api/v1/appearance", Map.of("name", "Nordlicht"), "\"1\"").status());
        var issued = accounts.create("test", "pending-" + COUNTER.incrementAndGet(), null);
        Browser browser = anonymous();
        assertEquals(200, browser.json("POST", "/api/v1/session",
            Map.of("username", issued.account().username(), "password", issued.oneTimePassword()), null).status());
        Response shown = browser.get("/api/v1/appearance/public");
        assertEquals(200, shown.status(), shown.body());
        assertEquals("Nordlicht", shown.json().get("name").asString());
        assertProblem(browser.get("/api/v1/appearance"), 403, "auth.password_change_required");
    }

    // ---------------------------------------------------------------- mail template (TASK-082)

    private static Map<String, Object> texts(String greeting, String signature, String footer) {
        return Map.of("greeting", greeting, "signature", signature, "footer", footer);
    }

    private static Map<String, Object> template(String address, Map<String, Object> de, Map<String, Object> en) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applicationUrl", address);
        body.put("de", de);
        body.put("en", en);
        return body;
    }

    @Test
    void theBuiltInTemplateCountsUntilOneIsSavedAndResetReturnsToIt() {
        Browser admin = person(AdministrationRight.APPEARANCE_MANAGE);
        Response read = admin.get("/api/v1/appearance/mail-template");
        assertEquals(200, read.status(), read.body());
        assertEquals("\"1\"", read.header("ETag"));
        JsonNode de = read.json().get("de");
        assertEquals(false, de.get("stored").asBoolean());
        assertEquals("Guten Tag {{contact.name}},", de.get("greeting").asString());
        assertEquals(de.get("greeting").asString(), de.get("builtIn").get("greeting").asString());
        assertEquals("Hello {{contact.name}},", read.json().get("en").get("greeting").asString());
        assertEquals(List.of("company.name", "contact.name", "ticket.number", "ticket.title", "sender.name", "mailbox.address", "portal.url"),
            JSON.convertValue(read.json().get("placeholders"), List.class));
        assertTrue(read.json().get("applicationUrl").isNull());

        Response saved = admin.json("PUT", "/api/v1/appearance/mail-template", template(" https://support.example.com/portal ",
            texts("Hallo {{contact.name}},", "Gruß\r\n{{sender.name}}", ""), texts("Hi {{contact.name}},", "Cheers", "{{portal.url}}")), "\"1\"");
        assertEquals(200, saved.status(), saved.body());
        assertEquals("\"2\"", saved.header("ETag"));
        assertEquals("https://support.example.com/portal", saved.json().get("applicationUrl").asString());
        assertEquals(true, saved.json().get("de").get("stored").asBoolean());
        assertEquals("Gruß\n{{sender.name}}", saved.json().get("de").get("signature").asString(), "line breaks are kept as one kind");
        assertEquals("", saved.json().get("de").get("footer").asString());
        assertEquals(List.of("mail_template.changed", "mail_template.changed", "mail_template.address_changed"),
            jdbc.queryForList("select event from appearance_audit_event order by id", String.class));

        // A second save with the same texts records nothing.
        Response same = admin.json("PUT", "/api/v1/appearance/mail-template", template("https://support.example.com/portal",
            texts("Hallo {{contact.name}},", "Gruß\n{{sender.name}}", ""), texts("Hi {{contact.name}},", "Cheers", "{{portal.url}}")), "\"2\"");
        assertEquals(200, same.status(), same.body());
        assertEquals(3, auditCount());

        assertProblem(admin.json("PUT", "/api/v1/appearance/mail-template", template(null, texts("", "", ""), texts("", "", "")), "\"2\""),
            412, "mail_template.version_conflict");
        assertProblem(admin.send("DELETE", "/api/v1/appearance/mail-template", null, null, null), 428, "mail_template.precondition_required");
        Response reset = admin.send("DELETE", "/api/v1/appearance/mail-template", null, null, "\"3\"");
        assertEquals(200, reset.status(), reset.body());
        assertEquals(false, reset.json().get("de").get("stored").asBoolean());
        assertEquals("Guten Tag {{contact.name}},", reset.json().get("de").get("greeting").asString());
        assertEquals("https://support.example.com/portal", reset.json().get("applicationUrl").asString(), "the address stays");
        assertEquals("mail_template.reset", jdbc.queryForObject("select event from appearance_audit_event order by id desc limit 1", String.class));
    }

    @Test
    void unknownPlaceholdersInvalidTextsAndAddressesAreRefusedWithThePartNamed() {
        Browser admin = person(AdministrationRight.APPEARANCE_MANAGE);
        Map<String, Object> fine = texts("Hallo", "", "");
        Response unknown = admin.json("PUT", "/api/v1/appearance/mail-template",
            template(null, fine, texts("Hi", "{{sender.name}} {{sender.nme}}", "{{ticket.number")), "\"1\"");
        assertProblem(unknown, 400, "mail_template.placeholder_unknown");
        assertEquals(List.of("en.signature: {{sender.nme}}", "en.footer: {{ticket.number"), JSON.convertValue(unknown.json().get("violations"), List.class));
        Response spaced = admin.json("PUT", "/api/v1/appearance/mail-template", template(null, texts("{{ contact.name }}", "", "x }}"), fine), "\"1\"");
        assertProblem(spaced, 400, "mail_template.placeholder_unknown");
        assertEquals(List.of("de.greeting: {{ contact.name }}", "de.footer: x }}"), JSON.convertValue(spaced.json().get("violations"), List.class));
        Response control = admin.json("PUT", "/api/v1/appearance/mail-template", template(null, texts("a\u0007b", "", ""), fine), "\"1\"");
        assertProblem(control, 400, "mail_template.text_invalid");
        assertEquals(List.of("de.greeting"), JSON.convertValue(control.json().get("violations"), List.class));
        assertProblem(admin.json("PUT", "/api/v1/appearance/mail-template", template(null, texts("x".repeat(2001), "", ""), fine), "\"1\""),
            400, "mail_template.text_invalid");
        assertEquals(200, admin.json("PUT", "/api/v1/appearance/mail-template", template(null, texts("ä".repeat(2000), "", ""), fine), "\"1\"").status());
        for (String address : List.of("http://support.example.com", "https://", "https://user:pw@support.example.com", "https://a b.example",
                "javascript:alert(1)", "https://support.example.com/\u00e4")) {
            assertProblem(admin.json("PUT", "/api/v1/appearance/mail-template", template(address, fine, fine), "\"2\""), 400,
                "mail_template.address_invalid");
        }
        Map<String, Object> missing = new LinkedHashMap<>();
        missing.put("de", fine);
        assertProblem(admin.json("PUT", "/api/v1/appearance/mail-template", missing, "\"2\""), 400, "mail_template.languages_missing");
        assertEquals(2, jdbc.queryForObject("select version from appearance_mail_template", Long.class), "nothing refused was stored");
    }

    @Test
    void thePreviewFillsTheUnsavedTextsWithSyntheticValuesAndEscapesThem() {
        Browser admin = person(AdministrationRight.APPEARANCE_MANAGE);
        assertEquals(200, admin.json("PUT", "/api/v1/appearance", Map.of("name", "Nordlicht"), "\"1\"").status());
        Response preview = admin.json("POST", "/api/v1/appearance/mail-template/preview", Map.of("language", "en", "name", "A & <B>",
            "accentColor", "#00AA11", "applicationUrl", "https://support.example.com", "texts",
            texts("Hi {{contact.name}} <x>", "{{sender.name}}\n{{company.name}}", "{{ticket.number}} {{ticket.title}}\n{{mailbox.address}} {{portal.url}}")), null);
        assertEquals(200, preview.status(), preview.body());
        assertEquals("en", preview.json().get("language").asString());
        String text = preview.json().get("text").asString();
        assertTrue(text.startsWith("Hi Alex Example <x>\n\nThank you for your message."), text);
        assertTrue(text.contains("Sam Support\nA & <B>\n\n-- \nHELP-42 Printer in hall 2 does not start\nsupport@example.com https://support.example.com\n"), text);
        String html = preview.json().get("html").asString();
        assertTrue(html.contains("Hi Alex Example &lt;x&gt;") && html.contains("A &amp; &lt;B&gt;") && html.contains("#00aa11"), html);
        assertTrue(!html.contains("<x>") && !html.contains("cid:logo"), html);
        assertEquals("A & <B>", preview.json().get("parts").get("name").asString());
        // Without an address the line with the link is left out; German uses German sample values; the saved name counts.
        Response german = admin.json("POST", "/api/v1/appearance/mail-template/preview", Map.of("language", "de", "texts",
            texts("Guten Tag {{contact.name}},", "{{company.name}}", "Ticket {{ticket.number}}\nPortal: {{portal.url}}")), null);
        assertEquals(200, german.status(), german.body());
        assertTrue(german.json().get("text").asString().startsWith("Guten Tag Erika Beispiel,\n\nVielen Dank"), german.body());
        assertTrue(german.json().get("text").asString().endsWith("Nordlicht\n\n-- \nTicket HELP-42\n"), german.body());
        assertProblem(admin.json("POST", "/api/v1/appearance/mail-template/preview", Map.of("language", "de", "texts", texts("{{x}}", "", "")), null),
            400, "mail_template.placeholder_unknown");
        assertEquals(1, jdbc.queryForObject("select version from appearance_mail_template", Long.class), "a preview stores nothing");
    }

    @Test
    void theMailTemplateNeedsTheRightAppearanceManage() {
        Browser other = person(AdministrationRight.ACCOUNTS_MANAGE);
        assertProblem(other.get("/api/v1/appearance/mail-template"), 403, "auth.forbidden");
        assertProblem(other.json("PUT", "/api/v1/appearance/mail-template", template(null, texts("", "", ""), texts("", "", "")), "\"1\""),
            403, "auth.forbidden");
        assertProblem(other.send("DELETE", "/api/v1/appearance/mail-template", null, null, "\"1\""), 403, "auth.forbidden");
        assertProblem(other.json("POST", "/api/v1/appearance/mail-template/preview", Map.of("language", "de", "texts", texts("", "", "")), null),
            403, "auth.forbidden");
        assertProblem(anonymous().get("/api/v1/appearance/mail-template"), 401, "auth.required");
        assertEquals(1, jdbc.queryForObject("select version from appearance_mail_template", Long.class));
    }
}
