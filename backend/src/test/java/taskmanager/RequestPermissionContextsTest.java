package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taskmanager.identity.AccessContext;
import taskmanager.identity.AccessContexts;
import taskmanager.identity.AuthenticationLevel;
import taskmanager.permissions.PermissionDenied;
import taskmanager.permissions.Permissions;
import taskmanager.platform.CompanyContext;

/** The builder keeps the promise of the port: no context without a complete login, a company and rights of that company. */
class RequestPermissionContextsTest {
    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();

    private static AccessContext request(AuthenticationLevel level) {
        return new AccessContext(ACCOUNT, "someone", SESSION, level, false, Instant.now());
    }

    private static RequestPermissionContexts builder(Optional<AccessContext> request, Optional<UUID> company, Permissions permissions) {
        AccessContexts identity = () -> request;
        CompanyContext companies = mock(CompanyContext.class);
        when(companies.find()).thenReturn(company);
        return new RequestPermissionContexts(identity, companies, permissions);
    }

    @Test
    void aCompleteLoginYieldsTheServerSideContext() {
        Permissions permissions = mock(Permissions.class);
        when(permissions.currentVersion(COMPANY)).thenReturn(7L);
        var context = builder(Optional.of(request(AuthenticationLevel.FULL)), Optional.of(COMPANY), permissions).current().orElseThrow();
        assertEquals(COMPANY, context.companyId());
        assertEquals(ACCOUNT, context.userId());
        assertEquals(SESSION, context.sessionId());
        assertEquals(7L, context.rightsVersion());
        assertFalse(context.passwordChangeRequired());
        assertFalse(context.external());
    }

    @Test
    void anIncompleteLoginNoCompanyNoRequestAndAnUnknownCompanyYieldNothing() {
        Permissions permissions = mock(Permissions.class);
        when(permissions.currentVersion(COMPANY)).thenReturn(1L);
        assertTrue(builder(Optional.of(request(AuthenticationLevel.PASSWORD_CHANGE_REQUIRED)), Optional.of(COMPANY), permissions).current().isEmpty(),
            "forced password change");
        assertTrue(builder(Optional.of(request(AuthenticationLevel.FULL)), Optional.empty(), permissions).current().isEmpty(), "no company");
        assertTrue(builder(Optional.empty(), Optional.of(COMPANY), permissions).current().isEmpty(), "no request");
        Permissions unknown = mock(Permissions.class);
        when(unknown.currentVersion(COMPANY)).thenThrow(new PermissionDenied());
        assertTrue(builder(Optional.of(request(AuthenticationLevel.FULL)), Optional.of(COMPANY), unknown).current().isEmpty(), "company without rights");
    }
}
