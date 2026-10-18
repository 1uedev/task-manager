package taskmanager.identity.web;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import taskmanager.identity.AccessContext;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.session.SessionService.Resolved;

@Component
class RequestAccessContexts implements AccessContexts {
    @Override
    public Optional<AccessContext> current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null || !(attributes.getAttribute(AuthenticationFilter.RESOLVED, RequestAttributes.SCOPE_REQUEST) instanceof Resolved resolved)) {
            return Optional.empty();
        }
        var view = resolved.view();
        return Optional.of(new AccessContext(view.accountId(), view.username(), resolved.sessionId(), view.level(),
            view.administrator(), view.expiresAt()));
    }
}
