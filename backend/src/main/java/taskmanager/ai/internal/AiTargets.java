package taskmanager.ai.internal;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Addresses of provider accesses in one canonical form and the target a stored API key is bound to (TASK-129, F1 and F3).
 * Two spellings of the same server compare equal: scheme and host in lower case and ASCII, no dot at the end of the host, no
 * default port, no slash at the end; user, query and fragment are refused. The hosts of OpenAI and Anthropic always count as
 * an external data path, and plain http is allowed only for hosts the operation released.
 */
final class AiTargets {
    /** The hosts that are always an external service, whatever an administrator states. */
    static final Set<String> EXTERNAL_HOSTS = Set.of("api.openai.com", "api.anthropic.com");

    private AiTargets() {
    }

    private static final Pattern ADDRESS = Pattern.compile("(?i)(https?)://([^/?#@\\s\\[\\]:]+|\\[[0-9A-Fa-f:.]+\\])(?::([0-9]{1,5}))?(/[A-Za-z0-9._~\\-/]*)?");

    /** The canonical form of an address: scheme, host, port and path; {@code null} when it is not an address of an access. */
    static Canonical canonical(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.strip();
        if (text.length() < 8 || text.length() > 200) {
            return null;
        }
        Matcher parts = ADDRESS.matcher(text);
        if (!parts.matches()) {
            return null;
        }
        try {
            String scheme = parts.group(1).toLowerCase(Locale.ROOT);
            String host = parts.group(2);
            if (host.startsWith("[")) {
                host = host.toLowerCase(Locale.ROOT);
            } else {
                while (host.endsWith(".")) {
                    host = host.substring(0, host.length() - 1);
                }
                host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
                if (host.isEmpty() || !host.matches("[a-z0-9]([a-z0-9.\\-]*[a-z0-9])?") || host.contains("..")) {
                    return null;
                }
            }
            int port = parts.group(3) == null ? -1 : Integer.parseInt(parts.group(3));
            if (port == 0 || port > 65535) {
                return null;
            }
            if (port == defaultPort(scheme)) {
                port = -1;
            }
            String path = parts.group(4) == null ? "" : parts.group(4);
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String url = scheme + "://" + host + (port < 0 ? "" : ":" + port) + path;
            URI checked = new URI(url);
            if (checked.getHost() == null) {
                return null;
            }
            return new Canonical(url, scheme, host, port);
        } catch (URISyntaxException | IllegalArgumentException invalid) {
            return null;
        }
    }

    /** An address in canonical form with its parts. {@code port} is -1 for the default port of the scheme. */
    record Canonical(String url, String scheme, String host, int port) {
        boolean https() {
            return scheme.equals("https");
        }

        /** Whether the operation released this host, as {@code host} or {@code host:port}, for plain http. */
        boolean released(java.util.List<String> plainHttpHosts) {
            int effective = port < 0 ? defaultPort(scheme) : port;
            return plainHttpHosts.contains(host) || plainHttpHosts.contains(host + ":" + effective);
        }

        /** Whether the host belongs to OpenAI or Anthropic, which is always an external data path. */
        boolean external() {
            return EXTERNAL_HOSTS.contains(host);
        }
    }

    /** The fingerprint of the target a key is bound to: the canonical address and the own CA of the access. */
    static String target(String baseUrl, String caCertificate) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((baseUrl + "\n" + (caCertificate == null ? "" : caCertificate)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is not available", missing);
        }
    }

    private static int defaultPort(String scheme) {
        return scheme.equals("https") ? 443 : 80;
    }
}
