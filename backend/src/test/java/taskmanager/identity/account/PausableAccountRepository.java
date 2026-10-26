package taskmanager.identity.account;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Stands in for the account repository in a test that needs a second change to complete between two steps of a directory sign
 * in: right after the lookup of an account by the identifier of its directory entry, the first call after {@link #arm()} waits
 * until {@link Pause#proceed()} is called. It returns what the real lookup returned and changes nothing else (TASK-050).
 */
public class PausableAccountRepository extends AccountRepository {
    /** One armed pause: the caller of the lookup waits at {@link #reached} until {@link #proceed} is released. */
    public static final class Pause {
        private final CountDownLatch reached = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        public boolean awaitReached(long seconds) throws InterruptedException {
            return reached.await(seconds, TimeUnit.SECONDS);
        }

        public void proceed() {
            release.countDown();
        }
    }

    private static final AtomicReference<Pause> ARMED = new AtomicReference<>();

    public PausableAccountRepository(JdbcClient jdbc) {
        super(jdbc);
    }

    public static Pause arm() {
        Pause pause = new Pause();
        ARMED.set(pause);
        return pause;
    }

    @Override
    public Optional<AccountRecord> findByExternalId(String externalId) {
        Optional<AccountRecord> found = super.findByExternalId(externalId);
        Pause pause = found.isPresent() ? ARMED.getAndSet(null) : null;
        if (pause != null) {
            pause.reached.countDown();
            try {
                pause.release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return found;
    }
}
