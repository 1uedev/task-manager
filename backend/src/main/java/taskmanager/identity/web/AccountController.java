package taskmanager.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import taskmanager.identity.account.AccountRecord;
import taskmanager.identity.account.AccountRepository.AuditRecord;
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
    private final Clock clock;

    AccountController(AccountService accounts, AdministrationRequests administration, Clock clock) {
        this.accounts = accounts;
        this.administration = administration;
        this.clock = clock;
    }

    private static final int AUDIT_DEFAULT = 50;
    private static final int AUDIT_MAX = 100;

    record CreateAccountRequest(String username, String displayName) {
    }

    record RenameRequest(String displayName) {
    }

    /**
     * An account as the administration sees it. {@code oneTimePasswordState} is {@code none}, {@code open}, {@code expired} or
     * {@code used}; {@code oneTimePasswordExpiresAt} is set while a one time password was issued and not replaced.
     * {@code loginDelayedUntil} is set while a waiting time after failed logins runs (TASK-045). {@code authKind} is {@code local} or
     * {@code ldap}; the password and the display name of an {@code ldap} account belong to the directory (TASK-046).
     */
    record AccountBody(UUID id, String username, String displayName, boolean active, boolean passwordChangeRequired,
            String oneTimePasswordState, Instant oneTimePasswordExpiresAt, Instant loginDelayedUntil, Instant createdAt, String authKind) {
        static AccountBody of(AccountRecord account, Instant now, Instant delayedUntil) {
            return new AccountBody(account.id(), account.username(), account.displayName(), account.active(),
                account.passwordChangeRequired(), account.oneTimeState(now).name().toLowerCase(java.util.Locale.ROOT),
                account.passwordChangeRequired() ? account.oneTimeExpiresAt() : null, delayedUntil, account.createdAt(), account.authKind());
        }
    }

    record IssuedBody(AccountBody account, String oneTimePassword, Instant oneTimePasswordExpiresAt) {
    }

    record AuditBody(long id, Instant occurredAt, String actor, String event, UUID accountId, String detail) {
        static AuditBody of(AuditRecord record) {
            return new AuditBody(record.id(), record.occurredAt(), record.actor(), record.event(), record.accountId(), record.detail());
        }
    }

    private AccountBody body(AccountRecord account) {
        return AccountBody.of(account, clock.instant(), accounts.loginDelays(List.of(account)).get(account.id()));
    }

    private IssuedBody issued(Issued issued) {
        return new IssuedBody(body(issued.account()), issued.oneTimePassword(), issued.oneTimeExpiresAt());
    }

    @GetMapping
    List<AccountBody> list() {
        return administration.run(AdministrationRight.ACCOUNTS_MANAGE, () -> {
            List<AccountRecord> list = accounts.list();
            Map<UUID, Instant> delays = accounts.loginDelays(list);
            Instant now = clock.instant();
            return list.stream().map(account -> AccountBody.of(account, now, delays.get(account.id()))).toList();
        });
    }

    /** The audit trail of the account administration, newest first; the next page starts below the smallest identifier of the last. */
    @GetMapping("/audit")
    List<AuditBody> audit(@RequestParam(required = false) Long before, @RequestParam(required = false) Integer limit) {
        int size = limit == null ? AUDIT_DEFAULT : limit;
        if (size < 1 || size > AUDIT_MAX || (before != null && before < 1)) {
            throw ApiProblemException.withViolations(400, "request.invalid", List.of("paging.invalid"));
        }
        return administration.run(AdministrationRight.ACCOUNTS_MANAGE, () -> accounts.audit(before, size).stream().map(AuditBody::of).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    IssuedBody create(@RequestBody(required = false) CreateAccountRequest body, HttpServletRequest request) {
        var actor = AdministrationRequests.actor(request);
        return administration.run(AdministrationRight.ACCOUNTS_MANAGE, () -> {
            if (body == null || body.username() == null) {
                throw ApiProblemException.withViolations(400, "request.invalid", List.of("username.required"));
            }
            return issued(accounts.create(actor.view().username(), body.username(), body.displayName()));
        });
    }

    /** Changes the display name; an empty one removes it. */
    @PutMapping("/{id}")
    AccountBody rename(@PathVariable UUID id, @RequestBody(required = false) RenameRequest body, HttpServletRequest request) {
        var actor = AdministrationRequests.actor(request);
        administration.early(AdministrationRight.ACCOUNTS_MANAGE);
        return body(accounts.rename(actor.view().username(), id, body == null ? null : body.displayName(),
            () -> administration.require(AdministrationRight.ACCOUNTS_MANAGE)));
    }

    /** Ends the waiting time after failed logins of the account. */
    @PostMapping("/{id}/clear-login-delay")
    AccountBody clearLoginDelay(@PathVariable UUID id, HttpServletRequest request) {
        var actor = AdministrationRequests.actor(request);
        administration.early(AdministrationRight.ACCOUNTS_MANAGE);
        return body(accounts.clearLoginDelay(actor.view().username(), id, () -> administration.require(AdministrationRight.ACCOUNTS_MANAGE)));
    }

    @PostMapping("/{id}/deactivate")
    AccountBody deactivate(@PathVariable UUID id, HttpServletRequest request) {
        var actor = AdministrationRequests.actor(request);
        // The deactivation takes its rights lock itself, ahead of the check; the snapshot keeps others from reaching it.
        administration.early(AdministrationRight.ACCOUNTS_MANAGE);
        return body(accounts.deactivate(actor.view().username(), id,
            () -> administration.require(AdministrationRight.ACCOUNTS_MANAGE)));
    }

    @PostMapping("/{id}/reactivate")
    AccountBody reactivate(@PathVariable UUID id, HttpServletRequest request) {
        var actor = AdministrationRequests.actor(request);
        // The snapshot keeps requests without the right away from the serialization the reactivation takes first.
        administration.early(AdministrationRight.ACCOUNTS_MANAGE);
        return body(accounts.reactivate(actor.view().username(), id,
            () -> administration.require(AdministrationRight.ACCOUNTS_MANAGE)));
    }

    /** Turns a directory account into a local one and issues its one time password (TASK-047). */
    @PostMapping("/{id}/convert-to-local")
    IssuedBody convertToLocal(@PathVariable UUID id, HttpServletRequest request) {
        var actor = AdministrationRequests.actor(request);
        administration.early(AdministrationRight.ACCOUNTS_MANAGE);
        return issued(accounts.convertToLocal(actor.view().username(), id,
            () -> administration.require(AdministrationRight.ACCOUNTS_MANAGE)));
    }

    @PostMapping("/{id}/password-reset")
    IssuedBody resetPassword(@PathVariable UUID id, HttpServletRequest request) {
        var actor = AdministrationRequests.actor(request);
        administration.early(AdministrationRight.ACCOUNTS_MANAGE);
        return issued(accounts.resetPassword(actor.view().username(), id,
            () -> administration.require(AdministrationRight.ACCOUNTS_MANAGE)));
    }
}
