package taskmanager.platform.web;

/** Initial technical problem codes. Clients branch on these codes, never on localized titles. */
final class ProblemCode {
    private ProblemCode() {
    }

    static String forStatus(int status) {
        return switch (status) {
            case 400 -> "request.invalid";
            case 404 -> "resource.not_found";
            case 405 -> "request.method_not_allowed";
            case 406 -> "request.not_acceptable";
            case 413 -> "request.too_large";
            case 415 -> "request.unsupported_media_type";
            case 503 -> "server.unavailable";
            default -> status >= 500 ? "server.internal_error" : "request.rejected";
        };
    }
}
