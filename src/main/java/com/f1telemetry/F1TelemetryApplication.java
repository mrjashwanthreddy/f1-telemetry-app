package com.f1telemetry;

import com.f1telemetry.config.SplashScreen;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.swing.*;

@SpringBootApplication
public class F1TelemetryApplication {

    // Shared reference so AppGuiLauncher can dismiss the splash when ready
    public static volatile SplashScreen splashScreen;

    public static void main(String[] args) {
        // Ensure AWT headless mode is disabled to support System Tray and GUI launching
        System.setProperty("java.awt.headless", "false");

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

        // Show splash screen immediately on EDT (before Spring Boot starts)
        SwingUtilities.invokeLater(() -> {
            splashScreen = new SplashScreen();
            splashScreen.showSplash();
        });

        // Spring Boot starts on main thread — splash is visible during initialization
        SpringApplication app = new SpringApplication(F1TelemetryApplication.class);
        app.setHeadless(false);
        app.run(args);

        // Note: AppGuiLauncher.onApplicationReady() will dismiss the splash
        // and show the main embedded browser window when Spring is fully ready.
    }
}
