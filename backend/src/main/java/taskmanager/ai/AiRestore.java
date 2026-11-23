package taskmanager.ai;

/** What the AI module does after a restore from a backup point (TASK-131, DEC-161). */
public interface AiRestore {
    /** Deletes every suggestion that is kept; none of them may be taken over after a restore. Returns how many. */
    int discardSuggestions();
}
