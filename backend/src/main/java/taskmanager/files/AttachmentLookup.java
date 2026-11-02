package taskmanager.files;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Port through which the helpdesk reads attachments of a ticket for the conversation of the portal (TASK-076): which files a
 * customer post may carry and which files the posts of a request show. No right is checked; the caller decided that the ticket
 * may be read, and it shows only attachments visible to the customer.
 */
public interface AttachmentLookup {
    /** An attachment of the ticket as the conversation needs it. */
    record Summary(UUID id, String name, long size, Instant uploadedAt, UUID uploadedBy, boolean customerVisible, boolean removed,
            UUID organizationId) {
        /**
         * Whether the portal of the organization shows the attachment: visible to the customer, not removed and written for this
         * organization (TASK-079, DEC-109). After a change of the customer the files of the former customer stay with it.
         */
        public boolean visibleTo(UUID organization) {
            return customerVisible && !removed && organization != null && organization.equals(organizationId);
        }
    }

    /**
     * The attachments of the ticket among the given identifiers, oldest first; identifiers of other tickets are left out. With
     * {@code lock} the rows are held {@code FOR SHARE} until the commit, so their visibility cannot change before it.
     */
    List<Summary> find(UUID ticketId, Collection<UUID> ids, boolean lock);
}
