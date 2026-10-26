package taskmanager.files.internal;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import taskmanager.platform.ApiProblemException;

/**
 * What may be uploaded (TASK-051, DEC-080): the cleaned file name and the refusal of executable and active content. There is
 * no virus scanner (OPEN-014); the refusal by extension and by the first bytes, the delivery only as a download and the
 * isolation of the stored content are the measures of this stage.
 */
final class UploadRules {
    static final int NAME_MAX = 255;
    private final long maxBytes;
    private final Set<String> blocked;

    UploadRules(long maxBytes, List<String> blockedExtensions) {
        this.maxBytes = maxBytes;
        this.blocked = blockedExtensions.stream().map(extension -> extension.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    long maxBytes() {
        return maxBytes;
    }

    /**
     * The name without path, control and format characters (the latter include the characters that reverse the direction of
     * the text and disguise an extension), trailing dots and spaces removed. Anything that stays empty or too long is refused.
     */
    static String clean(String raw) {
        if (raw == null) {
            throw invalidName();
        }
        String name = Normalizer.normalize(raw, Normalizer.Form.NFC);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        StringBuilder cleaned = new StringBuilder();
        name.codePoints().filter(point -> {
            int type = Character.getType(point);
            return type != Character.CONTROL && type != Character.FORMAT && type != Character.SURROGATE && type != Character.UNASSIGNED
                && type != Character.PRIVATE_USE && type != Character.LINE_SEPARATOR && type != Character.PARAGRAPH_SEPARATOR;
        }).forEach(cleaned::appendCodePoint);
        String result = cleaned.toString().strip();
        while (result.endsWith(".") || result.endsWith(" ")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.isEmpty() || result.codePointCount(0, result.length()) > NAME_MAX) {
            throw invalidName();
        }
        return result;
    }

    /** Refuses a name with a blocked extension. */
    void checkName(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && blocked.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT))) {
            throw ApiProblemException.of(415, "attachment.type_blocked");
        }
    }

    /** Refuses content that starts like a program, whatever the name says. */
    static void checkHead(byte[] head) {
        if (startsWith(head, 0x4D, 0x5A)                                   // MZ: Windows program
            || startsWith(head, 0x7F, 0x45, 0x4C, 0x46)                    // ELF: Linux program
            || startsWith(head, 0x23, 0x21)                                // #!: script with interpreter
            || startsWith(head, 0xFE, 0xED, 0xFA, 0xCE) || startsWith(head, 0xFE, 0xED, 0xFA, 0xCF)
            || startsWith(head, 0xCE, 0xFA, 0xED, 0xFE) || startsWith(head, 0xCF, 0xFA, 0xED, 0xFE)   // Mach-O
            || startsWith(head, 0xCA, 0xFE, 0xBA, 0xBE)) {                // Java class or universal binary
            throw ApiProblemException.of(415, "attachment.type_blocked");
        }
    }

    private static boolean startsWith(byte[] head, int... signature) {
        if (head.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((head[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static ApiProblemException invalidName() {
        return ApiProblemException.withViolations(400, "request.invalid", List.of("name.invalid"));
    }
}
