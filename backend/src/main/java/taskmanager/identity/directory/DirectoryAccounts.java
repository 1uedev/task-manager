package taskmanager.identity.directory;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.identity.account.AccountRecord;
import taskmanager.identity.account.AccountRepository;
import taskmanager.identity.account.PasswordHasher;
import taskmanager.identity.directory.DirectoryClient.DirectoryUser;
import taskmanager.platform.ApiProblemException;

/**
 * Assigns a person the directory has authenticated to an account (TASK-046, DEC-075). The assignment is by the immutable
 * identifier of the directory entry and never by the login name: a person whose name equals that of a local account, or of an
 * account of another entry, is refused instead of taking it over. An account that does not exist yet is created without any
 * right and with a password hash of random data nobody knows, so no local password can ever match it.
 */
@Service
public class DirectoryAccounts {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accounts;
    private final PasswordHasher hasher;

    DirectoryAccounts(AccountRepository accounts, PasswordHasher hasher) {
        this.accounts = accounts;
        this.hasher = hasher;
    }

    /**
     * Finds the account of the directory entry and locks it, or creates it under the login name. Runs in the transaction of the
     * caller. A concurrent first login of the same person ends in a duplicate key failure of one of them; the caller repeats.
     *
     * @throws ApiProblemException 401 {@code auth.invalid_credentials} when the login name belongs to another account
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public AccountRecord assign(DirectoryUser person, String loginName, Instant now) {
        AccountRecord known = accounts.findByExternalId(person.externalId()).flatMap(a -> accounts.findByIdForUpdate(a.id())).orElse(null);
        if (known != null) {
            // The entry may have been given the login name of another account since; the name belongs to that account (TASK-048).
            if (accounts.findByUsername(loginName).filter(named -> !named.id().equals(known.id())).isPresent()) {
                throw ApiProblemException.of(401, "auth.invalid_credentials");
            }
            if (person.displayName() != null && !Objects.equals(person.displayName(), known.displayName())) {
                accounts.setDisplayName(known.id(), person.displayName(), now);
                return accounts.findById(known.id()).orElseThrow();
            }
            return known;
        }
        if (accounts.findByUsername(loginName).isPresent()) {
            throw ApiProblemException.of(401, "auth.invalid_credentials");
        }
        AccountRecord created = new AccountRecord(UUID.randomUUID(), loginName, person.displayName(), hasher.hash(unknownSecret()), false,
            null, null, true, now, now, AccountRecord.LDAP, person.externalId());
        accounts.insert(created);
        accounts.audit(loginName, "account.directory_created", created.id(), null, now);
        return created;
    }

    private static String unknownSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
