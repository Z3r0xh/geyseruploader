package org.pluginupdater.core;

import org.pluginupdater.core.logging.LogAdapter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Handles sending notifications to Discord via webhook
 */
public class DiscordNotifier {
    private final Config config;
    private final LogAdapter log;
    private final HttpClient httpClient;

    public DiscordNotifier(Config config, LogAdapter log) {
        this.config = config;
        this.log = log;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Send notification to Discord when plugins are updated
     */
    public void notifyUpdates(List<UpdaterService.UpdateOutcome> results) {
        if (!config.discordWebhook.enabled) {
            return; // Discord notifications disabled
        }

        if (config.discordWebhook.webhookUrl == null || config.discordWebhook.webhookUrl.trim().isEmpty()) {
            log.warn("Discord webhook is enabled but no webhook URL is configured");
            return;
        }

        // Count updates and errors
        int updatedCount = 0;
        int errorCount = 0;
        StringBuilder updatedPlugins = new StringBuilder();
        StringBuilder errorPlugins = new StringBuilder();

        for (UpdaterService.UpdateOutcome result : results) {
            if (result.updated) {
                updatedCount++;
                if (updatedPlugins.length() > 0) updatedPlugins.append("\n");

                // Build version info
                updatedPlugins.append("• **").append(result.project.name()).append("**");
                if (result.oldVersion != null && result.newVersion != null) {
                    // Extract clean version from filename
                    String oldVer = extractVersion(result.oldVersion);
                    String newVer = extractVersion(result.newVersion);

                    // Check if versions are meaningful (not just the same filename)
                    if (!oldVer.equals(newVer)) {
                        updatedPlugins.append(": `").append(oldVer).append("` → `").append(newVer).append("`");
                    } else {
                        // For Geyser/Floodgate that use static names, just show "Updated to latest"
                        updatedPlugins.append(": Updated to latest");
                    }
                } else if (result.newVersion != null) {
                    String newVer = extractVersion(result.newVersion);
                    // Check if it's a static filename (Geyser/Floodgate)
                    if (newVer.matches("(?i)^(Geyser|floodgate).*")) {
                        updatedPlugins.append(": New install (latest)");
                    } else {
                        updatedPlugins.append(": New install `").append(newVer).append("`");
                    }
                }
            } else if (result.error.isPresent()) {
                errorCount++;
                if (errorPlugins.length() > 0) errorPlugins.append("\n");
                errorPlugins.append("• **")
                        .append(result.project.name())
                        .append("**: ")
                        .append(truncate(result.error.get(), 100));
            }
        }

        // Only send notification if there are updates or errors (based on config)
        boolean shouldNotify = false;
        if (updatedCount > 0 && config.discordWebhook.notifyOnUpdate) {
            shouldNotify = true;
        }
        if (errorCount > 0 && config.discordWebhook.notifyOnError) {
            shouldNotify = true;
        }

        if (!shouldNotify) {
            return;
        }

        // Build Discord embed
        StringBuilder embed = buildDiscordEmbed(updatedCount, errorCount, updatedPlugins.toString(), errorPlugins.toString());

        // Send to Discord
        sendToDiscord(embed.toString());
    }

    private StringBuilder buildDiscordEmbed(int updatedCount, int errorCount, String updatedPlugins, String errorPlugins) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"embeds\": [{");

        // Title
        if (updatedCount > 0 && errorCount > 0) {
            json.append("\"title\": \"🔄 Plugin Updates & Errors\",");
            json.append("\"color\": 16776960,"); // Yellow
        } else if (updatedCount > 0) {
            json.append("\"title\": \"✅ Plugins Updated\",");
            json.append("\"color\": 5763719,"); // Green
        } else {
            json.append("\"title\": \"❌ Plugin Update Errors\",");
            json.append("\"color\": 15548997,"); // Red
        }

        // Description
        json.append("\"description\": \"");
        if (updatedCount > 0) {
            json.append("**Updated (").append(updatedCount).append("):** ").append(updatedPlugins);
        }
        if (errorCount > 0) {
            if (updatedCount > 0) json.append("\\n\\n");
            json.append("**Errors (").append(errorCount).append("):**\\n").append(escapeJson(errorPlugins));
        }
        json.append("\",");

        // Footer
        json.append("\"footer\": {");
        json.append("\"text\": \"zPluginUpdater\"");
        json.append("},");

        // Timestamp
        json.append("\"timestamp\": \"").append(java.time.Instant.now().toString()).append("\"");

        json.append("}]}");
        return json;
    }

    private void sendToDiscord(String jsonPayload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.discordWebhook.webhookUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Discord notification sent successfully");
            } else {
                log.warn("Failed to send Discord notification: HTTP " + response.statusCode() + " - " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            log.warn("Error sending Discord notification: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Extract version information from filename
     * Removes .jar extension and common prefixes
     */
    private String extractVersion(String filename) {
        if (filename == null) return "Unknown";

        // Remove .jar extension
        String version = filename.replaceFirst("\\.jar$", "");

        // Remove common plugin name prefixes to show just version
        version = version.replaceFirst("^Geyser-Spigot-", "");
        version = version.replaceFirst("^Geyser-Bungee-", "");
        version = version.replaceFirst("^Geyser-Velocity-", "");
        version = version.replaceFirst("^floodgate-spigot-", "");
        version = version.replaceFirst("^floodgate-bungee-", "");
        version = version.replaceFirst("^floodgate-velocity-", "");
        version = version.replaceFirst("^geyserutils-geyser-", "");
        version = version.replaceFirst("^geyserutils-spigot-", "");
        version = version.replaceFirst("^geyserutils-bungee-", "");
        version = version.replaceFirst("^geyserutils-velocity-", "");
        version = version.replaceFirst("^GeyserModelEngine-Extension-", "");
        version = version.replaceFirst("^GeyserModelEngine-Plugin-", "");
        version = version.replaceFirst("^LuckPerms-", "");
        version = version.replaceFirst("^packetevents-spigot-", "");
        version = version.replaceFirst("^packetevents-bungeecord-", "");
        version = version.replaceFirst("^packetevents-velocity-", "");
        version = version.replaceFirst("^ProtocolLib-", "");
        version = version.replaceFirst("^ViaVersion-", "");
        version = version.replaceFirst("^ViaBackwards-", "");
        version = version.replaceFirst("^ViaRewind-", "");
        version = version.replaceFirst("^FastAsyncWorldEdit-", "");
        version = version.replaceFirst("^PlaceholderAPI-", "");
        version = version.replaceFirst("^NBTAPI-", "");

        return version;
    }

    /**
     * Send a test notification to Discord to verify webhook configuration
     * @return true if the test was successful, false otherwise
     */
    public boolean sendTestNotification() {
        if (config.discordWebhook.webhookUrl == null || config.discordWebhook.webhookUrl.trim().isEmpty()) {
            log.warn("Discord webhook URL is not configured");
            return false;
        }

        // Build test embed
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"embeds\": [{");
        json.append("\"title\": \"✅ Discord Webhook Test\",");
        json.append("\"color\": 5763719,"); // Green
        json.append("\"description\": \"This is a test notification from **zPluginUpdater**.\\n\\nIf you see this message, your Discord webhook is configured correctly!\",");
        json.append("\"footer\": {");
        json.append("\"text\": \"zPluginUpdater\"");
        json.append("},");
        json.append("\"timestamp\": \"").append(java.time.Instant.now().toString()).append("\"");
        json.append("}]}");

        // Send to Discord
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.discordWebhook.webhookUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Discord webhook test notification sent successfully");
                return true;
            } else {
                log.warn("Failed to send Discord webhook test: HTTP " + response.statusCode() + " - " + response.body());
                return false;
            }
        } catch (IOException | InterruptedException e) {
            log.warn("Error sending Discord webhook test: " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
