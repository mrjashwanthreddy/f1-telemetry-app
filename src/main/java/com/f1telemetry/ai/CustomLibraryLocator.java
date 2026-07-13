package com.f1telemetry.ai;

import com.github.kwhat.jnativehook.NativeLibraryLocator;
import java.io.*;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Iterator;

public class CustomLibraryLocator implements NativeLibraryLocator {

    @Override
    public Iterator<File> getLibraries() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        
        String libFolder = "";
        String libExtension = "";
        
        if (os.contains("win")) {
            libFolder = "windows";
            libExtension = ".dll";
        } else if (os.contains("mac")) {
            libFolder = "osx";
            libExtension = ".dylib";
        } else {
            libFolder = "linux";
            libExtension = ".so";
        }
        
        if (arch.contains("64")) {
            arch = "x86_64";
        } else {
            arch = "x86";
        }
        
        try {
            // Locate resource inside JNativeHook jar
            String resourcePath = "/com/github/kwhat/jnativehook/lib/" + libFolder + "/" + arch + "/JNativeHook" + libExtension;
            InputStream is = CustomLibraryLocator.class.getResourceAsStream(resourcePath);
            if (is == null) {
                // Fallback to legacy path org/jnativehook/...
                resourcePath = "/org/jnativehook/lib/" + libFolder + "/" + arch + "/JNativeHook" + libExtension;
                is = CustomLibraryLocator.class.getResourceAsStream(resourcePath);
            }
            
            if (is == null) {
                throw new FileNotFoundException("Could not find native JNativeHook library resource in classpath: " + resourcePath);
            }
            
            // Create a temporary file
            File tempFile = Files.createTempFile("JNativeHook-", libExtension).toFile();
            tempFile.deleteOnExit();
            
            // Unpack library
            try (FileOutputStream osStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    osStream.write(buffer, 0, bytesRead);
                }
            }
            
            System.out.println("[CustomLibraryLocator] Successfully extracted native library to: " + tempFile.getAbsolutePath());
            return Collections.singletonList(tempFile).iterator();
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract native JNativeHook library", e);
        }
    }
}
