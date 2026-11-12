package taskmanager.identity;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import taskmanager.identity.account.*;

/** Own PostgreSQL wait probes through real HTTP. Fixtures must be disposable. */
public class GuardProbes extends ProbeSupport {
    public static void main(String[]args)throws Exception{
        var rows=new ArrayList<Map<String,Object>>();var d=directory();String url="ldap://localhost:"+d.getListenPort();
        try(var ctx=app()){
            int port=Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));var admin=bootstrap(ctx,port);
            var jdbc=ctx.getBean(JdbcTemplate.class);var repo=ctx.getBean(AccountRepository.class);
            ok(admin.put("/api/v1/settings/ldap",settings(url,"entryUUID")).status()==200,"LDAP configured");
            // Stable LDAP actor, two independent local administrators. Rights changes are queued at the real revision lock.
            person(d,"ldap-manager","ldap-manager-secret",null,null);var ldapManager=logged(port,"ldap-manager","ldap-manager-secret");UUID ldapId=repo.findByUsername("ldap-manager").orElseThrow().id();grant(admin,ldapId,"LDAP administration","permissions.manage","accounts.manage");
            var peerIssued=admin.post("/api/v1/accounts",Map.of("username","local-peer"));ok(peerIssued.status()==201,"peer created");UUID peerId=UUID.fromString(peerIssued.json().get("account").get("id").asString());
            var peer=logged(port,"local-peer",peerIssued.json().get("oneTimePassword").asString());ok(peer.post("/api/v1/session/password",Map.of("currentPassword",peerIssued.json().get("oneTimePassword").asString(),"newPassword",PASSWORD)).status()==200,"peer change");
            UUID peerGroup=grant(admin,peerId,"Peer administration","permissions.manage","accounts.manage");UUID rootId=repo.findByUsername("review-root").orElseThrow().id();UUID rootGroup=jdbc.queryForObject("select group_id from permissions_membership where user_id=?",UUID.class,rootId);
            var actor2=logged(port,"ldap-manager","ldap-manager-secret");
            for(String operation:List.of("membership","action")){
                Future<ApiClient.Response> one,two;int waited;
                try(var held=new Held(ctx,()->jdbc.queryForList("select revision from permissions_revision for update"))){
                    one=POOL.submit(()->operation.equals("membership")?ldapManager.delete("/api/v1/permissions/groups/"+rootGroup+"/members/"+rootId):ldapManager.put("/api/v1/permissions/groups/"+rootGroup+"/actions",Map.of("action","accounts.manage","enabled",false)));
                    two=POOL.submit(()->operation.equals("membership")?actor2.delete("/api/v1/permissions/groups/"+peerGroup+"/members/"+peerId):actor2.put("/api/v1/permissions/groups/"+peerGroup+"/actions",Map.of("action","permissions.manage","enabled",false)));
                    waited=waitCount(jdbc,"select revision%for update%",2);
                }
                var r1=one.get(10,TimeUnit.SECONDS);var r2=two.get(10,TimeUnit.SECONDS);
                record(rows,"local-guard-race-"+operation,"observedRevisionWaiters",waited,"statuses",List.of(r1.status(),r2.status()),"codes",List.of(r1.status()==409?r1.code():"success",r2.status()==409?r2.code():"success"),"localAdministrators",localCount(jdbc));
                // Restore through the unchanged LDAP actor, never a direct privilege repair.
                ldapManager.put("/api/v1/permissions/groups/"+rootGroup+"/members/"+rootId,Map.of());ldapManager.put("/api/v1/permissions/groups/"+peerGroup+"/members/"+peerId,Map.of());
                ldapManager.put("/api/v1/permissions/groups/"+rootGroup+"/actions",Map.of("action","accounts.manage","enabled",true));ldapManager.put("/api/v1/permissions/groups/"+peerGroup+"/actions",Map.of("action","permissions.manage","enabled",true));
            }

            for(String operation:List.of("deactivation","mixed")){
                Future<ApiClient.Response> one,two;int waited;
                String lock=operation.equals("deactivation")?"select pg_advisory_xact_lock(7240001)":"select revision from permissions_revision for update";
                try(var held=new Held(ctx,()->jdbc.queryForList(lock))){
                    one=POOL.submit(()->ldapManager.post("/api/v1/accounts/"+rootId+"/deactivate",Map.of()));
                    two=POOL.submit(()->operation.equals("deactivation")?actor2.post("/api/v1/accounts/"+peerId+"/deactivate",Map.of()):actor2.delete("/api/v1/permissions/groups/"+peerGroup+"/members/"+peerId));
                    waited=waitCount(jdbc,operation.equals("deactivation")?"select pg_advisory_xact_lock%":"select revision%for %",2);
                }
                var r1=one.get(10,TimeUnit.SECONDS);var r2=two.get(10,TimeUnit.SECONDS);
                record(rows,"local-guard-race-"+operation,"observedWaiters",waited,"statuses",List.of(r1.status(),r2.status()),"codes",List.of(r1.status()==409?r1.code():"success",r2.status()==409?r2.code():"success"),"localAdministrators",localCount(jdbc));
                ldapManager.post("/api/v1/accounts/"+rootId+"/reactivate",Map.of());ldapManager.post("/api/v1/accounts/"+peerId+"/reactivate",Map.of());
                ldapManager.put("/api/v1/permissions/groups/"+peerGroup+"/members/"+peerId,Map.of());
                admin=logged(port,"review-root",PASSWORD);
            }
            // With only root left as local administration, LDAP administrators cannot remove the last local one.
            ok(ldapManager.delete("/api/v1/permissions/groups/"+peerGroup+"/members/"+peerId).status()==200,"remove spare local administration");
            record(rows,"last-local-guard","deactivateStatus",ldapManager.post("/api/v1/accounts/"+rootId+"/deactivate",Map.of()).status(),"membershipStatus",ldapManager.delete("/api/v1/permissions/groups/"+rootGroup+"/members/"+rootId).status(),"accountsRightStatus",ldapManager.put("/api/v1/permissions/groups/"+rootGroup+"/actions",Map.of("action","accounts.manage","enabled",false)).status(),"localAdministrators",localCount(jdbc));

            // Real HTTP conversion is paused by an AFTER UPDATE fixture trigger, before its commit; concurrent LDAP login sees the old external id and waits at the account.
            person(d,"convert-race","convert-race-secret",null,null);var oldSession=logged(port,"convert-race","convert-race-secret");UUID target=repo.findByUsername("convert-race").orElseThrow().id();grant(admin,target,"Keep conversion rights","appearance.manage");
            int membersBefore=jdbc.queryForObject("select count(*) from permissions_membership where user_id=?",Integer.class,target);
            jdbc.execute("create function review_pause_conversion() returns trigger language plpgsql as $$ begin perform pg_advisory_xact_lock(9480047); return new; end; $$");
            jdbc.execute("create trigger review_conversion_pause after update of auth_kind on identity_account for each row when (new.id='"+target+"'::uuid and old.auth_kind='ldap' and new.auth_kind='local') execute function review_pause_conversion()");
            var convertingAdmin=admin;Future<ApiClient.Response> conversion,concurrent;int conversionWait,loginWait;
            try(var held=new Held(ctx,()->jdbc.queryForList("select pg_advisory_xact_lock(9480047)"))){
                conversion=POOL.submit(()->convertingAdmin.post("/api/v1/accounts/"+target+"/convert-to-local",Map.of()));conversionWait=waitCount(jdbc,"update identity_account set auth_kind%",1);
                concurrent=POOL.submit(()->login(port,"convert-race","convert-race-secret"));loginWait=waitCount(jdbc,"%from identity_account where id =%for update%",1);
            }
            var converted=conversion.get(10,TimeUnit.SECONDS);var racedLogin=concurrent.get(10,TimeUnit.SECONDS);ok(converted.status()==200,"conversion completed");
            String otp=converted.json().get("oneTimePassword").asString();var rightful=login(port,"convert-race",otp);
            record(rows,"conversion-login-race","conversionWaitObserved",conversionWait,"loginWaitObserved",loginWait,"conversionStatus",converted.status(),"ldapLoginStatus",racedLogin.status(),"ldapLoginLevel",racedLogin.json().get("level"),"ldapLoginKind",racedLogin.json().get("authKind"),"issuedOtpLoginStatus",rightful.status(),"issuedOtpLoginCode",rightful.status()==200?"success":rightful.code(),"oldSessionAuthenticated",oldSession.get("/api/v1/session").json().get("authenticated"),"sameMembershipCount",membersBefore==jdbc.queryForObject("select count(*) from permissions_membership where user_id=?",Integer.class,target),"conversionAuditCount",jdbc.queryForObject("select count(*) from identity_audit_event where account_id=? and event='account.converted_to_local'",Integer.class,target));
            jdbc.execute("drop trigger review_conversion_pause on identity_account");jdbc.execute("drop function review_pause_conversion()");
            var localAgain=admin.post("/api/v1/accounts/"+target+"/convert-to-local",Map.of());record(rows,"already-local","status",localAgain.status(),"code",localAgain.code());
            d.modifyDN("uid=convert-race,"+PEOPLE,"uid=convert-alias",true);var alias=logged(port,"convert-alias","convert-race-secret");UUID aliasId=repo.findByUsername("convert-alias").orElseThrow().id();record(rows,"converted-entry-alias","newAccount",!target.equals(aliasId),"authKind",alias.get("/api/v1/session").json().get("authKind"),"memberships",jdbc.queryForObject("select count(*) from permissions_membership where user_id=?",Integer.class,aliasId));
        }finally{POOL.shutdownNow();d.shutDown(true);save(args[0],rows);}
    }
    static int localCount(JdbcTemplate jdbc){return jdbc.queryForObject("select count(*) from identity_account u where u.active and u.auth_kind='local' and exists(select 1 from permissions_membership m join permissions_action a on a.company_id=m.company_id and a.group_id=m.group_id where m.user_id=u.id and a.project_id is null and a.action_key='permissions.manage') and exists(select 1 from permissions_membership m join permissions_action a on a.company_id=m.company_id and a.group_id=m.group_id where m.user_id=u.id and a.project_id is null and a.action_key='accounts.manage')",Integer.class);}
}
