package com.f1telemetry.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Copies SLF4J MDC context from the calling thread to the async executor thread.
 *
 * Without this decorator, @Async methods running on thread pools would lose
 * the user/requestId context set by MdcLoggingFilter, making async logs
 * impossible to trace back to the originating user request.
 *
 * Usage: wire into ThreadPoolTaskExecutor via executor.setTaskDecorator(new MdcTaskDecorator()).
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // Capture the MDC context from the calling thread
        Map<String, String> callerMdc = MDC.getCopyOfContextMap();

        return () -> {
            try {
                // Restore caller's MDC context on the executor thread
                if (callerMdc != null) {
                    MDC.setContextMap(callerMdc);
                }
                runnable.run();
            } finally {
                // Always clean up to prevent leakage in the thread pool
                MDC.clear();
            }
        };
    }
}
