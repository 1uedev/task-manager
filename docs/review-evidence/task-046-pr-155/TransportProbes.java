package taskmanager.identity.directory;

import com.unboundid.ldap.listener.*;
import com.unboundid.ldap.listener.interceptor.*;
import com.unboundid.util.ssl.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.*;
import taskmanager.identity.config.LdapProperties;
import tools.jackson.databind.json.JsonMapper;

/** Independent TLS endpoints and deployment trust-store probes; only synthetic keys and credentials. */
public class TransportProbes {
    static final String PASS="synthetic-store-46",BASE="dc=tls,dc=test",SEARCH="cn=search,"+BASE,SECRET="synthetic-search-46";
    static final List<Map<String,Object>> RESULTS=new ArrayList<>();
    static final AtomicInteger SEARCH_BINDS=new AtomicInteger(),PERSON_BINDS=new AtomicInteger();
    static void record(String test,Object...pairs){var r=new LinkedHashMap<String,Object>();r.put("case",test);for(int i=0;i<pairs.length;i+=2)r.put((String)pairs[i],pairs[i+1]);RESULTS.add(r);System.out.println("REVIEW "+JsonMapper.builder().build().writeValueAsString(r));}
    static void keytool(String...args)throws Exception{
        var cmd=new ArrayList<String>();cmd.add(Path.of(System.getProperty("java.home"),"bin","keytool.exe").toString());cmd.addAll(List.of(args));
        var p=new ProcessBuilder(cmd).redirectErrorStream(true).start();p.getInputStream().readAllBytes();if(p.waitFor()!=0)throw new AssertionError("synthetic keytool operation failed");
    }
    static LdapSettings settings(String url,String security){return new LdapSettings(true,url,security,SEARCH,BASE,"(uid={0})","entryUUID","cn",null);}
    static String attempt(DirectoryClient client,LdapSettings settings){try{return client.authenticate(settings,"tls-person","synthetic-person-46").isPresent()?"authenticated":"refused";}catch(DirectoryUnavailableException e){return e.reason();}}
    public static void main(String[] args)throws Exception{
        Path temp=Files.createTempDirectory("review-ldap-tls-46-");Path server=temp.resolve("server.p12"),cert=temp.resolve("server.crt"),trust=temp.resolve("trust.p12");
        keytool("-genkeypair","-alias","review","-keyalg","RSA","-keysize","2048","-validity","2","-dname","CN=localhost","-ext","SAN=dns:localhost","-storetype","PKCS12","-keystore",server.toString(),"-storepass",PASS);
        keytool("-exportcert","-alias","review","-keystore",server.toString(),"-storepass",PASS,"-file",cert.toString());
        keytool("-importcert","-noprompt","-alias","review","-file",cert.toString(),"-storetype","PKCS12","-keystore",trust.toString(),"-storepass",PASS);
        var ssl=new SSLUtil(new KeyStoreKeyManager(server.toString(),PASS.toCharArray(),"PKCS12","review"),new TrustAllTrustManager());
        var cfg=new InMemoryDirectoryServerConfig(BASE);cfg.setSchema(null);cfg.addAdditionalBindCredentials(SEARCH,SECRET);cfg.setGenerateOperationalAttributes(true);
        cfg.setListenerConfigs(InMemoryListenerConfig.createLDAPSConfig("ldaps",0,ssl.createSSLServerSocketFactory()),InMemoryListenerConfig.createLDAPConfig("starttls",null,0,ssl.createSSLSocketFactory()));
        cfg.addInMemoryOperationInterceptor(new InMemoryOperationInterceptor(){public void processSimpleBindRequest(InMemoryInterceptedSimpleBindRequest r){
            if(r.getRequest().getBindDN().equals(SEARCH))SEARCH_BINDS.incrementAndGet();if(r.getRequest().getBindDN().startsWith("uid="))PERSON_BINDS.incrementAndGet();}});
        var d=new InMemoryDirectoryServer(cfg);d.add("dn: "+BASE,"objectClass: domain","dc: tls");
        d.add("dn: uid=tls-person,"+BASE,"objectClass: inetOrgPerson","uid: tls-person","cn: TLS","sn: T","userPassword: synthetic-person-46");d.startListening();
        try{
            var trusted=new DirectoryClient(new LdapProperties(SECRET,Duration.ofSeconds(2),trust.toString(),PASS,"PKCS12"));
            for(String security:List.of("ldaps","starttls")){
                int port=d.getListenPort(security);String scheme=security.equals("ldaps")?"ldaps":"ldap";
                String good=attempt(trusted,settings(scheme+"://localhost:"+port,security));int searchBefore=SEARCH_BINDS.get(),personBefore=PERSON_BINDS.get();
                String wrong=attempt(trusted,settings(scheme+"://127.0.0.1:"+port,security));
                record("hostname-"+security,"trusted",good,"wrongName",wrong,"wrongNameSearchBinds",SEARCH_BINDS.get()-searchBefore,"wrongNamePersonBinds",PERSON_BINDS.get()-personBefore);
                if(!good.equals("authenticated")||!wrong.equals("directory.tls_failed")||SEARCH_BINDS.get()!=searchBefore||PERSON_BINDS.get()!=personBefore)throw new AssertionError("hostname or credential-before-TLS boundary");
            }
            var untrusted=new DirectoryClient(new LdapProperties(SECRET,Duration.ofSeconds(2),"","","PKCS12"));
            for(String security:List.of("ldaps","starttls")){
                String scheme=security.equals("ldaps")?"ldaps":"ldap";String answer=attempt(untrusted,settings(scheme+"://localhost:"+d.getListenPort(security),security));
                record("untrusted-"+security,"reason",answer);if(!answer.equals("directory.tls_failed"))throw new AssertionError("untrusted certificate");
            }
            for(String mode:List.of("missing-file","wrong-password","wrong-type")){
                boolean failed=false;try{new DirectoryClient(new LdapProperties(SECRET,Duration.ofSeconds(2),mode.equals("missing-file")?temp.resolve("absent").toString():trust.toString(),mode.equals("wrong-password")?"incorrect":PASS,mode.equals("wrong-type")?"not-a-store":"PKCS12"));}catch(IllegalStateException expected){failed=true;}
                record("trust-store-"+mode,"failedClosed",failed);if(!failed)throw new AssertionError("unsafe trust fallback");
            }
        }finally{d.shutDown(true);Files.writeString(Path.of(args[0]),JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(RESULTS)+"\n");}
    }
}
