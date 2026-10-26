package taskmanager.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.identity.directory.LdapSettings;
import taskmanager.identity.directory.LdapSettingsService;
import taskmanager.identity.directory.LdapSettingsService.Input;
import taskmanager.platform.AdministrationRight;

/**
 * The connection to the LDAP directory and its check (TASK-046, DEC-075); every endpoint needs the right {@code accounts.manage}.
 * The password of the search account is never part of a request or an answer; the answer only says whether the operation provides it.
 */
@RestController
@RequestMapping("/api/v1/settings/ldap")
class LdapSettingsController {
    private final LdapSettingsService settings;
    private final AdministrationRequests administration;

    LdapSettingsController(LdapSettingsService settings, AdministrationRequests administration) {
        this.settings = settings;
        this.administration = administration;
    }

    /** {@code configured} is false until a connection was stored; the other fields are then empty and {@code enabled} is false. */
    record SettingsBody(boolean configured, boolean enabled, String url, String security, String bindDn, String searchBase,
            String userFilter, String idAttribute, String nameAttribute, boolean bindPasswordSet, Instant updatedAt) {
    }

    /** The values to check instead of the stored ones, and a login name to look up (optional). Without values the stored ones are checked. */
    record TestRequest(Input settings, String username) {
    }

    /** {@code reason} names what failed, from a fixed vocabulary, and is null when the check succeeded. */
    record TestBody(boolean ok, String reason) {
    }

    @GetMapping
    SettingsBody read() {
        return administration.run(AdministrationRight.ACCOUNTS_MANAGE, this::body);
    }

    @PutMapping
    SettingsBody change(@RequestBody(required = false) Input body, HttpServletRequest request) {
        var actor = AdministrationRequests.actor(request);
        administration.early(AdministrationRight.ACCOUNTS_MANAGE);
        return administration.run(AdministrationRight.ACCOUNTS_MANAGE, () -> {
            settings.change(actor.view().username(), body);
            return body();
        });
    }

    /** Checks the connection without storing anything. It always answers 200; a failed check is the content of the answer. */
    @PostMapping("/test")
    TestBody test(@RequestBody(required = false) TestRequest body) {
        // The right is checked in its own transaction; the network calls of the check must not hold a database connection.
        administration.run(AdministrationRight.ACCOUNTS_MANAGE, () -> Boolean.TRUE);
        String reason = settings.check(body == null ? null : body.settings(), body == null ? null : body.username());
        return new TestBody(reason == null, reason);
    }

    private SettingsBody body() {
        boolean passwordSet = settings.bindPasswordSet();
        return settings.find()
            .map(s -> new SettingsBody(true, s.enabled(), s.url(), s.security(), s.bindDn(), s.searchBase(), s.userFilter(),
                s.idAttribute(), s.nameAttribute(), passwordSet, s.updatedAt()))
            .orElseGet(() -> new SettingsBody(false, false, null, null, null, null, null, null, null, passwordSet, null));
    }
}
