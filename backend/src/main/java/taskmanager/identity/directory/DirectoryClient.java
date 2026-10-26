package taskmanager.identity.directory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import javax.naming.AuthenticationException;
import javax.naming.CompositeName;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.SizeLimitExceededException;
import javax.naming.directory.Attribute;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.stereotype.Component;
import taskmanager.identity.config.LdapProperties;

/**
 * Talks LDAPv3 to the directory with the JNDI provider of the Java runtime (TASK-046, DEC-075). Nothing is kept between calls:
 * every call opens its own connections and closes them. The login name only ever reaches the directory as an argument of the
 * search filter, where JNDI escapes it, and the password only reaches it in the bind as the person; neither is logged or
 * stored. The answer of the directory is reduced to the identifier of the entry and a display name.
 *
 * <p>A failure of the directory is a {@link DirectoryUnavailableException} with a reason of a fixed vocabulary:
 * {@code directory.unreachable}, {@code directory.tls_failed}, {@code directory.bind_failed} (the search account),
 * {@code directory.bind_password_missing}, {@code directory.search_failed} and {@code directory.id_attribute_missing}.
 * A wrong password or an unknown or ambiguous login name is not a failure of the directory; it is an empty answer.
 */
@Component
public class DirectoryClient {
    private static final String CONTEXT_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    private static final int MAX_PASSWORD_LENGTH = 1024;
    private static final int MAX_NAME_LENGTH = 100;

    /** What the directory knows about a person: the immutable identifier and a display name (null when it has none). */
    public record DirectoryUser(String externalId, String displayName) {
    }

    private enum Outcome { NONE, AMBIGUOUS, FOUND }

    private record Match(Outcome outcome, String dn, DirectoryUser user) {
        static final Match NONE = new Match(Outcome.NONE, null, null);
        static final Match AMBIGUOUS = new Match(Outcome.AMBIGUOUS, null, null);
    }

    private final LdapProperties properties;
    private final SSLSocketFactory tls;

    DirectoryClient(LdapProperties properties) {
        this.properties = properties;
        this.tls = socketFactory(properties);
        DirectorySocketFactory.use(tls);
    }

    private static SSLSocketFactory socketFactory(LdapProperties properties) {
        try {
            if (properties.trustStore().isEmpty()) {
                return (SSLSocketFactory) SSLSocketFactory.getDefault();
            }
            KeyStore store = KeyStore.getInstance(properties.trustStoreType());
            try (InputStream in = new FileInputStream(properties.trustStore())) {
                store.load(in, properties.trustStorePassword().toCharArray());
            }
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(store);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trust.getTrustManagers(), null);
            return context.getSocketFactory();
        } catch (IOException | GeneralSecurityException e) {
            // A trust store that cannot be read is a deployment error; the application must not start with silently weaker trust.
            throw new IllegalStateException("The trust store of the directory connection cannot be read", e);
        }
    }

    public boolean bindPasswordSet() {
        return properties.bindPasswordSet();
    }

    /**
     * Searches the entry of the login name and checks the password by binding as that entry. Empty when the name matches no entry
     * or more than one, or when the password is wrong or empty (an empty password would be an unauthenticated bind).
     */
    public Optional<DirectoryUser> authenticate(LdapSettings settings, String loginName, String password) {
        if (password.isEmpty() || password.length() > MAX_PASSWORD_LENGTH) {
            return Optional.empty();
        }
        Match match = search(settings, loginName);
        if (match.outcome() != Outcome.FOUND) {
            return Optional.empty();
        }
        try (Connection person = open(settings, match.dn(), password)) {
            return Optional.of(match.user());
        } catch (AuthenticationException wrongPassword) {
            return Optional.empty();
        } catch (NamingException e) {
            throw unavailable(e);
        }
    }

    /**
     * Checks the connection step by step without a password of a person: connecting, the encryption, the bind of the search account
     * and the search base; with a login name also that it finds exactly one entry with an identifier. Throws with the first reason
     * that fails (additionally {@code directory.user_not_found} and {@code directory.user_ambiguous}).
     */
    public void check(LdapSettings settings, String sampleLoginName) {
        requireBindPassword(settings);
        try (Connection search = openSearch(settings)) {
            SearchControls base = new SearchControls();
            base.setSearchScope(SearchControls.OBJECT_SCOPE);
            base.setReturningAttributes(new String[] { "1.1" });
            base.setTimeLimit(timeoutMillis());
            NamingEnumeration<SearchResult> result = search.context().search(new CompositeName().add(settings.searchBase()), "(objectClass=*)", base);
            result.close();
        } catch (NamingException e) {
            throw unavailable(e);
        }
        if (sampleLoginName != null && !sampleLoginName.isBlank()) {
            Match match = search(settings, sampleLoginName);
            if (match.outcome() == Outcome.NONE) {
                throw new DirectoryUnavailableException("directory.user_not_found");
            }
            if (match.outcome() == Outcome.AMBIGUOUS) {
                throw new DirectoryUnavailableException("directory.user_ambiguous");
            }
        }
    }

    private Match search(LdapSettings settings, String loginName) {
        requireBindPassword(settings);
        try (Connection search = openSearch(settings)) {
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setCountLimit(3);
            controls.setTimeLimit(timeoutMillis());
            controls.setReturningObjFlag(false);
            List<String> attributes = new ArrayList<>(List.of(settings.idAttribute()));
            if (settings.nameAttribute() != null) {
                attributes.add(settings.nameAttribute());
            }
            controls.setReturningAttributes(attributes.toArray(String[]::new));
            List<SearchResult> hits = new ArrayList<>();
            NamingEnumeration<SearchResult> results = search.context().search(new CompositeName().add(settings.searchBase()),
                settings.userFilter(), new Object[] { loginName }, controls);
            try {
                while (results.hasMore()) {
                    hits.add(results.next());
                    if (hits.size() > 1) {
                        return Match.AMBIGUOUS;
                    }
                }
            } catch (SizeLimitExceededException tooMany) {
                return Match.AMBIGUOUS;
            } finally {
                results.close();
            }
            if (hits.isEmpty()) {
                return Match.NONE;
            }
            SearchResult hit = hits.getFirst();
            String id = identifier(hit.getAttributes().get(settings.idAttribute()));
            if (id == null) {
                throw new DirectoryUnavailableException("directory.id_attribute_missing");
            }
            String name = settings.nameAttribute() == null ? null : displayName(hit.getAttributes().get(settings.nameAttribute()));
            return new Match(Outcome.FOUND, hit.getNameInNamespace(), new DirectoryUser(id, name));
        } catch (NamingException e) {
            throw unavailable(e);
        }
    }

    private void requireBindPassword(LdapSettings settings) {
        if (settings.bindDn() != null && !properties.bindPasswordSet()) {
            throw new DirectoryUnavailableException("directory.bind_password_missing");
        }
    }

    /** Marks an identifier that is stored as the hexadecimal form of its bytes; a text identifier never starts with it. */
    static final String BINARY_PREFIX = "hex:";

    /**
     * The identifier of the entry as the key an account is assigned by (TASK-048). The attribute is requested as raw bytes
     * (see {@link #open}), because JNDI would otherwise decode a binary value such as an {@code objectGUID} as text and turn
     * different values into the same replacement characters. Bytes that are printable UTF-8 text stay that text, so the
     * identifiers stored for text attributes such as {@code entryUUID} keep their form; every other value is stored as
     * {@code hex:} and its bytes in lower case hexadecimal. Both forms are injective, and the two never overlap. Null when the
     * attribute is absent, has several values, is empty or too long.
     */
    static String identifier(Attribute attribute) throws NamingException {
        if (attribute == null || attribute.size() != 1) {
            return null;
        }
        Object value = attribute.get();
        // A text value with replacement characters has already lost the bytes it came from; it must not name anyone.
        if (value instanceof String lossy && lossy.indexOf('\uFFFD') >= 0) {
            return null;
        }
        byte[] bytes = value instanceof byte[] raw ? raw : String.valueOf(value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            return null;
        }
        String text = printableText(bytes);
        String key = text != null ? text : BINARY_PREFIX + HexFormat.of().formatHex(bytes);
        return key.length() > 512 ? null : key;
    }

    /** The bytes as text if they are valid UTF-8 without control characters, blanks only, replacement characters or the binary prefix; else null. */
    private static String printableText(byte[] bytes) {
        String text = decodeUtf8(bytes);
        boolean plain = text != null && !text.isBlank() && !text.startsWith(BINARY_PREFIX) && text.indexOf('\uFFFD') < 0
            && text.chars().noneMatch(Character::isISOControl);
        return plain ? text : null;
    }

    /** Strict UTF-8: null for bytes that are not valid text. */
    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException notText) {
            return null;
        }
    }

    private static String displayName(Attribute attribute) throws NamingException {
        if (attribute == null || attribute.size() == 0) {
            return null;
        }
        // The identifier attribute is read as raw bytes; if the name is the same attribute it arrives as bytes too (TASK-049).
        Object value = attribute.get();
        String text = value instanceof byte[] bytes ? decodeUtf8(bytes) : String.valueOf(value);
        if (text == null) {
            return null;
        }
        String cleaned = text.chars().filter(c -> !Character.isISOControl(c))
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString().trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.codePointCount(0, cleaned.length()) > MAX_NAME_LENGTH
            ? cleaned.substring(0, cleaned.offsetByCodePoints(0, MAX_NAME_LENGTH)) : cleaned;
    }

    private int timeoutMillis() {
        return (int) Math.min(Integer.MAX_VALUE, properties.timeout().toMillis());
    }

    private Connection openSearch(LdapSettings settings) throws NamingException {
        try {
            return open(settings, settings.bindDn(), settings.bindDn() == null ? "" : properties.bindPassword());
        } catch (AuthenticationException e) {
            throw new DirectoryUnavailableException("directory.bind_failed", e);
        }
    }

    /** One connection to the directory, encrypted as configured and bound as {@code dn} (anonymous without a name). */
    private Connection open(LdapSettings settings, String dn, String password) throws NamingException {
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, CONTEXT_FACTORY);
        env.put(Context.PROVIDER_URL, settings.url());
        env.put(Context.REFERRAL, "ignore");
        env.put("java.naming.ldap.derefAliases", "never");
        // The identifier is read as raw bytes whatever its syntax, so that no binary value is decoded as text (TASK-048).
        env.put("java.naming.ldap.attributes.binary", settings.idAttribute());
        env.put("com.sun.jndi.ldap.connect.timeout", Integer.toString(timeoutMillis()));
        env.put("com.sun.jndi.ldap.read.timeout", Integer.toString(timeoutMillis()));
        boolean credentials = dn != null && !password.isEmpty();
        boolean upgrade = LdapSettings.STARTTLS.equals(settings.security());
        if (LdapSettings.LDAPS.equals(settings.security())) {
            env.put("java.naming.ldap.factory.socket", DirectorySocketFactory.class.getName());
        }
        if (credentials && !upgrade) {
            addCredentials(env, dn, password);
        }
        LdapContext context = new InitialLdapContext(env, null);
        StartTlsResponse layer = null;
        try {
            if (upgrade) {
                try {
                    layer = (StartTlsResponse) context.extendedOperation(new StartTlsRequest());
                } catch (NamingException refused) {
                    // A server that does not take the encryption on is a failure of the encryption and not of a search (TASK-048).
                    DirectoryUnavailableException reason = unavailable(refused);
                    throw "directory.search_failed".equals(reason.reason()) ? new DirectoryUnavailableException("directory.tls_failed", refused) : reason;
                }
                layer.negotiate(DirectorySocketFactory.verifying(tls));
                if (credentials) {
                    context.addToEnvironment(Context.SECURITY_AUTHENTICATION, "simple");
                    context.addToEnvironment(Context.SECURITY_PRINCIPAL, dn);
                    context.addToEnvironment(Context.SECURITY_CREDENTIALS, password);
                    context.reconnect(null);
                }
            }
            return new Connection(context, layer);
        } catch (NamingException | IOException | RuntimeException e) {
            close(context, layer);
            if (e instanceof NamingException naming) {
                throw naming;
            }
            if (e instanceof IOException io) {
                throw communication(io);
            }
            throw (RuntimeException) e;
        }
    }

    private static void addCredentials(Hashtable<String, Object> env, String dn, String password) {
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, dn);
        env.put(Context.SECURITY_CREDENTIALS, password);
    }

    private static NamingException communication(IOException io) {
        NamingException e = new javax.naming.CommunicationException("connection failed");
        e.setRootCause(io);
        return e;
    }

    private static void close(LdapContext context, StartTlsResponse tls) {
        try {
            if (tls != null) {
                tls.close();
            }
        } catch (IOException ignored) {
            // closing only
        }
        try {
            context.close();
        } catch (NamingException ignored) {
            // closing only
        }
    }

    /** Reduces a failure to a reason of the fixed vocabulary; nothing the server said is kept in the reason. */
    private static DirectoryUnavailableException unavailable(NamingException e) {
        for (Throwable t = e; t != null; t = next(t)) {
            if (t instanceof SSLException) {
                return new DirectoryUnavailableException("directory.tls_failed", e);
            }
            if (t instanceof SocketTimeoutException || t instanceof java.net.ConnectException || t instanceof java.net.UnknownHostException) {
                return new DirectoryUnavailableException("directory.unreachable", e);
            }
        }
        if (e instanceof AuthenticationException) {
            return new DirectoryUnavailableException("directory.bind_failed", e);
        }
        if (e instanceof javax.naming.CommunicationException || e instanceof javax.naming.ServiceUnavailableException
                || e instanceof javax.naming.TimeLimitExceededException) {
            return new DirectoryUnavailableException("directory.unreachable", e);
        }
        return new DirectoryUnavailableException("directory.search_failed", e);
    }

    private static Throwable next(Throwable t) {
        if (t.getCause() != null) {
            return t.getCause();
        }
        return t instanceof NamingException naming && naming.getRootCause() != t ? naming.getRootCause() : null;
    }

    /** A connection with its optional encryption layer; closing it closes both. */
    private record Connection(LdapContext context, StartTlsResponse tls) implements AutoCloseable {
        @Override
        public void close() {
            DirectoryClient.close(context, tls);
        }
    }
}
