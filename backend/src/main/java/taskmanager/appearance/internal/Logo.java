package taskmanager.appearance.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Accepted logo formats. The type is recognized from the leading bytes, never trusted from the request alone.
 * SVG is not accepted: it can carry script and would need sanitizing before it is safe to serve.
 *
 * <p>A recognized start is not enough (TASK-043, finding F2 of the TASK-036 review): PNG and JPEG are decoded completely
 * with the image readers of the JDK, and a reader warning such as a premature end counts as a defect. The JDK has no WebP
 * reader, so a WebP file is checked for its structure instead: the RIFF length matches the file, every chunk lies inside it
 * and an image chunk with a valid header and size is present. A full WebP decode needs a library, which is not chosen.
 */
public final class Logo {
    public static final int MAX_BYTES = 512 * 1024;
    /** Larger images are refused before they are decoded, so a small file cannot unpack into a huge bitmap. */
    static final int MAX_SIDE = 4096;
    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' };
    /** The closing chunk of every PNG; the JDK reader does not miss it, so it is checked here. */
    private static final byte[] PNG_END = { 0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xae, 0x42, 0x60, (byte) 0x82 };

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

    /** The type of a complete, usable image of one of the accepted formats; empty for anything else. */
    public static Optional<String> validType(byte[] content) {
        return detectType(content).filter(type -> switch (type) {
            case "image/png" -> content.length > PNG_END.length
                && Arrays.equals(content, content.length - PNG_END.length, content.length, PNG_END, 0, PNG_END.length)
                && decodes(content, "png");
            case "image/jpeg" -> decodes(content, "jpeg");
            default -> webp(content);
        });
    }

    private static boolean decodes(byte[] content, String format) {
        var readers = ImageIO.getImageReadersByFormatName(format);
        if (!readers.hasNext()) {
            return false;
        }
        ImageReader reader = readers.next();
        AtomicBoolean warned = new AtomicBoolean();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            reader.setInput(input, true, true);
            reader.addIIOReadWarningListener((source, warning) -> warned.set(true));
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE) {
                return false;
            }
            // For JPEG the raster, not a color converted image: a JPEG in CMYK is a valid logo the JDK cannot convert.
            var raster = reader.canReadRaster() ? reader.readRaster(0, null) : reader.read(0).getRaster();
            return !warned.get() && raster.getWidth() == width && raster.getHeight() == height;
        } catch (IOException | RuntimeException defect) {
            return false;
        } finally {
            reader.dispose();
        }
    }

    private static boolean webp(byte[] content) {
        if (content.length < 20 || le32(content, 4) != content.length - 8) {
            return false;
        }
        boolean image = false;
        int offset = 12;
        while (offset < content.length) {
            if (offset + 8 > content.length) {
                return false;
            }
            String chunk = new String(content, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long size = le32(content, offset + 4);
            int data = offset + 8;
            if (size > content.length - data) {
                return false;
            }
            switch (chunk) {
                case "VP8 " -> image |= vp8(content, data, (int) size);
                case "VP8L" -> image |= vp8l(content, data, (int) size);
                case "VP8X" -> {
                    if (size < 10 || offset != 12) return false;
                    int width = 1 + le24(content, data + 4), height = 1 + le24(content, data + 7);
                    if (width > MAX_SIDE || height > MAX_SIDE) return false;
                }
                case "ANMF" -> image |= size >= 16;
                default -> { }
            }
            // Chunks are padded to an even length.
            offset = data + (int) size + (int) (size & 1);
        }
        return image && offset == content.length;
    }

    /** Lossy: a key frame with the start code and a size inside the limits. */
    private static boolean vp8(byte[] content, int data, int size) {
        if (size < 10 || (content[data] & 1) != 0 || (content[data + 3] & 0xff) != 0x9d
            || (content[data + 4] & 0xff) != 0x01 || (content[data + 5] & 0xff) != 0x2a) {
            return false;
        }
        int width = le16(content, data + 6) & 0x3fff, height = le16(content, data + 8) & 0x3fff;
        return width > 0 && height > 0 && width <= MAX_SIDE && height <= MAX_SIDE;
    }

    /** Lossless: the signature byte and a size inside the limits. */
    private static boolean vp8l(byte[] content, int data, int size) {
        if (size < 5 || (content[data] & 0xff) != 0x2f) {
            return false;
        }
        long bits = le32(content, data + 1);
        int width = (int) (bits & 0x3fff) + 1, height = (int) ((bits >> 14) & 0x3fff) + 1;
        return width <= MAX_SIDE && height <= MAX_SIDE;
    }

    private static int le16(byte[] b, int at) {
        return (b[at] & 0xff) | (b[at + 1] & 0xff) << 8;
    }

    private static int le24(byte[] b, int at) {
        return le16(b, at) | (b[at + 2] & 0xff) << 16;
    }

    private static long le32(byte[] b, int at) {
        return (le24(b, at) | (long) (b[at + 3] & 0xff) << 24) & 0xffffffffL;
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        return content.length >= prefix.length && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
    }
}
