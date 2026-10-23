package taskmanager.identity.directory;

/** The directory could not answer. {@code reason} is one of a fixed vocabulary and never contains a secret or a server message. */
public class DirectoryUnavailableException extends RuntimeException {
    private final String reason;

    public DirectoryUnavailableException(String reason, Throwable cause) {
        super(reason);
        this.reason = reason;
        initCause(cause);
    }

    public DirectoryUnavailableException(String reason) {
        this(reason, null);
    }

    public String reason() {
        return reason;
    }
}
