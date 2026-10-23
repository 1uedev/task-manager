package taskmanager.projects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import taskmanager.projects.ProjectConfiguration.Field;
import taskmanager.projects.ProjectConfiguration.Status;
import taskmanager.projects.ProjectConfiguration.TicketType;
import taskmanager.projects.ProjectConfiguration.Transition;

/** The rules of a project configuration without a database (TASK-026). */
class ProjectConfigurationTest {
    private static final ProjectConfiguration INITIAL = ProjectConfiguration.initial();

    private static ProjectConfiguration with(List<TicketType> types, List<Status> statuses, String initial, List<Transition> transitions,
            List<Field> fields) {
        return new ProjectConfiguration(types, statuses, initial, transitions, fields);
    }

    @Test
    void theInitialConfigurationIsValidAndHasTheAgreedMinimum() {
        assertEquals(List.of(), INITIAL.violations());
        assertEquals(List.of("new", "in_progress", "done"), INITIAL.statuses().stream().map(Status::key).toList());
        assertEquals(List.of("Neu", "In Arbeit", "Erledigt"), INITIAL.statuses().stream().map(Status::name).toList());
        assertEquals("new", INITIAL.initialStatus());
        assertEquals(ProjectConfiguration.FIELD_KEYS, INITIAL.fields().stream().map(Field::key).toList());
        assertTrue(INITIAL.allowsTransition("new", "in_progress"));
        assertTrue(INITIAL.allowsTransition("done", "in_progress"));
        assertFalse(INITIAL.allowsTransition("new", "done"));
        assertFalse(INITIAL.allowsTransition("new", "new"), "staying is not a transition");
        assertTrue(INITIAL.hasTicketType("task"));
        assertFalse(INITIAL.hasTicketType("epic"));
        assertTrue(INITIAL.isRequired("title"));
        assertFalse(INITIAL.isRequired("assignee"));
    }

    @Test
    void missingListsAndAnUnknownInitialStatusAreNamed() {
        assertEquals(List.of("ticketTypes.required", "statuses.required", "initialStatus.unknown", "transitions.required", "fields.invalid"),
            with(null, List.of(), "new", null, null).violations());
    }

    @Test
    void keysNamesAndCategoriesAreChecked() {
        var types = List.of(new TicketType("Task", "Aufgabe"), new TicketType("bug", " "), new TicketType("bug", "Fehler"));
        var statuses = List.of(new Status("new", "Neu", "open"), new Status("new", "Doppelt", "waiting"),
            new Status("done", "x".repeat(81), "done"));
        assertEquals(List.of("ticketTypes.key_invalid", "ticketTypes.name_invalid", "ticketTypes.key_duplicate",
                "statuses.key_duplicate", "statuses.name_invalid", "statuses.category_invalid"),
            with(types, statuses, "new", List.of(), INITIAL.fields()).violations());
    }

    @Test
    void transitionsMustConnectTwoDifferentKnownStatusesOnce() {
        var transitions = List.of(new Transition("new", "gone"), new Transition("new", "new"), new Transition("new", "done"),
            new Transition("new", "done"));
        assertEquals(List.of("transitions.status_unknown", "transitions.same_status", "transitions.duplicate"),
            with(INITIAL.ticketTypes(), INITIAL.statuses(), "new", transitions, INITIAL.fields()).violations());
        assertEquals(List.of(), with(INITIAL.ticketTypes(), INITIAL.statuses(), "new", List.of(), INITIAL.fields()).violations(),
            "a workflow without transitions is allowed: every ticket stays in its initial status");
    }

    @Test
    void theFieldsAreFixedAndTitleTypeAndStatusStayRequired() {
        List<Field> reordered = new ArrayList<>(INITIAL.fields());
        Collections.swap(reordered, 0, 1);
        assertEquals(List.of("fields.invalid"), with(INITIAL.ticketTypes(), INITIAL.statuses(), "new", INITIAL.transitions(), reordered).violations());
        List<Field> extra = new ArrayList<>(INITIAL.fields());
        extra.add(new Field("priority", false));
        assertEquals(List.of("fields.invalid"), with(INITIAL.ticketTypes(), INITIAL.statuses(), "new", INITIAL.transitions(), extra).violations());
        List<Field> optionalStatus = new ArrayList<>(INITIAL.fields());
        optionalStatus.set(3, new Field("status", false));
        assertEquals(List.of("fields.required_fixed"),
            with(INITIAL.ticketTypes(), INITIAL.statuses(), "new", INITIAL.transitions(), optionalStatus).violations());
        List<Field> requiredAssignee = new ArrayList<>(INITIAL.fields());
        requiredAssignee.set(4, new Field("assignee", true));
        assertEquals(List.of(), with(INITIAL.ticketTypes(), INITIAL.statuses(), "new", INITIAL.transitions(), requiredAssignee).violations(),
            "description and assignee may become required");
    }

    @Test
    void theLimitsHold() {
        List<TicketType> types = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            types.add(new TicketType("t" + i, "Typ " + i));
        }
        List<Status> statuses = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            statuses.add(new Status("s" + i, "Status " + i, "open"));
        }
        List<Transition> transitions = new ArrayList<>();
        for (int i = 0; i < 31 && transitions.size() <= 200; i++) {
            for (int j = 0; j < 31 && transitions.size() <= 200; j++) {
                if (i != j) {
                    transitions.add(new Transition("s" + i, "s" + j));
                }
            }
        }
        assertEquals(List.of("ticketTypes.too_many", "statuses.too_many", "transitions.too_many"),
            with(types, statuses, "s0", transitions, INITIAL.fields()).violations());
    }

    @Test
    void namesAreStoredWithoutSurroundingBlanks() {
        var padded = with(List.of(new TicketType("task", "  Aufgabe ")), List.of(new Status("new", " Neu", "open")), "new", List.of(),
            INITIAL.fields());
        assertEquals(List.of(), padded.violations());
        assertEquals("Aufgabe", padded.normalized().ticketTypes().getFirst().name());
        assertEquals("Neu", padded.normalized().statuses().getFirst().name());
    }

    // ---------------------------------------------------------------- TASK-044

    private static ProjectConfiguration withFields(List<ProjectConfiguration.CustomField> fields, List<Transition> transitions,
            List<ProjectConfiguration.Workflow> workflows) {
        return new ProjectConfiguration(INITIAL.ticketTypes(), INITIAL.statuses(), "new", transitions, INITIAL.fields(), workflows, fields);
    }

    private static ProjectConfiguration.CustomField field(String key, String type, ProjectConfiguration.Option... options) {
        return new ProjectConfiguration.CustomField(key, key, type, false, true, List.of(options));
    }

    @Test
    void aConfigurationWithoutTheNewMembersReadsAsOneWithoutThem() throws Exception {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        String old = "{\"ticketTypes\":[{\"key\":\"task\",\"name\":\"Aufgabe\"}],\"statuses\":[{\"key\":\"new\",\"name\":\"Neu\",\"category\":\"open\"}],"
            + "\"initialStatus\":\"new\",\"transitions\":[],\"fields\":[{\"key\":\"title\",\"required\":true},{\"key\":\"description\",\"required\":false},"
            + "{\"key\":\"type\",\"required\":true},{\"key\":\"status\",\"required\":true},{\"key\":\"assignee\",\"required\":false}]}";
        ProjectConfiguration read = mapper.readValue(old, ProjectConfiguration.class);
        assertEquals(List.of(), read.violations());
        assertEquals(List.of(), read.customFields());
        assertEquals(List.of(), read.workflows());
        assertEquals("new", read.initialStatusOf("task"));
    }

    @Test
    void aTypeWithAWorkflowOfItsOwnKnowsOnlyTheStatusesItNames() {
        var workflow = new ProjectConfiguration.Workflow("bug", "in_progress", List.of(new Transition("in_progress", "done")));
        var config = withFields(List.of(), INITIAL.transitions(), List.of(workflow));
        assertEquals(List.of(), config.violations());
        assertEquals("in_progress", config.initialStatusOf("bug"));
        assertEquals("new", config.initialStatusOf("task"));
        assertEquals(java.util.Set.of("in_progress", "done"), config.statusKeysOf("bug"));
        assertEquals(java.util.Set.of("new", "in_progress", "done"), config.statusKeysOf("task"));
        assertTrue(config.allowsTransition("bug", "in_progress", "done"));
        assertFalse(config.allowsTransition("bug", "new", "in_progress"));
        assertTrue(config.allowsTransition("task", "new", "in_progress"));
        assertEquals(List.of("workflows.type_unknown", "workflows.type_duplicate", "workflows.initialStatus.unknown", "workflows.transitions.status_unknown"),
            withFields(List.of(), INITIAL.transitions(), List.of(new ProjectConfiguration.Workflow("nix", "new", List.of()), workflow, workflow,
                new ProjectConfiguration.Workflow("task", "nix", List.of(new Transition("new", "nix"))))).violations().stream()
                .sorted(java.util.Comparator.comparing(v -> List.of("workflows.type_unknown", "workflows.type_duplicate", "workflows.initialStatus.unknown",
                    "workflows.transitions.status_unknown").indexOf(v))).toList());
    }

    @Test
    void transitionsRequireOnlyKnownFieldsOnce() {
        var cause = field("ursache", "text");
        var ok = withFields(List.of(cause), List.of(new Transition("new", "in_progress", List.of("description", "assignee", "custom.ursache"))), List.of());
        assertEquals(List.of(), ok.violations());
        assertEquals(List.of("transitions.required_field_unknown"), withFields(List.of(cause),
            List.of(new Transition("new", "in_progress", List.of("title", "custom.fehlt"))), List.of()).violations());
        assertEquals(List.of("transitions.required_field_duplicate"), withFields(List.of(cause),
            List.of(new Transition("new", "in_progress", List.of("custom.ursache", "custom.ursache"))), List.of()).violations());
    }

    @Test
    void customFieldsNeedValidKeysTypesAndOptions() {
        var option = new ProjectConfiguration.Option("a", "Alpha", true);
        assertEquals(List.of(), withFields(List.of(field("prio", "select", option)), INITIAL.transitions(), List.of()).violations());
        assertEquals(List.of("customFields.options_required"), withFields(List.of(field("prio", "select")), INITIAL.transitions(), List.of()).violations());
        assertEquals(List.of("customFields.options_unexpected"), withFields(List.of(field("n", "integer", option)), INITIAL.transitions(), List.of()).violations());
        assertEquals(List.of("customFields.type_invalid"), withFields(List.of(field("n", "decimal")), INITIAL.transitions(), List.of()).violations());
        assertEquals(List.of("customFields.key_invalid", "customFields.key_duplicate"),
            withFields(List.of(field("N-1", "text"), field("n", "text"), field("n", "text")), INITIAL.transitions(), List.of()).violations());
        assertEquals(List.of("customFields.option_key_duplicate", "customFields.option_name_duplicate"),
            withFields(List.of(field("prio", "select", option, new ProjectConfiguration.Option("a", " alpha", true))), INITIAL.transitions(), List.of()).violations());
        var many = new ArrayList<ProjectConfiguration.CustomField>();
        for (int i = 0; i < 31; i++) {
            many.add(field("f" + i, "text"));
        }
        assertEquals(List.of("customFields.too_many"), withFields(many, INITIAL.transitions(), List.of()).violations());
    }

    @Test
    void aNewVersionKeepsEveryFieldAndOptionAndTheirTypes() {
        var a = new ProjectConfiguration.Option("a", "Alpha", true);
        var b = new ProjectConfiguration.Option("b", "Beta", true);
        var before = withFields(List.of(field("prio", "select", a, b), field("n", "integer")), INITIAL.transitions(), List.of());
        var inactive = new ProjectConfiguration.CustomField("prio", "Priorität", "select", false, true,
            List.of(a, new ProjectConfiguration.Option("b", "Beta (alt)", false)));
        assertEquals(List.of(), withFields(List.of(inactive, new ProjectConfiguration.CustomField("n", "n", "integer", false, false, List.of())),
            INITIAL.transitions(), List.of()).incompatibilities(before), "deactivating and renaming are fine");
        assertEquals(List.of("customFields.removed"), withFields(List.of(field("prio", "select", a, b)), INITIAL.transitions(), List.of()).incompatibilities(before));
        assertEquals(List.of("customFields.option_removed"),
            withFields(List.of(field("prio", "select", a), field("n", "integer")), INITIAL.transitions(), List.of()).incompatibilities(before));
        assertEquals(List.of("customFields.type_changed"),
            withFields(List.of(field("prio", "select", a, b), field("n", "text")), INITIAL.transitions(), List.of()).incompatibilities(before));
    }

    @Test
    void valuesAreCheckedAndStoredInTheirCanonicalForm() {
        var option = new ProjectConfiguration.Option("a", "Alpha", true);
        assertEquals("Hallo", ProjectConfiguration.canonical(field("t", "text"), "  Hallo "));
        assertEquals(null, ProjectConfiguration.canonical(field("t", "text"), "   "));
        assertEquals(null, ProjectConfiguration.canonical(field("t", "text"), "x".repeat(201)));
        assertEquals("x".repeat(201), ProjectConfiguration.canonical(field("l", "longtext"), "x".repeat(201)));
        assertEquals("7", ProjectConfiguration.canonical(field("n", "integer"), 7));
        assertEquals("-3", ProjectConfiguration.canonical(field("n", "integer"), -3L));
        assertEquals(null, ProjectConfiguration.canonical(field("n", "integer"), "7"));
        assertEquals(null, ProjectConfiguration.canonical(field("n", "integer"), 1.0));
        assertEquals("2026-11-05", ProjectConfiguration.canonical(field("d", "date"), "2026-11-05"));
        assertEquals(null, ProjectConfiguration.canonical(field("d", "date"), "2026-13-01"));
        assertEquals("false", ProjectConfiguration.canonical(field("b", "boolean"), false));
        assertEquals(null, ProjectConfiguration.canonical(field("b", "boolean"), "false"));
        assertEquals("a", ProjectConfiguration.canonical(field("s", "select", option), "a"));
        assertEquals(null, ProjectConfiguration.canonical(field("s", "select", option), "b"));
        String id = "5d3f9ba6-7a0f-4ef4-8f4f-1d7d6b9c0e11";
        assertEquals(id, ProjectConfiguration.canonical(field("u", "user"), id.toUpperCase()));
        assertEquals(null, ProjectConfiguration.canonical(field("u", "user"), "nobody"));
        assertEquals(null, ProjectConfiguration.canonical(field("t", "text"), null));
    }
}
