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
}
