import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.TaskManagerApplication;
import taskmanager.permissions.*;

/** Independent probes, synthetic database only. The production identity port is replaced only in this CLI context. */
public class CoreProbes {
    static final UUID COMPANY = UUID.randomUUID(), A = UUID.randomUUID(), B = UUID.randomUUID(), E = UUID.randomUUID();
    static final UUID PROJECT = UUID.randomUUID(), ORG = UUID.randomUUID(), SESSION = UUID.randomUUID();
    static final MutableClock CLOCK = new MutableClock();
    static final Map<UUID, Instant> ENDS = new ConcurrentHashMap<>();
    static class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-22T20:00:00Z"));
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now.get(); }
        void advance(long seconds) { now.updateAndGet(t -> t.plusSeconds(seconds)); }
    }
    @Configuration(proxyBeanMethods = false)
    static class Fixture {
        @Bean @Primary Clock reviewClock() { return CLOCK; }
        @Bean(name = "identityPermissionsAdapter") IdentityAccess syntheticIdentity() {
            return new IdentityAccess() {
                public boolean isCurrent(AccessContext a) { return a != null && a.companyId().equals(COMPANY); }
                public Optional<Instant> currentUntil(AccessContext a) {
                    return isCurrent(a) ? Optional.of(ENDS.getOrDefault(a.userId(), Instant.MAX)).filter(end -> CLOCK.instant().isBefore(end)) : Optional.empty();
                }
                public boolean userExists(UUID c, UUID u) { return c.equals(COMPANY) && Set.of(A,B,E).contains(u); }
                public boolean userActive(UUID c, UUID u) { return userExists(c,u) && !u.equals(E); }
            };
        }
    }
    static void ok(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static boolean denied(Runnable work) { try { work.run(); return false; } catch (PermissionDenied expected) { return true; } }
    static void out(String json) { System.out.println("REVIEW " + json); }
    public static void main(String[] args) {
        var app = new SpringApplication(TaskManagerApplication.class, Fixture.class);
        try (var ctx = app.run("--spring.main.web-application-type=none", "--spring.main.allow-bean-definition-overriding=true",
                "--spring.main.banner-mode=off", "--logging.level.root=ERROR")) {
            var jdbc = ctx.getBean(JdbcTemplate.class);
            var permissions = ctx.getBean(Permissions.class);
            var admin = ctx.getBean(PermissionAdministration.class);
            var tx = new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));
            tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            Supplier<AccessContext> actorA = () -> new AccessContext(COMPANY,A,SESSION,permissions.currentVersion(COMPANY),false,false,null);
            Supplier<AccessContext> actorB = () -> new AccessContext(COMPANY,B,SESSION,permissions.currentVersion(COMPANY),false,false,null);
            Supplier<ResourceContext> resource = () -> new ResourceContext(COMPANY,PROJECT,ORG,true,"ticket");
            tx.executeWithoutResult(s -> ctx.getBean(CompanyAdministration.class).initialize(COMPANY,A,Set.of("permissions.audit.read","ticket.edit","ticket.read")));
            UUID companyGroup = admin.groups(actorA.get()).getFirst().id();
            tx.executeWithoutResult(s -> admin.setMember(actorA.get(),companyGroup,B,true));
            UUID target = tx.execute(s -> admin.createGroup(actorA.get(),"Core target").id());
            UUID delegates = tx.execute(s -> admin.createGroup(actorA.get(),"Core delegates").id());
            tx.executeWithoutResult(s -> admin.setAction(actorA.get(),delegates,resource,"permissions.manage",true));
            for (UUID who : List.of(A,E)) tx.executeWithoutResult(s -> admin.setMember(actorA.get(),delegates,who,true));
            for (var op : PermissionAdministration.Operation.values()) tx.executeWithoutResult(s -> admin.setField(actorA.get(),delegates,resource,"title",op,PermissionAdministration.Effect.ALLOW));

            ok("5s".equals(jdbc.queryForObject("show lock_timeout",String.class)),"real application pool default");
            for (String previous : List.of("0","1234ms","37s")) tx.executeWithoutResult(s -> {
                jdbc.execute("set local lock_timeout = '"+previous+"'");
                permissions.require(actorA.get(),() -> {
                    ok("5s".equals(jdbc.queryForObject("show lock_timeout",String.class)),"timeout during resource check"); return resource.get();
                },"ticket.edit",Set.of(),Set.of());
                ok(previous.equals(jdbc.queryForObject("show lock_timeout",String.class)),"restore after require");
                ok(!permissions.inspect(actorA.get(),resource,"ticket.unknown",Set.of()).allowed(),"denial is expected");
                ok(previous.equals(jdbc.queryForObject("show lock_timeout",String.class)),"restore after denied inspect");
            });
            ok("5s".equals(jdbc.queryForObject("show lock_timeout",String.class)),"no pool contamination");
            out("{\"case\":\"P5\",\"during\":\"5s\",\"restored\":[\"0\",\"1234ms\",\"37s\"],\"poolDefault\":\"5s\"}");

            var external = new AccessContext(COMPANY,E,SESSION,permissions.currentVersion(COMPANY),false,true,ORG);
            ok(permissions.inspect(external,resource,"permissions.manage",Set.of()).allowed(),"external fixture actually holds project management");
            ok(denied(() -> admin.projectGroups(external,resource)),"external read refused");
            ok(denied(() -> tx.executeWithoutResult(s -> admin.setAction(external,target,resource,"ticket.read",true))),"external action refused");
            ok(denied(() -> tx.executeWithoutResult(s -> admin.setField(external,target,resource,"title",PermissionAdministration.Operation.READ,PermissionAdministration.Effect.ALLOW))),"external field refused");
            out("{\"case\":\"P3\",\"externalHasManage\":true,\"projectGroupReadDenied\":true,\"actionDenied\":true,\"fieldDenied\":true}");

            long before = permissions.currentVersion(COMPANY);
            tx.executeWithoutResult(s -> admin.setMember(actorA.get(),companyGroup,B,true));
            tx.executeWithoutResult(s -> admin.setAction(actorA.get(),delegates,resource,"permissions.manage",true));
            tx.executeWithoutResult(s -> admin.setField(actorA.get(),target,resource,"absent",PermissionAdministration.Operation.READ,PermissionAdministration.Effect.REMOVE));
            ok(before==permissions.currentVersion(COMPANY),"no-op version unchanged");
            tx.executeWithoutResult(s -> admin.setField(actorA.get(),target,resource,"title",PermissionAdministration.Operation.READ,PermissionAdministration.Effect.ALLOW));
            long first = permissions.currentVersion(COMPANY);
            tx.executeWithoutResult(s -> admin.setField(actorA.get(),target,resource,"title",PermissionAdministration.Operation.READ,PermissionAdministration.Effect.ALLOW));
            ok(first==permissions.currentVersion(COMPANY),"same author no-op");
            tx.executeWithoutResult(s -> admin.setField(actorB.get(),target,resource,"title",PermissionAdministration.Operation.READ,PermissionAdministration.Effect.ALLOW));
            ok(first+1==permissions.currentVersion(COMPANY),"another author counts");
            ok("ALLOW".equals(admin.audit(actorB.get(),null,1).getFirst().previousEffect()),"takeover previous value");
            tx.executeWithoutResult(s -> admin.setField(actorA.get(),target,resource,"title",PermissionAdministration.Operation.READ,PermissionAdministration.Effect.ALLOW));
            tx.executeWithoutResult(s -> admin.setMember(actorB.get(),companyGroup,A,false));
            long roleBefore=permissions.currentVersion(COMPANY);
            tx.executeWithoutResult(s -> admin.setField(actorA.get(),target,resource,"title",PermissionAdministration.Operation.READ,PermissionAdministration.Effect.ALLOW));
            ok(roleBefore+1==permissions.currentVersion(COMPANY),"same author in delegate role counts");
            ok(Boolean.FALSE.equals(jdbc.queryForObject("select set_by_company from permissions_field where company_id=? and group_id=? and field_key='title'",Boolean.class,COMPANY,target)),"delegate provenance");
            tx.executeWithoutResult(s -> admin.setMember(actorB.get(),companyGroup,A,true));
            out("{\"case\":\"P6\",\"noOpsUnchanged\":true,\"sameAuthorUnchanged\":true,\"otherAuthorCounted\":true,\"roleChangeCounted\":true,\"previousEffect\":\"ALLOW\"}");

            jdbc.execute("create table review_effect (id varchar(40) primary key)");
            ENDS.put(A,CLOCK.instant().plusSeconds(5));
            ok(denied(() -> tx.executeWithoutResult(s -> {
                permissions.require(actorA.get(),resource,"ticket.edit",Set.of(),Set.of());
                jdbc.update("insert into review_effect values ('expired')"); CLOCK.advance(6);
            })),"expired commit refused");
            ok(jdbc.queryForObject("select count(*) from review_effect",Integer.class)==0,"whole change rolled back");
            ENDS.put(A,Instant.MAX);
            tx.executeWithoutResult(s -> { permissions.require(actorA.get(),resource,"ticket.edit",Set.of(),Set.of()); jdbc.update("insert into review_effect values ('after-rollback')"); });
            ok(jdbc.queryForObject("select count(*) from review_effect",Integer.class)==1,"deadline cleaned after rollback");
            ENDS.put(A,CLOCK.instant().plusSeconds(5));
            ok(denied(() -> tx.executeWithoutResult(s -> {
                permissions.require(actorA.get(),resource,"ticket.edit",Set.of(),Set.of());
                ENDS.put(A,CLOCK.instant().plusSeconds(100));
                permissions.require(actorA.get(),resource,"ticket.read",Set.of(),Set.of());
                jdbc.update("insert into review_effect values ('earliest')"); CLOCK.advance(6);
            })),"later check does not extend earlier deadline");
            ok(jdbc.queryForObject("select count(*) from review_effect",Integer.class)==1,"earliest deadline rollback");
            out("{\"case\":\"commit\",\"expiredRolledBack\":true,\"nextTransactionSucceeded\":true,\"earliestDeadlineKept\":true}");
        }
    }
}
