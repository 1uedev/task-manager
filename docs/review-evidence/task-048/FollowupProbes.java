package taskmanager.identity;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import taskmanager.identity.account.*;

/** Independent HTTP regressions for TASK-048. Synthetic disposable database only. */
public class FollowupProbes extends ProbeSupport {
    public static void main(String[]args)throws Exception{
        var rows=new ArrayList<Map<String,Object>>();var d=directory();String url="ldap://localhost:"+d.getListenPort();
        try(var ctx=app()){
            int port=Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));var admin=bootstrap(ctx,port);
            var jdbc=ctx.getBean(JdbcTemplate.class);var repo=ctx.getBean(AccountRepository.class);
            byte[]a=HexFormat.of().parseHex("80808080808040808080808080808080"),b=HexFormat.of().parseHex("81818181818140818081818181818181");
            person(d,"binary-a","binary-a-secret","objectGUID",a);person(d,"binary-b","binary-b-secret","objectGUID",b);
            ok(admin.put("/api/v1/settings/ldap",settings(url,"objectGUID")).status()==200,"binary setting");
            ok(login(port,"binary-a","binary-a-secret").status()==200,"first binary login");UUID aId=repo.findByUsername("binary-a").orElseThrow().id();grant(admin,aId,"Only binary A","accounts.manage");
            var second=logged(port,"binary-b","binary-b-secret");var secondState=second.get("/api/v1/session");
            record(rows,"F1-original","returnedUsername",secondState.json().get("username"),"distinctAccounts",!aId.equals(repo.findByUsername("binary-b").orElseThrow().id()),"secondAccountsStatus",second.get("/api/v1/accounts").status(),"keys",jdbc.queryForList("select external_id from identity_account where username in ('binary-a','binary-b') order by username",String.class));

            ok(admin.put("/api/v1/settings/ldap",settings(url,"entryUUID")).status()==200,"text setting");
            String dnA=person(d,"collision-a","collision-a-secret",null,null),dnB=person(d,"collision-b","collision-b-secret",null,null);
            ok(login(port,"collision-a","collision-a-secret").status()==200&&login(port,"collision-b","collision-b-secret").status()==200,"known collision accounts");
            d.delete(dnB);d.modifyDN(dnA,"uid=collision-b",true);var collision=login(port,"collision-b","collision-a-secret");
            d.modifyDN("uid=collision-b,"+PEOPLE,"uid=renamed-free",true);var rename=login(port,"renamed-free","collision-a-secret");
            record(rows,"F2-original","collisionStatus",collision.status(),"freeRenameStatus",rename.status(),"freeRenameAccount",rename.json().get("username"));

            for(String variant:List.of("missing","null","blank"))for(String method:List.of("PUT","POST")){
                var input=settings(url,"entryUUID");if(variant.equals("missing"))input.remove("security");else input.put("security",variant.equals("blank")?" ":null);
                var reply=method.equals("PUT")?admin.put("/api/v1/settings/ldap",input):admin.post("/api/v1/settings/ldap/test",Map.of("settings",input));
                record(rows,"F3-original","variant",variant,"method",method,"status",reply.status(),"violations",reply.json().get("violations"));
            }
            int internal=0,cases=0;var inputResults=new ArrayList<Map<String,Object>>();
            for(String field:List.of("enabled","url","security","bindDn","searchBase","userFilter","idAttribute","nameAttribute"))for(String variant:List.of("missing","null","long","wrong-type")){
                var input=settings(url,"entryUUID");if(variant.equals("missing"))input.remove(field);else if(variant.equals("null"))input.put(field,null);else if(variant.equals("long"))input.put(field,"x".repeat(2000));else input.put(field,List.of("wrong"));
                var r=admin.put("/api/v1/settings/ldap",input);cases++;if(r.status()>=500)internal++;inputResults.add(Map.of("field",field,"variant",variant,"status",r.status()));
            }
            record(rows,"validator-matrix","cases",cases,"internalErrors",internal,"responses",inputResults);
            ok(admin.put("/api/v1/settings/ldap",settings(url,"entryUUID")).status()==200,"restore setting");
            var upgrade=settings(url,"entryUUID");upgrade.put("security","starttls");var refusedTls=admin.post("/api/v1/settings/ldap/test",Map.of("settings",upgrade));
            record(rows,"F4-original","status",refusedTls.status(),"answer",refusedTls.json());

        }finally{POOL.shutdownNow();d.shutDown(true);save(args[0],rows);}
    }
}
