package taskmanager.helpdesk.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.platform.ApiProblemException;
import taskmanager.projects.ProjectAccess;
import tools.jackson.databind.json.JsonMapper;

/**
 * The mailboxes of the intake (TASK-057). Everything here needs the company right {@code helpdesk.manage}. A password is
 * accepted only when the key of the operation is set; it is encrypted at once and never given back. A mailbox is never
 * deleted, because its mails refer to it; it is switched off instead.
 */
@Service
public class MailboxService {
    private static final Logger LOG = LoggerFactory.getLogger(MailboxService.class);
    private static final Set<String> SECURITY = Set.of("tls", "starttls", "none");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HelpdeskRepository repository;
    private final HelpdeskAccess access;
    private final ProjectAccess projects;
    private final SecretBox secrets;
    private final MailClient client;
    private final HelpdeskProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    MailboxService(HelpdeskRepository repository, HelpdeskAccess access, ProjectAccess projects, SecretBox secrets, MailClient client,
            HelpdeskProperties properties, Clock clock, PlatformTransactionManager transactions) {
        this.transaction = new TransactionTemplate(transactions);
        this.repository = repository;
        this.access = access;
        this.projects = projects;
        this.secrets = secrets;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * What an administrator sets. {@code password} {@code null} keeps the stored one, an empty text removes it; every
     * other value replaces it. {@code maxBytes} {@code null} takes the default of the operation.
     */
    public record Settings(String name, Boolean enabled, UUID projectId, String ticketType, String imapHost, Integer imapPort,
            String imapSecurity, String username, String password, String folder, String smtpHost, Integer smtpPort, String smtpSecurity,
            String address, String authservId, Long maxBytes) {
    }

    /** The outcome of a connection test: {@code null} code means it worked. */
    public record TestResult(boolean ok, String code, Long uidValidity) {
    }

    public boolean secretKeyAvailable() {
        return secrets.available();
    }

    @Transactional
    public List<Mailbox> list() {
        return repository.mailboxes(access.requireManage());
    }

    @Transactional
    public Mailbox create(UUID actor, Settings settings) {
        UUID companyId = access.requireManage();
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        Mailbox mailbox = validated(companyId, id, settings, null, 1, now, actor, now, actor);
        if (repository.mailboxNameTaken(companyId, mailbox.name(), null)) {
            throw ApiProblemException.of(409, "mailbox.name_taken");
        }
        repository.insertMailbox(mailbox);
        repository.audit(companyId, "mailbox", id, "mailbox.created", describe(mailbox), actor, now);
        return repository.mailbox(companyId, id).orElseThrow();
    }

    @Transactional
    public Mailbox update(UUID actor, UUID id, long expectedVersion, Settings settings) {
        UUID companyId = access.requireManage();
        Mailbox current = repository.lockMailbox(companyId, id).orElseThrow(() -> ApiProblemException.of(404, "mailbox.not_found"));
        if (current.version() != expectedVersion) {
            throw ApiProblemException.of(412, "mailbox.version_conflict");
        }
        Instant now = clock.instant();
        Mailbox changed = validated(companyId, id, settings, current, current.version() + 1, current.createdAt(), current.createdBy(), now, actor);
        if (repository.mailboxNameTaken(companyId, changed.name(), id)) {
            throw ApiProblemException.of(409, "mailbox.name_taken");
        }
        boolean restart = !changed.imapHost().equalsIgnoreCase(current.imapHost()) || changed.imapPort() != current.imapPort()
            || !changed.username().equals(current.username()) || !changed.folder().equals(current.folder());
        repository.updateMailbox(changed, restart);
        String operation = settings.password() == null ? "mailbox.changed" : settings.password().isEmpty() ? "mailbox.password_removed" : "mailbox.password_changed";
        repository.audit(companyId, "mailbox", id, operation, describe(changed), actor, now);
        if (restart) {
            repository.clearNotice(id);
        }
        return repository.mailbox(companyId, id).orElseThrow();
    }

    /** Opens the folder with the stored settings and reports the first failure; changes nothing. */
    public TestResult test(UUID id) {
        Mailbox mailbox = inTransaction(id);
        String password;
        try {
            password = mailbox.passwordCipher() == null ? "" : secrets.open(mailbox.id(), mailbox.passwordCipher());
        } catch (SecretBox.KeyMissing missing) {
            return new TestResult(false, "helpdesk.secret_key_missing", null);
        } catch (SecretBox.Unreadable unreadable) {
            return new TestResult(false, "helpdesk.password_unreadable", null);
        }
        try (MailClient.Connection connection = client.open(mailbox, password)) {
            return new TestResult(true, null, connection.uidValidity());
        } catch (MailClient.MailFailure failure) {
            LOG.info("The test of mailbox {} failed with {} ({})", id, failure.code(), MailClient.kinds(failure.getCause()));
            return new TestResult(false, failure.code(), null);
        }
    }

    private Mailbox inTransaction(UUID id) {
        return transaction.execute(status -> {
            UUID companyId = access.requireManage();
            return repository.mailbox(companyId, id).orElseThrow(() -> ApiProblemException.of(404, "mailbox.not_found"));
        });
    }

    // ------------------------------------------------------------------------------------------------ rules

    private Mailbox validated(UUID companyId, UUID id, Settings s, Mailbox current, long version, Instant createdAt, UUID createdBy,
            Instant now, UUID actor) {
        List<String> violations = new ArrayList<>();
        String name = ParsedMail.clean(s.name());
        if (name == null || name.contains("\n") || name.length() > DirectoryService.NAME_MAX) {
            violations.add("name.invalid");
        }
        String host = host(s.imapHost());
        if (host == null) {
            violations.add("imapHost.invalid");
        }
        if (s.imapPort() == null || s.imapPort() < 1 || s.imapPort() > 65535) {
            violations.add("imapPort.invalid");
        }
        if (s.imapSecurity() == null || !SECURITY.contains(s.imapSecurity())) {
            violations.add("imapSecurity.invalid");
        }
        String username = s.username() == null ? null : s.username().strip();
        if (username == null || username.isEmpty() || username.length() > 254) {
            violations.add("username.invalid");
        }
        String folder = s.folder() == null || s.folder().isBlank() ? "INBOX" : s.folder().strip();
        if (folder.length() > 200 || folder.chars().anyMatch(Character::isISOControl)) {
            violations.add("folder.invalid");
        }
        String smtpHost = s.smtpHost() == null || s.smtpHost().isBlank() ? null : host(s.smtpHost());
        if (s.smtpHost() != null && !s.smtpHost().isBlank() && smtpHost == null) {
            violations.add("smtpHost.invalid");
        }
        if (s.smtpPort() != null && (s.smtpPort() < 1 || s.smtpPort() > 65535)) {
            violations.add("smtpPort.invalid");
        }
        if (s.smtpSecurity() != null && !SECURITY.contains(s.smtpSecurity())) {
            violations.add("smtpSecurity.invalid");
        }
        String address = s.address() == null || s.address().isBlank() ? null : DirectoryService.normalizedAddress(s.address());
        if (s.address() != null && !s.address().isBlank() && address == null) {
            violations.add("address.invalid");
        }
        String authservId = s.authservId() == null || s.authservId().isBlank() ? null : s.authservId().strip();
        if (authservId != null && (authservId.length() > 253 || !authservId.matches("[A-Za-z0-9._-]+"))) {
            violations.add("authservId.invalid");
        }
        long maxBytes = s.maxBytes() == null ? properties.maxBytes() : s.maxBytes();
        if (maxBytes < 1 || maxBytes > properties.maxBytes()) {
            violations.add("maxBytes.invalid");
        }
        if (s.projectId() == null) {
            violations.add("projectId.unknown");
        } else if (!projects.lockForIntake(companyId, s.projectId())) {
            violations.add("projectId.unknown");
        } else {
            boolean typeKnown = s.ticketType() != null && projects.currentConfigurationVersion(s.projectId())
                .flatMap(v -> projects.configuration(s.projectId(), v)).map(c -> c.hasTicketType(s.ticketType())).orElse(false);
            if (!typeKnown) {
                violations.add("ticketType.unknown");
            }
        }
        if (!violations.isEmpty()) {
            throw ApiProblemException.withViolations(400, "request.invalid", violations);
        }
        byte[] cipher = current == null ? null : current.passwordCipher();
        if (s.password() != null) {
            if (s.password().isEmpty()) {
                cipher = null;
            } else if (s.password().length() > 1000) {
                throw ApiProblemException.withViolations(400, "request.invalid", List.of("password.invalid"));
            } else if (!secrets.available()) {
                throw ApiProblemException.of(409, "helpdesk.secret_key_missing");
            } else {
                cipher = secrets.seal(id, s.password());
            }
        }
        boolean enabled = s.enabled() == null || s.enabled();
        return new Mailbox(id, companyId, name, enabled, s.projectId(), s.ticketType(), host, s.imapPort(), s.imapSecurity(), username,
            cipher, folder, smtpHost, s.smtpPort(), s.smtpSecurity(), address, authservId, maxBytes,
            current == null ? null : current.uidValidity(), current == null ? 0 : current.lastUid(), current == null ? 1 : current.source(),
            current == null ? null : current.compareUntil(),
            current == null ? null : current.polledAt(), current == null ? null : current.succeededAt(),
            current == null ? null : current.errorCode(), current == null ? null : current.errorAt(),
            current == null ? null : current.notice(), current == null ? null : current.noticeAt(), version, createdAt, createdBy, now, actor);
    }

    /** A host name or address without scheme, path or port. */
    private static String host(String raw) {
        if (raw == null) {
            return null;
        }
        String host = raw.strip();
        return host.matches("[A-Za-z0-9.-]{1,253}|\\[[0-9A-Fa-f:.]{2,45}\\]") ? host : null;
    }

    private static String describe(Mailbox m) {
        return JSON.writeValueAsString(new Description(m.name(), m.enabled(), m.projectId(), m.ticketType(), m.imapHost(), m.imapPort(),
            m.imapSecurity(), m.username(), m.folder(), m.smtpHost(), m.smtpPort(), m.smtpSecurity(), m.address(), m.authservId(),
            m.maxBytes(), m.passwordSet()));
    }

    /** What the audit trail keeps of a mailbox: everything but the password, of which only its presence. */
    private record Description(String name, boolean enabled, UUID projectId, String ticketType, String imapHost, int imapPort,
            String imapSecurity, String username, String folder, String smtpHost, Integer smtpPort, String smtpSecurity, String address,
            String authservId, long maxBytes, boolean passwordSet) {
    }

}
