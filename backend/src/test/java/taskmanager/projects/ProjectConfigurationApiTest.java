package taskmanager.projects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import taskmanager.AdministrationFixture;
import taskmanager.TestDatabase;
import taskmanager.identity.account.AccountService;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.CompanyContext;
import taskmanager.search.internal.SearchProjection;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TASK-044: the configuration of a project with ticket types, workflows, required fields and custom fields, through the real
 * HTTP interface, the real permissions, tickets and search modules and a real PostgreSQL. The examples are the two projects of
 * docs/DOMAIN_MODEL.md; F-03 to F-06 of its test cases are the regressions at the end of each scenario.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProjectConfigurationApiTest {
    private static final String DATABASE = freshDatabase();
    private static final String STRONG = "Correct-Horse-Battery-9";
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> FIVE = ProjectConfiguration.FIELD_KEYS;

    /** Example A: customer service with a workflow of its own for questions and the fields of the examples. */
    private static final String SERVICE = """
        {"ticketTypes":[{"key":"stoerung","name":"Störung"},{"key":"frage","name":"Frage"}],
         "statuses":[{"key":"neu","name":"Neu","category":"open"},{"key":"in_bearbeitung","name":"In Bearbeitung","category":"in_progress"},
           {"key":"wartet","name":"Wartet auf Kunde","category":"in_progress"},{"key":"geloest","name":"Gelöst","category":"done"},
           {"key":"geschlossen","name":"Geschlossen","category":"done"}],
         "initialStatus":"neu",
         "transitions":[{"from":"neu","to":"in_bearbeitung","requiredFields":["assignee","custom.prioritaet"]},
           {"from":"in_bearbeitung","to":"wartet"},{"from":"wartet","to":"in_bearbeitung"},
           {"from":"in_bearbeitung","to":"geloest","requiredFields":["custom.interne_ursache"]},
           {"from":"geloest","to":"in_bearbeitung"},{"from":"geloest","to":"geschlossen"}],
         "workflows":[{"type":"frage","initialStatus":"neu","transitions":[{"from":"neu","to":"geloest"}]}],
         "fields":[{"key":"title","required":true},{"key":"description","required":false},{"key":"type","required":true},
           {"key":"status","required":true},{"key":"assignee","required":false}],
         "customFields":[
           {"key":"prioritaet","name":"Priorität","type":"select","options":[{"key":"niedrig","name":"Niedrig"},{"key":"hoch","name":"Hoch"}]},
           {"key":"interne_ursache","name":"Interne Ursache","type":"text"},
           {"key":"notiz","name":"Notiz","type":"longtext"},
           {"key":"restdauer","name":"Restdauer in Stunden","type":"integer"},
           {"key":"termin","name":"Termin","type":"date"},
           {"key":"bestaetigt","name":"Vom Kunden bestätigt","type":"boolean"},
           {"key":"ansprechpartner","name":"Ansprechpartner","type":"user"}]}
        """;

    /** Example B: software development, where the step to review needs a test instruction. */
    private static final String DEVELOPMENT = """
        {"ticketTypes":[{"key":"fehler","name":"Fehler"},{"key":"aufgabe","name":"Aufgabe"},{"key":"verbesserung","name":"Verbesserung"}],
         "statuses":[{"key":"eingang","name":"Eingang","category":"open"},{"key":"geplant","name":"Geplant","category":"open"},
           {"key":"in_entwicklung","name":"In Entwicklung","category":"in_progress"},{"key":"in_pruefung","name":"In Prüfung","category":"in_progress"},
           {"key":"erledigt","name":"Erledigt","category":"done"}],
         "initialStatus":"eingang",
         "transitions":[{"from":"eingang","to":"geplant","requiredFields":["description","custom.prioritaet"]},
           {"from":"geplant","to":"in_entwicklung","requiredFields":["assignee"]},
           {"from":"in_entwicklung","to":"in_pruefung","requiredFields":["custom.pruefanleitung"]},
           {"from":"in_pruefung","to":"erledigt"},{"from":"in_pruefung","to":"in_entwicklung"},{"from":"erledigt","to":"in_entwicklung"}],
         "fields":[{"key":"title","required":true},{"key":"description","required":false},{"key":"type","required":true},
           {"key":"status","required":true},{"key":"assignee","required":false}],
         "customFields":[
           {"key":"prioritaet","name":"Priorität","type":"select","options":[{"key":"p1","name":"P1"},{"key":"p2","name":"P2"}]},
           {"key":"pruefanleitung","name":"Prüfanleitung","type":"longtext"}]}
        """;

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
    @Autowired SearchProjection projection;

    // ---------------------------------------------------------------- a minimal browser

    record Response(int status, String body, Map<String, List<String>> headers) {
        String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name)).flatMap(e -> e.getValue().stream())
                .findFirst().orElse(null);
        }

        JsonNode json() {
            return JSON.readTree(body);
        }

        String code() {
            return json().get("code").asString();
        }

        List<String> violations() {
            return strings(json().get("violations"));
        }

        JsonNode fields() {
            return json().get("fields");
        }

        JsonNode custom() {
            return json().get("customFields");
        }
    }

    final class Browser {
        private final HttpClient http = HttpClient.newHttpClient();
        final Map<String, String> cookies = new LinkedHashMap<>();

        Response send(String method, String path, Object body, String ifMatch) {
            try {
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
                if (body != null) {
                    request.header("Content-Type", "application/json");
                    request.method(method, HttpRequest.BodyPublishers.ofString(body instanceof String s ? s : JSON.writeValueAsString(body)));
                } else {
                    request.method(method, HttpRequest.BodyPublishers.noBody());
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
                return new Response(response.statusCode(), response.body(), response.headers().map());
            } catch (java.io.IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private record Person(Browser browser, UUID id) {
        Response get(String path) {
            return browser.send("GET", path, null, null);
        }

        Response send(String method, String path, Object body, String ifMatch) {
            return browser.send(method, path, body, ifMatch);
        }
    }

    private Person person(AdministrationRight... rights) {
        var issued = accounts.create("test", "config-" + COUNTER.incrementAndGet(), null);
        if (rights.length > 0) {
            AdministrationFixture.grant(jdbc, companies, transactions, issued.account().id(), rights);
        }
        Browser browser = new Browser();
        assertEquals(200, browser.send("GET", "/api/v1/session", null, null).status());
        assertEquals(200, browser.send("POST", "/api/v1/session",
            Map.of("username", issued.account().username(), "password", issued.oneTimePassword()), null).status());
        assertEquals(200, browser.send("POST", "/api/v1/session/password",
            Map.of("currentPassword", issued.oneTimePassword(), "newPassword", STRONG), null).status());
        return new Person(browser, issued.account().id());
    }

    /**
     * Grants rules in a group of their own for one project: an action key such as {@code ticket.read}, or a field rule such as
     * {@code READ:title}, {@code WRITE:custom.notiz} or {@code DENY-READ:custom.interne_ursache} for tickets. {@code *} stands
     * for the five fixed fields.
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
                for (String field : parts[1].equals("*") ? FIVE : List.of(parts[1])) {
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

    /** Everything on the five fixed fields and on the given custom fields. */
    private static String[] full(String... custom) {
        List<String> rules = new ArrayList<>(List.of("ticket.read", "ticket.create", "ticket.edit", "READ:*", "WRITE:*"));
        for (String key : custom) {
            rules.add("READ:custom." + key);
            rules.add("WRITE:custom." + key);
        }
        return rules.toArray(String[]::new);
    }

    private record Project(UUID id, String key, Person manager) {
        String path() {
            return "/api/v1/projects/" + id;
        }

        String tickets() {
            return path() + "/tickets";
        }
    }

    /** A project of its own manager, with the given configuration published and the people as members. */
    private Project project(String configuration, Person... members) {
        Person manager = person(AdministrationRight.PROJECTS_MANAGE);
        String key = "C" + COUNTER.incrementAndGet();
        Response created = manager.send("POST", "/api/v1/projects", Map.of("key", key, "name", "Konfiguration " + key), null);
        assertEquals(201, created.status(), created.body());
        Project project = new Project(UUID.fromString(created.json().get("id").asString()), key, manager);
        String version = created.header("ETag");
        for (Person member : members) {
            Response added = manager.send("PUT", project.path() + "/members/" + member.id(), null, version);
            assertEquals(200, added.status(), added.body());
            version = added.header("ETag");
        }
        if (configuration != null) {
            Response published = manager.send("PUT", project.path() + "/configuration", configuration, version);
            assertEquals(200, published.status(), published.body());
        }
        return project;
    }

    private String version(Project project) {
        return project.manager().get(project.path()).header("ETag");
    }

    private long configurationVersion(Project project) {
        return project.manager().get(project.path()).json().get("configurationVersion").asLong();
    }

    private Response saveDraft(Project project, String ifMatch, long base, String configuration) {
        return project.manager().send("PUT", project.path() + "/configuration/draft",
            "{\"baseVersion\":" + base + ",\"configuration\":" + configuration + "}", ifMatch);
    }

    private Response publish(Project project, long revision, String mappings) {
        String body = "{\"draftRevision\":" + revision + (mappings == null ? "" : "," + mappings) + "}";
        return project.manager().send("POST", project.path() + "/configuration/publish", body, version(project));
    }

    private static Response create(Person person, Project project, Map<String, Object> body) {
        return person.send("POST", project.tickets(), body, null);
    }

    private static Response patch(Person person, Project project, long number, Object body, String ifMatch) {
        return person.send("PATCH", project.tickets() + "/" + number, body, ifMatch);
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static void assertProblem(Response response, int status, String code) {
        assertEquals(status, response.status(), response.body());
        assertEquals(code, response.code(), response.body());
    }

    private static List<String> strings(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asString).toList();
    }

    private static List<String> names(JsonNode object) {
        List<String> names = new ArrayList<>();
        object.properties().forEach(entry -> names.add(entry.getKey()));
        return names;
    }

    private static List<String> changedFields(Response history, int index) {
        return StreamSupport.stream(history.json().get(index).get("changes").spliterator(), false).map(c -> c.get("field").asString()).toList();
    }

    // ---------------------------------------------------------------- two projects with their own rules

    @Test
    void twoProjectsRunTheirOwnTypesWorkflowsAndFieldsSideBySide() {
        Person customer = person();
        Person support = person();
        Project service = project(SERVICE, customer, support);
        grant(customer.id(), service.id(), full("prioritaet", "notiz"));
        grant(support.id(), service.id(), full("prioritaet", "interne_ursache", "notiz", "restdauer", "termin", "bestaetigt", "ansprechpartner"));

        // F-03: the customer creates without the internal cause, and does not even see the field.
        Response created = create(customer, service, map("type", "stoerung", "title", "Drucker streikt", "customFields", map("prioritaet", "niedrig")));
        assertEquals(201, created.status(), created.body());
        assertEquals("neu", created.fields().get("status").asString());
        assertEquals(List.of("prioritaet"), names(created.custom()));
        assertEquals(List.of("custom.prioritaet", "custom.notiz"),
            strings(created.json().get("writableFields")).stream().filter(f -> f.startsWith("custom.")).toList(),
            "the customer is offered only the custom fields with a rule");

        // F-03: internal solving without the cause is refused and leaves the ticket as it was.
        Response moved = patch(support, service, 1, map("status", "in_bearbeitung", "assignee", support.id().toString()), "\"1\"");
        assertEquals(200, moved.status(), moved.body());
        assertEquals("in_bearbeitung", moved.fields().get("status").asString(), "the priority was set at creation, so the step is allowed");
        Response unsolved = patch(support, service, 1, map("status", "geloest"), "\"2\"");
        assertProblem(unsolved, 400, "request.invalid");
        assertEquals(List.of("custom.interne_ursache.required_for_transition"), unsolved.violations());
        Response unchanged = support.get(service.tickets() + "/1");
        assertEquals("in_bearbeitung", unchanged.fields().get("status").asString());
        assertEquals("2", unchanged.header("ETag").replace("\"", ""), "a refused step does not change the version");
        Response solved = patch(support, service, 1, map("status", "geloest", "customFields", map("interne_ursache", "Treiber veraltet")), "\"2\"");
        assertEquals(200, solved.status(), solved.body());
        assertEquals("Treiber veraltet", solved.custom().get("interne_ursache").asString());
        assertFalse(customer.get(service.tickets() + "/1").body().contains("Treiber"), "the customer never sees the internal cause");
        assertFalse(customer.get(service.tickets() + "/1/history").body().contains("Treiber"));

        // A step the workflow does not know is refused as before.
        assertProblem(patch(support, service, 1, map("status", "neu"), "\"3\""), 409, "ticket.transition_not_allowed");

        // The type "frage" has a workflow of its own: it starts in "neu", knows only "geloest", and skips "in_bearbeitung".
        Response question = create(customer, service, map("type", "frage", "title", "Wie geht das?"));
        assertEquals(201, question.status(), question.body());
        assertProblem(patch(support, service, 2, map("status", "in_bearbeitung"), "\"1\""), 400, "request.invalid");
        assertEquals(200, patch(support, service, 2, map("status", "geloest"), "\"1\"").status());
        // "geloest" exists for faults as well, so a solved question can become a fault.
        assertEquals(200, patch(support, service, 2, map("type", "stoerung"), "\"2\"").status());

        // Example B: a second project with other types, an initial status and a required test instruction.
        Person developer = person();
        Project development = project(DEVELOPMENT, developer);
        grant(developer.id(), development.id(), full("prioritaet", "pruefanleitung"));
        Response bug = create(developer, development, map("type", "fehler", "title", "Absturz"));
        assertEquals(201, bug.status(), bug.body());
        assertEquals("eingang", bug.fields().get("status").asString());
        assertProblem(create(developer, development, map("type", "stoerung", "title", "Falsches Projekt")), 400, "request.invalid");
        assertEquals(List.of("description.required_for_transition", "custom.prioritaet.required_for_transition"),
            patch(developer, development, 1, map("status", "geplant"), "\"1\"").violations());
        Response planned = patch(developer, development, 1,
            map("status", "geplant", "description", "Erwartet: Speichern. Beobachtet: Absturz.", "customFields", map("prioritaet", "p1")), "\"1\"");
        assertEquals(200, planned.status(), planned.body());
        assertEquals(200, patch(developer, development, 1, map("status", "in_entwicklung", "assignee", developer.id().toString()), "\"2\"").status());
        // F-04: no test instruction, no step to review, and the ticket stays as it is.
        Response review = patch(developer, development, 1, map("status", "in_pruefung"), "\"3\"");
        assertProblem(review, 400, "request.invalid");
        assertEquals(List.of("custom.pruefanleitung.required_for_transition"), review.violations());
        assertEquals("in_entwicklung", developer.get(development.tickets() + "/1").fields().get("status").asString());
        assertEquals(200, patch(developer, development, 1,
            map("status", "in_pruefung", "customFields", map("pruefanleitung", "Datei speichern und neu öffnen")), "\"3\"").status());
        // The statuses of one project do not exist in the other.
        assertProblem(patch(developer, development, 1, map("status", "neu"), "\"4\""), 400, "request.invalid");
        assertEquals(2, service.manager().get(service.path()).json().get("configuration").get("ticketTypes").size());
        assertEquals(3, development.manager().get(development.path()).json().get("configuration").get("ticketTypes").size());
    }

    // ---------------------------------------------------------------- custom field values

    @Test
    void customValuesAreTypedValidatedAndRecordedInTheHistory() {
        Person worker = person();
        Person outsider = person();
        Project project = project(SERVICE, worker);
        grant(worker.id(), project.id(), full("prioritaet", "interne_ursache", "notiz", "restdauer", "termin", "bestaetigt", "ansprechpartner"));
        Map<String, Object> values = map("prioritaet", "hoch", "interne_ursache", "  Kabel ", "notiz", "Langer Text", "restdauer", 12,
            "termin", "2026-11-05", "bestaetigt", true, "ansprechpartner", worker.id().toString());
        Response created = create(worker, project, map("type", "stoerung", "title", "Alles", "customFields", values));
        assertEquals(201, created.status(), created.body());
        JsonNode custom = created.custom();
        assertEquals("hoch", custom.get("prioritaet").asString());
        assertEquals("Kabel", custom.get("interne_ursache").asString(), "text is trimmed");
        assertTrue(custom.get("restdauer").isNumber(), "a whole number is a JSON number");
        assertEquals(12, custom.get("restdauer").asLong());
        assertTrue(custom.get("bestaetigt").isBoolean());
        assertTrue(custom.get("bestaetigt").asBoolean());
        assertEquals("2026-11-05", custom.get("termin").asString());
        assertTrue(strings(created.json().get("writableFields")).containsAll(List.of("custom.notiz", "custom.restdauer", "custom.termin")));

        Response history = worker.get(project.tickets() + "/1/history");
        assertEquals(List.of("title", "type", "status", "custom.ansprechpartner", "custom.bestaetigt", "custom.interne_ursache", "custom.notiz",
            "custom.prioritaet", "custom.restdauer", "custom.termin"), changedFields(history, 0));

        for (var invalid : List.<Map.Entry<String, Object>>of(Map.entry("restdauer", "12"), Map.entry("restdauer", 1.5), Map.entry("termin", "2026-02-30"),
                Map.entry("termin", "05.11.2026"), Map.entry("bestaetigt", "true"), Map.entry("prioritaet", "mittel"),
                Map.entry("interne_ursache", "  "), Map.entry("interne_ursache", "x".repeat(201)), Map.entry("ansprechpartner", "kein-konto"),
                Map.entry("restdauer", 9_007_199_254_740_992L))) {
            Response refused = patch(worker, project, 1, map("customFields", map(invalid.getKey(), invalid.getValue())), "\"1\"");
            assertProblem(refused, 400, "request.invalid");
            assertEquals(List.of("custom." + invalid.getKey() + ".invalid"), refused.violations(), invalid.toString());
        }
        Response foreign = patch(worker, project, 1, map("customFields", map("ansprechpartner", outsider.id().toString())), "\"1\"");
        assertEquals(List.of("custom.ansprechpartner.not_member"), foreign.violations());
        assertProblem(patch(worker, project, 1, map("customFields", map("restdauer", List.of(1))), "\"1\""), 400, "request.invalid");
        assertEquals("1", worker.get(project.tickets() + "/1").header("ETag").replace("\"", ""), "refused changes write nothing");

        // Changing and clearing is recorded with old and new value; a value that stays is not written.
        Response changed = patch(worker, project, 1, map("customFields", map("restdauer", 8, "notiz", null, "prioritaet", "hoch")), "\"1\"");
        assertEquals(200, changed.status(), changed.body());
        assertFalse(names(changed.custom()).contains("notiz"));
        history = worker.get(project.tickets() + "/1/history");
        assertEquals(2, history.json().size());
        assertEquals(List.of("custom.notiz", "custom.restdauer"), changedFields(history, 1).stream().sorted().toList());
        JsonNode restdauer = StreamSupport.stream(history.json().get(1).get("changes").spliterator(), false)
            .filter(c -> c.get("field").asString().equals("custom.restdauer")).findFirst().orElseThrow();
        assertEquals("12", restdauer.get("from").asString());
        assertEquals("8", restdauer.get("to").asString());
        Response same = patch(worker, project, 1, map("customFields", map("restdauer", 8)), "\"2\"");
        assertEquals("2", same.header("ETag").replace("\"", ""), "no real change keeps the version");

        // A field that does not exist is refused; so is a field without a rule, like any hidden field.
        assertProblem(patch(worker, project, 1, map("customFields", map("gibt_es_nicht", "x")), "\"2\""), 403, "auth.forbidden");
        Person partial = person();
        Project limited = project(SERVICE, partial);
        grant(partial.id(), limited.id(), full("prioritaet"));
        assertProblem(create(partial, limited, map("type", "stoerung", "title", "Ohne Recht", "customFields", map("interne_ursache", "x"))),
            403, "auth.forbidden");
        assertEquals(201, create(partial, limited, map("type", "stoerung", "title", "Mit Recht", "customFields", map("prioritaet", "niedrig"))).status());
    }

    @Test
    void requiredCustomFieldsBindCreationAndCannotBeEmptiedButStayOutOfTheWayOfExistingTickets() {
        Person worker = person();
        Project project = project(SERVICE, worker);
        grant(worker.id(), project.id(), full("prioritaet", "interne_ursache"));
        Response old = create(worker, project, map("type", "stoerung", "title", "Vorher"));
        assertEquals(201, old.status(), old.body());

        // New version: the cause is required at creation, and the step to "wartet" needs the priority.
        String stricter = SERVICE.replace("{\"key\":\"interne_ursache\",\"name\":\"Interne Ursache\",\"type\":\"text\"}",
            "{\"key\":\"interne_ursache\",\"name\":\"Interne Ursache\",\"type\":\"text\",\"required\":true}")
            .replace("{\"from\":\"in_bearbeitung\",\"to\":\"wartet\"}", "{\"from\":\"in_bearbeitung\",\"to\":\"wartet\",\"requiredFields\":[\"custom.prioritaet\"]}");
        Response saved = saveDraft(project, null, configurationVersion(project), stricter);
        assertEquals(200, saved.status(), saved.body());
        assertEquals(200, publish(project, saved.json().get("revision").asLong(), null).status());

        // Existing tickets stay valid: they can be edited without the new required field.
        assertEquals(200, patch(worker, project, 1, map("title", "Nachher"), "\"1\"").status());
        // New tickets need it.
        Response missing = create(worker, project, map("type", "stoerung", "title", "Ohne"));
        assertProblem(missing, 400, "request.invalid");
        assertEquals(List.of("custom.interne_ursache.required"), missing.violations());
        Response created = create(worker, project, map("type", "stoerung", "title", "Mit", "customFields", map("interne_ursache", "Ursache")));
        assertEquals(201, created.status(), created.body());
        // A required field cannot be emptied.
        Response emptied = patch(worker, project, 2, map("customFields", map("interne_ursache", null)), "\"1\"");
        assertEquals(List.of("custom.interne_ursache.required"), emptied.violations());
        // A person who may not write a field that is required at creation cannot create at all: a conflict of rights and configuration.
        Person narrow = person();
        Project narrowProject = project(stricter, narrow);
        grant(narrow.id(), narrowProject.id(), full("prioritaet"));
        assertProblem(create(narrow, narrowProject, map("type", "stoerung", "title", "Unmöglich")), 409, "ticket.required_field_inaccessible");
    }

    // ---------------------------------------------------------------- changing the configuration

    @Test
    void aStatusOrTypeThatTicketsStillUseCanOnlyGoWithAMappingAndMovesThemWithHistory() {
        Person worker = person();
        Project project = project(null, worker);
        grant(worker.id(), project.id(), full());
        assertEquals(201, create(worker, project, map("type", "task", "title", "Eins")).status());
        assertEquals(201, create(worker, project, map("type", "bug", "title", "Zwei")).status());
        assertEquals(200, patch(worker, project, 1, map("status", "in_progress"), "\"1\"").status());
        assertEquals(200, patch(worker, project, 2, map("status", "in_progress"), "\"1\"").status());

        // The new version has "review" instead of "in_progress" and no "bug".
        String next = """
            {"ticketTypes":[{"key":"task","name":"Aufgabe"}],
             "statuses":[{"key":"new","name":"Neu","category":"open"},{"key":"review","name":"Prüfung","category":"in_progress"},
               {"key":"done","name":"Erledigt","category":"done"}],
             "initialStatus":"new",
             "transitions":[{"from":"new","to":"review"},{"from":"review","to":"done"},{"from":"review","to":"new"},{"from":"done","to":"review"}],
             "fields":[{"key":"title","required":true},{"key":"description","required":false},{"key":"type","required":true},
               {"key":"status","required":true},{"key":"assignee","required":false}]}
            """;
        long base = configurationVersion(project);
        Response draft = saveDraft(project, null, base, next);
        assertEquals(200, draft.status(), draft.body());
        long revision = draft.json().get("revision").asLong();

        Response usage = project.manager().get(project.path() + "/configuration/usage");
        assertEquals(200, usage.status(), usage.body());
        assertEquals(2, usage.json().size());
        assertEquals(1, usage.json().get(0).get("count").asLong());

        // F-05: without a checked mapping nothing is migrated and nothing is published.
        Response refused = publish(project, revision, null);
        assertProblem(refused, 409, "project.configuration_in_use");
        assertEquals(List.of("type.in_use:bug", "status.in_use:in_progress"), refused.violations());
        assertEquals(base, configurationVersion(project), "the configuration did not move");
        assertEquals("in_progress", worker.get(project.tickets() + "/1").fields().get("status").asString());
        assertEquals(200, project.manager().get(project.path() + "/configuration/draft").status(), "the draft is kept");
        // The direct path has no mapping either.
        assertProblem(project.manager().send("PUT", project.path() + "/configuration", next, version(project)), 409, "project.configuration_in_use");

        // A mapping to something the new version does not have is refused as input.
        Response unknown = publish(project, revision, "\"typeMapping\":{\"bug\":\"task\"},\"statusMapping\":{\"in_progress\":\"gibt_es_nicht\"}");
        assertProblem(unknown, 400, "request.invalid");
        assertEquals(List.of("statusMapping.target_unknown"), unknown.violations());
        assertEquals(base, configurationVersion(project));

        Response published = publish(project, revision, "\"typeMapping\":{\"bug\":\"task\"},\"statusMapping\":{\"in_progress\":\"review\"}");
        assertEquals(200, published.status(), published.body());
        assertEquals(base + 1, published.json().get("configurationVersion").asLong());
        assertEquals(404, project.manager().get(project.path() + "/configuration/draft").status(), "publishing consumes the draft");
        for (int number : new int[] {1, 2}) {
            Response ticket = worker.get(project.tickets() + "/" + number);
            assertEquals("review", ticket.fields().get("status").asString());
            assertEquals("task", ticket.fields().get("type").asString());
            assertEquals("3", ticket.header("ETag").replace("\"", ""), "a moved ticket gets a new version");
        }
        Response history = worker.get(project.tickets() + "/2/history");
        assertEquals(3, history.json().size());
        assertEquals(List.of("type", "status"), changedFields(history, 2));
        assertEquals(project.manager().id().toString(), history.json().get(2).get("actor").asString(), "the history records who published");
        // The projection followed: a search by the new status finds both.
        Response found = worker.get("/api/v1/search/tickets?status=review");
        assertEquals(2, found.json().get("total").asLong(), found.body());
        assertEquals(0, worker.get("/api/v1/search/tickets?status=in_progress").json().get("total").asLong());
    }

    @Test
    void aWorkflowThatDropsAStatusOfATypeNeedsAMappingForTheTicketsInIt() {
        Person worker = person();
        Project project = project(SERVICE, worker);
        grant(worker.id(), project.id(), full("prioritaet", "interne_ursache"));
        assertEquals(201, create(worker, project, map("type", "frage", "title", "Offene Frage")).status());
        // Questions get a stricter workflow without "neu": the first status becomes "geloest".
        String next = SERVICE.replace("\"workflows\":[{\"type\":\"frage\",\"initialStatus\":\"neu\",\"transitions\":[{\"from\":\"neu\",\"to\":\"geloest\"}]}]",
            "\"workflows\":[{\"type\":\"frage\",\"initialStatus\":\"geloest\",\"transitions\":[{\"from\":\"geloest\",\"to\":\"geschlossen\"}]}]");
        Response draft = saveDraft(project, null, configurationVersion(project), next);
        assertEquals(200, draft.status(), draft.body());
        long revision = draft.json().get("revision").asLong();
        Response refused = publish(project, revision, null);
        assertProblem(refused, 409, "project.configuration_in_use");
        assertEquals(List.of("status.in_use:neu"), refused.violations());
        // The target must exist for the type, too.
        Response badTarget = publish(project, revision, "\"statusMapping\":{\"neu\":\"wartet\"}");
        assertProblem(badTarget, 409, "project.configuration_in_use");
        assertEquals(List.of("status.mapping_invalid:neu"), badTarget.violations());
        assertEquals(200, publish(project, revision, "\"statusMapping\":{\"neu\":\"geloest\"}").status());
        assertEquals("geloest", worker.get(project.tickets() + "/1").fields().get("status").asString());
        // A type that was only changed on the ticket must fit the new workflow.
        Response fault = create(worker, project, map("type", "stoerung", "title", "Fehler"));
        assertEquals("neu", fault.fields().get("status").asString());
        Response toQuestion = patch(worker, project, 2, map("type", "frage"), "\"1\"");
        assertEquals(List.of("type.status_unavailable"), toQuestion.violations());
    }

    @Test
    void customFieldsAndOptionsAreDeactivatedNeverRemovedAndKeepTheirValues() {
        Person worker = person();
        Project project = project(SERVICE, worker);
        grant(worker.id(), project.id(), full("prioritaet", "interne_ursache", "notiz"));
        assertEquals(201, create(worker, project, map("type", "stoerung", "title", "Alt", "customFields",
            map("prioritaet", "hoch", "notiz", "bleibt"))).status());

        // Removing a field or an option, or changing a type, is refused with a reason, whichever way it is published.
        for (var attempt : List.of(
                Map.entry("customFields.removed", SERVICE.replace("{\"key\":\"notiz\",\"name\":\"Notiz\",\"type\":\"longtext\"},", "")),
                Map.entry("customFields.type_changed", SERVICE.replace("\"key\":\"notiz\",\"name\":\"Notiz\",\"type\":\"longtext\"",
                    "\"key\":\"notiz\",\"name\":\"Notiz\",\"type\":\"text\"")),
                Map.entry("customFields.option_removed", SERVICE.replace(",{\"key\":\"hoch\",\"name\":\"Hoch\"}", "")))) {
            assertFalse(attempt.getValue().equals(SERVICE), attempt.getKey() + " needs a different document");
            Response refused = project.manager().send("PUT", project.path() + "/configuration", attempt.getValue(), version(project));
            assertProblem(refused, 409, "project.configuration_incompatible");
            assertEquals(List.of(attempt.getKey()), refused.violations());
        }

        // F-06: the option is deactivated and renamed; the stored value stays, but nobody can choose it any more.
        String deactivated = SERVICE.replace("{\"key\":\"hoch\",\"name\":\"Hoch\"}", "{\"key\":\"hoch\",\"name\":\"High (alt)\",\"active\":false}")
            .replace("{\"key\":\"notiz\",\"name\":\"Notiz\",\"type\":\"longtext\"}", "{\"key\":\"notiz\",\"name\":\"Notiz\",\"type\":\"longtext\",\"active\":false}");
        long base = configurationVersion(project);
        Response draft = saveDraft(project, null, base, deactivated);
        assertEquals(200, publish(project, draft.json().get("revision").asLong(), null).status());
        Response old = worker.get(project.tickets() + "/1");
        assertEquals("hoch", old.custom().get("prioritaet").asString(), "the stored option stays readable");
        assertEquals("bleibt", old.custom().get("notiz").asString(), "the value of an inactive field stays readable");
        assertFalse(strings(old.json().get("writableFields")).contains("custom.notiz"), "an inactive field is not offered for writing");
        Response rejected = create(worker, project, map("type", "stoerung", "title", "Neu", "customFields", map("prioritaet", "hoch")));
        assertEquals(List.of("custom.prioritaet.option_inactive"), rejected.violations());
        assertEquals(List.of("custom.notiz.inactive"),
            create(worker, project, map("type", "stoerung", "title", "Neu", "customFields", map("notiz", "x"))).violations());
        // The old ticket keeps its value when it is written again, and can be edited otherwise; the value can be cleared.
        assertEquals(200, patch(worker, project, 1, map("title", "Alt bearbeitet", "customFields", map("prioritaet", "hoch")), "\"1\"").status());
        Response cleared = patch(worker, project, 1, map("customFields", map("notiz", null)), "\"2\"");
        assertEquals(200, cleared.status(), cleared.body());
        assertFalse(names(cleared.custom()).contains("notiz"));
        assertEquals(201, create(worker, project, map("type", "stoerung", "title", "Neu", "customFields", map("prioritaet", "niedrig"))).status());
        // The name and the state of an option can be changed back: it is a new version, not a new option.
        Response reactivate = saveDraft(project, null, configurationVersion(project), SERVICE);
        assertEquals(200, publish(project, reactivate.json().get("revision").asLong(), null).status());
        assertEquals(201, create(worker, project, map("type", "stoerung", "title", "Wieder", "customFields", map("prioritaet", "hoch"))).status());
    }

    @Test
    void draftsAreSavedWithRevisionsAndPublishedOnlyFromTheirBase() {
        Person worker = person();
        Person reader = person();
        Project project = project(null, worker, reader);
        grant(reader.id(), project.id(), "project.read");
        String initial = project.manager().get(project.path()).json().get("configuration").toString();
        long base = configurationVersion(project);
        assertProblem(project.manager().get(project.path() + "/configuration/draft"), 404, "project.draft_not_found");

        Response first = saveDraft(project, null, base, initial);
        assertEquals(200, first.status(), first.body());
        assertEquals("\"1\"", first.header("ETag"));
        assertEquals(base, first.json().get("baseVersion").asLong());
        assertProblem(saveDraft(project, null, base, initial), 412, "project.draft_conflict");
        Response second = saveDraft(project, "\"1\"", base, initial);
        assertEquals("\"2\"", second.header("ETag"));
        assertProblem(saveDraft(project, "\"1\"", base, initial), 412, "project.draft_conflict");
        assertProblem(saveDraft(project, "\"2\"", base + 1, initial), 409, "project.draft_stale");
        Response invalid = saveDraft(project, "\"2\"", base, initial.replace("\"initialStatus\":\"new\"", "\"initialStatus\":\"gibt_es_nicht\""));
        assertProblem(invalid, 400, "request.invalid");
        assertEquals(List.of("initialStatus.unknown"), invalid.violations());
        assertEquals("\"2\"", project.manager().get(project.path() + "/configuration/draft").header("ETag"), "a refused save keeps the draft");

        // Only the administration of the project works on the configuration.
        assertProblem(reader.get(project.path() + "/configuration/draft"), 403, "auth.forbidden");
        assertProblem(reader.send("GET", project.path() + "/configuration/usage", null, null), 403, "auth.forbidden");
        assertProblem(worker.get(project.path() + "/configuration/draft"), 404, "project.not_found");
        assertProblem(saveDraft(new Project(project.id(), project.key(), reader), null, base, initial), 403, "auth.forbidden");

        assertProblem(project.manager().send("DELETE", project.path() + "/configuration/draft", null, null), 428, "project.precondition_required");
        assertProblem(project.manager().send("DELETE", project.path() + "/configuration/draft", null, "\"1\""), 412, "project.draft_conflict");
        assertProblem(publish(project, 1, null), 412, "project.draft_conflict");
        assertProblem(project.manager().send("POST", project.path() + "/configuration/publish", "{\"draftRevision\":2}", "\"999\""), 412,
            "project.version_conflict");
        assertEquals(204, project.manager().send("DELETE", project.path() + "/configuration/draft", null, "\"2\"").status());
        assertProblem(publish(project, 2, null), 404, "project.draft_not_found");

        // A newer version published meanwhile makes an older draft stale instead of overwriting it.
        Response draft = saveDraft(project, null, base, initial);
        assertEquals(200, project.manager().send("PUT", project.path() + "/configuration", initial, version(project)).status());
        assertProblem(publish(project, draft.json().get("revision").asLong(), null), 409, "project.draft_stale");
    }

    @Test
    void anInvalidConfigurationNamesItsReasonsAndChangesNothing() {
        Person worker = person();
        Project project = project(null, worker);
        long base = configurationVersion(project);
        for (var attempt : List.of(
                Map.entry("transitions.required_field_unknown", SERVICE.replace("\"requiredFields\":[\"assignee\",\"custom.prioritaet\"]", "\"requiredFields\":[\"title\"]")),
                Map.entry("workflows.type_unknown", SERVICE.replace("\"type\":\"frage\",\"initialStatus\"", "\"type\":\"nichts\",\"initialStatus\"")),
                Map.entry("workflows.initialStatus.unknown", SERVICE.replace("\"initialStatus\":\"neu\",\"transitions\":[{\"from\":\"neu\",\"to\":\"geloest\"}]",
                    "\"initialStatus\":\"nirgends\",\"transitions\":[{\"from\":\"neu\",\"to\":\"geloest\"}]")),
                Map.entry("customFields.type_invalid", SERVICE.replace("\"type\":\"integer\"", "\"type\":\"dezimal\"")),
                Map.entry("customFields.key_duplicate", SERVICE.replace("\"key\":\"restdauer\"", "\"key\":\"notiz\"")),
                Map.entry("customFields.key_invalid", SERVICE.replace("\"key\":\"restdauer\"", "\"key\":\"Rest-Dauer\"")),
                Map.entry("customFields.options_required", SERVICE.replace(
                    "\"options\":[{\"key\":\"niedrig\",\"name\":\"Niedrig\"},{\"key\":\"hoch\",\"name\":\"Hoch\"}]", "\"options\":[]")),
                Map.entry("customFields.options_unexpected", SERVICE.replace("\"key\":\"notiz\",\"name\":\"Notiz\",\"type\":\"longtext\"",
                    "\"key\":\"notiz\",\"name\":\"Notiz\",\"type\":\"longtext\",\"options\":[{\"key\":\"a\",\"name\":\"A\"}]")),
                Map.entry("customFields.option_name_duplicate", SERVICE.replace("{\"key\":\"hoch\",\"name\":\"Hoch\"}", "{\"key\":\"hoch\",\"name\":\" niedrig \"}")))) {
            assertFalse(attempt.getValue().equals(SERVICE), attempt.getKey() + " needs a different document");
            Response refused = project.manager().send("PUT", project.path() + "/configuration", attempt.getValue(), version(project));
            assertProblem(refused, 400, "request.invalid");
            assertTrue(refused.violations().contains(attempt.getKey()), attempt.getKey() + " in " + refused.violations());
        }
        assertEquals(base, configurationVersion(project));
    }

    // ---------------------------------------------------------------- rights and search

    @Test
    void customFieldRulesHideValuesInAnswersHistoryAndSearchLikeStandardFields() {
        Person insider = person();
        Person outsider = person();
        Project project = project(SERVICE, insider, outsider);
        grant(insider.id(), project.id(), full("prioritaet", "interne_ursache", "notiz"));
        // The outsider reads everything but the internal cause, and may not write it either.
        grant(outsider.id(), project.id(), "ticket.read", "ticket.edit", "READ:*", "WRITE:*", "READ:custom.prioritaet", "READ:custom.notiz",
            "WRITE:custom.notiz", "DENY-READ:custom.interne_ursache");
        assertEquals(201, create(insider, project, map("type", "stoerung", "title", "Sichtbar", "customFields",
            map("interne_ursache", "Zirbeldrüse", "notiz", "Kunde meldet sich", "prioritaet", "hoch"))).status());

        Response seen = outsider.get(project.tickets() + "/1");
        assertEquals(List.of("prioritaet", "notiz"), names(seen.custom()).stream().sorted(java.util.Comparator.comparing(
            (String n) -> !n.equals("prioritaet")).thenComparing(n -> n)).toList());
        assertFalse(seen.body().contains("Zirbeldrüse"));
        assertFalse(outsider.get(project.tickets() + "/1/history").body().contains("Zirbeldrüse"));
        assertFalse(outsider.get(project.tickets()).body().contains("Zirbeldrüse"));
        assertProblem(patch(outsider, project, 1, map("customFields", map("interne_ursache", "ändern")), "\"1\""), 403, "auth.forbidden");
        assertEquals(200, patch(outsider, project, 1, map("customFields", map("notiz", "Antwort")), "\"1\"").status());

        // Search: the word only matches where the field may be read; the filter likewise, and the hit shows only readable values.
        Response insiderFound = insider.get("/api/v1/search/tickets?q=Zirbeldr%C3%BCse");
        assertEquals(1, insiderFound.json().get("total").asLong(), insiderFound.body());
        assertEquals("Zirbeldrüse", insiderFound.json().get("items").get(0).get("customFields").get("interne_ursache").asString());
        assertEquals(0, outsider.get("/api/v1/search/tickets?q=Zirbeldr%C3%BCse").json().get("total").asLong(),
            "a hidden field neither matches nor ranks");
        assertEquals(1, insider.get("/api/v1/search/tickets?custom=interne_ursache%3DZirbeldr%C3%BCse").json().get("total").asLong());
        assertEquals(0, outsider.get("/api/v1/search/tickets?custom=interne_ursache%3DZirbeldr%C3%BCse").json().get("total").asLong());
        Response outsiderHit = outsider.get("/api/v1/search/tickets?q=Antwort");
        assertEquals(1, outsiderHit.json().get("total").asLong(), outsiderHit.body());
        assertEquals(List.of("notiz", "prioritaet"), names(outsiderHit.json().get("items").get(0).get("customFields")).stream().sorted().toList());
        assertEquals(1, outsider.get("/api/v1/search/tickets?custom=prioritaet%3Dhoch").json().get("total").asLong());
        assertEquals(0, outsider.get("/api/v1/search/tickets?custom=prioritaet%3Dniedrig").json().get("total").asLong());
        assertProblem(outsider.get("/api/v1/search/tickets?custom=kaputt"), 400, "request.invalid");
        assertProblem(outsider.get("/api/v1/search/tickets?custom=Gross%3Dx"), 400, "request.invalid");

        // The projection can be built again from the tickets and keeps the custom values.
        projection.rebuild(companies.find().orElseThrow());
        assertEquals(1, insider.get("/api/v1/search/tickets?q=Zirbeldr%C3%BCse").json().get("total").asLong());
        assertEquals(1, insider.get("/api/v1/search/tickets?custom=prioritaet%3Dhoch&q=Antwort").json().get("total").asLong());
        // A cleared value leaves the projection with the change.
        assertEquals(200, patch(insider, project, 1, map("customFields", map("interne_ursache", null)), "\"2\"").status());
        assertEquals(0, insider.get("/api/v1/search/tickets?q=Zirbeldr%C3%BCse").json().get("total").asLong());
    }

    @Test
    void theGroupAdministrationOffersAndChecksTheCustomFieldsOfAProject() {
        Person administrator = person(AdministrationRight.PROJECTS_MANAGE);
        AdministrationFixture.grantAdministration(jdbc, companies, transactions, administrator.id());
        Project project = project(SERVICE, administrator);
        Project plain = project(null);
        UUID group = UUID.fromString(administrator.send("POST", "/api/v1/permissions/groups", Map.of("name", "Support " + COUNTER.incrementAndGet()), null)
            .json().get("id").asString());

        Response rights = administrator.get("/api/v1/permissions/projects/" + project.id() + "/groups");
        assertEquals(200, rights.status(), rights.body());
        List<String> offered = strings(StreamSupport.stream(rights.json().get("fields").spliterator(), false)
            .filter(f -> f.get("resourceType").asString().equals("ticket")).findFirst().orElseThrow().get("fields"));
        assertTrue(offered.containsAll(List.of("title", "custom.prioritaet", "custom.interne_ursache")), offered.toString());
        List<String> plainOffered = strings(StreamSupport.stream(administrator.get("/api/v1/permissions/projects/" + plain.id() + "/groups")
            .json().get("fields").spliterator(), false).findFirst().orElseThrow().get("fields"));
        assertFalse(plainOffered.stream().anyMatch(f -> f.startsWith("custom.")));

        Map<String, Object> rule = map("resourceType", "ticket", "field", "custom.interne_ursache", "projectId", project.id().toString(),
            "operation", "read", "effect", "allow");
        Response set = administrator.send("PUT", "/api/v1/permissions/groups/" + group + "/fields", rule, null);
        assertEquals(200, set.status(), set.body());
        assertTrue(set.body().contains("custom.interne_ursache"));
        // Not for a field the project does not have, not for another project's field, and not without a project.
        rule.put("field", "custom.gibt_es_nicht");
        Response unknown = administrator.send("PUT", "/api/v1/permissions/groups/" + group + "/fields", rule, null);
        assertProblem(unknown, 400, "request.invalid");
        assertEquals(List.of("field.unknown"), unknown.violations());
        rule.put("field", "custom.interne_ursache");
        rule.put("projectId", plain.id().toString());
        assertEquals(List.of("field.unknown"), administrator.send("PUT", "/api/v1/permissions/groups/" + group + "/fields", rule, null).violations());
        rule.put("projectId", null);
        assertEquals(List.of("field.unknown"), administrator.send("PUT", "/api/v1/permissions/groups/" + group + "/fields", rule, null).violations());
        rule.put("projectId", project.id().toString());
        rule.put("field", "custom.Gross");
        assertEquals(List.of("field.unknown"), administrator.send("PUT", "/api/v1/permissions/groups/" + group + "/fields", rule, null).violations());
    }

    @Test
    void theRegisteredConfigurationOfAnOldProjectReadsAsOneWithoutNewFeatures() {
        Person worker = person();
        Project project = project(null, worker);
        grant(worker.id(), project.id(), full());
        // A document written before TASK-044 has none of the new members.
        jdbc.update("""
            update project_configuration set document = (document - 'workflows' - 'customFields')
                || jsonb_build_object('transitions', (select jsonb_agg(t - 'requiredFields') from jsonb_array_elements(document -> 'transitions') t))
            where project_id = ?
            """, project.id());
        Response read = project.manager().get(project.path());
        assertEquals(200, read.status(), read.body());
        assertEquals(0, read.json().get("configuration").get("customFields").size());
        assertEquals(0, read.json().get("configuration").get("workflows").size());
        assertEquals(0, read.json().get("configuration").get("transitions").get(0).get("requiredFields").size());
        Response ticket = create(worker, project, map("type", "task", "title", "Alt"));
        assertEquals(201, ticket.status(), ticket.body());
        assertEquals(0, ticket.custom().size());
        assertEquals(200, patch(worker, project, 1, map("status", "in_progress"), "\"1\"").status());
    }
}
