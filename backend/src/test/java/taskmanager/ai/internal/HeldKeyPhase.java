package taskmanager.ai.internal;

import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * TASK-138, H1: holds the key phase of the next execution, without a hook in the product code. The test configuration puts a
 * {@link ConfigService} in front of the real one whose {@code key} waits, when armed, until the test releases it. The wait does
 * not end on an interrupt: the cancellation of the execution arrives while it waits and stays set when the phase continues,
 * exactly as when a cancellation comes in while the key is opened.
 */
@TestConfiguration(proxyBeanMethods = false)
public class HeldKeyPhase {
    private static volatile CountDownLatch held;
    private static volatile CountDownLatch release;
    private static volatile boolean armed;
    private static volatile boolean interruptedWhileHeld;
    private static final java.util.concurrent.atomic.AtomicInteger COMPLETIONS = new java.util.concurrent.atomic.AtomicInteger();

    /** The adapters with a count of the calls of {@code complete}, so that the test sees the call itself, not only the network. */
    @Bean
    @Primary
    AiAdapters countedAdapters(AiHttp http) {
        return new AiAdapters(http) {
            @Override
            Completion complete(AiProvider provider, String key, AiModel model, Prompt prompt, java.time.Duration timeout) {
                COMPLETIONS.incrementAndGet();
                return super.complete(provider, key, model, prompt, timeout);
            }
        };
    }

    /** How often an adapter was asked for a completion since the last {@link #arm()}. */
    public static int completions() {
        return COMPLETIONS.get();
    }

    @Bean
    @Primary
    ConfigService heldKeyConfig(AiStore store, AiAccess access, AiSecrets secrets, AiProperties properties, AiAdapters countedAdapters, Clock clock,
            PlatformTransactionManager transactions, ApplicationEventPublisher events) {
        return new ConfigService(store, access, secrets, properties, countedAdapters, clock, transactions, events) {
            @Override
            String key(AiProvider provider) {
                String key = super.key(provider);
                if (armed) {
                    armed = false;
                    held.countDown();
                    boolean interrupted = false;
                    while (true) {
                        try {
                            if (release.await(10, TimeUnit.SECONDS)) {
                                break;
                            }
                            throw new IllegalStateException("the held key phase was never released");
                        } catch (InterruptedException cancellation) {
                            interrupted = true;
                        }
                    }
                    interruptedWhileHeld = interrupted;
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
                return key;
            }
        };
    }

    /** Holds the key phase of the next execution. */
    public static void arm() {
        held = new CountDownLatch(1);
        release = new CountDownLatch(1);
        interruptedWhileHeld = false;
        COMPLETIONS.set(0);
        armed = true;
    }

    public static void awaitHeld() throws InterruptedException {
        if (!held.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("no execution reached its key phase");
        }
    }

    public static void release() {
        release.countDown();
    }

    /** Whether the cancellation reached the held execution as an interrupt, which it keeps afterwards. */
    public static boolean interruptedWhileHeld() {
        return interruptedWhileHeld;
    }

    public static void disarm() {
        armed = false;
        if (release != null) {
            release.countDown();
        }
    }
}
