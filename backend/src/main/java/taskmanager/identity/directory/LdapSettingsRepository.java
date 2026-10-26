package taskmanager.identity.directory;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import taskmanager.identity.account.Db;

/** The one row of the directory connection. */
@Repository
class LdapSettingsRepository {
    private final JdbcClient jdbc;

    LdapSettingsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<LdapSettings> find() {
        return jdbc.sql("select enabled, url, security, bind_dn, search_base, user_filter, id_attribute, name_attribute, updated_at from identity_ldap_setting where id = 1")
            .query((rs, row) -> new LdapSettings(rs.getBoolean("enabled"), rs.getString("url"), rs.getString("security"),
                rs.getString("bind_dn"), rs.getString("search_base"), rs.getString("user_filter"), rs.getString("id_attribute"),
                rs.getString("name_attribute"), Db.instant(rs, "updated_at")))
            .optional();
    }

    void save(LdapSettings s, Instant now) {
        jdbc.sql("""
                insert into identity_ldap_setting (id, enabled, url, security, bind_dn, search_base, user_filter, id_attribute, name_attribute, updated_at)
                values (1, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set enabled = excluded.enabled, url = excluded.url, security = excluded.security,
                    bind_dn = excluded.bind_dn, search_base = excluded.search_base, user_filter = excluded.user_filter,
                    id_attribute = excluded.id_attribute, name_attribute = excluded.name_attribute, updated_at = excluded.updated_at
                """)
            .params(s.enabled(), s.url(), s.security(), s.bindDn(), s.searchBase(), s.userFilter(), s.idAttribute(), s.nameAttribute(), Db.ts(now))
            .update();
    }
}
