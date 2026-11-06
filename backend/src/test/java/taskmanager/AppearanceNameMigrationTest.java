package taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Migration V0054 (TASK-087, DEC-117; finding F1 and hint H1 of docs/REVIEW_TASK-081_PR-243.md): a name V0046 let through
 * loses the spaces of any kind at its edges, and a name that is still not valid then becomes the product name again, so the
 * interface accepts every stored appearance. Synthetic names only.
 */
class AppearanceNameMigrationTest {
    @Test
    void storedNamesFollowTheRuleOfTheServerAfterTheUpgrade() throws Exception {
        Map<String, String> expected = new java.util.LinkedHashMap<>();
        expected.put("\u00a0Acme\u00a0", "Acme");
        expected.put("\u2007\u202fNordlicht GmbH\u3000", "Nordlicht GmbH");
        expected.put("Acme\u00a0GmbH", "Acme\u00a0GmbH");
        expected.put("\u00a0", "Task Manager");
        expected.put("A\u200bB", "Task Manager");
        expected.put("A\u2028B", "Task Manager");
        expected.put("\u00a0" + "x".repeat(78) + "\u00a0", "x".repeat(78));
        for (var entry : expected.entrySet()) {
            String url = TestDatabase.createEmptyDatabase();
            migrate(url, "0052");
            JdbcTemplate db = db(url);
            db.update("update appearance_setting set name = ? where id = 1", entry.getKey());
            migrate(url, null);
            assertEquals(entry.getValue(), db.queryForObject("select name from appearance_setting where id = 1", String.class),
                entry.getKey().codePoints().mapToObj(c -> String.format("U+%04X", c)).toList().toString());
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
