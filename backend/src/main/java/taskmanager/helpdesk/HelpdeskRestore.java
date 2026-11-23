package taskmanager.helpdesk;

import java.time.Instant;

/**
 * What the helpdesk does after a restore from a backup point (TASK-131, DEC-161). A reply that waited or was being sent at the
 * backup point may have reached the customer after it; the restored job must not send it a second time.
 */
public interface HelpdeskRestore {
    /**
     * Sets every reply that waits or is being sent to uncertain with the code {@code helpdesk.restored}. Its job finds it no
     * longer pending and sends nothing; a person sends it again or records it as sent, as with every uncertain reply.
     * Returns how many.
     */
    int holdOpenReplies(Instant at);
}
