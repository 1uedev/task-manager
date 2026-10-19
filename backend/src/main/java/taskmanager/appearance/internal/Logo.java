package taskmanager.appearance.internal;

import java.util.Arrays;
import java.util.Optional;

/**
 * Accepted logo formats. The type is recognized from the leading bytes, never trusted from the request alone.
 * SVG is not accepted: it can carry script and would need sanitizing before it is safe to serve.
 */
public final class Logo {
    public static final int MAX_BYTES = 512 * 1024;
    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' };

    private Logo() {
    }

    public static Optional<String> detectType(byte[] content) {
        if (startsWith(content, PNG)) {
            return Optional.of("image/png");
        }
        if (content.length >= 3 && (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff) {
            return Optional.of("image/jpeg");
        }
        if (content.length >= 12 && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
            && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        return content.length >= prefix.length && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
    }
}
