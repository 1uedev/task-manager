package taskmanager.identity;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real populated Flyway V0021 -> V0022 upgrade, CLI recovery and post-upgrade HTTP identity probes. */
public class UpgradeProbe extends ProbeSupport {
    public static void main(String[]args)throws Exception{
        var rows=new ArrayList<Map<String,Object>>();String url=System.getenv("TASK_MANAGER_DB_URL"),user=System.getenv("TASK_MANAGER_DB_USER"),password=System.getenv("TASK_MANAGER_DB_PASSWORD");
        Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").target("21").load().migrate();
        var values=new LinkedHashMap<String,String>();values.put("legacy-bad","bad\ufffdkey");values.put("legacy-control","ab\u0001cd");values.put("legacy-c1","ab\u0085cd");values.put("legacy-del","ab\u007fcd");values.put("legacy-clean","entry-uuid-48");values.put("legacy-prefix","hex:80");values.put("legacy-trim","trim-id");values.put("legacy-emoji","uuid-😀");values.put("legacy-format","id\u200b");
        var ids=new LinkedHashMap<String,UUID>();UUID company=UUID.randomUUID(),group=UUID.randomUUID();
        try(var c=DriverManager.getConnection(url,user,password)){
            for(var e:values.entrySet()){
                UUID id=UUID.randomUUID();ids.put(e.getKey(),id);
                try(var p=c.prepareStatement("insert into identity_account(id,username,password_hash,password_change_required,active,created_at,updated_at,auth_kind,external_id) values (?,?,'synthetic-unknown-legacy-hash',false,true,'2026-10-01T00:00:00Z','2026-10-01T00:00:00Z','ldap',?)")){p.setObject(1,id);p.setString(2,e.getKey());p.setString(3,e.getValue());p.executeUpdate();}
                try(var p=c.prepareStatement("insert into identity_session(id,account_id,token_hash,csrf_hash,started_at,expires_at,restricted,created_at) values (?,?,?,?,now(),now()+interval '1 day',false,now())")){p.setObject(1,UUID.randomUUID());p.setObject(2,id);p.setBytes(3,MessageDigest.getInstance("SHA-256").digest(e.getKey().getBytes(StandardCharsets.UTF_8)));p.setBytes(4,new byte[32]);p.executeUpdate();}
            }
            // Snapshot of a legacy installation with only a directory administration; no local one exists before the migration.
            try(var p=c.prepareStatement("insert into platform_company(id) values (?)")){p.setObject(1,company);p.executeUpdate();}
            try(var p=c.prepareStatement("insert into permissions_revision(company_id,revision) values (?,10)")){p.setObject(1,company);p.executeUpdate();}
            try(var p=c.prepareStatement("insert into permissions_group(company_id,id,name) values (?,?,'Legacy rescue')")){p.setObject(1,company);p.setObject(2,group);p.executeUpdate();}
            for(String right:List.of("permissions.manage","accounts.manage"))try(var p=c.prepareStatement("insert into permissions_action(company_id,group_id,action_key) values (?,?,?)")){p.setObject(1,company);p.setObject(2,group);p.setString(3,right);p.executeUpdate();}
            try(var p=c.prepareStatement("insert into permissions_membership(company_id,group_id,user_id) values (?,?,?)")){p.setObject(1,company);p.setObject(2,group);p.setObject(3,ids.get("legacy-bad"));p.executeUpdate();}
        }
        var migration=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").load();var first=migration.migrate();var second=migration.migrate();
        record(rows,"flyway-upgrade","firstMigrations",first.migrationsExecuted,"secondMigrations",second.migrationsExecuted);
        try(var ctx=app()){
            int port=Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));var jdbc=ctx.getBean(JdbcTemplate.class);
            var after=jdbc.queryForList("select username,auth_kind,external_id,active,updated_at::text from identity_account order by username");
            record(rows,"migration-selection","accounts",after,"revokedSessions",jdbc.queryForList("select a.username,s.revoked_at is not null as revoked,s.revoke_reason from identity_session s join identity_account a on a.id=s.account_id order by a.username"),"auditEvents",jdbc.queryForList("select event,actor,detail from identity_audit_event order by id"),"localAdministratorsAfter",localCount(jdbc));
            String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
            var child=new ProcessBuilder(java,"-jar",Path.of("backend/target/task-manager-backend-0.0.1-SNAPSHOT.jar").toAbsolutePath().toString(),"reset-admin-password","legacy-bad").redirectErrorStream(true).start();
            if(!child.waitFor(45,TimeUnit.SECONDS)){child.destroyForcibly();throw new AssertionError("own CLI recovery timed out");}
            String output=new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8);var match=Pattern.compile("One time password \\(shown only now\\): (.+)").matcher(output);ok(child.exitValue()==0&&match.find(),"real CLI recovery succeeded");String otp=match.group(1).trim();
            var admin=logged(port,"legacy-bad",otp);ok(admin.post("/api/v1/session/password",Map.of("currentPassword",otp,"newPassword",PASSWORD)).status()==200,"recovered local forced change");
            record(rows,"relative-guard-recovery","cliExit",child.exitValue(),"localAdministratorsAfterRecovery",localCount(jdbc),"accountsReadable",admin.get("/api/v1/accounts").status(),"lastLocalDeactivation",admin.post("/api/v1/accounts/"+ids.get("legacy-bad")+"/deactivate",Map.of()).status());
            var reset=admin.post("/api/v1/accounts/"+ids.get("legacy-control")+"/password-reset",Map.of());ok(reset.status()==200,"migrated local reset");ok(admin.post("/api/v1/accounts/"+ids.get("legacy-control")+"/reactivate",Map.of()).status()==200,"migrated local reactivation");
            var recovered=logged(port,"legacy-control",reset.json().get("oneTimePassword").asString());var changed=recovered.post("/api/v1/session/password",Map.of("currentPassword",reset.json().get("oneTimePassword").asString(),"newPassword",PASSWORD));
            record(rows,"migrated-account-recovery","passwordChangeStatus",changed.status(),"authKind",changed.json().get("authKind"),"memberships",jdbc.queryForObject("select count(*) from permissions_membership where user_id=?",Integer.class,ids.get("legacy-control")));

            var d=directory();try{
                person(d,"prefix-new","prefix-new-secret","employeeNumber",new byte[]{(byte)0x80});person(d,"trim-owner","trim-owner-secret","employeeNumber"," trim-id ".getBytes(StandardCharsets.UTF_8));person(d,"trim-new","trim-new-secret","employeeNumber","trim-id".getBytes(StandardCharsets.UTF_8));
                ok(admin.put("/api/v1/settings/ldap",settings("ldap://localhost:"+d.getListenPort(),"employeeNumber")).status()==200,"legacy id setting");
                grant(admin,ids.get("legacy-prefix"),"Legacy prefix authority","accounts.manage");grant(admin,ids.get("legacy-trim"),"Legacy trimmed authority","accounts.manage");
                var pref=logged(port,"prefix-new","prefix-new-secret");record(rows,"legacy-prefix-collision","returnedUsername",pref.get("/api/v1/session").json().get("username"),"accountsStatus",pref.get("/api/v1/accounts").status(),"newAccountExists",jdbc.queryForObject("select count(*) from identity_account where username='prefix-new'",Integer.class));
                // The original owner has the original account name; its raw padded id used to be trimmed by PR #155.
                d.modifyDN("uid=trim-owner,"+PEOPLE,"uid=legacy-trim",true);var original=login(port,"legacy-trim","trim-owner-secret");
                var trim=logged(port,"trim-new","trim-new-secret");record(rows,"legacy-trim-collision","originalOwnerStatus",original.status(),"returnedUsername",trim.get("/api/v1/session").json().get("username"),"accountsStatus",trim.get("/api/v1/accounts").status());
            }finally{d.shutDown(true);}
        }finally{POOL.shutdownNow();save(args[0],rows);}
    }
    static int localCount(JdbcTemplate jdbc){return jdbc.queryForObject("select count(*) from identity_account u where u.active and u.auth_kind='local' and exists(select 1 from permissions_membership m join permissions_action a on a.company_id=m.company_id and a.group_id=m.group_id where m.user_id=u.id and a.project_id is null and a.action_key='permissions.manage') and exists(select 1 from permissions_membership m join permissions_action a on a.company_id=m.company_id and a.group_id=m.group_id where m.user_id=u.id and a.project_id is null and a.action_key='accounts.manage')",Integer.class);}
}
