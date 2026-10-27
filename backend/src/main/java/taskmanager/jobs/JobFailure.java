package taskmanager.jobs;

import java.util.regex.Pattern;

/**
 * A failure of a job with a stable code that the administration sees (TASK-054). The message is written to the log under a
 * reference and never stored with the job, so it may not reach the interface; the code must not carry secrets either.
 */
public class JobFailure extends RuntimeException {
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_.]{0,63}");
    private final String code;
    private final boolean permanent;

    private JobFailure(String code, boolean permanent, String message, Throwable cause) {
        super(message, cause);
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("A failure code is lower case, starts with a letter and has at most 64 characters");
        }
        this.code = code;
        this.permanent = permanent;
    }

    /** A failure that may go away: the job runs again after a delay, unless this was its last attempt. */
    public static JobFailure retryable(String code, String message, Throwable cause) {
        return new JobFailure(code, false, message, cause);
    }

    /** A failure that does not go away by trying again: the job fails for good at once. */
    public static JobFailure permanent(String code, String message, Throwable cause) {
        return new JobFailure(code, true, message, cause);
    }

    public String code() {
        return code;
    }

    public boolean permanent() {
        return permanent;
    }
}
