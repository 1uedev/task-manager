package taskmanager.identity;

import com.unboundid.ldap.listener.*;
import com.unboundid.ldap.sdk.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.TaskManagerApplication;
import taskmanager.identity.account.*;
import tools.jackson.databind.json.JsonMapper;

/** Synthetic disposable fixture. Product beans and HTTP routes are unmodified. Never use a product database. */
public class ProbeSupport {
    static final String BASE="dc=review,dc=test",PEOPLE="ou=people,"+BASE,SEARCH="cn=search,"+BASE;
    static final String SECRET="synthetic-search-secret-48",PASSWORD="Synthetic-Review-Password-48!";
    static final ExecutorService POOL=Executors.newFixedThreadPool(12);
    static void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
    static ConfigurableApplicationContext app(String...extra){
        var args=new ArrayList<>(List.of("--server.port=0","--spring.main.banner-mode=off","--logging.level.root=ERROR","--taskmanager.identity.source-failure-threshold=100000","--taskmanager.identity.ldap.bind-password="+SECRET,"--taskmanager.identity.ldap.timeout=2s"));
        args.addAll(List.of(extra));return new SpringApplication(TaskManagerApplication.class).run(args.toArray(String[]::new));
    }
    static InMemoryDirectoryServer directory()throws Exception{
        var cfg=new InMemoryDirectoryServerConfig(BASE);cfg.setSchema(null);cfg.setGenerateOperationalAttributes(true);cfg.addAdditionalBindCredentials(SEARCH,SECRET);
        cfg.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("plain",0));var d=new InMemoryDirectoryServer(cfg);
        d.add("dn: "+BASE,"objectClass: domain","dc: review");d.add("dn: "+PEOPLE,"objectClass: organizationalUnit","ou: people");d.startListening();return d;
    }
    static String person(InMemoryDirectoryServer d,String name,String pass,String attr,byte[] value)throws Exception{
        String dn="uid="+name+","+PEOPLE;var e=new Entry(dn);e.addAttribute("objectClass","inetOrgPerson");e.addAttribute("uid",name);e.addAttribute("cn","Display "+name);e.addAttribute("sn","Review");e.addAttribute("userPassword",pass);
        if(attr!=null)e.addAttribute(attr,value);d.add(e);return dn;
    }
    static Map<String,Object> settings(String url,String id){var b=new LinkedHashMap<String,Object>();b.put("enabled",true);b.put("url",url);b.put("security","none");b.put("bindDn",SEARCH);b.put("searchBase",PEOPLE);b.put("userFilter","(uid={0})");b.put("idAttribute",id);b.put("nameAttribute","cn");return b;}
    static ApiClient browser(int port){var b=new ApiClient(port);ok(b.get("/api/v1/session").status()==200,"CSRF seed");return b;}
    static ApiClient logged(int port,String name,String pass){var b=browser(port);ok(b.post("/api/v1/session",Map.of("username",name,"password",pass)).status()==200,"login "+name);return b;}
    static ApiClient.Response login(int port,String name,String pass){return browser(port).post("/api/v1/session",Map.of("username",name,"password",pass));}
    static ApiClient bootstrap(ConfigurableApplicationContext ctx,int port){var first=ctx.getBean(AccountService.class).bootstrapAdministrator("review-root");var b=logged(port,"review-root",first.oneTimePassword());ok(b.post("/api/v1/session/password",Map.of("currentPassword",first.oneTimePassword(),"newPassword",PASSWORD)).status()==200,"root forced change");return b;}
    static UUID grant(ApiClient admin,UUID user,String name,String...rights){var made=admin.post("/api/v1/permissions/groups",Map.of("name",name));ok(made.status()==201,"group creation");String group=made.json().get("id").asString();for(String right:rights)ok(admin.put("/api/v1/permissions/groups/"+group+"/actions",Map.of("action",right,"enabled",true)).status()==200,"right grant");ok(admin.put("/api/v1/permissions/groups/"+group+"/members/"+user,Map.of()).status()==200,"member grant");return UUID.fromString(group);}
    static void record(List<Map<String,Object>>rows,String test,Object...pairs){var r=new LinkedHashMap<String,Object>();r.put("case",test);for(int i=0;i<pairs.length;i+=2)r.put((String)pairs[i],pairs[i+1]);rows.add(r);System.out.println("REVIEW "+JsonMapper.builder().build().writeValueAsString(r));}
    static void save(String path,Object value)throws Exception{Files.writeString(Path.of(path),JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(value)+"\n");}
    static int waitCount(JdbcTemplate jdbc,String pattern,int desired)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);int n=0;while(System.nanoTime()<end){n=jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like ?",Integer.class,pattern);if(n>=desired)return n;Thread.sleep(15);}return n;}
    static class Held implements AutoCloseable{
        final CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1);final Future<?> future;
        Held(ConfigurableApplicationContext ctx,Runnable work)throws Exception{future=POOL.submit(()->new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class)).executeWithoutResult(s->{work.run();locked.countDown();try{ok(release.await(20,TimeUnit.SECONDS),"held transaction timeout");}catch(InterruptedException e){throw new RuntimeException(e);}}));ok(locked.await(5,TimeUnit.SECONDS),"fixture acquired lock");}
        public void close()throws Exception{release.countDown();future.get(10,TimeUnit.SECONDS);}
    }
}
