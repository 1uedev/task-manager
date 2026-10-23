package taskmanager.identity.directory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.SocketFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * The socket factory the directory connection uses for {@code ldaps}. JNDI names such a factory by class, so the trust
 * configured at startup is held in one static field; the application talks to one directory only. Every socket it creates
 * checks that the certificate belongs to the host name of the address (endpoint identification), which JNDI does not do for
 * a factory of its own.
 */
public final class DirectorySocketFactory extends SocketFactory {
    private static volatile SSLSocketFactory delegate = (SSLSocketFactory) SSLSocketFactory.getDefault();
    private static final DirectorySocketFactory INSTANCE = new DirectorySocketFactory();

    private DirectorySocketFactory() {
    }

    /** Called by JNDI. */
    public static SocketFactory getDefault() {
        return INSTANCE;
    }

    static void use(SSLSocketFactory factory) {
        delegate = factory;
    }

    /** The factory with the same endpoint identification, for the StartTLS upgrade of an open connection. */
    static SSLSocketFactory verifying(SSLSocketFactory factory) {
        return new SSLSocketFactory() {
            @Override
            public String[] getDefaultCipherSuites() {
                return factory.getDefaultCipherSuites();
            }

            @Override
            public String[] getSupportedCipherSuites() {
                return factory.getSupportedCipherSuites();
            }

            @Override
            public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
                return harden(factory.createSocket(s, host, port, autoClose));
            }

            @Override
            public Socket createSocket(String host, int port) throws IOException {
                return harden(factory.createSocket(host, port));
            }

            @Override
            public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
                return harden(factory.createSocket(host, port, localHost, localPort));
            }

            @Override
            public Socket createSocket(InetAddress host, int port) throws IOException {
                return harden(factory.createSocket(host, port));
            }

            @Override
            public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
                return harden(factory.createSocket(address, port, localAddress, localPort));
            }

            @Override
            public Socket createSocket() throws IOException {
                return harden(factory.createSocket());
            }
        };
    }

    private static Socket harden(Socket socket) {
        if (socket instanceof SSLSocket ssl) {
            SSLParameters parameters = ssl.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("LDAPS");
            ssl.setSSLParameters(parameters);
        }
        return socket;
    }

    private static SSLSocketFactory current() {
        return verifying(delegate);
    }

    @Override
    public Socket createSocket() throws IOException {
        return current().createSocket();
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return current().createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return current().createSocket(host, port, localHost, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return current().createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return current().createSocket(address, port, localAddress, localPort);
    }
}
