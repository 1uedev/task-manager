package taskmanager.identity.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import taskmanager.identity.AccountNames;

/** The port {@link AccountNames} on top of the account table (TASK-082). */
@Component
class AccountNamesAdapter implements AccountNames {
    private final AccountRepository accounts;

    AccountNamesAdapter(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public Optional<String> shownName(UUID accountId) {
        return accounts.findById(accountId).map(account -> account.displayName() == null || account.displayName().isBlank()
            ? account.username() : account.displayName());
    }
}
