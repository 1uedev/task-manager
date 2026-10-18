package taskmanager.permissions.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taskmanager.permissions.AccessContext;
import taskmanager.permissions.ResourceContext;

class PolicyTest {
    private final UUID company = UUID.randomUUID(), project = UUID.randomUUID(), organization = UUID.randomUUID();
    private final AccessContext actor = new AccessContext(company, UUID.randomUUID(), UUID.randomUUID(), 0, false, false, null);
    private final ResourceContext resource = new ResourceContext(company, project, organization, true, "ticket");

    @Test
    void unionOfActionsStillNeedsAnAllowAndProjectMembership() {
        var policy = new Policy(List.of(new Policy.Action(null, "ticket.read"), new Policy.Action(project, "ticket.edit")), List.of());
        assertTrue(policy.decide(actor, resource, "ticket.read", Set.of()).allowed());
        assertTrue(policy.decide(actor, resource, "ticket.edit", Set.of()).allowed());
        assertFalse(policy.decide(actor, resource, "ticket.delete", Set.of()).allowed());
        assertFalse(policy.decide(actor, new ResourceContext(company, project, organization, false, "ticket"), "ticket.read", Set.of()).allowed());
        assertFalse(policy.decide(actor, new ResourceContext(UUID.randomUUID(), project, organization, true, "ticket"), "ticket.read", Set.of()).allowed());
    }

    @Test
    void explicitFieldDenyWinsInEitherRuleOrderAndWriteRequiresRead() {
        var allow = new Policy.Field(null, "ticket", "secret", "READ", "ALLOW");
        var deny = new Policy.Field(project, "ticket", "secret", "READ", "DENY");
        var write = new Policy.Field(project, "ticket", "secret", "WRITE", "ALLOW");
        for (var rules : List.of(List.of(allow, deny, write), List.of(deny, write, allow), List.of(write))) {
            var decision = new Policy(List.of(new Policy.Action(project, "ticket.edit")), rules)
                .decide(actor, resource, "ticket.edit", Set.of("secret", "absent"));
            assertTrue(decision.allowed());
            assertTrue(decision.readableFields().isEmpty());
            assertTrue(decision.writableFields().isEmpty());
        }
    }

    @Test
    void readAndWriteDeniesAreSeparateAndOtherProjectDoesNotMatch() {
        var policy = new Policy(List.of(new Policy.Action(project, "ticket.edit")), List.of(
            new Policy.Field(null, "ticket", "title", "READ", "ALLOW"),
            new Policy.Field(null, "ticket", "title", "WRITE", "ALLOW"),
            new Policy.Field(project, "ticket", "title", "WRITE", "DENY"),
            new Policy.Field(UUID.randomUUID(), "ticket", "title", "READ", "DENY")));
        var decision = policy.decide(actor, resource, "ticket.edit", Set.of("title"));
        assertEquals(Set.of("title"), decision.readableFields());
        assertTrue(decision.writableFields().isEmpty());
    }

    @Test
    void externalRequiresOwnOrganizationAndProjectEvenWithCompanyActionGrant() {
        var external = new AccessContext(company, actor.userId(), actor.sessionId(), 0, false, true, organization);
        var policy = new Policy(List.of(new Policy.Action(null, "ticket.read")), List.of());
        assertTrue(policy.decide(external, resource, "ticket.read", Set.of()).allowed());
        assertFalse(policy.decide(external, ResourceContext.company(company), "ticket.read", Set.of()).allowed());
        assertFalse(policy.decide(external, new ResourceContext(company, project, UUID.randomUUID(), true, "ticket"), "ticket.read", Set.of()).allowed());
    }
}
