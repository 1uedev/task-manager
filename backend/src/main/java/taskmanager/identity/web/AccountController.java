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
import taskmanager.platform.ApiProblemException;

/** Administrative account management. The one time password appears only in the response that issues it. */
@RestController
@RequestMapping("/api/v1/accounts")
class AccountController {
    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    record CreateAccountRequest(String username, String displayName, Boolean administrator) {
    }

    record AccountBody(UUID id, String username, String displayName, boolean active, boolean administrator,
            boolean passwordChangeRequired, Instant createdAt) {
        static AccountBody of(AccountRecord account) {
            return new AccountBody(account.id(), account.username(), account.displayName(), account.active(),
                account.administrator(), account.passwordChangeRequired(), account.createdAt());
        }
    }

    record IssuedBody(AccountBody account, String oneTimePassword, Instant oneTimePasswordExpiresAt) {
        static IssuedBody of(Issued issued) {
            return new IssuedBody(AccountBody.of(issued.account()), issued.oneTimePassword(), issued.oneTimeExpiresAt());
        }
    }

    @GetMapping
    List<AccountBody> list(HttpServletRequest request) {
        AdministratorOnly.require(request);
        return accounts.list().stream().map(AccountBody::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    IssuedBody create(@RequestBody(required = false) CreateAccountRequest body, HttpServletRequest request) {
        var actor = AdministratorOnly.require(request);
        if (body == null || body.username() == null) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("username.required"));
        }
        return IssuedBody.of(accounts.create(actor.view().username(), body.username(), body.displayName(),
            Boolean.TRUE.equals(body.administrator())));
    }

    @PostMapping("/{id}/deactivate")
    AccountBody deactivate(@PathVariable UUID id, HttpServletRequest request) {
        var actor = AdministratorOnly.require(request);
        return AccountBody.of(accounts.deactivate(actor.view().username(), id));
    }

    @PostMapping("/{id}/reactivate")
    AccountBody reactivate(@PathVariable UUID id, HttpServletRequest request) {
        var actor = AdministratorOnly.require(request);
        return AccountBody.of(accounts.reactivate(actor.view().username(), id));
    }

    @PostMapping("/{id}/password-reset")
    IssuedBody resetPassword(@PathVariable UUID id, HttpServletRequest request) {
        var actor = AdministratorOnly.require(request);
        return IssuedBody.of(accounts.resetPassword(actor.view().username(), id));
    }
}
