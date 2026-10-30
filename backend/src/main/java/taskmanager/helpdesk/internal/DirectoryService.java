package taskmanager.helpdesk.internal;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import tools.jackson.databind.json.JsonMapper;

/**
 * Customer organizations and their contacts (TASK-057). Everything here needs the company right {@code helpdesk.manage},
 * checked first in the transaction. Nothing is deleted: a customer or contact stays for the tickets that name it.
 */
@Service
public class DirectoryService {
    static final int NAME_MAX = 120;
    static final int ADDRESSES_MAX = 20;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** A dot-atom, the {@code @} and host name labels, in lower case (TASK-062, X-F3). */
    private static final Pattern PLAIN_ADDRESS = Pattern.compile(
        "[a-z0-9!#$%&'*+/=?^_`{|}~-]{1,64}(\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]{1,64})*"
            + "@[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*");

    private final HelpdeskRepository repository;
    private final HelpdeskAccess access;
    private final ProjectAccess projects;
    private final Clock clock;

    DirectoryService(HelpdeskRepository repository, HelpdeskAccess access, ProjectAccess projects, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.projects = projects;
        this.clock = clock;
    }

    @Transactional
    public List<Customer> customers() {
        return repository.customers(access.requireManage());
    }

    @Transactional
    public Customer createCustomer(UUID actor, String rawName, List<UUID> rawProjects) {
        UUID companyId = access.requireManage();
        String name = name(rawName);
        List<UUID> released = projects(companyId, rawProjects);
        if (repository.customerNameTaken(companyId, name, null)) {
            throw ApiProblemException.of(409, "customer.name_taken");
        }
        Instant now = clock.instant();
        Customer customer = new Customer(UUID.randomUUID(), companyId, name, released, 1, now, actor, now, actor);
        repository.insertCustomer(customer);
        repository.audit(companyId, "customer", customer.id(), "customer.created", describe(customer), actor, now);
        return customer;
    }

    @Transactional
    public Customer updateCustomer(UUID actor, UUID id, long expectedVersion, String rawName, List<UUID> rawProjects) {
        UUID companyId = access.requireManage();
        Customer current = repository.lockCustomer(companyId, id).orElseThrow(() -> ApiProblemException.of(404, "customer.not_found"));
        if (current.version() != expectedVersion) {
            throw ApiProblemException.of(412, "customer.version_conflict");
        }
        String name = name(rawName);
        List<UUID> released = projects(companyId, rawProjects);
        if (repository.customerNameTaken(companyId, name, id)) {
            throw ApiProblemException.of(409, "customer.name_taken");
        }
        if (current.name().equals(name) && Set.copyOf(current.projects()).equals(Set.copyOf(released))) {
            return current;
        }
        Instant now = clock.instant();
        Customer changed = new Customer(id, companyId, name, released, current.version() + 1, current.createdAt(), current.createdBy(), now, actor);
        repository.updateCustomer(changed);
        repository.audit(companyId, "customer", id, "customer.changed", describe(changed), actor, now);
        return repository.customer(companyId, id).orElseThrow();
    }

    @Transactional
    public List<Contact> contacts(UUID customerId) {
        return repository.contacts(access.requireManage(), customerId);
    }

    @Transactional
    public Contact createContact(UUID actor, UUID customerId, String rawName, List<String> rawAddresses) {
        UUID companyId = access.requireManage();
        List<String> violations = new ArrayList<>();
        String name = nameOrViolation(rawName, violations);
        List<String> addresses = addresses(rawAddresses, violations);
        if (customerId == null || repository.customer(companyId, customerId).isEmpty()) {
            violations.add("customerId.unknown");
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        if (!repository.addressesTaken(companyId, addresses, null).isEmpty()) {
            throw ApiProblemException.of(409, "contact.address_taken");
        }
        Instant now = clock.instant();
        Contact contact = new Contact(UUID.randomUUID(), companyId, customerId, name, addresses, 1, now, actor, now, actor);
        repository.insertContact(contact);
        repository.audit(companyId, "contact", contact.id(), "contact.created", describe(contact), actor, now);
        return repository.contact(companyId, contact.id()).orElseThrow();
    }

    @Transactional
    public Contact updateContact(UUID actor, UUID id, long expectedVersion, UUID customerId, String rawName, List<String> rawAddresses) {
        UUID companyId = access.requireManage();
        Contact current = repository.lockContact(companyId, id).orElseThrow(() -> ApiProblemException.of(404, "contact.not_found"));
        if (current.version() != expectedVersion) {
            throw ApiProblemException.of(412, "contact.version_conflict");
        }
        List<String> violations = new ArrayList<>();
        String name = nameOrViolation(rawName, violations);
        List<String> addresses = addresses(rawAddresses, violations);
        if (customerId == null || repository.customer(companyId, customerId).isEmpty()) {
            violations.add("customerId.unknown");
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        if (!repository.addressesTaken(companyId, addresses, id).isEmpty()) {
            throw ApiProblemException.of(409, "contact.address_taken");
        }
        if (current.name().equals(name) && current.customerId().equals(customerId) && Set.copyOf(current.addresses()).equals(Set.copyOf(addresses))) {
            return current;
        }
        Instant now = clock.instant();
        Contact changed = new Contact(id, companyId, customerId, name, addresses, current.version() + 1, current.createdAt(),
            current.createdBy(), now, actor);
        repository.updateContact(changed);
        repository.audit(companyId, "contact", id, "contact.changed", describe(changed), actor, now);
        return repository.contact(companyId, id).orElseThrow();
    }

    // ------------------------------------------------------------------------------------------------ rules

    private static String name(String raw) {
        List<String> violations = new ArrayList<>();
        String name = nameOrViolation(raw, violations);
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        return name;
    }

    private static String nameOrViolation(String raw, List<String> violations) {
        String name = ParsedMail.clean(raw);
        if (name == null || name.contains("\n") || name.codePointCount(0, name.length()) > NAME_MAX) {
            violations.add("name.invalid");
            return null;
        }
        return name;
    }

    /** The projects as given, each existing in the company, without duplicates. */
    private List<UUID> projects(UUID companyId, List<UUID> raw) {
        List<UUID> released = raw == null ? List.of() : List.copyOf(new LinkedHashSet<>(raw));
        for (UUID project : released) {
            if (project == null || projects.key(project).isEmpty()) {
                throw ApiProblemException.withViolations(400, "request.invalid", List.of("projects.unknown"));
            }
        }
        return released;
    }

    /** Valid single addresses in lower case, without duplicates, at least one and at most {@value #ADDRESSES_MAX}. */
    static List<String> addresses(List<String> raw, List<String> violations) {
        Set<String> result = new LinkedHashSet<>();
        if (raw == null || raw.isEmpty() || raw.size() > ADDRESSES_MAX) {
            violations.add("addresses.invalid");
            return List.of();
        }
        for (String value : raw) {
            String address = normalizedAddress(value);
            if (address == null) {
                violations.add("addresses.invalid");
                return List.of();
            }
            result.add(address);
        }
        return List.copyOf(result);
    }

    /**
     * One plain address such as {@code lea@nordwerk.example} in lower case, or {@code null}. Only a dot-atom before and host
     * names after the {@code @} are accepted: no comment, no quoted part, no group, no name and no control character, so the
     * value can stand alone in a header and as a recipient of SMTP (TASK-062, X-F3).
     */
    static String normalizedAddress(String value) {
        if (value == null) {
            return null;
        }
        String address = value.strip().toLowerCase(Locale.ROOT);
        if (address.length() < 3 || address.length() > 254 || !PLAIN_ADDRESS.matcher(address).matches()) {
            return null;
        }
        try {
            InternetAddress parsed = new InternetAddress(address, true);
            parsed.validate();
            return address.equals(parsed.getAddress()) && !parsed.isGroup() ? address : null;
        } catch (AddressException invalid) {
            return null;
        }
    }


    private static String describe(Customer customer) {
        return JSON.writeValueAsString(new CustomerDescription(customer.name(), customer.projects()));
    }

    private static String describe(Contact contact) {
        return JSON.writeValueAsString(new ContactDescription(contact.name(), contact.customerId(), contact.addresses()));
    }

    private record CustomerDescription(String name, List<UUID> projects) {
    }

    private record ContactDescription(String name, UUID customerId, List<String> addresses) {
    }
}
