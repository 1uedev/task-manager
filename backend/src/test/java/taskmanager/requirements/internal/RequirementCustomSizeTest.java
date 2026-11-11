package taskmanager.requirements.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import taskmanager.projects.ProjectConfiguration;

/**
 * TASK-100 (F3 of the review of TASK-098): the limit of the stored custom values of a revision holds every combination the
 * configuration allows, and a larger stored form is recognized before the database refuses it.
 */
class RequirementCustomSizeTest {

    @Test
    void theLargestPermittedCombinationFits() {
        Map<String, String> values = new TreeMap<>();
        for (int i = 0; i < 30; i++) {
            // The longest key and the character with the longest stored form, an escaped control character of 6 bytes.
            values.put(("k" + i + "_".repeat(40)).substring(0, 40), "\u0001".repeat(ProjectConfiguration.LONGTEXT_MAX));
        }
        assertTrue(RequirementRepository.customFits(values));
    }

    @Test
    void aLargerStoredFormIsRecognized() {
        assertFalse(RequirementRepository.customFits(Map.of("note", "x".repeat(RequirementRepository.CUSTOM_MAX_BYTES))));
    }
}
