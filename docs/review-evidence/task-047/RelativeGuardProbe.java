package taskmanager.identity;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import taskmanager.identity.account.*;

/** Two explicit legacy snapshots, not reachable removals claimed to pass the current guard. */
public class RelativeGuardProbe extends ProbeSupport {
    record Cli(int exit,String output){}
    static Cli cli(String command,String username)throws Exception{
        var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-jar","backend/target/task-manager-backend-0.0.1-SNAPSHOT.jar",command,username).redirectErrorStream(true).start();
        ok(child.waitFor(45,TimeUnit.SECONDS),"CLI finished");return new Cli(child.exitValue(),new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
    }
    public static void main(String[]args)throws Exception{
        var rows=new ArrayList<Map<String,Object>>();var d=directory();
        try(var ctx=app()){
            int port=Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));var admin=bootstrap(ctx,port);var jdbc=ctx.getBean(JdbcTemplate.class);var repo=ctx.getBean(AccountRepository.class);
            ok(admin.put("/api/v1/settings/ldap",settings("ldap://localhost:"+d.getListenPort(),"entryUUID")).status()==200,"LDAP configured");
            person(d,"legacy-ldap-admin","legacy-ldap-secret",null,null);var ldap=logged(port,"legacy-ldap-admin","legacy-ldap-secret");
            UUID ldapId=repo.findByUsername("legacy-ldap-admin").orElseThrow().id();grant(admin,ldapId,"Legacy LDAP administration","permissions.manage","accounts.manage");
            UUID root=repo.findByUsername("review-root").orElseThrow().id();UUID rootGroup=jdbc.queryForObject("select group_id from permissions_membership where user_id=?",UUID.class,root);
            // Snapshot 1: an inactive local permissions manager exists, without accounts.manage. Earlier versions allowed this.
            jdbc.update("delete from permissions_action where group_id=? and action_key='accounts.manage'",rootGroup);jdbc.update("update identity_account set active=false where id=?",root);
            var rescue=cli("reset-admin-password","review-root");var matcher=Pattern.compile("One time password \\(shown only now\\): (.+)").matcher(rescue.output());ok(rescue.exit()==0&&matcher.find(),"local emergency reset");String otp=matcher.group(1).trim();
            var restored=logged(port,"review-root",otp);ok(restored.post("/api/v1/session/password",Map.of("currentPassword",otp,"newPassword",PASSWORD)).status()==200,"local forced change");
            int before=restored.get("/api/v1/accounts").status();var grant=restored.put("/api/v1/permissions/groups/"+rootGroup+"/actions",Map.of("action","accounts.manage","enabled",true));
            record(rows,"recover-via-local-permissions-manager","cliExit",rescue.exit(),"accountsBeforeGrant",before,"grantStatus",grant.status(),"accountsAfterGrant",restored.get("/api/v1/accounts").status(),"localAdministrators",GuardProbes.localCount(jdbc));
            // Snapshot 2: no local account holds permissions.manage. Keep one active LDAP administrator only.
            jdbc.update("delete from permissions_membership where user_id=?",root);
            ok(ldap.put("/api/v1/settings/ldap",settings("ldap://localhost:1","entryUUID")).status()==200,"directory outage configured");
            var localRefused=cli("reset-admin-password","review-root");var directoryRefused=cli("reset-admin-password","legacy-ldap-admin");var bootstrap=cli("bootstrap-admin","rescue-new");
            record(rows,"legacy-without-any-local-permissions-manager","localAdministrators",GuardProbes.localCount(jdbc),"localResetExit",localRefused.exit(),"localResetNotAdministrator",localRefused.output().contains("Refused: account.not_administrator"),"directoryResetExit",directoryRefused.exit(),"directoryResetManaged",directoryRefused.output().contains("Refused: account.directory_managed"),"bootstrapExit",bootstrap.exit(),"bootstrapAccountsExist",bootstrap.output().contains("Refused: bootstrap.accounts_exist"));
            // An already open LDAP session works during the outage and can establish a local administration.
            var membership=ldap.put("/api/v1/permissions/groups/"+rootGroup+"/members/"+root,Map.of());
            record(rows,"recover-via-still-open-directory-session","membershipStatus",membership.status(),"localAdministrators",GuardProbes.localCount(jdbc));
        }finally{POOL.shutdownNow();d.shutDown(true);save(args[0],rows);}
    }
}
