package taskmanager.identity.directory;

import com.unboundid.ldap.listener.*;
import com.unboundid.ldap.listener.interceptor.*;
import com.unboundid.ldap.sdk.*;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import taskmanager.identity.config.LdapProperties;
import taskmanager.platform.ApiProblemException;
import tools.jackson.databind.json.JsonMapper;

/** Real LDAP wire behavior: case-insensitive binary attribute request, optional display name and failure reasons. */
public class WireProbe {
    static final String BASE="dc=wire,dc=test",SEARCH="cn=search,"+BASE,SECRET="synthetic-search-48";
    static final AtomicInteger BINDS=new AtomicInteger(),SEARCHES=new AtomicInteger();
    static final List<Map<String,Object>> ROWS=new ArrayList<>();
    static void record(String test,Object...pairs){var r=new LinkedHashMap<String,Object>();r.put("case",test);for(int i=0;i<pairs.length;i+=2)r.put((String)pairs[i],pairs[i+1]);ROWS.add(r);System.out.println("REVIEW "+JsonMapper.builder().build().writeValueAsString(r));}
    static LdapSettings settings(String url,String id,String name,String security,String bind){return new LdapSettings(true,url,security,bind,BASE,"(uid={0})",id,name,null);}
    static Object attempt(DirectoryClient client,LdapSettings s,String who){try{return client.authenticate(s,who,"wire-person-secret").map(u->(Object)Map.of("key",u.externalId(),"display",u.displayName()==null?"<null>":u.displayName())).orElse("not-authenticated");}catch(DirectoryUnavailableException e){return e.reason();}}
    public static void main(String[]args)throws Exception{
        var cfg=new InMemoryDirectoryServerConfig(BASE);cfg.setSchema(null);cfg.setGenerateOperationalAttributes(true);cfg.addAdditionalBindCredentials(SEARCH,SECRET);cfg.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("plain",0));
        cfg.addInMemoryOperationInterceptor(new InMemoryOperationInterceptor(){public void processSimpleBindRequest(InMemoryInterceptedSimpleBindRequest r){if(!r.getRequest().getBindDN().isEmpty())BINDS.incrementAndGet();}public void processSearchRequest(InMemoryInterceptedSearchRequest r){SEARCHES.incrementAndGet();}});
        var d=new InMemoryDirectoryServer(cfg);d.add("dn: "+BASE,"objectClass: domain","dc: wire");
        var e=new Entry("uid=case-user,"+BASE);e.addAttribute("objectClass","inetOrgPerson");e.addAttribute("uid","case-user");e.addAttribute("cn","Display Case");e.addAttribute("sn","Review");e.addAttribute("userPassword","wire-person-secret");e.addAttribute("objectGUID",new byte[]{(byte)0x80,(byte)0x81});d.add(e);
        d.startListening();String url="ldap://localhost:"+d.getListenPort();var client=new DirectoryClient(new LdapProperties(SECRET,Duration.ofSeconds(2),"","","PKCS12"));
        try{
            for(String id:List.of("objectGUID","OBJECTGUID","objectguid","oBjEcTgUiD","entryUUID","uid"))record("wire-attribute","idAttribute",id,"result",attempt(client,settings(url,id,"cn","none",SEARCH),"case-user"));
            record("same-identifier-name","result",attempt(client,settings(url,"uid","UID","none",SEARCH),"case-user"));
            record("missing-attribute","result",attempt(client,settings(url,"employeeNumber","cn","none",SEARCH),"case-user"));
            d.modify("dn: uid=case-user,"+BASE,"changetype: modify","add: objectGUID","objectGUID:: gg==");
            record("multiple-values","result",attempt(client,settings(url,"objectGUID","cn","none",SEARCH),"case-user"));
            var validator=new LdapSettingsService(null,null,client,Clock.systemUTC());
            for(String id:List.of("objectGUID;binary","entryUUID;lang-en","1.2.3")){
                int status=200;Object violations=List.of();try{validator.validate(new LdapSettingsService.Input(true,url,"none",SEARCH,BASE,"(uid={0})",id,"cn"));}catch(ApiProblemException refused){status=refused.status();violations=refused.code();}
                record("attribute-options","idAttribute",id,"status",status,"code",violations);
            }
            int before=BINDS.get(),searchBefore=SEARCHES.get();Object tls;
            try{client.check(settings(url,"entryUUID","cn","starttls",SEARCH),"case-user");tls="ok";}catch(DirectoryUnavailableException refused){tls=refused.reason();}
            record("F4-no-starttls","reason",tls,"credentialBinds",BINDS.get()-before,"searches",SEARCHES.get()-searchBefore);
            int free;try(var socket=new ServerSocket(0)){free=socket.getLocalPort();}
            try{client.check(settings("ldap://localhost:"+free,"entryUUID","cn","starttls",SEARCH),null);}catch(DirectoryUnavailableException refused){record("unreachable-before-tls","reason",refused.reason());}
            try{client.check(settings(url,"entryUUID","cn","none","cn=wrong,"+BASE),null);}catch(DirectoryUnavailableException refused){record("search-bind-rejected","reason",refused.reason());}
        }finally{d.shutDown(true);Files.writeString(Path.of(args[0]),JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(ROWS)+"\n");}
    }
}
