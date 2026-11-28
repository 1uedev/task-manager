import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import tools.jackson.databind.json.JsonMapper;

/** Populated V0020 -> V0021 upgrade on a fresh disposable database; synthetic hash placeholders only. */
public class MigrationProbe {
    public static void main(String[]args)throws Exception{
        String url=System.getenv("TASK_MANAGER_DB_URL"),user=System.getenv("TASK_MANAGER_DB_USER"),password=System.getenv("TASK_MANAGER_DB_PASSWORD");
        Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").target("20").load().migrate();
        UUID id=UUID.randomUUID();
        try(var c=DriverManager.getConnection(url,user,password);var p=c.prepareStatement("insert into identity_account(id,username,password_hash,password_change_required,active,created_at,updated_at) values (?,'pre-ldap-local','synthetic-hash-placeholder',false,true,now(),now())")){
            p.setObject(1,id);p.executeUpdate();
        }
        Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").load().migrate();
        var result=new LinkedHashMap<String,Object>();
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
            var r=s.executeQuery("select auth_kind,external_id,password_hash from identity_account where username='pre-ldap-local'");r.next();
            result.put("existingKind",r.getString(1));result.put("existingExternalIdAbsent",r.getString(2)==null);result.put("existingHashUnchanged",r.getString(3).equals("synthetic-hash-placeholder"));r.close();
            var cases=new LinkedHashMap<String,String>();
            cases.put("invalid-kind","update identity_account set auth_kind='other' where username='pre-ldap-local'");
            cases.put("ldap-without-id","update identity_account set auth_kind='ldap' where username='pre-ldap-local'");
            cases.put("local-with-id","update identity_account set external_id='entry-46' where username='pre-ldap-local'");
            cases.put("empty-id","update identity_account set auth_kind='ldap',external_id='' where username='pre-ldap-local'");
            cases.put("long-id","update identity_account set auth_kind='ldap',external_id=repeat('x',513) where username='pre-ldap-local'");
            var rejected=new LinkedHashMap<String,String>();for(var entry:cases.entrySet()){try{s.executeUpdate(entry.getValue());throw new AssertionError("constraint missed "+entry.getKey());}catch(SQLException expected){rejected.put(entry.getKey(),expected.getSQLState());}}
            result.put("constraints",rejected);
            s.executeUpdate("update identity_account set auth_kind='ldap',external_id='entry-46' where username='pre-ldap-local'");
            try{s.executeUpdate("insert into identity_account(id,username,password_hash,password_change_required,active,created_at,updated_at,auth_kind,external_id) values ('"+UUID.randomUUID()+"','duplicate-id','synthetic',false,true,now(),now(),'ldap','entry-46')");throw new AssertionError("unique external id");}catch(SQLException expected){result.put("duplicateExternalIdSqlState",expected.getSQLState());}
            try{s.executeUpdate("insert into identity_ldap_setting(id,enabled,url,security,search_base,user_filter,id_attribute,updated_at) values(2,false,'ldap://localhost','none','dc=test','(uid={0})','entryUUID',now())");throw new AssertionError("singleton check");}catch(SQLException expected){result.put("secondSettingsRowSqlState",expected.getSQLState());}
        }
        Files.writeString(Path.of(args[0]),JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(result)+"\n");System.out.println("REVIEW "+result);
    }
}
