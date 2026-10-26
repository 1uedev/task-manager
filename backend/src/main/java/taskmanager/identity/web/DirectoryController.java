package taskmanager.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.identity.account.AccountRecord;
import taskmanager.identity.account.AccountService;
import taskmanager.platform.ApiProblemException;

/**
 * The names of the accounts of the company for every complete login (TASK-030, DEC-070): an interface needs them to show who
 * changed a ticket, who is assigned and who is a member, and to pick a member or an assignee. Only the identifier, the login
 * name, the display name and whether the account is active; nothing about passwords, sessions or rights. There are only
 * internal accounts yet; portal accounts will need their own rule.
 */
@RestController
@RequestMapping("/api/v1/directory")
class DirectoryController {
    private final AccountService accounts;

    DirectoryController(AccountService accounts) {
        this.accounts = accounts;
    }

    record Entry(UUID id, String username, String displayName, boolean active) {
        static Entry of(AccountRecord account) {
            return new Entry(account.id(), account.username(), account.displayName(), account.active());
        }
    }

    @GetMapping("/accounts")
    List<Entry> accounts(HttpServletRequest request) {
        if (AdministrationRequests.actor(request).view().level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        return accounts.list().stream().sorted(Comparator.comparing(AccountRecord::username)).map(Entry::of).toList();
    }
}
