package taskmanager.helpdesk.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.tickets.TicketOrganizations;

/**
 * The customer of a ticket as its organization for the portal (TASK-075). A change of the customer writes the link row, so the
 * shared row lock makes it wait until the reading transaction ends; READ COMMITTED evaluates the condition again on a row that
 * changed while the lock was awaited.
 */
@Component
class JdbcTicketOrganizations implements TicketOrganizations {
    private final JdbcClient jdbc;

    JdbcTicketOrganizations(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> lockOrganization(UUID ticketId, UUID organizationId) {
        if (ticketId == null || organizationId == null) {
            return Optional.empty();
        }
        return jdbc.sql("select customer_id from helpdesk_ticket_customer where ticket_id = ? and customer_id = ? for share")
            .params(ticketId, organizationId).query((rs, row) -> rs.getObject(1, UUID.class)).optional();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> lockCustomer(UUID ticketId) {
        if (ticketId == null) {
            return Optional.empty();
        }
        return jdbc.sql("select customer_id from helpdesk_ticket_customer where ticket_id = ? for share")
            .param(ticketId).query((rs, row) -> rs.getObject(1, UUID.class)).optional().filter(java.util.Objects::nonNull);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> lockTicketsOf(UUID projectId, UUID organizationId) {
        if (projectId == null || organizationId == null) {
            return List.of();
        }
        return jdbc.sql("select ticket_id from helpdesk_ticket_customer where project_id = ? and customer_id = ? for share")
            .params(projectId, organizationId).query((rs, row) -> rs.getObject(1, UUID.class)).list();
    }
}
