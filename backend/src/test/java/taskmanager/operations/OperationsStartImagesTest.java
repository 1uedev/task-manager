package taskmanager.operations;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Both image settings are checked together at the start of every process of the template (TASK-139, finding F7 of the
 * review of TASK-137): one wrong value with a valid second digest is refused, whichever of the two it is.
 */
class OperationsStartImagesTest {
    private static final String APP = "sha256:" + "a".repeat(64);
    private static final String BACKUP = "sha256:" + "b".repeat(64);

    private static Map<String, String> settings(String app, String backup) {
        Map<String, String> environment = new HashMap<>();
        environment.put("TASK_MANAGER_IMAGE_DIGEST", app);
        environment.put("TASK_MANAGER_BACKUP_IMAGE_DIGEST", backup);
        environment.put("TASK_MANAGER_IMAGE", "");
        environment.put("TASK_MANAGER_BACKUP_IMAGE", "");
        return environment;
    }

    @Test
    void twoDigestsAreAccepted() {
        assertNull(OperationsStart.imageRefusal(settings(APP, BACKUP)));
    }

    @Test
    void oneWrongValueBesideAValidDigestIsRefused() {
        for (String wrong : new String[] { "v1.2.3", "", "sha256:abc", "SHA256:" + "a".repeat(64), APP + " " }) {
            String app = OperationsStart.imageRefusal(settings(wrong, BACKUP));
            assertTrue(app != null && app.startsWith("TASK_MANAGER_IMAGE_DIGEST must be the digest"), wrong + ": " + app);
            String backup = OperationsStart.imageRefusal(settings(APP, wrong));
            assertTrue(backup != null && backup.startsWith("TASK_MANAGER_BACKUP_IMAGE_DIGEST must be the digest"),
                wrong + ": " + backup);
        }
        Map<String, String> missing = settings(APP, BACKUP);
        missing.remove("TASK_MANAGER_BACKUP_IMAGE_DIGEST");
        assertTrue(OperationsStart.imageRefusal(missing).startsWith("TASK_MANAGER_BACKUP_IMAGE_DIGEST"));
    }

    @Test
    void theSettingsOfTheFormBeforeTask137AreRefused() {
        for (String old : new String[] { "TASK_MANAGER_IMAGE", "TASK_MANAGER_BACKUP_IMAGE" }) {
            Map<String, String> environment = settings(APP, BACKUP);
            environment.put(old, "ghcr.io/1uedev/task-manager:1.0.0");
            assertTrue(OperationsStart.imageRefusal(environment).contains("no longer read"), old);
        }
    }
}
