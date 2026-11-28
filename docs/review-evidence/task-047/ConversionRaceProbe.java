package taskmanager.identity;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import taskmanager.TaskManagerApplication;
import taskmanager.identity.account.*;

/** Pauses only the scheduler gap AFTER an actual external-id read; no product result or argument is changed. */
public class ConversionRaceProbe extends ProbeSupport {
    static final CountDownLatch SELECTED=new CountDownLatch(1),RESUME=new CountDownLatch(1);
    static final AtomicBoolean ARMED=new AtomicBoolean();
    static volatile String external;
    public static void main(String[]args)throws Exception{
        var rows=new ArrayList<Map<String,Object>>();var d=directory();
        var application=new SpringApplication(TaskManagerApplication.class);
        application.addInitializers(ctx->ctx.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor(){
            @Override public Object postProcessAfterInitialization(Object bean,String name){
                if(!(bean instanceof AccountRepository))return bean;
                var proxy=new ProxyFactory(bean);proxy.setProxyTargetClass(true);
                proxy.addAdvice((MethodInterceptor) invocation->{
                    Object result=invocation.proceed();
                    if(invocation.getMethod().getName().equals("findByExternalId")&&Objects.equals(external,invocation.getArguments()[0])&&ARMED.compareAndSet(true,false)){
                        ok(((Optional<?>)result).isPresent(),"old directory mapping selected");SELECTED.countDown();
                        ok(RESUME.await(30,TimeUnit.SECONDS),"scheduler pause timed out");
                    }
                    return result;
                });return proxy.getProxy();
            }
        }));
        try(var ctx=application.run("--server.port=0","--spring.main.banner-mode=off","--logging.level.root=ERROR","--taskmanager.identity.source-failure-threshold=100000","--taskmanager.identity.ldap.bind-password="+SECRET)){
            int port=Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));var admin=bootstrap(ctx,port);
            var repo=ctx.getBean(AccountRepository.class);var jdbc=ctx.getBean(JdbcTemplate.class);
            ok(admin.put("/api/v1/settings/ldap",settings("ldap://localhost:"+d.getListenPort(),"entryUUID")).status()==200,"LDAP configured");
            person(d,"race-person","only-directory-secret",null,null);var old=logged(port,"race-person","only-directory-secret");
            AccountRecord target=repo.findByUsername("race-person").orElseThrow();external=target.externalId();
            grant(admin,target.id(),"Retained conversion authority","accounts.manage");
            var waitingBrowser=browser(port);ARMED.set(true);
            Future<ApiClient.Response> pending=POOL.submit(()->waitingBrowser.post("/api/v1/session",Map.of("username","race-person","password","only-directory-secret")));
            ok(SELECTED.await(8,TimeUnit.SECONDS),"LDAP lookup reached scheduling gap");
            var converted=admin.post("/api/v1/accounts/"+target.id()+"/convert-to-local",Map.of());ok(converted.status()==200,"conversion committed");
            String otp=converted.json().get("oneTimePassword").asString();var rightful=logged(port,"race-person",otp);
            ok(rightful.post("/api/v1/session/password",Map.of("currentPassword",otp,"newPassword",PASSWORD)).status()==200,"local forced change committed");
            record(rows,"before-resume","kind",repo.findById(target.id()).orElseThrow().authKind(),"externalCleared",repo.findById(target.id()).orElseThrow().externalId()==null,"localPasswordChangeComplete",!repo.findById(target.id()).orElseThrow().passwordChangeRequired(),"oldSessionAuthenticated",old.get("/api/v1/session").json().get("authenticated"));
            RESUME.countDown();var issued=pending.get(10,TimeUnit.SECONDS);
            record(rows,"ldap-after-conversion-and-password-change","status",issued.status(),"level",issued.json().get("level"),"authKind",issued.json().get("authKind"),"username",issued.json().get("username"),"accountsStatus",waitingBrowser.get("/api/v1/accounts").status(),"freshDirectoryPasswordStatus",login(port,"race-person","only-directory-secret").status(),"localAccountCount",jdbc.queryForObject("select count(*) from identity_account where username='race-person' and auth_kind='local'",Integer.class));
        }finally{RESUME.countDown();POOL.shutdownNow();d.shutDown(true);save(args[0],rows);}
    }
}
