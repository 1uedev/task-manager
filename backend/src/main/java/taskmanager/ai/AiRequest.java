package taskmanager.ai;

import java.util.List;
import java.util.UUID;

/**
 * What the module of a use case asks the AI foundation to run (TASK-125). It has checked its own rights and assembled the
 * sources with its own read rules; the foundation checks {@code ai.use}, availability and limits, runs the model and keeps
 * the validated answer as a suggestion for the person. {@code references} names the objects and versions the sources came
 * from, so the module can tell later whether the suggestion still matches them; it is stored as JSON and never sent.
 *
 * @param language {@code de} or {@code en}: the skill and the frame in that language
 * @param goal an optional short goal of the person, at most 500 characters
 */
public record AiRequest(AiUseCase useCase, String language, UUID projectId, String objectKind, UUID objectId, Long objectVersion,
        String goal, List<AiSource> sources, Object references) {
}
