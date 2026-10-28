package taskmanager.helpdesk.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A person of one customer organization with the mail addresses that identify them (TASK-057); no account. */
public record Contact(UUID id, UUID companyId, UUID customerId, String name, List<String> addresses, long version, Instant createdAt,
        UUID createdBy, Instant updatedAt, UUID updatedBy) {
    public Contact {
        addresses = List.copyOf(addresses);
    }
}
