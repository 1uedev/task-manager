package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Migration V0066 (TASK-114, DEC-144, F6 of docs/REVIEW_TASK-112_PR-317.md): entries of Git links in the histories of tickets
 * and requirements keep only the kind of the object, never the internal identifier of the link; other entries stay as they
 * are. Synthetic data only.
 */
class GitHistoryMigrationTest {
    private static final Instant T0 = Instant.parse("2026-11-14T10:00:00Z");

    @Test
    void gitEntriesKeepTheKindOnly() {
        String url = createDatabase();
        migrate(url, "0065");
        JdbcTemplate db = db(url);
        UUID company = UUID.randomUUID(), project = UUID.randomUUID(), ticket = UUID.randomUUID(), requirement = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        String link = "commit:" + UUID.randomUUID();
        db.update("insert into ticket (id, company_id, project_id, number, title, type, status, configuration_version, version, created_at, "
            + "created_by, updated_at, updated_by) values (?, ?, ?, 1, 'Anmeldung', 'task', 'new', 1, 1, ?, ?, ?, ?)",
            ticket, company, project, Timestamp.from(T0), actor, Timestamp.from(T0), actor);
        db.update("insert into ticket_change (ticket_id, version, occurred_at, actor_account_id, field, old_value, new_value) values (?, 1, ?, ?, 'git', null, ?)",
            ticket, Timestamp.from(T0), actor, link);
        db.update("insert into ticket_change (ticket_id, version, occurred_at, actor_account_id, field, old_value, new_value) values (?, 1, ?, ?, 'git', ?, null)",
            ticket, Timestamp.from(T0.plusSeconds(60)), actor, "pull_request:" + UUID.randomUUID());
        db.update("insert into ticket_change (ticket_id, version, occurred_at, actor_account_id, field, old_value, new_value) values (?, 1, ?, ?, 'title', 'a:b', 'c:d')",
            ticket, Timestamp.from(T0.plusSeconds(120)), actor);
        db.update("""
                insert into requirement (id, company_id, project_id, number, status, current_revision, next_criterion, created_at, created_by,
                    updated_at, updated_by, version)
                values (?, ?, ?, 1, 'draft', 1, 1, ?, ?, ?, ?, 1)
                """, requirement, company, project, Timestamp.from(T0), actor, Timestamp.from(T0), actor);
        db.update("""
                insert into requirement_event (company_id, requirement_id, revision, kind, field, from_value, to_value, actor_id, occurred_at)
                values (?, ?, 1, 'git_linked', 'git', null, ?, ?, ?), (?, ?, 1, 'git_unlinked', 'git', ?, null, ?, ?),
                    (?, ?, 1, 'changed', 'title', 'x:y', 'z:w', ?, ?)
                """, company, requirement, "merge_request:" + UUID.randomUUID(), actor, Timestamp.from(T0), company, requirement,
            "repository:" + UUID.randomUUID(), actor, Timestamp.from(T0), company, requirement, actor, Timestamp.from(T0));

        migrate(url, null);

        assertEquals(List.of(Arrays.asList(null, "commit"), Arrays.asList("pull_request", null), List.of("a:b", "c:d")),
            db.query("select old_value, new_value from ticket_change where ticket_id = ? order by id",
                (rs, row) -> Arrays.asList(rs.getString(1), rs.getString(2)), ticket));
        assertEquals(List.of(Arrays.asList(null, "merge_request"), Arrays.asList("repository", null), List.of("x:y", "z:w")),
            db.query("select from_value, to_value from requirement_event where requirement_id = ? order by id",
                (rs, row) -> Arrays.asList(rs.getString(1), rs.getString(2)), requirement));
    }

    private static String createDatabase() {
        try {
            return TestDatabase.createEmptyDatabase();
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void migrate(String url, String target) {
        var configuration = Flyway.configure().dataSource(url, TestDatabase.username(), TestDatabase.password());
        (target == null ? configuration : configuration.target(target)).load().migrate();
    }

    private static JdbcTemplate db(String url) {
        return new JdbcTemplate(new DriverManagerDataSource(url, TestDatabase.username(), TestDatabase.password()));
    }
}
