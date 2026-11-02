package taskmanager.identity.account;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import taskmanager.identity.PortalAccountDirectory;

/** The port {@link PortalAccountDirectory} on top of the account service (TASK-075). */
@Component
class PortalAccountAdapter implements PortalAccountDirectory {
    private final AccountService accounts;

    PortalAccountAdapter(AccountService accounts) {
        this.accounts = accounts;
    }

    static PortalAccount of(AccountRecord account) {
        return new PortalAccount(account.id(), account.username(), account.displayName(), account.active(), account.organizationId(),
            account.contactId());
    }

    @Override
    public List<PortalAccount> list() {
        return accounts.list().stream().filter(AccountRecord::portal).map(PortalAccountAdapter::of).toList();
    }

    @Override
    public Issued create(String actor, String username, String displayName, UUID organizationId, UUID contactId) {
        AccountService.Issued issued = accounts.createPortal(actor, username, displayName, organizationId, contactId);
        return new Issued(of(issued.account()), issued.oneTimePassword(), issued.oneTimeExpiresAt());
    }

    @Override
    public PortalAccount setContact(String actor, UUID accountId, UUID contactId, Consumer<PortalAccount> verify) {
        return of(accounts.setContact(actor, accountId, contactId, locked -> verify.accept(of(locked))));
    }
}
