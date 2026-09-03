package com.aierp;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleBoundariesTest {

    @Test
    void verifies_closed_business_module_boundaries() {
        ApplicationModules.of(AiErpApplication.class).verify();
    }
}
