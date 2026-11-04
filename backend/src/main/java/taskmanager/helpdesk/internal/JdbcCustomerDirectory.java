package taskmanager.helpdesk.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import taskmanager.helpdesk.CustomerDirectory;

/** The customers of the helpdesk tables for other modules (TASK-068). */
@Component
class JdbcCustomerDirectory implements CustomerDirectory {
    private final HelpdeskRepository repository;

    JdbcCustomerDirectory(HelpdeskRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Summary> customers(UUID companyId) {
        return repository.customers(companyId).stream().map(JdbcCustomerDirectory::summary).toList();
    }

    @Override
    public Optional<Summary> customer(UUID companyId, UUID customerId) {
        return repository.customer(companyId, customerId).map(JdbcCustomerDirectory::summary);
    }

    @Override
    public Optional<UUID> customerOf(UUID ticketId) {
        return repository.ticketCustomer(ticketId).map(HelpdeskRepository.TicketCustomer::customerId);
    }

    @Override
    public List<ContactSummary> contacts(UUID companyId, UUID customerId) {
        return customerId == null ? List.of()
            : repository.contacts(companyId, customerId).stream().map(contact -> new ContactSummary(contact.id(), contact.name())).toList();
    }

    @Override
    public Optional<UUID> lockCustomerOfContact(UUID companyId, UUID contactId) {
        return contactId == null ? Optional.empty() : repository.shareContact(companyId, contactId).map(Contact::customerId);
    }

    private static Summary summary(Customer customer) {
        return new Summary(customer.id(), customer.name(), customer.projects());
    }
}
