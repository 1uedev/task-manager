package taskmanager.permissions.internal;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import taskmanager.permissions.GroupDirectory;

/** Reads groups and memberships for {@link GroupDirectory}; groups are never deleted, so a stored identifier stays valid. */
@Repository
class JdbcGroupDirectory implements GroupDirectory {
    private final JdbcTemplate jdbc;

    JdbcGroupDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Summary> groups(UUID companyId) {
        return jdbc.query("select id, name from permissions_group where company_id = ? order by lower(name), id",
            (rs, row) -> new Summary(rs.getObject("id", UUID.class), rs.getString("name")), companyId);
    }

    @Override
    public Set<UUID> groupsOf(UUID companyId, UUID accountId) {
        return new HashSet<>(jdbc.queryForList("select group_id from permissions_membership where company_id = ? and user_id = ?", UUID.class,
            companyId, accountId));
    }
}
