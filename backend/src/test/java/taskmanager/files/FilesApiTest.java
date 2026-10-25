package taskmanager.files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
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
import taskmanager.files.internal.AttachmentCleanup;
import taskmanager.identity.account.AccountService;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.CompanyContext;
import taskmanager.projects.ProjectConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-051: attachments of tickets through the real HTTP interface, the real permissions, projects and tickets modules, a
 * file store on a real directory and a real PostgreSQL. Business actions and field rules are granted directly in the
 * tables, as the group administration is not what is tested here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FilesApiTest {
    private static final String DATABASE = freshDatabase();
    private static final Path STORE = newStore();
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final List<String> ALL_FIELDS = ProjectConfiguration.FIELD_KEYS;
    private static final String[] TICKETS = {"ticket.read", "ticket.create", "ticket.edit", "READ:*", "WRITE:*", "READ:attachments", "WRITE:attachments"};
    private static final long LIMIT = 100_000;

    private static String freshDatabase() {
        try {
            return TestDatabase.createEmptyDatabase();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path newStore() {
        try {
            return Files.createTempDirectory("taskmanager-files-api");
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
        registry.add("taskmanager.files.max-bytes", () -> LIMIT);
    }

    @AfterAll
    static void stopThreads() {
        POOL.shutdownNow();
    }

    @Value("${local.server.port}") int port;
    @Autowired AccountService accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyContext companies;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AttachmentCleanup cleanup;

    // ---------------------------------------------------------------- a minimal browser that also sends and receives bytes

    record Response(int status, byte[] bytes, Map<String, List<String>> headers) {
        String body() {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                .flatMap(e -> e.getValue().stream()).findFirst().orElse(null);
        }

        JsonNode json() {
            return JSON.readTree(bytes);
        }

        String code() {
            return json().get("code").asString();
        }

        List<String> violations() {
            return StreamSupport.stream(json().get("violations").spliterator(), false).map(JsonNode::asString).toList();
        }
    }

    final class Browser {
        private final HttpClient http = HttpClient.newHttpClient();
        final Map<String, String> cookies = new LinkedHashMap<>();

        Response send(String method, String path, byte[] body, String contentType, String ifMatch) {
            try {
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
                if (contentType != null) {
                    request.header("Content-Type", contentType);
                }
                request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
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
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        Response json(String method, String path, Object body, String ifMatch) {
            return send(method, path, body == null ? null : JSON.writeValueAsBytes(body), body == null ? null : "application/json", ifMatch);
        }

        Response get(String path) {
            return send("GET", path, null, null, null);
        }
    }

    /** A person with a complete login. */
    private record Person(Browser browser, UUID id) {
        Response get(String path) {
            return browser.get(path);
        }
    }

    private Browser anonymous() {
        Browser browser = new Browser();
        assertEquals(200, browser.get("/api/v1/session").status());
        return browser;
    }

    private Person person(AdministrationRight... rights) {
        var issued = accounts.create("test", "files-" + COUNTER.incrementAndGet(), null);
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
        return new Person(browser, issued.account().id());
    }

    /**
     * Grants rules in a group of their own, for one project or, with {@code null}, for every project: an action key such as
     * {@code ticket.read}, or a field rule {@code READ:title}, {@code WRITE:attachments}, {@code DENY-READ:attachments} for
     * the resource type {@code ticket}; {@code *} stands for the five fields of the ticket.
     */
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
                boolean deny = parts[0].startsWith("DENY-");
                String operation = deny ? parts[0].substring(5) : parts[0];
                for (String field : parts[1].equals("*") ? ALL_FIELDS : List.of(parts[1])) {
                    jdbc.update("""
                            insert into permissions_field(company_id, group_id, project_id, resource_type, field_key, operation, effect)
                            values (?, ?, ?, 'ticket', ?, ?, ?)
                            """, companyId, group, project, field, operation, deny ? "DENY" : "ALLOW");
                }
            }
            jdbc.update("insert into permissions_membership(company_id, group_id, user_id) values (?, ?, ?)", companyId, group, account);
            jdbc.update("update permissions_revision set revision = revision + 1 where company_id = ?", companyId);
        });
    }

    private record Project(UUID id, String key, Person manager) {
    }

    private Project project(Person... members) {
        Person manager = person(AdministrationRight.PROJECTS_MANAGE);
        String key = "F" + COUNTER.incrementAndGet();
        Response created = manager.browser().json("POST", "/api/v1/projects", Map.of("key", key, "name", "Dateien " + key), null);
        assertEquals(201, created.status(), created.body());
        UUID id = UUID.fromString(created.json().get("id").asString());
        String version = created.header("ETag");
        for (Person member : members) {
            Response added = manager.browser().json("PUT", "/api/v1/projects/" + id + "/members/" + member.id(), null, version);
            assertEquals(200, added.status(), added.body());
            version = added.header("ETag");
        }
        return new Project(id, key, manager);
    }

    private static long ticket(Person person, Project project, String title) {
        Response created = person.browser().json("POST", "/api/v1/projects/" + project.id() + "/tickets", Map.of("type", "task", "title", title), null);
        assertEquals(201, created.status(), created.body());
        return created.json().get("number").asLong();
    }

    private static String attachments(Project project, long number) {
        return "/api/v1/projects/" + project.id() + "/tickets/" + number + "/attachments";
    }

    private static String history(Project project, long number) {
        return "/api/v1/projects/" + project.id() + "/tickets/" + number + "/history";
    }

    private static Response upload(Person person, Project project, long number, String name, byte[] content) {
        String query = name == null ? "" : "?name=" + java.net.URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return person.browser().send("POST", attachments(project, number) + query, content, "application/octet-stream", null);
    }

    private static UUID uploaded(Person person, Project project, long number, String name, byte[] content) {
        Response response = upload(person, project, number, name, content);
        assertEquals(201, response.status(), response.body());
        return UUID.fromString(response.json().get("id").asString());
    }

    private static Response delete(Person person, String path) {
        return person.browser().send("DELETE", path, null, null, null);
    }

    private static void assertProblem(Response response, int status, String code) {
        assertEquals(status, response.status(), response.body());
        assertEquals(code, response.code(), response.body());
    }

    private static byte[] bytes(int length, int seed) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i * 31 + seed);
        }
        data[0] = 'D';
        return data;
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static List<String> names(Response response) {
        assertEquals(200, response.status(), response.body());
        return StreamSupport.stream(response.json().spliterator(), false).map(item -> item.get("name").asString()).toList();
    }

    private static List<Path> storedFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(STORE.resolve("content"))) {
            return walk.filter(Files::isRegularFile).toList();
        }
    }

    private static long stagingFiles() throws IOException {
        try (Stream<Path> files = Files.list(STORE.resolve("staging"))) {
            return files.count();
        }
    }

    private long rows(Project project) {
        return jdbc.queryForObject("select count(*) from file_attachment where project_id = ?", Long.class, project.id());
    }

    private List<String> audit(UUID attachment) {
        return jdbc.queryForList("select operation from file_audit_event where attachment_id = ? order by id", String.class, attachment);
    }

    // ---------------------------------------------------------------- the round trip

    @Test
    void aMemberUploadsAFileAndDownloadsExactlyTheSameBytes() throws Exception {
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        long number = ticket(worker, project, "Mit Anhang");
        byte[] content = bytes(70_000, 3);

        Response created = upload(worker, project, number, "../../Übersicht Q3.pdf", content);
        assertEquals(201, created.status(), created.body());
        JsonNode body = created.json();
        assertEquals("Übersicht Q3.pdf", body.get("name").asString(), "the path is cleaned off");
        assertEquals(content.length, body.get("size").asLong());
        assertEquals(sha256(content), body.get("sha256").asString());
        assertEquals(worker.id().toString(), body.get("uploadedBy").asString());
        UUID id = UUID.fromString(body.get("id").asString());
        assertEquals(attachments(project, number) + "/" + id, created.header("Location"));

        assertEquals(List.of("Übersicht Q3.pdf"), names(worker.get(attachments(project, number))));
        Response download = worker.get(attachments(project, number) + "/" + id);
        assertEquals(200, download.status());
        assertArrayEquals(content, download.bytes());
        assertEquals("application/octet-stream", download.header("Content-Type"));
        assertEquals(String.valueOf(content.length), download.header("Content-Length"));
        assertTrue(download.header("Content-Disposition").startsWith("attachment;"), download.header("Content-Disposition"));
        assertTrue(download.header("Content-Disposition").contains("filename*=UTF-8''"), download.header("Content-Disposition"));
        assertEquals("nosniff", download.header("X-Content-Type-Options"));
        assertEquals("default-src 'none'; sandbox", download.header("Content-Security-Policy"));
        assertTrue(download.header("Cache-Control").contains("no-store"), download.header("Cache-Control"));
        assertEquals("\"1\"", worker.get("/api/v1/projects/" + project.id() + "/tickets/" + number).header("ETag"),
            "an attachment does not change the version of the ticket");

        Path stored = STORE.resolve("content").resolve(id.toString().substring(0, 2)).resolve(id.toString());
        assertArrayEquals(content, Files.readAllBytes(stored), "the content lies in the file store under the identifier of the attachment");
        assertEquals(List.of("attachment.added"), audit(id));
    }

    @Test
    void theLargestAcceptedFileGoesThroughAndABinarySafeBodyIsNotChanged() throws Exception {
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        long number = ticket(worker, project, "Grenze");
        byte[] content = new byte[(int) LIMIT];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 251);
        }
        content[0] = 'B';
        UUID id = uploaded(worker, project, number, "genau.bin", content);
        assertArrayEquals(content, worker.get(attachments(project, number) + "/" + id).bytes());
    }

    // ---------------------------------------------------------------- refusals leave nothing behind

    @Test
    void refusedUploadsLeaveNoRowAndNoFile() throws Exception {
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        long number = ticket(worker, project, "Abgelehnt");
        long filesBefore = storedFiles().size();
        long stagingBefore = stagingFiles();

        assertProblem(upload(worker, project, number, "leer.txt", new byte[0]), 400, "attachment.empty");
        assertProblem(upload(worker, project, number, "gross.bin", bytes((int) LIMIT + 1, 5)), 413, "attachment.too_large");
        assertProblem(upload(worker, project, number, "programm.EXE", bytes(100, 1)), 415, "attachment.type_blocked");
        assertProblem(upload(worker, project, number, "Rechnung‮txt.exe", bytes(100, 1)), 415, "attachment.type_blocked");
        assertProblem(upload(worker, project, number, "seite.html", bytes(100, 1)), 415, "attachment.type_blocked");
        byte[] program = bytes(100, 1);
        program[0] = 'M';
        program[1] = 'Z';
        assertProblem(upload(worker, project, number, "harmlos.txt", program), 415, "attachment.type_blocked");
        byte[] elf = {0x7F, 'E', 'L', 'F', 1, 2, 3};
        assertProblem(upload(worker, project, number, "bild.png", elf), 415, "attachment.type_blocked");
        Response noName = upload(worker, project, number, null, bytes(10, 1));
        assertProblem(noName, 400, "request.invalid");
        assertEquals(List.of("name.invalid"), noName.violations());
        assertProblem(upload(worker, project, number, "///", bytes(10, 1)), 400, "request.invalid");

        assertEquals(0, rows(project));
        assertEquals(filesBefore, storedFiles().size());
        assertEquals(stagingBefore, stagingFiles());
        assertEquals(List.of(), names(worker.get(attachments(project, number))));
        assertEquals(0, jdbc.queryForObject("select count(*) from ticket_change where field = 'attachments' and ticket_id = "
            + "(select id from ticket where project_id = ? and number = ?)", Long.class, project.id(), number));
    }

    // ---------------------------------------------------------------- rights

    @Test
    void theFieldRightsDecideWhoReadsAndWhoChangesAttachments() throws Exception {
        Person worker = person();
        Person viewer = person();
        Person blind = person();
        Person outsider = person();
        Project project = project(worker, viewer, blind);
        grant(worker.id(), project.id(), TICKETS);
        grant(viewer.id(), project.id(), "ticket.read", "READ:*", "READ:attachments");
        grant(blind.id(), project.id(), "ticket.read", "ticket.edit", "READ:*", "WRITE:*");
        grant(outsider.id(), null, TICKETS);
        long number = ticket(worker, project, "Rechte");
        byte[] content = bytes(1_000, 9);
        UUID id = uploaded(worker, project, number, "geheim.txt", content);
        String path = attachments(project, number);

        assertArrayEquals(content, viewer.get(path + "/" + id).bytes(), "reading needs only the read rule");
        assertEquals(List.of("geheim.txt"), names(viewer.get(path)));
        assertProblem(upload(viewer, project, number, "neu.txt", content), 403, "auth.forbidden");
        assertProblem(delete(viewer, path + "/" + id), 403, "auth.forbidden");

        assertProblem(blind.get(path), 403, "auth.forbidden");
        assertProblem(blind.get(path + "/" + id), 403, "auth.forbidden");
        assertProblem(upload(blind, project, number, "neu.txt", content), 403, "auth.forbidden");
        assertProblem(delete(blind, path + "/" + id), 403, "auth.forbidden");

        assertProblem(outsider.get(path), 404, "project.not_found");
        assertProblem(outsider.get(path + "/" + id), 404, "project.not_found");
        assertProblem(upload(outsider, project, number, "neu.txt", content), 404, "project.not_found");
        assertProblem(delete(outsider, path + "/" + id), 404, "project.not_found");
        assertProblem(anonymous().get(path), 401, "auth.required");
        assertProblem(anonymous().send("POST", path + "?name=x.txt", content, "application/octet-stream", null), 401, "auth.required");

        assertEquals(1, rows(project), "every refusal above left the attachments as they were");
        assertEquals(List.of("attachment.added"), audit(id));
        assertProblem(worker.get(attachments(project, 99)), 404, "ticket.not_found");
        assertProblem(upload(worker, project, 99, "neu.txt", content), 404, "ticket.not_found");
    }

    @Test
    void aDirectLinkNeverReachesAnotherTicketAndEndsWhenTheRightIsWithdrawn() throws Exception {
        Person worker = person();
        Person reader = person();
        Project project = project(worker, reader);
        Project other = project(worker, reader);
        grant(worker.id(), project.id(), TICKETS);
        grant(worker.id(), other.id(), TICKETS);
        grant(reader.id(), project.id(), "ticket.read", "READ:*", "READ:attachments");
        long first = ticket(worker, project, "Eins");
        long second = ticket(worker, project, "Zwei");
        long foreign = ticket(worker, other, "Fremd");
        UUID id = uploaded(worker, project, first, "eins.txt", bytes(100, 2));

        assertProblem(worker.get(attachments(project, second) + "/" + id), 404, "attachment.not_found");
        assertProblem(delete(worker, attachments(project, second) + "/" + id), 404, "attachment.not_found");
        assertProblem(worker.get(attachments(other, foreign) + "/" + id), 404, "attachment.not_found");
        assertProblem(worker.get(attachments(project, first) + "/" + UUID.randomUUID()), 404, "attachment.not_found");
        assertEquals(200, reader.get(attachments(project, first) + "/" + id).status());

        jdbc.update("delete from permissions_membership where user_id = ?", reader.id());
        jdbc.update("update permissions_revision set revision = revision + 1");
        Response gone = reader.get(attachments(project, first) + "/" + id);
        assertTrue(gone.status() == 403 || gone.status() == 404 || gone.status() == 401, "after the withdrawal: " + gone.status() + " " + gone.body());
        assertFalse(gone.status() == 200);
    }

    // ---------------------------------------------------------------- removal and history

    @Test
    void removalHidesTheAttachmentAndIsRecordedInTheHistoryForItsReadersOnly() throws Exception {
        Person worker = person();
        Person viewer = person();
        Person blind = person();
        Project project = project(worker, viewer, blind);
        grant(worker.id(), project.id(), TICKETS);
        grant(viewer.id(), project.id(), "ticket.read", "READ:*", "READ:attachments");
        grant(blind.id(), project.id(), "ticket.read", "READ:*");
        long number = ticket(worker, project, "Verlauf");
        String path = attachments(project, number);
        byte[] content = bytes(500, 4);
        UUID kept = uploaded(worker, project, number, "bleibt.txt", content);
        UUID gone = uploaded(worker, project, number, "geht.txt", content);

        assertEquals(204, delete(worker, path + "/" + gone).status());
        assertEquals(204, delete(worker, path + "/" + gone).status(), "removing again changes nothing");
        assertEquals(List.of("bleibt.txt"), names(viewer.get(path)));
        assertProblem(viewer.get(path + "/" + gone), 404, "attachment.not_found");
        assertProblem(delete(worker, path + "/" + UUID.randomUUID()), 404, "attachment.not_found");
        assertEquals(List.of("attachment.added", "attachment.removed"), audit(gone));
        assertEquals(200, worker.get(path + "/" + kept).status());

        Response seen = viewer.get(history(project, number));
        assertEquals(200, seen.status(), seen.body());
        List<String> entries = new ArrayList<>();
        for (JsonNode entry : seen.json()) {
            JsonNode change = entry.get("changes").get(0);
            if (!change.get("field").asString().equals("attachments")) {
                entries.add("fields");
                continue;
            }
            assertEquals(1, entry.get("changes").size());
            assertEquals(worker.id().toString(), entry.get("actor").asString());
            assertEquals(1, entry.get("version").asLong(), "an attachment does not raise the version");
            JsonNode from = change.get("from");
            JsonNode to = change.get("to");
            JsonNode value = JSON.readTree((from.isNull() ? to : from).asString());
            assertEquals(content.length, value.get("size").asLong());
            assertEquals(sha256(content), value.get("sha256").asString());
            entries.add((from.isNull() ? "+" : "-") + value.get("name").asString());
        }
        assertEquals(List.of("fields", "+bleibt.txt", "+geht.txt", "-geht.txt"), entries);

        Response hidden = blind.get(history(project, number));
        assertEquals(200, hidden.status(), hidden.body());
        assertEquals(1, hidden.json().size(), hidden.body());
        assertFalse(hidden.body().contains("attachments"), hidden.body());
    }

    // ---------------------------------------------------------------- clean up

    @Test
    void theCleanUpRemovesAbandonedContentAndTheContentOfRemovedAttachmentsAfterTheirPeriods() throws Exception {
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        long number = ticket(worker, project, "Aufräumen");
        UUID live = uploaded(worker, project, number, "lebt.txt", bytes(100, 1));
        UUID removed = uploaded(worker, project, number, "entfernt.txt", bytes(100, 2));
        UUID recent = uploaded(worker, project, number, "frisch entfernt.txt", bytes(100, 3));
        assertEquals(204, delete(worker, attachments(project, number) + "/" + removed).status());
        assertEquals(204, delete(worker, attachments(project, number) + "/" + recent).status());
        Instant eightDaysAgo = Instant.now().minusSeconds(8 * 86_400);
        jdbc.update("update file_attachment set removed_at = ? where id = ?", java.sql.Timestamp.from(eightDaysAgo), removed);

        // content without a row: one that is old (a crash between the file and the row), one that is young (an upload being committed)
        UUID orphan = UUID.randomUUID();
        UUID young = UUID.randomUUID();
        Path orphanFile = place(orphan, 3 * 86_400);
        Path youngFile = place(young, 60);
        Path oldPart = STORE.resolve("staging").resolve("upload-old.part");
        Path freshPart = STORE.resolve("staging").resolve("upload-fresh.part");
        Files.writeString(oldPart, "x");
        Files.writeString(freshPart, "x");
        Files.setLastModifiedTime(oldPart, FileTime.from(Instant.now().minusSeconds(3 * 86_400)));

        AttachmentCleanup.Result result = cleanup.run();

        assertTrue(result.orphans() >= 1 && result.purged() >= 1 && result.staging() >= 1, result.toString());
        Path quarantined = STORE.resolve("quarantine").resolve(orphan.toString());
        assertFalse(Files.exists(orphanFile), "old content without a row leaves the store");
        assertTrue(Files.exists(quarantined), "but it is only quarantined, not deleted at once");
        assertTrue(Files.exists(youngFile), "young content without a row stays, it may be being committed");
        assertFalse(Files.exists(oldPart));
        assertTrue(Files.exists(freshPart));
        assertTrue(Files.exists(path(live)), "an attachment that is visible keeps its content");
        assertTrue(Files.exists(path(recent)), "a removed attachment keeps its content during the retention period");
        assertFalse(Files.exists(path(removed)), "after the retention period the content goes");
        assertNotNull(jdbc.queryForObject("select purged_at from file_attachment where id = ?", java.sql.Timestamp.class, removed));
        assertEquals(1, jdbc.queryForObject("select count(*) from file_attachment where id = ?", Long.class, removed), "the row stays for the history");
        assertEquals(List.of("attachment.added", "attachment.removed", "attachment.purged"), audit(removed));

        AttachmentCleanup.Result again = cleanup.run();
        assertEquals(0, again.purged(), "a second run finds nothing more to purge for these");
        assertTrue(Files.exists(quarantined), "the quarantine lasts for the retention period");
        Files.setLastModifiedTime(quarantined, FileTime.from(Instant.now().minusSeconds(8 * 86_400)));
        assertTrue(cleanup.run().expired() >= 1);
        assertFalse(Files.exists(quarantined), "after the retention period the quarantined content is deleted");
        Files.deleteIfExists(youngFile);
        Files.deleteIfExists(freshPart);
    }

    private static Path path(UUID id) {
        return STORE.resolve("content").resolve(id.toString().substring(0, 2)).resolve(id.toString());
    }

    private static Path place(UUID id, long ageSeconds) throws IOException {
        Path file = path(id);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "orphan");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(ageSeconds)));
        return file;
    }

    // ---------------------------------------------------------------- concurrency

    @Test
    void parallelUploadsToOneTicketAllSucceedAndEachIsRecordedOnce() throws Exception {
        Person worker = person();
        Project project = project(worker);
        grant(worker.id(), project.id(), TICKETS);
        long number = ticket(worker, project, "Parallel");
        List<Future<Response>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int n = i;
            futures.add(POOL.submit(() -> upload(worker, project, number, "p" + n + ".txt", bytes(2_000 + n, n))));
        }
        for (Future<Response> future : futures) {
            Response response = future.get();
            assertEquals(201, response.status(), response.body());
        }
        assertEquals(8, names(worker.get(attachments(project, number))).size());
        assertEquals(8, jdbc.queryForObject("select count(*) from ticket_change where field = 'attachments' and ticket_id = "
            + "(select id from ticket where project_id = ? and number = ?)", Long.class, project.id(), number));
    }
}
