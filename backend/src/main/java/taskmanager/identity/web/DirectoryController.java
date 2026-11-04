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
 * name, the display name, whether the account is active and whether it is a portal account; nothing about passwords, sessions
 * or rights. Portal accounts (TASK-075) are listed for the internal interface, which shows them as members and authors but never
 * offers them as assignees; a portal account itself is refused, because it would learn the accounts of other customers.
 */
@RestController
@RequestMapping("/api/v1/directory")
class DirectoryController {
    private final AccountService accounts;

    DirectoryController(AccountService accounts) {
        this.accounts = accounts;
    }

    record Entry(UUID id, String username, String displayName, boolean active, String kind) {
        static Entry of(AccountRecord account) {
            return new Entry(account.id(), account.username(), account.displayName(), account.active(), account.kind());
        }
    }

    @GetMapping("/accounts")
    List<Entry> accounts(HttpServletRequest request) {
        var view = AdministrationRequests.actor(request).view();
        if (view.level() != AuthenticationLevel.FULL) {
            throw ApiProblemException.of(403, "auth.password_change_required");
        }
        if (view.portal()) {
            throw ApiProblemException.of(403, "auth.forbidden");
        }
        return accounts.list().stream().sorted(Comparator.comparing(AccountRecord::username)).map(Entry::of).toList();
    }
}
