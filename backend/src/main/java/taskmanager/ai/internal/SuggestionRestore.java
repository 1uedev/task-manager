package taskmanager.ai.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import taskmanager.ai.AiRestore;

/** Discards the kept suggestions after a restore (TASK-131, rule 10); the record of the runs stays. */
@Component
class SuggestionRestore implements AiRestore {
    private final JdbcClient jdbc;

    SuggestionRestore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public int discardSuggestions() {
        return jdbc.sql("delete from ai_suggestion").update();
    }
}
