package com.example.qualification;

import com.example.qualification.valid.Composition;
import com.example.qualification.valid.ProbeApplication;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = false)
@SpringBootTest(classes = ProbeApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TransactionQualificationTest {
    // Official postgres:18.6 manifest. Digest references need an explicit image-family declaration.
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer(
        DockerImageName.parse("postgres@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336")
            .asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired Composition composition;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void clearFixtures() {
        jdbc.update("delete from probe_projection");
        jdbc.update("delete from probe_record");
    }
    private void assertCounts(int expected) {
        assertEquals(expected, jdbc.queryForObject("select count(*) from probe_record", Integer.class));
        assertEquals(expected, jdbc.queryForObject("select count(*) from probe_projection", Integer.class));
    }
    @Test void migrationAndDatabaseVersionAreReal() {
        assertTrue(jdbc.queryForObject("show server_version", String.class).startsWith("18.6"));
        assertEquals(1, jdbc.queryForObject("select count(*) from flyway_schema_history where success = true", Integer.class));
    }
    @Test void jpaAndJdbcCommitTogether() {
        UUID id = UUID.randomUUID();
        composition.create(id, "Synthetic record", "Visible search text", false);
        assertCounts(1);
        assertEquals("Visible search text", jdbc.queryForObject("select body from probe_projection where record_id = ?", String.class, id));
    }
    @Test void applicationFailureRollsBackBothWrites() {
        assertThrows(IllegalStateException.class, () -> composition.create(UUID.randomUUID(), "rollback", "also rollback", true));
        assertCounts(0);
    }
    @Test void jdbcConstraintFailureAlsoRollsBackFlushedJpaRow() {
        assertThrows(DataIntegrityViolationException.class, () -> composition.create(UUID.randomUUID(), "rollback", null, false));
        assertCounts(0);
    }
}
