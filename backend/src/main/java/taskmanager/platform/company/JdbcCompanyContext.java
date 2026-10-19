package taskmanager.platform.company;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.platform.CompanyContext;

/** Reads the identifier on every call; it is never cached, so a bootstrapped installation is seen at once. */
@Service
class JdbcCompanyContext implements CompanyContext {
    private final JdbcClient jdbc;

    JdbcCompanyContext(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> find() {
        return jdbc.sql("select id from platform_company").query(UUID.class).optional();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID create() {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into platform_company (id) values (?)").param(id).update();
        return id;
    }
}
