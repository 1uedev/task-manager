package taskmanager.operations;

/**
 * The schema of the database does not fit this image, or the setting of the schema mode is wrong (TASK-132, O11). Public,
 * so that the entry point can answer the command {@code migrate} with exit code 2 instead of a stack trace.
 */
public class SchemaVersionException extends IllegalStateException {
    public SchemaVersionException(String message) {
        super(message);
    }
}
