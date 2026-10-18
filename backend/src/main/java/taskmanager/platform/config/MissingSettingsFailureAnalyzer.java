package taskmanager.platform.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

class MissingSettingsFailureAnalyzer extends AbstractFailureAnalyzer<MissingSettingsException> {
    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, MissingSettingsException cause) {
        return new FailureAnalysis(
            "The application cannot start because required settings are missing: " + String.join(", ", cause.missing()),
            "Set the listed environment variables (see backend/README.md) and start again.",
            cause);
    }
}
