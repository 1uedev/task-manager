package taskmanager.identity;

import com.unboundid.ldap.listener.*;
import com.unboundid.ldap.listener.interceptor.*;
import com.unboundid.ldap.sdk.*;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import taskmanager.TaskManagerApplication;
import taskmanager.identity.account.*;
import taskmanager.identity.config.LdapProperties;
import taskmanager.identity.directory.*;
import tools.jackson.databind.json.JsonMapper;

/** Independent probes: unmodified product beans, real HTTP, disposable PostgreSQL and synthetic LDAP only. */
public class ReviewLdapProbes {
    static final String BASE="dc=review,dc=test", PEOPLE="ou=people,"+BASE, SEARCH="cn=search,"+BASE;
    static final String SEARCH_SECRET="synthetic-search-secret-46", PASSWORD="Synthetic-Review-Password-46!";
    static final List<Map<String,Object>> RESULTS=new ArrayList<>();
    static final ExecutorService POOL=Executors.newFixedThreadPool(12);
    static final AtomicInteger PERSON_BINDS=new AtomicInteger(), EMPTY_PERSON_BINDS=new AtomicInteger(), SEARCHES=new AtomicInteger();
    static final AtomicInteger SECONDARY_REQUESTS=new AtomicInteger();
    static final AtomicInteger SEARCH_BINDS=new AtomicInteger();
    static volatile String lastFilter,lastBase,lastAliases;
    static volatile int lastSize,lastTime;
    static volatile CountDownLatch entered,release;
    static volatile String referral;
    static void ok(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    static void record(String name,Object...pairs){var row=new LinkedHashMap<String,Object>();row.put("case",name);for(int i=0;i<pairs.length;i+=2)row.put((String)pairs[i],pairs[i+1]);RESULTS.add(row);System.out.println("REVIEW "+JsonMapper.builder().build().writeValueAsString(row));}
    static class Observer extends InMemoryOperationInterceptor {
        public void processSimpleBindRequest(InMemoryInterceptedSimpleBindRequest r){
            if(r.getRequest().getBindDN().equals(SEARCH))SEARCH_BINDS.incrementAndGet();
            if(r.getRequest().getBindDN().startsWith("uid=")){
                PERSON_BINDS.incrementAndGet();if(r.getRequest().getPassword().getValueLength()==0)EMPTY_PERSON_BINDS.incrementAndGet();
            }
        }
        public void processSearchRequest(InMemoryInterceptedSearchRequest r)throws LDAPException{
            var q=r.getRequest();SEARCHES.incrementAndGet();lastFilter=q.getFilter().toString();lastBase=q.getBaseDN();
            lastAliases=q.getDereferencePolicy().toString();lastSize=q.getSizeLimit();lastTime=q.getTimeLimitSeconds();
            if(lastFilter.contains("referral-probe"))throw new LDAPException(ResultCode.REFERRAL,"synthetic referral",null,new String[]{referral});
            if(lastFilter.contains("diagnostic-probe"))throw new LDAPException(ResultCode.OTHER,"synthetic diagnostic "+SEARCH_SECRET);
            if(lastFilter.contains("time-limit-probe"))throw new LDAPException(ResultCode.TIME_LIMIT_EXCEEDED,"synthetic limit");
            if(lastFilter.contains("slow-probe")){
                entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            }
        }
        public void processSearchResult(InMemoryInterceptedSearchResult r){
            if(r.getRequest().getFilter().toString().contains("limit-probe")&&!r.getRequest().getFilter().toString().contains("time-limit"))
                r.setResult(new LDAPResult(r.getResult().getMessageID(),ResultCode.SIZE_LIMIT_EXCEEDED,"synthetic size limit",null,(String[])null,(Control[])null));
        }
    }
    static InMemoryDirectoryServer directory(boolean observe)throws Exception{
        var c=new InMemoryDirectoryServerConfig(BASE);c.setSchema(null);c.setGenerateOperationalAttributes(true);
        c.addAdditionalBindCredentials(SEARCH,SEARCH_SECRET);c.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("plain",0));
        if(observe)c.addInMemoryOperationInterceptor(new Observer());
        else c.addInMemoryOperationInterceptor(new InMemoryOperationInterceptor(){
            public void processSearchRequest(InMemoryInterceptedSearchRequest r){SECONDARY_REQUESTS.incrementAndGet();}
            public void processSimpleBindRequest(InMemoryInterceptedSimpleBindRequest r){SECONDARY_REQUESTS.incrementAndGet();}
        });
        var d=new InMemoryDirectoryServer(c);d.add("dn: "+BASE,"objectClass: domain","dc: review");
        d.add("dn: "+PEOPLE,"objectClass: organizationalUnit","ou: people");d.startListening();return d;
    }
    static String add(InMemoryDirectoryServer d,String uid,String pass)throws Exception{
        String dn="uid="+uid+","+PEOPLE;var e=new Entry(dn);e.addAttribute("objectClass","inetOrgPerson");
        e.addAttribute("uid",uid);e.addAttribute("cn","Synthetic "+uid);e.addAttribute("sn","Review");e.addAttribute("userPassword",pass);d.add(e);return dn;
    }
    static Map<String,Object> settings(String url){
        var b=new LinkedHashMap<String,Object>();b.put("enabled",true);b.put("url",url);b.put("security","none");b.put("bindDn",SEARCH);
        b.put("searchBase",PEOPLE);b.put("userFilter","(uid={0})");b.put("idAttribute","entryUUID");b.put("nameAttribute","cn");return b;
    }
    static LdapSettings direct(String url,String base,String filter){return new LdapSettings(true,url,"none",SEARCH,base,filter,"entryUUID","cn",null);}
    static ApiClient browser(int port){var b=new ApiClient(port);ok(b.get("/api/v1/session").status()==200,"csrf seed");return b;}
    static ApiClient.Response login(int port,String name,String password){return browser(port).post("/api/v1/session",Map.of("username",name,"password",password));}
    public static void main(String[] args)throws Exception{
        var d=directory(true);var secondary=directory(false);String url="ldap://localhost:"+d.getListenPort();referral="ldap://localhost:"+secondary.getListenPort()+"/"+PEOPLE;
        var app=new SpringApplication(TaskManagerApplication.class);
        try(var ctx=app.run("--server.port=0","--spring.main.banner-mode=off","--logging.level.root=ERROR",
                "--taskmanager.identity.source-failure-threshold=100000","--taskmanager.identity.ldap.bind-password="+SEARCH_SECRET,
                "--taskmanager.identity.ldap.timeout=2s")){
            int port=Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));
            var accounts=ctx.getBean(AccountService.class);var repo=ctx.getBean(AccountRepository.class);var jdbc=ctx.getBean(JdbcTemplate.class);
            var tx=new TransactionTemplate(ctx.getBean(PlatformTransactionManager.class));var client=ctx.getBean(DirectoryClient.class);
            var first=accounts.bootstrapAdministrator("review-root");var admin=browser(port);
            ok(admin.post("/api/v1/session",Map.of("username","review-root","password",first.oneTimePassword())).status()==200,"bootstrap login");
            ok(admin.post("/api/v1/session/password",Map.of("currentPassword",first.oneTimePassword(),"newPassword",PASSWORD)).status()==200,"password change");
            ok(admin.put("/api/v1/settings/ldap",settings(url)).status()==200,"configuration");

            // Missing/null enum and malformed grammar: observe actual API responses, not a replacement validator.
            for(String mode:List.of("missing","null","blank")){
                var input=settings(url);if(mode.equals("missing"))input.remove("security");else input.put("security",mode.equals("blank")?" ":null);
                var answer=admin.put("/api/v1/settings/ldap",input);record("security-"+mode,"status",answer.status(),"code",answer.code());
            }
            for(String filter:List.of("(uid={0}garbage)","(uid={0}\\zz)","(uid={0}\\)","(uid={0}=)")){
                var input=settings(url);input.put("userFilter",filter);var answer=admin.post("/api/v1/settings/ldap/test",Map.of("settings",input,"username","none"));
                record("filter-configuration","filter",filter,"status",answer.status(),"response",answer.json());
            }

            // Known identifiers with a colliding login name, not merely a new stranger reusing an old name.
            String dnA=add(d,"collision-a","person-a-pass"),dnB=add(d,"collision-b","person-b-pass");
            var a=login(port,"collision-a","person-a-pass");var b=login(port,"collision-b","person-b-pass");ok(a.status()==200&&b.status()==200,"two initial identities");
            UUID idA=repo.findByUsername("collision-a").orElseThrow().id(),idB=repo.findByUsername("collision-b").orElseThrow().id();
            d.delete(dnB);d.modifyDN(dnA,"uid=collision-b",true);
            var collision=login(port,"collision-b","person-a-pass");
            record("known-entry-name-collision","status",collision.status(),"returnedUsername",collision.json().get("username"),"originalIdsDistinct",!idA.equals(idB),"returnedOriginalA",collision.status()==200&&collision.json().get("username").asString().equals("collision-a"));

            // Isolated one-result wildcard control: proves escaping, even if a broad search otherwise returns ambiguous.
            String specialPass=" Leading-Ä(!)\\* trailing ";String specialDN=add(d,"literal-user",specialPass);
            String[] names={"*","literal*","*)(uid=*)","\\2a","a\u0000b","(uid=*)","literal-user"};
            var encoded=new ArrayList<Map<String,Object>>();
            for(String name:names){boolean present=client.authenticate(direct(url,PEOPLE,"(uid={0})"),name,specialPass).isPresent();
                encoded.add(Map.of("name",name,"present",present,"wireFilter",lastFilter));ok(present==name.equals("literal-user"),"escaped value");}
            boolean unescapedControl=client.authenticate(direct(url,PEOPLE,"(&(uid=literal*)(cn={0}))"),"Synthetic literal-user",specialPass).isPresent();
            ok(unescapedControl,"wildcard control really has one usable hit");
            int beforeEmpty=PERSON_BINDS.get(),beforeSearch=SEARCHES.get();
            ok(client.authenticate(direct(url,PEOPLE,"(uid={0})"),"literal-user","").isEmpty(),"empty password");
            ok(client.authenticate(direct(url,PEOPLE,"(uid={0})"),"literal-user","x".repeat(1025)).isEmpty(),"oversize password");
            record("filter-and-password","attempts",encoded,"wildcardControlHasOneHit",unescapedControl,"emptyOrOversizeOpenedNoSearch",beforeSearch==SEARCHES.get(),"openedNoPersonBind",beforeEmpty==PERSON_BINDS.get(),"emptyPersonBindCount",EMPTY_PERSON_BINDS.get());
            add(d,"long-password","x".repeat(1024));ok(client.authenticate(direct(url,PEOPLE,"(uid={0})"),"long-password","x".repeat(1024)).isPresent(),"1024 accepted unchanged");

            d.add("dn: ou=a/b,"+BASE,"objectClass: organizationalUnit","ou: a/b");
            d.add("dn: uid=slash,ou=a/b,"+BASE,"objectClass: inetOrgPerson","uid: slash","cn: Slash","sn: S","userPassword: slash-secret");
            ok(client.authenticate(direct(url,"ou=a/b,"+BASE,"(uid={0})"),"slash","slash-secret").isPresent(),"unsplit slash base");
            record("search-controls","base",lastBase,"aliasPolicy",lastAliases,"sizeLimit",lastSize,"timeLimitSeconds",lastTime);
            d.add("dn: uid=alias-probe,"+PEOPLE,"objectClass: alias","uid: alias-probe","aliasedObjectName: "+specialDN);
            boolean alias=client.authenticate(direct(url,PEOPLE,"(uid={0})"),"alias-probe",specialPass).isPresent();
            record("alias","authenticated",alias,"wirePolicy",lastAliases);ok(!alias,"alias does not authenticate target");
            String referralReason="none";try{client.check(direct(url,PEOPLE,"(uid={0})"),"referral-probe");}catch(DirectoryUnavailableException e){referralReason=e.reason();}
            record("referral","reason",referralReason,"secondaryRequests",SECONDARY_REQUESTS.get());ok(SECONDARY_REQUESTS.get()==0,"referral not followed");
            add(d,"limit-probe","limit-secret");ok(client.authenticate(direct(url,PEOPLE,"(uid={0})"),"limit-probe","limit-secret").isEmpty(),"partial result with size limit not accepted");
            record("size-limit","partialResultAccepted",false);
            var time=admin.post("/api/v1/settings/ldap/test",Map.of("username","time-limit-probe"));
            var diag=admin.post("/api/v1/settings/ldap/test",Map.of("username","diagnostic-probe"));
            record("diagnostics","timeLimit",time.json(),"serverDiagnostic",diag.json(),"secretInResponse",diag.body().contains(SEARCH_SECRET));
            ok(!diag.body().contains(SEARCH_SECRET),"no diagnostic leak");

            // StartTLS unsupported: no search-account or person credential may be sent before a successful upgrade.
            var upgrade=settings(url);upgrade.put("security","starttls");int beforeTls=PERSON_BINDS.get(),beforeSearchBinds=SEARCH_BINDS.get(),beforeTlsSearches=SEARCHES.get();
            var tls=admin.post("/api/v1/settings/ldap/test",Map.of("settings",upgrade,"username","literal-user"));
            record("starttls-no-support","answer",tls.json(),"personBinds",PERSON_BINDS.get()-beforeTls,"searchAccountBinds",SEARCH_BINDS.get()-beforeSearchBinds,"searches",SEARCHES.get()-beforeTlsSearches);
            ok(!tls.json().get("ok").asBoolean()&&SEARCH_BINDS.get()==beforeSearchBinds&&SEARCHES.get()==beforeTlsSearches,"no plaintext fallback");

            // A real pending first assignment forces duplicate-key waiting and retry; observe the database wait, not a sleep.
            String raceDN=add(d,"race-person","race-secret");String external=d.getEntry(raceDN).getAttributeValue("entryUUID");
            var assigned=new CountDownLatch(1);var commit=new CountDownLatch(1);
            Future<?> creator=POOL.submit(()->tx.executeWithoutResult(s->{ctx.getBean(taskmanager.identity.session.SessionDuration.class).hoursForShare();
                ctx.getBean(DirectoryAccounts.class).assign(new DirectoryClient.DirectoryUser(external,"Race"),"race-person",Instant.now());assigned.countDown();
                try{ok(commit.await(15,TimeUnit.SECONDS),"release creator");}catch(InterruptedException e){throw new RuntimeException(e);}}));
            ok(assigned.await(10,TimeUnit.SECONDS),"pending first assignment");var racers=new ArrayList<Future<Integer>>();
            for(int i=0;i<4;i++)racers.add(POOL.submit(()->login(port,"race-person","race-secret").status()));
            long stop=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);int blocked=0;
            while(System.nanoTime()<stop){blocked=jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like 'insert into identity_account%'",Integer.class);if(blocked>=4)break;Thread.sleep(20);}
            commit.countDown();creator.get(15,TimeUnit.SECONDS);var statuses=new ArrayList<Integer>();for(var f:racers)statuses.add(f.get(15,TimeUnit.SECONDS));
            record("forced-first-login-race","observedInsertWaiters",blocked,"statuses",statuses,"accountCount",jdbc.queryForObject("select count(*) from identity_account where external_id=?",Integer.class,external));
            ok(blocked==4&&statuses.stream().allMatch(s->s==200),"forced retry result");

            // Login holds the shared duration row before waiting at an existing account. Shortening queues behind it.
            UUID raceId=repo.findByUsername("race-person").orElseThrow().id();var accountHeld=new CountDownLatch(1);var accountRelease=new CountDownLatch(1);
            Future<?> accountHolder=POOL.submit(()->tx.executeWithoutResult(s->{repo.findByIdForUpdate(raceId).orElseThrow();accountHeld.countDown();
                try{ok(accountRelease.await(15,TimeUnit.SECONDS),"release account");}catch(InterruptedException e){throw new RuntimeException(e);}}));
            ok(accountHeld.await(5,TimeUnit.SECONDS),"account held");var loginWaiting=POOL.submit(()->login(port,"race-person","race-secret"));
            boolean accountWait=waitFor(jdbc,"%from identity_account%for update%");
            var shortening=POOL.submit(()->admin.put("/api/v1/settings/session-duration",Map.of("hours",1)));
            boolean settingWait=waitFor(jdbc,"%identity_setting%for update%");accountRelease.countDown();accountHolder.get(10,TimeUnit.SECONDS);
            var racedLogin=loginWaiting.get(10,TimeUnit.SECONDS);var shortened=shortening.get(10,TimeUnit.SECONDS);
            Boolean limited=jdbc.queryForObject("select bool_and(expires_at <= started_at + interval '1 hour') from identity_session where account_id=? and revoked_at is null",Boolean.class,raceId);
            record("duration-race","accountWaitObserved",accountWait,"settingWaitObserved",settingWait,"loginStatus",racedLogin.status(),"shortenStatus",shortened.status(),"sessionsLimitedToOneHour",limited);
            ok(accountWait&&settingWait&&racedLogin.status()==200&&shortened.status()==200&&Boolean.TRUE.equals(limited),"duration ordering");

            var deactivated=new CountDownLatch(1);var deactivateCommit=new CountDownLatch(1);
            var deactivate=POOL.submit(()->tx.executeWithoutResult(s->{accounts.deactivate("fixture",raceId);deactivated.countDown();
                try{ok(deactivateCommit.await(15,TimeUnit.SECONDS),"deactivation release");}catch(InterruptedException e){throw new RuntimeException(e);}}));
            ok(deactivated.await(5,TimeUnit.SECONDS),"pending deactivation");var duringDeactivate=POOL.submit(()->login(port,"race-person","race-secret"));
            boolean deactivationWait=waitFor(jdbc,"%from identity_account%for update%");deactivateCommit.countDown();deactivate.get(10,TimeUnit.SECONDS);var refused= duringDeactivate.get(10,TimeUnit.SECONDS);
            record("deactivation-race","accountWaitObserved",deactivationWait,"status",refused.status(),"activeSessions",jdbc.queryForObject("select count(*) from identity_session where account_id=? and revoked_at is null",Integer.class,raceId));
            ok(deactivationWait&&refused.status()==401,"deactivation wins before account lock");accounts.reactivate("fixture",raceId);

            // Deliberately give the LDAP fixture a known local hash, to test the auth-kind gate rather than randomness alone.
            jdbc.update("update identity_account set password_hash=? where id=?",ctx.getBean(PasswordHasher.class).hash(PASSWORD),raceId);
            var disabled=settings(url);disabled.put("enabled",false);ok(admin.put("/api/v1/settings/ldap",disabled).status()==200,"disable directory");
            var localHashAttempt=login(port,"race-person",PASSWORD);ok(localHashAttempt.status()==401,"LDAP account never uses local hash");
            ok(admin.put("/api/v1/settings/ldap",settings(url)).status()==200,"enable directory");
            record("auth-kind-gate","knownLocalHashStillRefused",localHashAttempt.status()==401,"directoryPasswordStillWorks",login(port,"race-person","race-secret").status()==200);

            // An in-flight LDAP call holds no application DB connection. A local sign-in still completes.
            var local=accounts.create("fixture","local-while-slow",null);add(d,"slow-probe","slow-secret");entered=new CountDownLatch(1);release=new CountDownLatch(1);
            Future<ApiClient.Response> waiting=POOL.submit(()->login(port,"slow-probe","slow-secret"));ok(entered.await(5,TimeUnit.SECONDS),"network reached");
            int active=ctx.getBean(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();var localReply=login(port,"local-while-slow",local.oneTimePassword());
            Thread.sleep(2200);release.countDown();var timeout=waiting.get(10,TimeUnit.SECONDS);
            record("network-timeout","activeDbConnectionsWhileLdapWaits",active,"localStatus",localReply.status(),"directoryStatus",timeout.status(),"directoryCode",timeout.code());
            ok(active==0&&localReply.status()==200&&timeout.status()==503,"outage isolation");

            // Explicitly unavailable direct property toString must not contain either deployment secret.
            var props=new LdapProperties(SEARCH_SECRET,Duration.ofSeconds(2),"","synthetic-store-secret","PKCS12");
            ok(!props.toString().contains(SEARCH_SECRET)&&!props.toString().contains("synthetic-store-secret"),"redacted record");
            record("property-redaction","safe",true);
        }finally{POOL.shutdownNow();d.shutDown(true);secondary.shutDown(true);Files.writeString(Path.of(args[0]),JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(RESULTS)+"\n");}
    }
    static boolean waitFor(JdbcTemplate jdbc,String pattern)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        while(System.nanoTime()<until){if(jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like ?",Integer.class,pattern)>0)return true;Thread.sleep(20);}return false;
    }
}
