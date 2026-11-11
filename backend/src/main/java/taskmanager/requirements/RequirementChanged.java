package taskmanager.requirements;

import java.util.List;
import java.util.UUID;

/**
 * The searchable state of a requirement after a change, published inside the transaction of the change (TASK-103, DEC-133).
 * {@code stages} holds the current revision ({@code current}) and, once approved, the newest approved revision
 * ({@code approved}); they can be the same revision. The text of the description is plain text, the custom values are in
 * their canonical text with the kind of their field.
 */
public record RequirementChanged(UUID requirementId, UUID companyId, UUID projectId, long number, String status, UUID ownerId,
        int currentRevision, Integer approvedRevision, List<Stage> stages) {

    public RequirementChanged {
        stages = List.copyOf(stages);
    }

    public record Stage(String stage, int revision, String title, String description, String priority, List<Criterion> criteria,
            List<CustomValue> custom) {
        public Stage {
            criteria = List.copyOf(criteria);
            custom = List.copyOf(custom);
        }
    }

    /** A criterion with its fixed number inside the requirement. */
    public record Criterion(int id, String text) {
    }

    /** The value of one custom field of requirements; {@code type} is the kind of the field. */
    public record CustomValue(String key, String type, String value) {
    }
}
