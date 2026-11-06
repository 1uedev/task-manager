package taskmanager.identity;

import com.unboundid.ldap.sdk.Entry;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import taskmanager.TaskManagerApplication;
import taskmanager.identity.account.*;
import tools.jackson.databind.json.JsonMapper;

/** Two distinct, immutable, 16-byte directory identifiers; real LDAP, unmodified product, real HTTP. */
public class BinaryIdentifierProbe {
    public static void main(String[]args)throws Exception{
        var d=ReviewLdapProbes.directory(true);String url="ldap://localhost:"+d.getListenPort();var result=new LinkedHashMap<String,Object>();
        byte[] a=new byte[16],b=new byte[16];Arrays.fill(a,(byte)0x80);Arrays.fill(b,(byte)0x81);a[6]=b[6]=0x40;a[8]=b[8]=(byte)0x80;
        for(String name:List.of("binary-a","binary-b")){
            var e=new Entry("uid="+name+","+ReviewLdapProbes.PEOPLE);e.addAttribute("objectClass","inetOrgPerson");e.addAttribute("uid",name);
            e.addAttribute("cn",name);e.addAttribute("sn","Review");e.addAttribute("userPassword",name+"-secret");e.addAttribute("objectGUID",name.equals("binary-a")?a:b);d.add(e);
        }
        var app=new SpringApplication(TaskManagerApplication.class);
        try(var ctx=app.run("--server.port=0","--spring.main.banner-mode=off","--logging.level.root=ERROR",
                "--taskmanager.identity.source-failure-threshold=100000","--taskmanager.identity.ldap.bind-password="+ReviewLdapProbes.SEARCH_SECRET)){
            int port=Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));var accounts=ctx.getBean(AccountService.class);var repo=ctx.getBean(AccountRepository.class);var jdbc=ctx.getBean(JdbcTemplate.class);
            var first=accounts.bootstrapAdministrator("binary-root");var admin=ReviewLdapProbes.browser(port);
            ReviewLdapProbes.ok(admin.post("/api/v1/session",Map.of("username","binary-root","password",first.oneTimePassword())).status()==200,"root login");
            ReviewLdapProbes.ok(admin.post("/api/v1/session/password",Map.of("currentPassword",first.oneTimePassword(),"newPassword",ReviewLdapProbes.PASSWORD)).status()==200,"root password");
            var settings=ReviewLdapProbes.settings(url);settings.put("idAttribute","objectGUID");ReviewLdapProbes.ok(admin.put("/api/v1/settings/ldap",settings).status()==200,"binary-id configuration");
            var firstLogin=ReviewLdapProbes.login(port,"binary-a","binary-a-secret");ReviewLdapProbes.ok(firstLogin.status()==200,"first binary identity");
            UUID id=repo.findByUsername("binary-a").orElseThrow().id();
            var group=admin.post("/api/v1/permissions/groups",Map.of("name","Binary owner only"));ReviewLdapProbes.ok(group.status()==201,"rights group");
            String groupId=group.json().get("id").asString();
            ReviewLdapProbes.ok(admin.put("/api/v1/permissions/groups/"+groupId+"/actions",Map.of("action","accounts.manage","enabled",true)).status()==200,"company right only for A");
            ReviewLdapProbes.ok(admin.put("/api/v1/permissions/groups/"+groupId+"/members/"+id,Map.of()).status()==200,"A membership");
            var other=ReviewLdapProbes.browser(port);var second=other.post("/api/v1/session",Map.of("username","binary-b","password","binary-b-secret"));
            var accountRead=other.get("/api/v1/accounts");
            result.put("wireIdA",HexFormat.of().formatHex(a));result.put("wireIdB",HexFormat.of().formatHex(b));result.put("wireIdsDifferent",!Arrays.equals(a,b));
            result.put("loginA",firstLogin.status());result.put("loginB",second.status());result.put("returnedUsernameB",second.json().get("username"));
            result.put("accountAId",id.toString());result.put("accountBExists",repo.findByUsername("binary-b").isPresent());
            String stored=jdbc.queryForObject("select external_id from identity_account where id=?",String.class,id);
            result.put("storedExternalId",stored);result.put("storedExternalIdCodePoints",stored.codePoints().mapToObj(c->String.format("U+%04X",c)).toList());
            result.put("BReadsAccountsWithARight",accountRead.status());
            System.out.println("REVIEW "+JsonMapper.builder().build().writeValueAsString(result));
        }finally{d.shutDown(true);Files.writeString(Path.of(args[0]),JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(result)+"\n");}
    }
}
