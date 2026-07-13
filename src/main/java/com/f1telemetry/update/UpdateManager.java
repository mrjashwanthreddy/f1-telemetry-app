package com.f1telemetry.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Update Manager that queries the GitHub Releases API, downloads the F1Telemetry-Windows.zip asset,
 * extracts it dynamically to hot-swap all executable, jar, and runtime files, and restarts the application.
 */
@Slf4j
@Service
public class UpdateManager {

    @Value("${app.version:0.0.1}")
    private String currentVersion;

    // Configurable GitHub repo properties
    @Value("${github.owner:jashwanth-sde}")
    private String githubOwner;

    @Value("${github.repo:f1-telemetry}")
    private String githubRepo;

    public void checkForUpdatesAsync(boolean silentIfNoUpdate) {
        new Thread(() -> {
            try {
                // Query GitHub Releases API
                String apiUrl = String.format("https://api.github.com/repos/%s/%s/releases/latest", githubOwner, githubRepo);
                log.info("Checking for updates at: {}", apiUrl);
                
                GitHubRelease release = fetchLatestGitHubRelease(apiUrl);
                if (release != null && isNewerVersion(release.tagName, currentVersion)) {
                    // Find the F1Telemetry-Windows.zip asset
                    String downloadUrl = null;
                    for (GitHubAsset asset : release.assets) {
                        if (asset.name.equalsIgnoreCase("F1Telemetry-Windows.zip")) {
                            downloadUrl = asset.browserDownloadUrl;
                            break;
                        }
                    }

                    if (downloadUrl != null) {
                        String finalDownloadUrl = downloadUrl;
                        SwingUtilities.invokeLater(() -> showUpdatePrompt(release.tagName, release.body, finalDownloadUrl));
                    }
                } else if (!silentIfNoUpdate) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            null,
                            "Your application is up to date (v" + currentVersion + ").",
                            "No Updates Found",
                            JOptionPane.INFORMATION_MESSAGE
                    ));
                }
            } catch (Exception e) {
                log.error("Failed to check for updates: {}", e.getMessage());
                if (!silentIfNoUpdate) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            null,
                            "Failed to check for updates: " + e.getMessage(),
                            "Update Error",
                            JOptionPane.ERROR_MESSAGE
                    ));
                }
            }
        }).start();
    }

    private GitHubRelease fetchLatestGitHubRelease(String apiUrl) throws Exception {
        URL url = new URI(apiUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestMethod("GET");
        // GitHub API requires a User-Agent header
        conn.setRequestProperty("User-Agent", "F1Telemetry-UpdateManager");

        if (conn.getResponseCode() == 200) {
            try (InputStream is = conn.getInputStream()) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(is);

                GitHubRelease release = new GitHubRelease();
                release.tagName = root.path("tag_name").asText();
                release.body = root.path("body").asText();

                JsonNode assetsNode = root.path("assets");
                if (assetsNode.isArray()) {
                    for (JsonNode assetNode : assetsNode) {
                        GitHubAsset asset = new GitHubAsset();
                        asset.name = assetNode.path("name").asText();
                        asset.browserDownloadUrl = assetNode.path("browser_download_url").asText();
                        release.assets.add(asset);
                    }
                }
                return release;
            }
        }
        return null;
    }

    private boolean isNewerVersion(String latestTag, String current) {
        if (latestTag == null || current == null) return false;
        
        // Strip any leading "v" or non-numeric prefixes (e.g. v1.0.0 -> 1.0.0)
        String latest = latestTag.replaceAll("^[^0-9]+", "");
        String curr = current.replaceAll("^[^0-9]+", "");

        String[] latestParts = latest.split("\\.");
        String[] currentParts = curr.split("\\.");
        int length = Math.max(latestParts.length, currentParts.length);
        
        for (int i = 0; i < length; i++) {
            int l = 0, c = 0;
            try {
                l = i < latestParts.length ? Integer.parseInt(latestParts[i].split("[^0-9]")[0]) : 0;
            } catch (Exception e) {}
            try {
                c = i < currentParts.length ? Integer.parseInt(currentParts[i].split("[^0-9]")[0]) : 0;
            } catch (Exception e) {}
            
            if (l > c) return true;
            if (l < c) return false;
        }
        return false;
    }

    private void showUpdatePrompt(String version, String body, String downloadUrl) {
        String message = String.format(
                "A new update (%s) is available!\n\nRelease Notes:\n%s\n\nWould you like to download and install this update now?",
                version, body == null || body.isBlank() || body.trim().equalsIgnoreCase("null") ? "Bug fixes and performance improvements." : body
        );
        int choice = JOptionPane.showConfirmDialog(
                null,
                message,
                "Update Available",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE
        );

        if (choice == JOptionPane.YES_OPTION) {
            downloadAndExtractZip(downloadUrl);
        }
    }

    private void downloadAndExtractZip(String downloadUrl) {
        JDialog progressDialog = new JDialog((Frame) null, "Installing Update", true);
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(300, 30));

        JLabel label = new JLabel("Downloading update package...", SwingConstants.CENTER);
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        progressDialog.setLayout(new BorderLayout());
        progressDialog.add(label, BorderLayout.NORTH);
        progressDialog.add(progressBar, BorderLayout.CENTER);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(null);

        new Thread(() -> {
            File zipFile = null;
            try {
                // Find root execution folder where F1Telemetry.exe sits
                File installRoot = new File(".").getAbsoluteFile();
                zipFile = new File(installRoot, "update.zip");

                // 1. Download ZIP file
                URL url = new URI(downloadUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "F1Telemetry-UpdateManager");
                int fileLength = conn.getContentLength();

                try (InputStream is = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(zipFile)) {

                    byte[] buffer = new byte[8192];
                    int totalBytesRead = 0;
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;
                        if (fileLength > 0) {
                            final int progress = (int) (((double) totalBytesRead / fileLength) * 100);
                            SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
                        }
                    }
                }

                // 2. Extract ZIP file
                SwingUtilities.invokeLater(() -> {
                    label.setText("Extracting update files...");
                    progressBar.setIndeterminate(true);
                });

                extractZip(zipFile, installRoot);

                // Delete downloaded ZIP
                zipFile.delete();

                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(
                            null,
                            "Update installed successfully! The application will now restart.",
                            "Update Complete",
                            JOptionPane.INFORMATION_MESSAGE
                    );

                    // Launch the new F1Telemetry.exe
                    try {
                        File exeFile = new File(installRoot, "F1Telemetry.exe");
                        if (exeFile.exists()) {
                            Runtime.getRuntime().exec(exeFile.getAbsolutePath());
                        }
                    } catch (Exception ex) {
                        log.error("Failed to restart executable: {}", ex.getMessage());
                    }

                    System.exit(0);
                });

            } catch (Throwable t) {
                log.error("Update failed: {}", t.getMessage(), t);
                if (zipFile != null && zipFile.exists()) zipFile.delete();
                SwingUtilities.invokeLater(() -> {
                    progressDialog.dispose();
                    JOptionPane.showMessageDialog(
                            null,
                            "Update failed: " + t.getMessage(),
                            "Update Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                });
            }
        }).start();

        progressDialog.setVisible(true);
    }

    private void extractZip(File zipFile, File destDir) throws IOException {
        byte[] buffer = new byte[8192];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Strip the root F1Telemetry folder wrapper if present in the ZIP
                String entryName = entry.getName();
                if (entryName.startsWith("F1Telemetry/") || entryName.startsWith("F1Telemetry\\")) {
                    entryName = entryName.substring("F1Telemetry/".length());
                }
                
                if (entryName.isEmpty()) {
                    zis.closeEntry();
                    continue;
                }

                // Skip extracting the JRE runtime directory to prevent locked JVM file errors (like jvm.dll)
                if (entryName.startsWith("runtime/") || entryName.startsWith("runtime\\")) {
                    zis.closeEntry();
                    continue;
                }

                // Resolve entry file path
                File newFile = new File(destDir, entryName);
                
                // Prevent Zip Slip vulnerability
                String destDirPath = destDir.getCanonicalPath();
                String newFilePath = newFile.getCanonicalPath();
                if (!newFilePath.startsWith(destDirPath + File.separator)) {
                    throw new IOException("Entry is outside of the target dir: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    // Create parent directories if missing
                    newFile.getParentFile().mkdirs();

                    // If file exists and is locked (e.g. running F1Telemetry.exe or Jar), rename it first
                    if (newFile.exists()) {
                        File oldFile = new File(newFile.getParent(), newFile.getName() + ".old");
                        if (oldFile.exists()) oldFile.delete();
                        newFile.renameTo(oldFile); // safe renaming on Windows
                    }

                    // Write out file contents
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static class GitHubRelease {
        public String tagName;
        public String body;
        public java.util.List<GitHubAsset> assets = new java.util.ArrayList<>();
    }

    private static class GitHubAsset {
        public String name;
        public String browserDownloadUrl;
    }
}
