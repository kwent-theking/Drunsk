package com.drunsk.update;

import com.drunsk.DrunskConfig;
import com.drunsk.DrunskClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Opt-in updater: downloads a release artifact for review, never replaces a loaded JAR. */
public final class DrunskUpdateChecker {
    private static final URI RELEASE = URI.create("https://api.github.com/repos/kwent-theking/Drunsk/releases/latest");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private DrunskUpdateChecker() {}

    public static void checkAsync() {
        if (!DrunskConfig.util.autoUpdate) return;
        Thread.startVirtualThread(() -> {
            try {
                var request = HttpRequest.newBuilder(RELEASE)
                        .timeout(Duration.ofSeconds(12))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "Drunsk-UpdateChecker")
                        .GET().build();
                var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    DrunskClient.LOGGER.warn("[Drunsk] update check returned HTTP {}", response.statusCode());
                    return;
                }
                DrunskClient.LOGGER.info("[Drunsk] latest release metadata received; automatic replacement is intentionally disabled");
                Path dir = DrunskConfig.updateDirectory();
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("latest-release.json"), response.body());
            } catch (Exception e) {
                DrunskClient.LOGGER.warn("[Drunsk] update check failed: {}", e.toString());
            }
        });
    }
}
