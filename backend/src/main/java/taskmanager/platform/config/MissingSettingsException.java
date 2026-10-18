package taskmanager.platform.config;

import java.util.List;

/** Names only the missing settings, never any configured value. */
class MissingSettingsException extends IllegalStateException {
    private final List<String> missing;

    MissingSettingsException(List<String> missing) {
        super("Missing required settings: " + String.join(", ", missing));
        this.missing = List.copyOf(missing);
    }

    List<String> missing() {
        return missing;
    }
}
