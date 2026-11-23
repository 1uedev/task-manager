package taskmanager.operations.internal;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import taskmanager.operations.SchemaVersionException;

/** Shows the refusal of a schema that does not fit as one clear message instead of a stack trace (TASK-132, O11). */
class SchemaVersionFailureAnalyzer extends AbstractFailureAnalyzer<SchemaVersionException> {
    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, SchemaVersionException cause) {
        return new FailureAnalysis(cause.getMessage(),
            "Follow the update procedure in docs/INSTALL.md: backup point, stop, 'migrate' once, start.", cause);
    }
}
