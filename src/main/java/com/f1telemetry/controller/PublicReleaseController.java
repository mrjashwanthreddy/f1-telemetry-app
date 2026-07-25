package com.f1telemetry.controller;

import com.f1telemetry.update.UpdateManager;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Public Controller that exposes latest release info and proxies binary zip downloads
 * from GitHub Releases so user browsers never see or redirect to the GitHub URL.
 */
@Slf4j
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicReleaseController {

    private final UpdateManager updateManager;

    @GetMapping("/release/latest")
    public ResponseEntity<Map<String, Object>> getLatestRelease() {
        try {
            UpdateManager.GitHubRelease release = updateManager.getLatestReleaseInfo();
            if (release == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No release found"));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("version", release.tagName != null ? release.tagName : "0.0.7");
            response.put("notes", release.body != null ? release.body : "");
            response.put("downloadUrl", "/api/public/download/latest");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching release info: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to query release info"));
        }
    }

    @GetMapping("/download/latest")
    public void downloadLatestRelease(HttpServletResponse response) {
        try {
            String downloadUrl = updateManager.getLatestWindowsZipDownloadUrl();
            if (downloadUrl == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Release ZIP asset not found");
                return;
            }

            log.info("Proxying release zip download from GitHub...");
            URL url = new URI(downloadUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "F1Telemetry-WebProxy");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

            // Handle HTTP 302 redirects if GitHub redirects to S3/CDN release bucket
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
                String redirectUrl = conn.getHeaderField("Location");
                if (redirectUrl != null) {
                    url = new URI(redirectUrl).toURL();
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "F1Telemetry-WebProxy");
                }
            }

            int contentLength = conn.getContentLength();

            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=\"F1Telemetry-Windows.zip\"");
            if (contentLength > 0) {
                response.setContentLength(contentLength);
            }

            try (InputStream is = conn.getInputStream();
                 OutputStream os = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
            }

            log.info("Successfully proxied release download to client.");
        } catch (Exception e) {
            log.error("Failed to proxy release zip download: {}", e.getMessage(), e);
            try {
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Download proxy error");
                }
            } catch (Exception ignored) {}
        }
    }
}
