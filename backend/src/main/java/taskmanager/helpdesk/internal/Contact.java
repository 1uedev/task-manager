package taskmanager.helpdesk.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A person of one customer organization with the mail addresses that identify them (TASK-057); no account. Mails to the contact
 * are written in its {@code language}, {@code de} or {@code en} (TASK-082).
 */
public record Contact(UUID id, UUID companyId, UUID customerId, String name, List<String> addresses, long version, Instant createdAt,
        UUID createdBy, Instant updatedAt, UUID updatedBy, String language) {
    /** The languages a contact can have; German is the default. */
    public static final List<String> LANGUAGES = List.of("de", "en");

    public Contact {
        addresses = List.copyOf(addresses);
    }
}
