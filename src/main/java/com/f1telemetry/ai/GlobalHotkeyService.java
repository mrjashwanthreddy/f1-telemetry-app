package com.f1telemetry.ai;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.f1telemetry.domain.User;
import com.f1telemetry.domain.UserPreference;
import com.f1telemetry.service.ActiveUserService;
import com.f1telemetry.service.PreferenceService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registers a global OS-level keyboard hook using JNativeHook.
 *
 * Supports two modes:
 *  1. Normal Mode: Monitors key presses. If the pressed key code matches the active user's
 *     voiceHotkey preference, it broadcasts a VOICE_TRIGGER WebSocket alert to trigger the mic.
 *  2. Bind Mode: Intercepts the next key pressed, saves its keycode and display label to the
 *     active user's preferences, broadcasts a HOTKEY_BOUND event, and exits bind mode.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalHotkeyService implements NativeKeyListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ActiveUserService activeUserService;
    private final PreferenceService preferenceService;

    private volatile boolean bindMode = false;

    public void setBindMode(boolean bindMode) {
        this.bindMode = bindMode;
    }

    /** Set bindMode to true to capture the next keypress as a hotkey binding. */
    public void enterBindMode() {
        this.bindMode = true;
        log.info("[GlobalHotkey] Entered hotkey binding mode. Listening for next keypress...");
    }

    @PostConstruct
    public void registerHook() {
        try {
            // Suppress JNativeHook's verbose logging
            Logger nativeLogger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            nativeLogger.setLevel(Level.WARNING);
            nativeLogger.setUseParentHandlers(false);

            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            log.info("[GlobalHotkey] Registered successfully.");
        } catch (NativeHookException e) {
            log.warn("[GlobalHotkey] Could not register native hook: {}. Voice hotkey will be unavailable.", e.getMessage());
        }
    }

    @PreDestroy
    public void unregisterHook() {
        try {
            GlobalScreen.removeNativeKeyListener(this);
            GlobalScreen.unregisterNativeHook();
            log.info("[GlobalHotkey] Native hook unregistered.");
        } catch (NativeHookException e) {
            log.debug("[GlobalHotkey] Error unregistering hook: {}", e.getMessage());
        }
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent event) {
        int keyCode = event.getKeyCode();
        String keyText = NativeKeyEvent.getKeyText(keyCode);

        if (bindMode) {
            bindMode = false; // exit bind mode immediately
            log.info("[GlobalHotkey] Captured binding keycode: {} ({})", keyCode, keyText);
            
            saveNewHotkeyPreference(keyCode, keyText);
            return;
        }

        // Normal mode check
        int targetHotkey = getActiveVoiceHotkey();
        if (keyCode == targetHotkey) {
            log.debug("[GlobalHotkey] Hotkey triggered: {}", keyText);
            messagingTemplate.convertAndSend("/topic/live-alerts",
                new com.f1telemetry.engine.AlertEvent(
                    "VOICE_TRIGGER",
                    "🎙️ Voice query activated — speak now",
                    "INFO",
                    System.currentTimeMillis()
                )
            );
        }
    }

    private int getActiveVoiceHotkey() {
        User activeUser = activeUserService.getActiveUser();
        if (activeUser != null) {
            try {
                UserPreference prefs = preferenceService.getPreferences(activeUser.getUsername());
                return prefs.getVoiceHotkey();
            } catch (Exception e) {
                // fall back to default
            }
        }
        return 70; // Fallback default Scroll Lock
    }

    public void saveNewHotkeyPreference(int keyCode, String keyText) {
        User activeUser = activeUserService.getActiveUser();
        if (activeUser == null) {
            log.warn("[GlobalHotkey] Cannot save key binding: No active driver logged in.");
            return;
        }

        try {
            // Update preferences database
            UserPreference prefs = preferenceService.getPreferences(activeUser.getUsername());
            prefs.setVoiceHotkey(keyCode);
            prefs.setVoiceHotkeyLabel(keyText);
            preferenceService.updatePreferences(activeUser.getUsername(), prefs);

            log.info("[GlobalHotkey] Saved hotkey preference for driver {}: {} ({})",
                activeUser.getUsername(), keyCode, keyText);

            // Broadcast success back to Settings UI
            messagingTemplate.convertAndSend("/topic/live-alerts",
                new com.f1telemetry.engine.AlertEvent(
                    "HOTKEY_BOUND",
                    keyText, // carrying the label in message
                    "SUCCESS",
                    System.currentTimeMillis()
                )
            );
        } catch (Exception e) {
            log.error("[GlobalHotkey] Failed to save hotkey preference", e);
        }
    }

    @Override public void nativeKeyReleased(NativeKeyEvent event) { /* not used */ }
    @Override public void nativeKeyTyped(NativeKeyEvent event) { /* not used */ }
}
