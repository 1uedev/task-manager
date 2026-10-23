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
        jdbc.update("update appearance_setting set accent_color = '#f97316', logo = null, logo_type = null, version = 1 where id = 1");
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
        assertProblem(admin.json("PUT", "/api/v1/appearance", Map.of(), "\"1\""), 400, "appearance.color_invalid");
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
}
