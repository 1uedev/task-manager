package com.example.qualification;

import com.example.qualification.valid.ProbeApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;
import static org.junit.jupiter.api.Assertions.*;

class ModuleQualificationTest {
    @Test void publicContractsHaveNoCycleOrInternalAccess() {
        ApplicationModules modules = ApplicationModules.of(ProbeApplication.class);
        assertEquals(2, modules.stream().count());
        modules.verify();
    }
    @Test void foreignInternalAccessIsRejected() {
        assertThrows(Violations.class, () -> ApplicationModules.of("com.example.qualification.invalidaccess").verify());
    }
    @Test void dependencyCyclesAreRejected() {
        assertThrows(Violations.class, () -> ApplicationModules.of("com.example.qualification.invalidcycle").verify());
    }
}
