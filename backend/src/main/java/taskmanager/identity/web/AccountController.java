package taskmanager.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import taskmanager.identity.account.AccountRecord;
import taskmanager.identity.account.AccountService;
import taskmanager.identity.account.AccountService.Issued;
import taskmanager.platform.AdministrationRight;
import taskmanager.platform.ApiProblemException;

/**
 * Administrative account management; every endpoint needs the right {@code accounts.manage}. The one time password appears
 * only in the response that issues it.
 */
@RestController
@RequestMapping("/api/v1/accounts")
class AccountController {
    private final AccountService accounts;
    private final AdministrationRequests administration;

    AccountController(AccountService accounts, AdministrationRequests administration) {
        this.accounts = accounts;
        this.administration = administration;
    }

    record CreateAccountRequest(String username, String displayName) {
    }

    record AccountBody(UUID id, String username, String displayName, boolean active, boolean passwordChangeRequired,
            Instant createdAt) {
        static AccountBody of(AccountRecord account) {
            return new AccountBody(account.id(), account.username(), account.displayName(), account.active(),
                account.passwordChangeRequired(), account.createdAt());
        }
    }

    record IssuedBody(AccountBody account, String oneTimePassword, Instant oneTimePasswordExpiresAt) {
        static IssuedBody of(Issued issued) {
            return new IssuedBody(AccountBody.of(issued.account()), issued.oneTimePassword(), issued.oneTimeExpiresAt());
        }
    }

    @GetMapping
    List<AccountBody> list() {
        return administration.run(AdministrationRight.ACCOUNTS_MANAGE, () -> accounts.list().stream().map(AccountBody::of).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    IssuedBody create(@RequestBody(required = false) CreateAccountRequest body, HttpServletRequest request) {
        var actor = AdministrationRequests.actor(request);
        return administration.run(AdministrationRight.ACCOUNTS_MANAGE, () -> {
            if (body == null || body.username() == null) {
                throw ApiProblemException.withViolations(400, "request.invalid", List.of("username.required"));
            }
            return IssuedBody.of(accounts.create(actor.view().username(), body.username(), body.displayName()));
        });
    }

    @PostMapping("/{id}/deactivate")
    AccountBody deactivate(@PathVariable UUID id, HttpServletRequest request) {
        var actor = AdministrationRequests.actor(request);
        // The deactivation takes its rights lock itself, ahead of the check; the snapshot keeps others from reaching it.
        administration.early(AdministrationRight.ACCOUNTS_MANAGE);
        return AccountBody.of(accounts.deactivate(actor.view().username(), id,
            () -> administration.require(AdministrationRight.ACCOUNTS_MANAGE)));
    }

    @PostMapping("/{id}/reactivate")
    AccountBody reactivate(@PathVariable UUID id, HttpServletRequest request) {
        var actor = AdministrationRequests.actor(request);
        return administration.run(AdministrationRight.ACCOUNTS_MANAGE, () -> AccountBody.of(accounts.reactivate(actor.view().username(), id)));
    }

    @PostMapping("/{id}/password-reset")
    IssuedBody resetPassword(@PathVariable UUID id, HttpServletRequest request) {
        var actor = AdministrationRequests.actor(request);
        return administration.run(AdministrationRight.ACCOUNTS_MANAGE, () -> IssuedBody.of(accounts.resetPassword(actor.view().username(), id)));
    }
}
