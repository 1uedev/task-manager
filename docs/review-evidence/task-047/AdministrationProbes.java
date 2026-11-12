package taskmanager.identity;

import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import taskmanager.identity.account.*;

/** Authorization refresh, conversion versus deactivation, and self conversion through unchanged HTTP APIs. */
public class AdministrationProbes extends ProbeSupport {
    public static void main(String[]args)throws Exception{
        var rows=new ArrayList<Map<String,Object>>();var d=directory();
        try(var ctx=app()){
            int port=Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));var admin=bootstrap(ctx,port);
            var jdbc=ctx.getBean(JdbcTemplate.class);var repo=ctx.getBean(AccountRepository.class);
            ok(admin.put("/api/v1/settings/ldap",settings("ldap://localhost:"+d.getListenPort(),"entryUUID")).status()==200,"LDAP configured");
            person(d,"limited-manager","limited-manager-secret",null,null);var manager=logged(port,"limited-manager","limited-manager-secret");
            UUID managerId=repo.findByUsername("limited-manager").orElseThrow().id();UUID group=grant(admin,managerId,"Account delegation","accounts.manage");
            person(d,"stale-target","stale-target-secret",null,null);logged(port,"stale-target","stale-target-secret");UUID target=repo.findByUsername("stale-target").orElseThrow().id();
            Future<ApiClient.Response> conversion;int wait;
            try(var held=new Held(ctx,()->jdbc.queryForList("select pg_advisory_xact_lock(7240001)"))){
                conversion=POOL.submit(()->manager.post("/api/v1/accounts/"+target+"/convert-to-local",Map.of()));
                wait=waitCount(jdbc,"select pg_advisory_xact_lock%",1);
                ok(admin.put("/api/v1/permissions/groups/"+group+"/actions",Map.of("action","accounts.manage","enabled",false)).status()==200,"right revoked while conversion queued");
            }
            var stale=conversion.get(10,TimeUnit.SECONDS);record(rows,"revoked-right-after-early","observedSerialWaiters",wait,"status",stale.status(),"code",stale.code(),"targetKind",repo.findById(target).orElseThrow().authKind());
            ok(admin.put("/api/v1/permissions/groups/"+group+"/actions",Map.of("action","accounts.manage","enabled",true)).status()==200,"right restored");

            for(String first:List.of("conversion","deactivation")){
                String name="ordered-"+first;person(d,name,"ordered-person-secret",null,null);logged(port,name,"ordered-person-secret");UUID id=repo.findByUsername(name).orElseThrow().id();
                String column=first.equals("conversion")?"auth_kind":"active";
                jdbc.execute("create function review_order_pause() returns trigger language plpgsql as $$ begin perform pg_advisory_xact_lock(9470001); return new; end; $$");
                jdbc.execute("create trigger review_order_pause after update of "+column+" on identity_account for each row when (new.id='"+id+"'::uuid) execute function review_order_pause()");
                Future<ApiClient.Response> one,two;int firstWait,secondWait,secondRightsWait;
                try(var held=new Held(ctx,()->jdbc.queryForList("select pg_advisory_xact_lock(9470001)"))){
                    one=POOL.submit(()->admin.post("/api/v1/accounts/"+id+"/"+(first.equals("conversion")?"convert-to-local":"deactivate"),Map.of()));
                    firstWait=waitCount(jdbc,"update identity_account set "+column+"%",1);
                    two=POOL.submit(()->admin.post("/api/v1/accounts/"+id+"/"+(first.equals("conversion")?"deactivate":"convert-to-local"),Map.of()));
                    secondWait=first.equals("conversion")?waitCount(jdbc,"select pg_advisory_xact_lock%",1):0;
                    secondRightsWait=first.equals("deactivation")?waitCount(jdbc,"select revision%for share%",1):0;
                }
                var r1=one.get(10,TimeUnit.SECONDS);var r2=two.get(10,TimeUnit.SECONDS);var state=repo.findById(id).orElseThrow();
                String otp=(first.equals("conversion")?r1:r2).json().get("oneTimePassword").asString();
                record(rows,"conversion-deactivation-"+first+"-first","firstUpdateWaiters",firstWait,"secondSerialWaiters",secondWait,"secondEarlyRightsWaiters",secondRightsWait,"statuses",List.of(r1.status(),r2.status()),"active",state.active(),"kind",state.authKind(),"externalCleared",state.externalId()==null,"otpLoginStatus",login(port,name,otp).status());
                jdbc.execute("drop trigger review_order_pause on identity_account");jdbc.execute("drop function review_order_pause()");
            }
            var self=manager.post("/api/v1/accounts/"+managerId+"/convert-to-local",Map.of());
            record(rows,"self-conversion","status",self.status(),"otpReturned",self.json().has("oneTimePassword"),"sessionAuthenticated",manager.get("/api/v1/session").json().get("authenticated"),"protectedAccountsStatus",manager.get("/api/v1/accounts").status());
        }finally{POOL.shutdownNow();d.shutDown(true);save(args[0],rows);}
    }
}
