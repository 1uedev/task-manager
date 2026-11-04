package taskmanager.tags.internal;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.PermissionDenied;
import taskmanager.platform.ApiProblemException;
import taskmanager.tags.internal.TagService.BulkOutcome;

/**
 * Bulk change of tags at tickets (TASK-073, DEC-103): add or remove 1 to {@value #MAX_TAGS} existing tags at 1 to
 * {@value #MAX_TICKETS} tickets, also across projects. There is no transaction over all tickets: the tags are checked once,
 * then every ticket is checked and changed in a transaction of its own with the current rights, so a refusal at one
 * ticket leaves the others as they are and a change of a single ticket at the same time waits for or follows it. The
 * result counts changed, unchanged, not fitting and refused tickets and names a ticket only with the key and, for a refusal,
 * the code when the caller may still read it. Repeating a bulk change adds no assignment twice.
 */
@Service
public class TagBulk {
    public static final int MAX_TICKETS = 500;
    public static final int MAX_TAGS = 10;
    private static final Set<String> RETRYABLE_STATES = Set.of("55P03", "40P01", "40001");

    private final TagService tags;

    TagBulk(TagService tags) {
        this.tags = tags;
    }

    /** One ticket of the result; {@code key} and {@code code} are {@code null} for a ticket the caller may not read. */
    public record Item(UUID ticketId, String key, BulkOutcome outcome, String code) {
    }

    public record Result(int changed, int unchanged, int notMatching, int refused, List<Item> items) {
    }

    /** Runs the bulk change; the lists are free of duplicates and within their limits (the controller checks them). */
    public Result apply(AccessContext actor, List<UUID> tickets, List<UUID> tagIds, boolean add) {
        List<Tag> chosen = tags.bulkTags(actor, tagIds, add);
        int[] counts = new int[BulkOutcome.values().length];
        List<Item> items = new ArrayList<>();
        for (UUID ticket : tickets) {
            BulkOutcome outcome;
            String code = null;
            try {
                outcome = tags.bulkTicket(actor, ticket, chosen, add);
            } catch (ApiProblemException refused) {
                outcome = BulkOutcome.REFUSED;
                code = refused.code();
            } catch (PermissionDenied denied) {
                // The check right before the commit (TASK-043), above all the end of the session during a long bulk change:
                // this ticket is rolled back and counted as refused, the result still reaches the caller (TASK-086, Claude F2).
                outcome = BulkOutcome.REFUSED;
                code = "auth.forbidden";
            } catch (DataAccessException failure) {
                if (!retryable(failure)) {
                    throw failure;
                }
                outcome = BulkOutcome.REFUSED;
                code = "tag.busy";
            }
            counts[outcome.ordinal()]++;
            String key = readableKey(actor, ticket);
            items.add(new Item(ticket, key, outcome, key == null ? null : code));
        }
        return new Result(counts[BulkOutcome.CHANGED.ordinal()], counts[BulkOutcome.UNCHANGED.ordinal()],
            counts[BulkOutcome.NOT_MATCHING.ordinal()], counts[BulkOutcome.REFUSED.ordinal()], items);
    }

    /** The key when the caller may still read the ticket; a refusal or a busy lock only leaves the key out (Claude F2). */
    private String readableKey(AccessContext actor, UUID ticket) {
        try {
            return tags.readableKey(actor, ticket).orElse(null);
        } catch (ApiProblemException | PermissionDenied refused) {
            return null;
        } catch (DataAccessException failure) {
            if (!retryable(failure)) {
                throw failure;
            }
            return null;
        }
    }

    private static boolean retryable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PessimisticLockingFailureException
                    || (cause instanceof SQLException sql && RETRYABLE_STATES.contains(sql.getSQLState()))) {
                return true;
            }
        }
        return false;
    }
}
