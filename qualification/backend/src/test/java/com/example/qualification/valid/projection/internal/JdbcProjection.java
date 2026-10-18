package com.example.qualification.valid.projection.internal;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.example.qualification.valid.projection.Projection;

@Service
class JdbcProjection implements Projection {
    private final JdbcTemplate jdbc;
    JdbcProjection(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    @Transactional
    public void index(UUID id, String text) {
        jdbc.update("insert into probe_projection (record_id, body) values (?, ?)", id, text);
    }
}
