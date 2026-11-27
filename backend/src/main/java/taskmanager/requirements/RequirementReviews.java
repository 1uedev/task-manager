package taskmanager.requirements;

import java.util.List;
import java.util.UUID;
import taskmanager.permissions.AccessContext;
import taskmanager.projects.ProjectConfiguration;

/**
 * What the AI review of a requirement reads (TASK-127, DEC-173): the content of one revision as the caller may read it, with
 * {@code requirement.read} and the field rules of the resource type {@code requirement}. Every part the caller may not read is
 * {@code null} or missing; an unknown requirement, one of a project the caller may not read and an unknown revision answer 404
 * like the routes of the requirements.
 */
public interface RequirementReviews {
    /** One acceptance criterion with its fixed number. */
    record Criterion(int id, String text) {
    }

    /** A readable custom field with the stored value as text: the key of an option, the identifier of a person or the value. */
    record Field(ProjectConfiguration.CustomField definition, String value) {
    }

    /**
     * The revision: {@code description} is its visible text, one block per line, and {@code current} says whether it is the
     * current revision of the requirement. The content of the current revision changes with every save until it is approved.
     */
    record Review(UUID id, UUID projectId, String key, int revision, boolean current, String title, String description,
            List<Criterion> criteria, List<Field> fields) {
    }

    Review review(AccessContext actor, UUID requirementId, int revision);
}
