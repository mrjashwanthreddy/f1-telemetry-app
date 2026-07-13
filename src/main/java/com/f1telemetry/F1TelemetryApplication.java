package com.f1telemetry;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class F1TelemetryApplication {

    public static void main(String[] args) {
        // Register custom native library locator for JNativeHook
        System.setProperty("jnativehook.lib.locator", "com.f1telemetry.ai.CustomLibraryLocator");

        // Load .env file into system properties so Spring @Value can resolve them.
        // Silently ignored if .env doesn't exist (production uses real env vars instead).
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue())
            );
        } catch (DotenvException e) {
            System.out.println("[F1Telemetry] No .env file found — using system environment variables.");
        }

        SpringApplication.run(F1TelemetryApplication.class, args);
    }
}
