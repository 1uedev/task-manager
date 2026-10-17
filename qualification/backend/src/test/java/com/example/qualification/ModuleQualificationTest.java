package com.example.qualification;

import com.example.qualification.valid.ProbeApplication;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;
import static org.junit.jupiter.api.Assertions.*;

class ModuleQualificationTest {
    // Qualification fixtures deliberately live in src/test, which Modulith excludes by default.
    private static final ImportOption INCLUDE_TEST_FIXTURES = location -> true;

    @Test void publicContractsHaveNoCycleOrInternalAccess() {
        ApplicationModules modules = ApplicationModules.of(ProbeApplication.class, INCLUDE_TEST_FIXTURES);
        assertEquals(2, modules.stream().count());
        modules.verify();
    }
    @Test void foreignInternalAccessIsRejected() {
        ApplicationModules modules = ApplicationModules.of("com.example.qualification.invalidaccess", INCLUDE_TEST_FIXTURES);
        assertEquals(2, modules.stream().count());
        assertThrows(Violations.class, modules::verify);
    }
    @Test void dependencyCyclesAreRejected() {
        ApplicationModules modules = ApplicationModules.of("com.example.qualification.invalidcycle", INCLUDE_TEST_FIXTURES);
        assertEquals(2, modules.stream().count());
        assertThrows(Violations.class, modules::verify);
    }
}
