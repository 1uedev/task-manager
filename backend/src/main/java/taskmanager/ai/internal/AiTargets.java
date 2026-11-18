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
                host = ipv6(host);
                if (host == null) {
                    return null;
                }
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

    /**
     * The one form of an IPv6 literal with brackets (RFC 5952): lower case, no leading zeros in a group, the longest run of at
     * least two zero groups written as {@code ::}, an IPv4 tail turned into two groups. No name is resolved. {@code null} for
     * anything that is not a valid literal (TASK-130, H1).
     */
    static String ipv6(String bracketed) {
        if (bracketed == null || bracketed.length() < 4 || bracketed.charAt(0) != '[' || bracketed.charAt(bracketed.length() - 1) != ']') {
            return null;
        }
        String text = bracketed.substring(1, bracketed.length() - 1).toLowerCase(Locale.ROOT);
        int[] groups = new int[8];
        int gap = text.indexOf("::");
        if (gap >= 0 && text.indexOf("::", gap + 1) >= 0) {
            return null;
        }
        java.util.List<Integer> head = parseGroups(gap < 0 ? text : text.substring(0, gap), gap < 0);
        java.util.List<Integer> tail = gap < 0 ? java.util.List.of() : parseGroups(text.substring(gap + 2), true);
        if (head == null || tail == null) {
            return null;
        }
        if (gap < 0 ? head.size() != 8 : head.size() + tail.size() > 7) {
            return null;
        }
        for (int i = 0; i < head.size(); i++) {
            groups[i] = head.get(i);
        }
        for (int i = 0; i < tail.size(); i++) {
            groups[8 - tail.size() + i] = tail.get(i);
        }
        int bestStart = -1;
        int bestLength = 0;
        for (int i = 0; i < 8; ) {
            if (groups[i] != 0) {
                i++;
                continue;
            }
            int j = i;
            while (j < 8 && groups[j] == 0) {
                j++;
            }
            if (j - i > bestLength) {
                bestStart = i;
                bestLength = j - i;
            }
            i = j;
        }
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < 8; i++) {
            if (bestLength >= 2 && i == bestStart) {
                out.append(i == 0 ? "::" : ":");
                i += bestLength - 1;
                continue;
            }
            out.append(Integer.toHexString(groups[i]));
            if (i < 7) {
                out.append(':');
            }
        }
        return out.append(']').toString();
    }

    /** Groups of 16 bits separated by colons; the last one may be a dotted IPv4 address when {@code allowV4}. */
    private static java.util.List<Integer> parseGroups(String text, boolean allowV4) {
        java.util.List<Integer> groups = new java.util.ArrayList<>();
        if (text.isEmpty()) {
            return groups;
        }
        String[] parts = text.split(":", -1);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.contains(".")) {
                if (!allowV4 || i != parts.length - 1 || !part.matches("(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])(\\.(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])){3}")) {
                    return null;
                }
                String[] octets = part.split("\\.");
                groups.add(Integer.parseInt(octets[0]) << 8 | Integer.parseInt(octets[1]));
                groups.add(Integer.parseInt(octets[2]) << 8 | Integer.parseInt(octets[3]));
            } else if (part.matches("[0-9a-f]{1,4}")) {
                groups.add(Integer.parseInt(part, 16));
            } else {
                return null;
            }
        }
        return groups;
    }

    /** The canonical form of an entry of the plain http release: {@code host} or {@code host:port}, IPv6 with brackets. */
    static String releasedEntry(String entry) {
        int close = entry.indexOf(']');
        if (entry.startsWith("[") && close > 0) {
            String host = ipv6(entry.substring(0, close + 1));
            return host == null ? null : host + entry.substring(close + 1);
        }
        return entry.toLowerCase(Locale.ROOT);
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
