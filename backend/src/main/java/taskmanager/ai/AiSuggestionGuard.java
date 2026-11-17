package taskmanager.ai;

import java.util.UUID;
import taskmanager.permissions.AccessContext;

/**
 * Lets the module of a use case decide whether a stored suggestion still matches its object and the rights of the person
 * (TASK-125, rule 12; prüffall A09). The foundation asks it every time the person reads the suggestion; {@code false} makes the
 * suggestion invalid, and it is no longer shown. Without a guard for its use case a business execution delivers no content at all, only {@code ai.source_unchecked}
 * (TASK-129, F8); trials of the administration have no business object and need none.
 */
public interface AiSuggestionGuard {
    AiUseCase useCase();

    boolean stillValid(AccessContext actor, UUID projectId, String objectKind, UUID objectId, Long objectVersion, String referencesJson);
}
