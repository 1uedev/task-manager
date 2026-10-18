package taskmanager.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import taskmanager.identity.session.SessionDuration;

/** The fixed session duration in hours. A shortened value reaches existing sessions at their next request. */
@RestController
@RequestMapping("/api/v1/settings/session-duration")
class SettingsController {
    private final SessionDuration duration;

    SettingsController(SessionDuration duration) {
        this.duration = duration;
    }

    record DurationRequest(Integer hours) {
    }

    record DurationBody(int hours, int minimumHours, int maximumHours) {
    }

    @GetMapping
    DurationBody read(HttpServletRequest request) {
        AdministratorOnly.require(request);
        return body();
    }

    @PutMapping
    DurationBody change(@RequestBody(required = false) DurationRequest body, HttpServletRequest request) {
        var actor = AdministratorOnly.require(request);
        duration.change(actor.view().username(), body == null ? null : body.hours());
        return body();
    }

    private DurationBody body() {
        return new DurationBody(duration.hours(), SessionDuration.MIN_HOURS, SessionDuration.MAX_HOURS);
    }
}
