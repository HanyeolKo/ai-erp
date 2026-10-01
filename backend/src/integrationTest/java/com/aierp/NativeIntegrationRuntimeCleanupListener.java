package com.aierp;

import java.lang.reflect.Field;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/** Closes native services after Spring has disposed of its cached application context. */
final class NativeIntegrationRuntimeCleanupListener implements TestExecutionListener, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void afterTestClass(TestContext testContext) throws Exception {
        Field runtimeField = testContext.getTestClass().getDeclaredField("RUNTIME");
        runtimeField.setAccessible(true);
        ((NativeIntegrationRuntime) runtimeField.get(null)).close();
    }
}
