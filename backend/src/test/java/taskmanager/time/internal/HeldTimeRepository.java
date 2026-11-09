package taskmanager.time.internal;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;
import org.mockito.Mockito;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * TASK-092, R1: wraps the time repository of a test context so that a test can hold the next read of a time list or an export
 * right after the rights were checked. While the read is held, the test commits a booking in another transaction and then lets
 * the read go on; count, sums, days and rows of the answer must still fit each other. Without a hook the repository works
 * unchanged.
 */
public class HeldTimeRepository implements BeanPostProcessor {
    private static final AtomicReference<Runnable> BEFORE_READ = new AtomicReference<>();

    /** Runs the hook once, right before the next read of count, sums, days and rows of a list or an export. */
    public static void beforeNextRead(Runnable hook) {
        BEFORE_READ.set(hook);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof TimeRepository real)) {
            return bean;
        }
        return Mockito.mock(TimeRepository.class, invocation -> {
            Runnable held = "snapshot".equals(invocation.getMethod().getName()) ? BEFORE_READ.getAndSet(null) : null;
            if (held != null) {
                held.run();
            }
            invocation.getMethod().setAccessible(true);
            try {
                return invocation.getMethod().invoke(real, invocation.getArguments());
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
}
