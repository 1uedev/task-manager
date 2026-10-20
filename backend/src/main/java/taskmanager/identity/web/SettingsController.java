package taskmanager.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.identity.session.SessionDuration;
import taskmanager.platform.AdministrationRight;

/**
 * The fixed session duration in hours; both endpoints need the right {@code session_duration.manage}. A shortened value
 * reaches existing sessions at their next request.
 */
@RestController
@RequestMapping("/api/v1/settings/session-duration")
class SettingsController {
    private final SessionDuration duration;
    private final AdministrationRequests administration;

    SettingsController(SessionDuration duration, AdministrationRequests administration) {
        this.duration = duration;
        this.administration = administration;
    }

    record DurationRequest(Integer hours) {
    }

    record DurationBody(int hours, int minimumHours, int maximumHours) {
    }

    @GetMapping
    DurationBody read() {
        return administration.run(AdministrationRight.SESSION_DURATION_MANAGE, this::body);
    }

    @PutMapping
    DurationBody change(@RequestBody(required = false) DurationRequest body, HttpServletRequest request) {
        var actor = AdministrationRequests.actor(request);
        // The change locks the setting row and the administrative serialization first and calls the check behind them, in
        // its own transaction; the snapshot keeps requests without the right away from both.
        administration.early(AdministrationRight.SESSION_DURATION_MANAGE);
        duration.change(actor.view().username(), body == null ? null : body.hours(),
            () -> administration.require(AdministrationRight.SESSION_DURATION_MANAGE));
        return body();
    }

    private DurationBody body() {
        return new DurationBody(duration.hours(), SessionDuration.MIN_HOURS, SessionDuration.MAX_HOURS);
    }
}
