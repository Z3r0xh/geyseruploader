package org.pluginupdater.core;

import org.pluginupdater.core.logging.LogAdapter;
import org.pluginupdater.core.util.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class UpdaterService {
    // ANSI Color codes for console output
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_GRAY = "\u001B[90m";

    private static final String USER_AGENT = "zPluginUpdater/1.0.0 (https://github.com/Z3r0xh/geyseruploader)";
    private static final String GEYSER_BASE = "https://download.geysermc.org/v2/projects";
    private static final String LUCKPERMS_API = "https://metadata.luckperms.net/data/downloads";
    private static final String PACKETEVENTS_JENKINS = "https://ci.codemc.io/job/retrooper/job/packetevents";
    private static final String PACKETEVENTS_GITHUB_API = "https://api.github.com/repos/retrooper/packetevents/releases/latest";
    private static final String PROTOCOLLIB_GITHUB_API = "https://api.github.com/repos/dmulloy2/ProtocolLib";
    private static final String VIAVERSION_JENKINS = "https://ci.viaversion.com/job/ViaVersion";
    private static final String VIABACKWARDS_JENKINS = "https://ci.viaversion.com/job/ViaBackwards";
    private static final String VIAREWIND_JENKINS = "https://ci.viaversion.com/job/ViaRewind";
    private static final String VIAREWIND_LEGACY_JENKINS = "https://ci.viaversion.com/job/ViaRewind%20Legacy%20Support";
    private static final String GEYSERUTILS_GITHUB_API = "https://api.github.com/repos/GeyserExtensionists/GeyserUtils/releases/latest";
    private static final String GEYSERMODELENGINE_GITHUB_API = "https://api.github.com/repos/xSquishyLiam/mc-GeyserModelEngine-plugin/releases/latest";
    private static final String FAWE_JENKINS = "https://ci.athion.net/job/FastAsyncWorldEdit";
    private static final String PLACEHOLDERAPI_GITHUB_API = "https://api.github.com/repos/PlaceholderAPI/PlaceholderAPI";
    private static final String ITEMNBTAPI_GITHUB_API = "https://api.github.com/repos/tr7zw/Item-NBT-API";
    private final HttpClient http;
    private final LogAdapter log;
    private final Config cfg;

    public UpdaterService(LogAdapter log, Config cfg) {
        this.log = log;
        this.cfg = cfg;
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public static class UpdateOutcome {
        public final Project project;
        public final boolean updated;
        public final boolean skippedNoChange;
        public final Optional<String> error;
        public final String oldVersion;  // Previous version (null if not installed)
        public final String newVersion;  // New version downloaded (null if error/skipped)

        public UpdateOutcome(Project project, boolean updated, boolean skippedNoChange, Optional<String> error) {
            this(project, updated, skippedNoChange, error, null, null);
        }

        public UpdateOutcome(Project project, boolean updated, boolean skippedNoChange, Optional<String> error, String oldVersion, String newVersion) {
            this.project = project;
            this.updated = updated;
            this.skippedNoChange = skippedNoChange;
            this.error = error;
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
        }
    }

    public List<UpdateOutcome> checkAndUpdate(Platform platform, Path pluginsDir) {
        return checkAndUpdate(platform, pluginsDir, false);
    }

    public List<UpdateOutcome> checkAndUpdate(Platform platform, Path pluginsDir, boolean isPeriodic) {
        List<Project> targets = collectTargets(platform);
        if (targets.isEmpty()) {
            return Collections.singletonList(new UpdateOutcome(Project.GEYSER, false, false,
                    Optional.of("No targets enabled")));
        }
        List<UpdateOutcome> results = new ArrayList<>();
        for (Project p : targets) {
            results.add(updateOne(p, platform, pluginsDir));
        }

        // Send Discord notifications if enabled
        try {
            DiscordNotifier discordNotifier = new DiscordNotifier(cfg, log);

            // Send update notification
            discordNotifier.notifyUpdates(results);

            // Send periodic summary if this is a periodic check
            if (isPeriodic && cfg.discordWebhook.notifyPeriodicSummary) {
                List<VersionInfo> versions = checkVersions(platform, pluginsDir);
                discordNotifier.notifyPeriodicCheck(versions);
            }
        } catch (Exception e) {
            log.warn("Failed to send Discord notification: " + e.getMessage());
        }

        return results;
    }

    public List<VersionInfo> checkVersions(Platform platform, Path pluginsDir) {
        List<VersionInfo> results = new ArrayList<>();

        // Check all projects (not just enabled ones)
        for (Project project : Project.values()) {
            results.add(checkVersionForProject(project, platform, pluginsDir));
        }

        return results;
    }

    private VersionInfo checkVersionForProject(Project project, Platform platform, Path pluginsDir) {
        // Check if target is enabled
        boolean enabled = isProjectEnabled(project);

        if (!enabled) {
            return VersionInfo.disabled(project);
        }

        // Special validation: Geyser extensions require Geyser to be installed
        if (project.isGeyserExtension() && !cfg.targets.geyser) {
            return VersionInfo.error(project, "Requires Geyser to be enabled in config");
        }

        try {
            // Determine search directory: extensions folder for Geyser extensions, plugins folder otherwise
            Path searchDir = pluginsDir;
            if (project.isGeyserExtension()) {
                try {
                    Path extensionsDir = findGeyserExtensionsFolder(platform, pluginsDir);
                    if (extensionsDir != null) {
                        searchDir = extensionsDir;
                    }
                } catch (IOException e) {
                    return VersionInfo.error(project, "Geyser folder not found. Make sure Geyser is installed.");
                }
            }

            // Find installed version
            Path existing = findExistingJar(project, searchDir);
            String installedVersion = existing != null ? existing.getFileName().toString() : null;

            // Get latest version URL (don't download, just check)
            String latestVersion = getLatestVersionString(project, platform);

            // Compare versions
            if (installedVersion == null) {
                return VersionInfo.notInstalled(project, latestVersion);
            }

            // Normalize filenames to remove browser download suffixes before comparing
            String normalizedInstalled = normalizeFilename(installedVersion);
            String normalizedLatest = normalizeFilename(latestVersion);

            // Check if update is available by comparing normalized version strings
            boolean updateAvailable = isUpdateAvailable(normalizedInstalled, normalizedLatest, project);

            if (updateAvailable) {
                return VersionInfo.updateAvailable(project, installedVersion, latestVersion);
            } else {
                return VersionInfo.upToDate(project, installedVersion, latestVersion);
            }
        } catch (Exception e) {
            return VersionInfo.error(project, e.getMessage());
        }
    }

    private boolean isProjectEnabled(Project project) {
        if (project == Project.GEYSER) return cfg.targets.geyser;
        if (project == Project.FLOODGATE) return cfg.targets.floodgate;
        if (project == Project.LUCKPERMS) return cfg.targets.luckperms;
        if (project == Project.FAWE) return cfg.targets.fawe;
        if (project == Project.PLACEHOLDERAPI) return cfg.targets.placeholderapi;
        if (project == Project.ITEMNBTAPI) return cfg.targets.itemnbtapi;
        if (project == Project.PACKETEVENTS) return cfg.targets.packetevents.enabled;
        if (project == Project.PROTOCOLLIB) return cfg.targets.protocollib.enabled;
        if (project == Project.VIAVERSION) return cfg.targets.viaPlugins.viaVersion;
        if (project == Project.VIABACKWARDS) return cfg.targets.viaPlugins.viaBackwards;
        if (project == Project.VIAREWIND) return cfg.targets.viaPlugins.viaRewind;
        if (project == Project.VIAREWIND_LEGACY) return cfg.targets.viaPlugins.viaRewindLegacy;
        if (project == Project.GEYSERUTILS_EXTENSION) return cfg.targets.geyserExtensions.geyserUtils.extension;
        if (project == Project.GEYSERUTILS_PLUGIN) return cfg.targets.geyserExtensions.geyserUtils.plugin;
        if (project == Project.GEYSERMODELENGINE_EXTENSION) return cfg.targets.geyserExtensions.geyserModelEngineExtension.enabled;
        if (project == Project.THIRDPARTYCOSMETICS_EXTENSION) return cfg.targets.geyserExtensions.thirdPartyCosmetics;
        if (project == Project.EMOTEOFFHAND_EXTENSION) return cfg.targets.geyserExtensions.emoteOffhand;
        if (project == Project.GEYSERMODELENGINE_PLUGIN) return cfg.targets.geyserExtensions.geyserModelEnginePlugin;
        return false;
    }

    private String getLatestVersionString(Project project, Platform platform) throws IOException {
        // ThirdPartyCosmetics and EmoteOffhand use GeyserMC API directly (no version in URL)
        if (project == Project.THIRDPARTYCOSMETICS_EXTENSION) {
            return "thirdpartycosmetics.jar";
        } else if (project == Project.EMOTEOFFHAND_EXTENSION) {
            return "emoteoffhand.jar";
        } else if (project.isGeyserExtension() || project.isGeyserRelatedPlugin()) {
            String url = getGeyserExtensionDownloadUrl(project, platform);
            int lastSlash = url.lastIndexOf('/');
            return url.substring(lastSlash + 1);
        } else if (project.isViaPlugin()) {
            // VIA plugins are Spigot only
            if (platform != Platform.SPIGOT) {
                throw new IOException("VIA plugins are only available for Spigot");
            }
            String url = getViaPluginDownloadUrl(project);
            int lastSlash = url.lastIndexOf('/');
            return url.substring(lastSlash + 1);
        } else if (project.isProtocolLib()) {
            // ProtocolLib is Spigot only
            if (platform != Platform.SPIGOT) {
                throw new IOException("ProtocolLib is only available for Spigot");
            }
            // Check if using dev builds or stable releases
            if (cfg.targets.protocollib.enabled && cfg.targets.protocollib.useDevBuilds) {
                // Get from "dev-build" tag on GitHub
                String url = getProtocolLibDevBuildUrl();
                int lastSlash = url.lastIndexOf('/');
                return url.substring(lastSlash + 1);
            } else {
                // Get from latest stable release
                String url = getProtocolLibStableUrl();
                int lastSlash = url.lastIndexOf('/');
                return url.substring(lastSlash + 1);
            }
        } else if (project.isPacketEvents()) {
            // Check if using dev builds or stable releases
            if (cfg.targets.packetevents.enabled && cfg.targets.packetevents.useDevBuilds) {
                // Get from Jenkins artifact name
                String apiUrl = PACKETEVENTS_JENKINS + "/lastSuccessfulBuild/api/json";
                HttpRequest req = createRequestBuilder(apiUrl)
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();
                try {
                    HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                        return extractPacketEventsArtifact(resp.body(), platform);
                    }
                    throw new IOException("Failed to fetch PacketEvents Jenkins version");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted", e);
                }
            } else {
                // Get from GitHub releases
                String url = getPacketEventsGitHubUrl(platform);
                // Extract filename from URL
                int lastSlash = url.lastIndexOf('/');
                return url.substring(lastSlash + 1);
            }
        } else if (project.isLuckPerms()) {
            // Get from LuckPerms API
            HttpRequest req = createRequestBuilder(LUCKPERMS_API)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            try {
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    String url = extractLuckPermsUrl(resp.body(), platform);
                    // Extract version from URL (e.g., LuckPerms-Bukkit-5.4.139.jar)
                    int lastSlash = url.lastIndexOf('/');
                    return url.substring(lastSlash + 1);
                }
                throw new IOException("Failed to fetch LuckPerms version");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            }
        } else if (project == Project.FAWE) {
            // FAWE from Jenkins
            if (platform != Platform.SPIGOT) {
                throw new IOException("FAWE is only available for Spigot");
            }
            String apiUrl = FAWE_JENKINS + "/lastSuccessfulBuild/api/json";
            HttpRequest req = createRequestBuilder(apiUrl)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            try {
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    String relativePath = extractFAWEArtifact(resp.body());
                    // Extract just the filename from the path
                    int lastSlash = relativePath.lastIndexOf('/');
                    return lastSlash != -1 ? relativePath.substring(lastSlash + 1) : relativePath;
                }
                throw new IOException("Failed to fetch FAWE Jenkins version");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            }
        } else if (project == Project.PLACEHOLDERAPI) {
            // PlaceholderAPI from GitHub latest tag
            if (platform != Platform.SPIGOT) {
                throw new IOException("PlaceholderAPI is only available for Spigot");
            }
            String url = getPlaceholderAPIUrl();
            int lastSlash = url.lastIndexOf('/');
            return url.substring(lastSlash + 1);
        } else if (project == Project.ITEMNBTAPI) {
            // ItemNBTAPI from GitHub releases
            if (platform != Platform.SPIGOT) {
                throw new IOException("ItemNBTAPI is only available for Spigot");
            }
            String url = getItemNBTAPIUrl();
            int lastSlash = url.lastIndexOf('/');
            return url.substring(lastSlash + 1);
        } else if (project.isGeyserExtension() || project.isGeyserRelatedPlugin()) {
            // Geyser extensions and related plugins from GitHub releases
            String url = getGeyserExtensionDownloadUrl(project, platform);
            int lastSlash = url.lastIndexOf('/');
            return url.substring(lastSlash + 1);
        } else {
            // Geyser/Floodgate - construct expected filename based on project and platform
            return getDefaultFilename(project, platform);
        }
    }

    private boolean isUpdateAvailable(String installed, String latest, Project project) {
        // Simple string comparison - if names don't match, update is available
        return !installed.equalsIgnoreCase(latest);
    }

    private List<Project> collectTargets(Platform platform) {
        List<Project> targets = new ArrayList<>();
        if (cfg.targets.geyser) targets.add(Project.GEYSER);
        if (cfg.targets.floodgate) targets.add(Project.FLOODGATE);
        if (cfg.targets.luckperms) targets.add(Project.LUCKPERMS);
        if (cfg.targets.packetevents.enabled) targets.add(Project.PACKETEVENTS);
        if (cfg.targets.protocollib.enabled) targets.add(Project.PROTOCOLLIB);
        if (cfg.targets.viaPlugins.viaVersion) targets.add(Project.VIAVERSION);
        if (cfg.targets.viaPlugins.viaBackwards) targets.add(Project.VIABACKWARDS);
        if (cfg.targets.viaPlugins.viaRewind) targets.add(Project.VIAREWIND);
        if (cfg.targets.viaPlugins.viaRewindLegacy) targets.add(Project.VIAREWIND_LEGACY);
        // FAWE, PlaceholderAPI, and ItemNBTAPI are Spigot only
        if (platform == Platform.SPIGOT) {
            if (cfg.targets.fawe) targets.add(Project.FAWE);
            if (cfg.targets.placeholderapi) targets.add(Project.PLACEHOLDERAPI);
            if (cfg.targets.itemnbtapi) targets.add(Project.ITEMNBTAPI);
        }
        // Geyser extensions
        if (cfg.targets.geyserExtensions.geyserUtils.extension) {
            targets.add(Project.GEYSERUTILS_EXTENSION);
        }
        if (cfg.targets.geyserExtensions.geyserUtils.plugin) {
            targets.add(Project.GEYSERUTILS_PLUGIN);
        }
        if (cfg.targets.geyserExtensions.geyserModelEngineExtension.enabled) {
            targets.add(Project.GEYSERMODELENGINE_EXTENSION);
        }
        if (cfg.targets.geyserExtensions.thirdPartyCosmetics) {
            targets.add(Project.THIRDPARTYCOSMETICS_EXTENSION);
        }
        if (cfg.targets.geyserExtensions.emoteOffhand) {
            targets.add(Project.EMOTEOFFHAND_EXTENSION);
        }
        if (cfg.targets.geyserExtensions.geyserModelEnginePlugin) {
            // Plugin is only for Spigot
            if (platform == Platform.SPIGOT) {
                targets.add(Project.GEYSERMODELENGINE_PLUGIN);
            }
        }
        return targets;
    }

    private UpdateOutcome updateOne(Project project, Platform platform, Path pluginsDir) {
        // Special validation: Geyser extensions require Geyser to be installed
        if (project.isGeyserExtension() && !cfg.targets.geyser) {
            return new UpdateOutcome(project, false, false,
                Optional.of("Requires Geyser to be enabled in config"));
        }

        try {
            // For Geyser extensions, we need to work with the Geyser extensions folder
            Path targetDir = project.isGeyserExtension() ? findGeyserExtensionsFolder(platform, pluginsDir) : pluginsDir;

            if (project.isGeyserExtension() && targetDir == null) {
                return new UpdateOutcome(project, false, false,
                    Optional.of("Geyser folder not found. Make sure Geyser is installed."));
            }

            Path existing = findExistingJar(project, targetDir);
            String oldVersion = existing != null ? existing.getFileName().toString() : null;
            String downloadUrl = buildDownloadUrl(project, platform);

            Path tmp = Files.createTempFile("geyserupdater-" + project.apiName(), ".jar");
            try {
                downloadTo(downloadUrl, tmp);
            } catch (IOException e) {
                return new UpdateOutcome(project, false, false,
                    Optional.of(cfg.messages.downloadFailed.replace("{error}", e.getMessage())));
            }

            // If existing file exists, compare by filename instead of hash for GitHub releases
            // (GitHub releases don't change for the same version, so filename comparison is reliable)
            if (existing != null && Files.exists(existing)) {
                String existingFilename = existing.getFileName().toString();
                String newFilename;

                // Determine what the new filename would be
                if (project == Project.GEYSER || project == Project.FLOODGATE) {
                    newFilename = defaultDestination(project, platform, targetDir).getFileName().toString();
                } else if (project == Project.THIRDPARTYCOSMETICS_EXTENSION) {
                    newFilename = "thirdpartycosmetics.jar";
                } else if (project == Project.EMOTEOFFHAND_EXTENSION) {
                    newFilename = "emoteoffhand.jar";
                } else {
                    newFilename = extractFilenameFromUrl(downloadUrl);
                }

                // Normalize filenames to remove browser download suffixes like (1), (2), etc.
                String normalizedExisting = normalizeFilename(existingFilename);
                String normalizedNew = normalizeFilename(newFilename);

                log.info(ANSI_CYAN + "Comparing versions - Existing: " + existingFilename + ", Latest: " + newFilename + ANSI_RESET);

                // If normalized filenames match, compare file sizes to detect updates
                if (normalizedExisting.equals(normalizedNew)) {
                    long existingSize = Files.size(existing);
                    long newSize = Files.size(tmp);

                    // Special case: SNAPSHOT versions and GitHub releases should check timestamps
                    // because they can change without changing version number or file size
                    boolean isSnapshot = normalizedNew.toLowerCase().contains("-SNAPSHOT");
                    boolean isGitHubRelease = project.isGeyserExtension() || project.isGeyserRelatedPlugin() ||
                                              project == Project.GEYSERMODELENGINE_EXTENSION ||
                                              project == Project.GEYSERMODELENGINE_PLUGIN ||
                                              project == Project.GEYSERUTILS_EXTENSION ||
                                              project == Project.GEYSERUTILS_PLUGIN;

                    // For GitHub releases (including extensions), always check timestamp when sizes match
                    if ((isSnapshot || isGitHubRelease) && existingSize == newSize) {
                        // For SNAPSHOT versions from GitHub, check if we should update
                        if (newSize < 1_000_000 && existingSize > 10_000_000) {
                            // Downloaded file is suspiciously small - likely an error page or rate limit response
                            Files.deleteIfExists(tmp);
                            log.warn(ANSI_RED + project.apiName() + " SNAPSHOT download appears corrupted (size: " + newSize + " bytes vs existing " + existingSize + " bytes) - skipping update" + ANSI_RESET);
                            return new UpdateOutcome(project, false, false, Optional.of("Downloaded file too small - possible download error"));
                        } else if (existingSize == newSize) {
                            // Same size - check if GitHub release is newer than local file
                            try {
                                long localModified = Files.getLastModifiedTime(existing).toMillis();
                                Long githubPublished = getGitHubReleaseTimestamp(project);

                                if (githubPublished != null && githubPublished > localModified) {
                                    log.info(ANSI_YELLOW + project.apiName() + " has same size but GitHub release is newer - updating" + ANSI_RESET);
                                    // Continue with update
                                } else {
                                    Files.deleteIfExists(tmp);
                                    log.info(ANSI_GREEN + project.apiName() + " is up to date (verified by GitHub timestamp)" + ANSI_RESET);
                                    return new UpdateOutcome(project, false, true, Optional.empty());
                                }
                            } catch (Exception e) {
                                // If we can't get timestamp, skip update to be safe
                                Files.deleteIfExists(tmp);
                                log.info(ANSI_YELLOW + project.apiName() + " has matching size - skipping update (could not verify timestamp)" + ANSI_RESET);
                                return new UpdateOutcome(project, false, true, Optional.empty());
                            }
                        } else if (isSnapshot) {
                            log.info(ANSI_YELLOW + project.apiName() + " SNAPSHOT has different size: " + existingSize + " -> " + newSize + " bytes - updating" + ANSI_RESET);
                            // Continue with update
                        }
                    } else if (existingSize == newSize) {
                        Files.deleteIfExists(tmp);
                        log.info(ANSI_GREEN + project.apiName() + " is already up to date (filename and size match)" + ANSI_RESET);
                        return new UpdateOutcome(project, false, true, Optional.empty());
                    } else {
                        log.info(ANSI_YELLOW + project.apiName() + " has an update (same filename but different size: " + existingSize + " -> " + newSize + " bytes)" + ANSI_RESET);
                        // Continue with update
                    }
                } else {
                    // Filenames are different - compare normalized versions to prevent downgrade
                    int versionComparison = compareVersions(normalizedExisting, normalizedNew);
                    if (versionComparison > 0) {
                        // Existing version is NEWER than the "latest" - prevent downgrade
                        Files.deleteIfExists(tmp);
                        log.warn(ANSI_RED + project.apiName() + " - Existing version (" + normalizedExisting + ") is newer than available version (" + normalizedNew + "). Skipping downgrade." + ANSI_RESET);
                        return new UpdateOutcome(project, false, true, Optional.empty());
                    } else if (versionComparison < 0) {
                        // New version is NEWER - proceed with update
                        log.info(ANSI_YELLOW + project.apiName() + " has a new version available: " + normalizedNew + ANSI_RESET);
                    } else {
                        // Versions are equal (but filenames differ slightly) - skip update
                        Files.deleteIfExists(tmp);
                        log.info(ANSI_GREEN + project.apiName() + " versions are equivalent, skipping update" + ANSI_RESET);
                        return new UpdateOutcome(project, false, true, Optional.empty());
                    }
                }
            }

            // Determine destination
            Path dest;
            // For Geyser/Floodgate/ThirdPartyCosmetics/EmoteOffhand, always use default naming since API doesn't provide filename
            // For others, extract filename from URL to preserve version information
            if (project == Project.GEYSER || project == Project.FLOODGATE
                || project == Project.THIRDPARTYCOSMETICS_EXTENSION || project == Project.EMOTEOFFHAND_EXTENSION) {
                dest = defaultDestination(project, platform, targetDir);
                // If we're updating and the old file exists with a different name, delete it
                if (existing != null && !existing.equals(dest)) {
                    Files.deleteIfExists(existing);
                }
            } else {
                String filename = extractFilenameFromUrl(downloadUrl);
                dest = targetDir.resolve(filename);
                // If existing file exists with a different name, delete it before moving new one
                if (existing != null && !existing.equals(dest)) {
                    Files.deleteIfExists(existing);
                }
            }
            // Move atomically
            FileUtils.atomicMove(tmp, dest);

            // If this is GeyserModelEngineExtension and cleanOnUpdate is enabled, create cleanup marker
            if (project == Project.GEYSERMODELENGINE_EXTENSION && cfg.targets.geyserExtensions.geyserModelEngineExtension.cleanOnUpdate) {
                createCleanupMarker(targetDir);
            }

            String newVersion = dest.getFileName().toString();
            return new UpdateOutcome(project, true, false, Optional.empty(), oldVersion, newVersion);
        } catch (Exception ex) {
            return new UpdateOutcome(project, false, false, Optional.of(ex.getMessage()));
        }
    }

    private String buildDownloadUrl(Project project, Platform platform) throws IOException {
        // ThirdPartyCosmetics and EmoteOffhand use GeyserMC API directly (no platform suffix)
        if (project == Project.THIRDPARTYCOSMETICS_EXTENSION) {
            return GEYSER_BASE + "/thirdpartycosmetics/versions/latest/builds/latest/downloads/thirdpartycosmetics";
        } else if (project == Project.EMOTEOFFHAND_EXTENSION) {
            return GEYSER_BASE + "/emoteoffhand/versions/latest/builds/latest/downloads/emoteoffhand";
        } else if (project.isGeyserExtension() || project.isGeyserRelatedPlugin()) {
            return getGeyserExtensionDownloadUrl(project, platform);
        } else if (project.isViaPlugin()) {
            return getViaPluginDownloadUrl(project);
        } else if (project.isProtocolLib()) {
            return getProtocolLibDownloadUrl(platform);
        } else if (project.isLuckPerms()) {
            return getLuckPermsDownloadUrl(platform);
        } else if (project.isPacketEvents()) {
            return getPacketEventsDownloadUrl(platform);
        } else if (project == Project.FAWE) {
            return getFAWEDownloadUrl(platform);
        } else if (project == Project.PLACEHOLDERAPI) {
            return getPlaceholderAPIUrl();
        } else if (project == Project.ITEMNBTAPI) {
            return getItemNBTAPIUrl();
        } else {
            // GeyserMC API (Geyser & Floodgate)
            // Special case: Floodgate uses "bungee" instead of "bungeecord" in the API
            String platformName = platform.apiName();
            if (project == Project.FLOODGATE && platform == Platform.BUNGEECORD) {
                platformName = "bungee";
            }
            return GEYSER_BASE + "/" + project.apiName() + "/versions/latest/builds/latest/downloads/" + platformName;
        }
    }

    private String getLuckPermsDownloadUrl(Platform platform) throws IOException {
        // Fetch the LuckPerms metadata API to get download URLs
        HttpRequest req = createRequestBuilder(LUCKPERMS_API)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                String body = resp.body();
                // Parse JSON to extract the correct platform URL
                return extractLuckPermsUrl(body, platform);
            } else {
                throw new IOException("HTTP " + resp.statusCode() + " when fetching LuckPerms metadata");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private String extractLuckPermsUrl(String json, Platform platform) throws IOException {
        // Simple JSON parsing for the downloads object
        // Expected format: {"downloads":{"bukkit":"url","bungee":"url","velocity":"url"}}
        String platformKey = mapPlatformToLuckPerms(platform);

        // Find the platform key in JSON
        String searchKey = "\"" + platformKey + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            throw new IOException("Platform " + platformKey + " not found in LuckPerms downloads");
        }

        startIndex += searchKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) {
            throw new IOException("Invalid JSON format for LuckPerms downloads");
        }

        return json.substring(startIndex, endIndex);
    }

    private String mapPlatformToLuckPerms(Platform platform) {
        // Map our platform names to LuckPerms platform names
        switch (platform) {
            case SPIGOT:
                return "bukkit";  // LuckPerms uses "bukkit" for Spigot/Paper
            case BUNGEECORD:
                return "bungee";
            case VELOCITY:
                return "velocity";
            default:
                return platform.apiName();
        }
    }

    private String getPacketEventsDownloadUrl(Platform platform) throws IOException {
        // Check if using dev builds or stable releases (only if packetevents is enabled)
        if (cfg.targets.packetevents.enabled && cfg.targets.packetevents.useDevBuilds) {
            // Dev builds from Jenkins
            return getPacketEventsJenkinsUrl(platform);
        } else {
            // Stable releases from GitHub
            return getPacketEventsGitHubUrl(platform);
        }
    }

    private String getPacketEventsJenkinsUrl(Platform platform) throws IOException {
        // Fetch Jenkins API to get artifact name
        String apiUrl = PACKETEVENTS_JENKINS + "/lastSuccessfulBuild/api/json";
        HttpRequest req = createRequestBuilder(apiUrl)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                String body = resp.body();
                String artifactFileName = extractPacketEventsArtifact(body, platform);
                return PACKETEVENTS_JENKINS + "/lastSuccessfulBuild/artifact/build/libs/" + artifactFileName;
            } else {
                throw new IOException("HTTP " + resp.statusCode() + " when fetching PacketEvents Jenkins API");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private String getPacketEventsGitHubUrl(Platform platform) throws IOException {
        // Fetch GitHub API to get latest release
        HttpRequest req = createRequestBuilder(PACKETEVENTS_GITHUB_API)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                String body = resp.body();
                return extractPacketEventsGitHubAsset(body, platform);
            } else {
                throw new IOException("HTTP " + resp.statusCode() + " when fetching PacketEvents GitHub API");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private String extractPacketEventsGitHubAsset(String json, Platform platform) throws IOException {
        // Get platform-specific artifact name
        String platformName = mapPlatformToPacketEvents(platform);

        // Parse GitHub API response to find the asset browser_download_url
        // Example: "browser_download_url":"https://github.com/retrooper/packetevents/releases/download/v2.10.1/packetevents-spigot-2.10.1.jar"
        String searchPattern = "\"browser_download_url\":\"";
        int startPos = json.indexOf(searchPattern);

        while (startPos != -1) {
            int urlStart = startPos + searchPattern.length();
            int urlEnd = json.indexOf("\"", urlStart);
            if (urlEnd == -1) break;

            String url = json.substring(urlStart, urlEnd);
            // Check if this URL is for our platform and NOT the API jar
            // Look for exact pattern: packetevents-{platform}-{version}.jar
            if (url.contains("/packetevents-" + platformName + "-") &&
                url.endsWith(".jar") &&
                !url.contains("-api-") &&
                !url.contains("-javadoc") &&
                !url.contains("-sources")) {
                return url;
            }

            // Look for next occurrence
            startPos = json.indexOf(searchPattern, urlEnd);
        }

        throw new IOException("PacketEvents GitHub asset for platform " + platformName + " not found");
    }

    private String extractPacketEventsArtifact(String json, Platform platform) throws IOException {
        // Get platform-specific artifact name
        String platformName = mapPlatformToPacketEvents(platform);

        // Look for artifact with pattern: packetevents-{platform}-{version}.jar
        // We need to find the fileName in the artifacts array that matches our platform
        String searchPattern = "\"fileName\":\"";
        int startIndex = json.indexOf(searchPattern);

        while (startIndex != -1) {
            int fileNameStart = startIndex + searchPattern.length();
            int fileNameEnd = json.indexOf("\"", fileNameStart);
            if (fileNameEnd == -1) break;

            String fileName = json.substring(fileNameStart, fileNameEnd);
            // Check if this is the platform-specific plugin JAR (not API, javadoc, or sources)
            if (fileName.startsWith("packetevents-" + platformName + "-") &&
                fileName.endsWith(".jar") &&
                !fileName.contains("-api-") &&
                !fileName.contains("-javadoc") &&
                !fileName.contains("-sources")) {
                return fileName;
            }

            // Look for next occurrence
            startIndex = json.indexOf(searchPattern, fileNameEnd);
        }

        throw new IOException("PacketEvents artifact for platform " + platformName + " not found");
    }

    private String mapPlatformToPacketEvents(Platform platform) {
        // Map our platform names to PacketEvents artifact names
        switch (platform) {
            case SPIGOT:
                return "spigot";
            case BUNGEECORD:
                return "bungeecord";
            case VELOCITY:
                return "velocity";
            default:
                return platform.apiName();
        }
    }

    private String getProtocolLibDownloadUrl(Platform platform) throws IOException {
        // ProtocolLib is Spigot only
        if (platform != Platform.SPIGOT) {
            throw new IOException("ProtocolLib is only available for Spigot");
        }

        // Check if using dev builds or stable releases (only if protocollib is enabled)
        if (cfg.targets.protocollib.enabled && cfg.targets.protocollib.useDevBuilds) {
            // Dev builds from "dev-build" tag on GitHub
            return getProtocolLibDevBuildUrl();
        } else {
            // Stable releases from GitHub latest release
            return getProtocolLibStableUrl();
        }
    }

    private String getProtocolLibStableUrl() throws IOException {
        // Fetch GitHub API to get latest stable release
        String apiUrl = PROTOCOLLIB_GITHUB_API + "/releases/latest";
        HttpRequest req = createRequestBuilder(apiUrl)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                String body = resp.body();
                return extractProtocolLibAsset(body);
            } else {
                throw new IOException("HTTP " + resp.statusCode() + " when fetching ProtocolLib GitHub API");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private String getProtocolLibDevBuildUrl() throws IOException {
        // Fetch GitHub API to get the "dev-build" tag/release
        String apiUrl = PROTOCOLLIB_GITHUB_API + "/releases/tags/dev-build";
        HttpRequest req = createRequestBuilder(apiUrl)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                String body = resp.body();
                return extractProtocolLibAsset(body);
            } else {
                throw new IOException("HTTP " + resp.statusCode() + " when fetching ProtocolLib dev-build tag");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private String extractProtocolLibAsset(String json) throws IOException {
        // Parse GitHub API response to find the JAR asset browser_download_url
        // Example: "browser_download_url":"https://github.com/dmulloy2/ProtocolLib/releases/download/5.3.0/ProtocolLib.jar"
        String searchPattern = "\"browser_download_url\":\"";
        int startPos = json.indexOf(searchPattern);

        while (startPos != -1) {
            int urlStart = startPos + searchPattern.length();
            int urlEnd = json.indexOf("\"", urlStart);
            if (urlEnd == -1) break;

            String url = json.substring(urlStart, urlEnd);
            // Check if this URL is for ProtocolLib JAR
            if (url.contains("ProtocolLib") && url.endsWith(".jar")) {
                return url;
            }

            // Look for next occurrence
            startPos = json.indexOf(searchPattern, urlEnd);
        }

        throw new IOException("ProtocolLib JAR asset not found in GitHub release");
    }

    private String getGeyserExtensionDownloadUrl(Project project, Platform platform) throws IOException {
        String apiUrl;
        String fileNameHint;

        switch (project) {
            case GEYSERUTILS_EXTENSION:
                apiUrl = GEYSERUTILS_GITHUB_API;
                fileNameHint = "geyserutils-geyser";
                break;
            case GEYSERUTILS_PLUGIN:
                apiUrl = GEYSERUTILS_GITHUB_API;
                // Platform-specific plugin
                switch (platform) {
                    case SPIGOT:
                        fileNameHint = "geyserutils-spigot";
                        break;
                    case BUNGEECORD:
                        fileNameHint = "geyserutils-bungee";
                        break;
                    case VELOCITY:
                        fileNameHint = "geyserutils-velocity";
                        break;
                    default:
                        throw new IOException("Unsupported platform for GeyserUtils plugin: " + platform);
                }
                break;
            case GEYSERMODELENGINE_EXTENSION:
                apiUrl = GEYSERMODELENGINE_GITHUB_API;
                fileNameHint = "GeyserModelEngineExtension";
                break;
            case GEYSERMODELENGINE_PLUGIN:
                // Plugin is Spigot only
                if (platform != Platform.SPIGOT) {
                    throw new IOException("GeyserModelEngine plugin is only available for Spigot");
                }
                apiUrl = GEYSERMODELENGINE_GITHUB_API;
                fileNameHint = "GeyserModelEngine";
                break;
            default:
                throw new IOException("Unknown Geyser extension/plugin: " + project.name());
        }

        // Fetch GitHub API to get latest release
        HttpRequest req = createRequestBuilder(apiUrl)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                String body = resp.body();
                return extractGeyserExtensionAsset(body, fileNameHint);
            } else {
                throw new IOException("HTTP " + resp.statusCode() + " when fetching " + project.name() + " GitHub API");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private String extractGeyserExtensionAsset(String json, String fileNameHint) throws IOException {
        // Parse GitHub API response to find the JAR asset browser_download_url
        String searchPattern = "\"browser_download_url\":\"";
        int startPos = json.indexOf(searchPattern);

        while (startPos != -1) {
            int urlStart = startPos + searchPattern.length();
            int urlEnd = json.indexOf("\"", urlStart);
            if (urlEnd == -1) break;

            String url = json.substring(urlStart, urlEnd);
            // Check if this URL contains the file hint and is a JAR
            if (url.contains(fileNameHint) && url.endsWith(".jar")) {
                return url;
            }

            // Look for next occurrence
            startPos = json.indexOf(searchPattern, urlEnd);
        }

        throw new IOException("JAR asset with hint '" + fileNameHint + "' not found in GitHub release");
    }

    private String getViaPluginDownloadUrl(Project project) throws IOException {
        String jenkinsUrl;
        switch (project) {
            case VIAVERSION:
                jenkinsUrl = VIAVERSION_JENKINS;
                break;
            case VIABACKWARDS:
                jenkinsUrl = VIABACKWARDS_JENKINS;
                break;
            case VIAREWIND:
                jenkinsUrl = VIAREWIND_JENKINS;
                break;
            case VIAREWIND_LEGACY:
                jenkinsUrl = VIAREWIND_LEGACY_JENKINS;
                break;
            default:
                throw new IOException("Unknown VIA plugin: " + project.name());
        }

        // Fetch Jenkins API to get artifact name
        String apiUrl = jenkinsUrl + "/lastSuccessfulBuild/api/json";
        HttpRequest req = createRequestBuilder(apiUrl)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                String body = resp.body();
                String artifactFileName = extractViaPluginArtifact(body, project);
                return jenkinsUrl + "/lastSuccessfulBuild/artifact/build/libs/" + artifactFileName;
            } else {
                throw new IOException("HTTP " + resp.statusCode() + " when fetching " + project.name() + " Jenkins API");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private String extractViaPluginArtifact(String json, Project project) throws IOException {
        // Look for artifact fileName in the Jenkins API response
        // The artifact name varies but should contain the project name
        String searchPattern = "\"fileName\":\"";
        int startIndex = json.indexOf(searchPattern);

        while (startIndex != -1) {
            startIndex += searchPattern.length();
            int endIndex = json.indexOf("\"", startIndex);
            if (endIndex == -1) break;

            String fileName = json.substring(startIndex, endIndex);
            // Check if this is the JAR file we want
            if (fileName.endsWith(".jar") && !fileName.contains("javadoc") && !fileName.contains("sources")) {
                return fileName;
            }

            // Look for next occurrence
            startIndex = json.indexOf(searchPattern, endIndex);
        }

        throw new IOException(project.name() + " artifact not found in Jenkins build");
    }

    private String getFAWEDownloadUrl(Platform platform) throws IOException {
        // FAWE is Spigot only
        if (platform != Platform.SPIGOT) {
            throw new IOException("FAWE is only available for Spigot");
        }

        // Fetch Jenkins API to get artifact name
        String apiUrl = FAWE_JENKINS + "/lastSuccessfulBuild/api/json";
        HttpRequest req = createRequestBuilder(apiUrl)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                String body = resp.body();
                String artifactFileName = extractFAWEArtifact(body);
                return FAWE_JENKINS + "/lastSuccessfulBuild/artifact/" + artifactFileName;
            } else {
                throw new IOException("HTTP " + resp.statusCode() + " when fetching FAWE Jenkins API");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private String extractFAWEArtifact(String json) throws IOException {
        // Look for artifact with relativePath that contains "Paper" (the main FAWE version)
        // Example: "relativePath":"worldedit-bukkit/build/libs/FastAsyncWorldEdit-Paper-2.14.3.jar"
        String searchPattern = "\"relativePath\":\"";
        int startIndex = json.indexOf(searchPattern);

        while (startIndex != -1) {
            int pathStart = startIndex + searchPattern.length();
            int pathEnd = json.indexOf("\"", pathStart);
            if (pathEnd == -1) break;

            String relativePath = json.substring(pathStart, pathEnd);
            // Check if this is the Paper JAR file (main version for Paper/Spigot)
            if (relativePath.contains("FastAsyncWorldEdit-Paper-") &&
                relativePath.endsWith(".jar") &&
                !relativePath.contains("javadoc") &&
                !relativePath.contains("sources")) {
                return relativePath;
            }

            // Look for next occurrence
            startIndex = json.indexOf(searchPattern, pathEnd);
        }

        throw new IOException("FAWE Paper artifact not found in Jenkins build");
    }

    private String getPlaceholderAPIUrl() throws IOException {
        // Fetch GitHub API to get latest tag
        String apiUrl = PLACEHOLDERAPI_GITHUB_API + "/tags";
        HttpRequest req = createRequestBuilder(apiUrl)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                String body = resp.body();
                // Extract the latest tag name (first in the array)
                String latestTag = extractLatestTag(body);
                // Build download URL: https://github.com/PlaceholderAPI/PlaceholderAPI/releases/download/{tag}/PlaceholderAPI-{tag}.jar
                return "https://github.com/PlaceholderAPI/PlaceholderAPI/releases/download/" + latestTag + "/PlaceholderAPI-" + latestTag + ".jar";
            } else {
                throw new IOException("HTTP " + resp.statusCode() + " when fetching PlaceholderAPI tags");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private String extractLatestTag(String json) throws IOException {
        // Parse the first tag from the tags array
        // Example: [{"name":"2.11.6",...}]
        String searchPattern = "\"name\":\"";
        int startPos = json.indexOf(searchPattern);
        if (startPos != -1) {
            int tagStart = startPos + searchPattern.length();
            int tagEnd = json.indexOf("\"", tagStart);
            if (tagEnd != -1) {
                return json.substring(tagStart, tagEnd);
            }
        }
        throw new IOException("Could not find latest PlaceholderAPI tag");
    }

    private String getItemNBTAPIUrl() throws IOException {
        // Fetch GitHub API to get releases with assets
        String apiUrl = ITEMNBTAPI_GITHUB_API + "/releases";
        HttpRequest req = createRequestBuilder(apiUrl)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                String body = resp.body();
                // Find first release with "item-nbt-api-plugin-*.jar" asset
                return extractItemNBTAPIAsset(body);
            } else {
                throw new IOException("HTTP " + resp.statusCode() + " when fetching ItemNBTAPI releases");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private String extractItemNBTAPIAsset(String json) throws IOException {
        // Parse releases array to find first (latest) release with "item-nbt-api-plugin-*.jar" asset
        // GitHub returns releases in descending order (newest first)

        // Split by releases (each release starts with "tag_name")
        String[] releaseParts = json.split("\"tag_name\"");

        for (int i = 1; i < releaseParts.length; i++) {
            String releasePart = releaseParts[i];

            // Check if this release has assets
            if (!releasePart.contains("\"assets\"")) continue;

            // Find the assets section for this release
            int assetsStart = releasePart.indexOf("\"assets\"");
            int nextReleaseOrEnd = releasePart.length();
            String assetsSection = releasePart.substring(assetsStart, nextReleaseOrEnd);

            // Look for browser_download_url in this release's assets
            String searchPattern = "\"browser_download_url\":\"";
            int urlPos = assetsSection.indexOf(searchPattern);

            while (urlPos != -1) {
                int urlStart = urlPos + searchPattern.length();
                int urlEnd = assetsSection.indexOf("\"", urlStart);
                if (urlEnd == -1) break;

                String url = assetsSection.substring(urlStart, urlEnd);

                // Check if this is the plugin JAR we want
                if (url.contains("item-nbt-api-plugin-") && url.endsWith(".jar")) {
                    return url;
                }

                // Continue searching in this release
                urlPos = assetsSection.indexOf(searchPattern, urlEnd);
            }
        }

        throw new IOException("Could not find ItemNBTAPI plugin JAR in any release with assets");
    }

    private Path findGeyserExtensionsFolder(Platform platform, Path pluginsDir) throws IOException {
        // Find the Geyser folder based on platform
        String geyserFolderName;
        switch (platform) {
            case SPIGOT:
                geyserFolderName = "Geyser-Spigot";
                break;
            case BUNGEECORD:
                geyserFolderName = "Geyser-BungeeCord";
                break;
            case VELOCITY:
                geyserFolderName = "Geyser-Velocity";
                break;
            default:
                return null;
        }

        Path geyserFolder = pluginsDir.resolve(geyserFolderName);
        if (!Files.exists(geyserFolder) || !Files.isDirectory(geyserFolder)) {
            return null;
        }

        // Find or create the extensions folder
        Path extensionsFolder = geyserFolder.resolve("extensions");
        if (!Files.exists(extensionsFolder)) {
            Files.createDirectories(extensionsFolder);
        }

        return extensionsFolder;
    }

    /**
     * Create a cleanup marker for GeyserModelEngineExtension
     * The cleanup will be executed on server shutdown
     */
    private void createCleanupMarker(Path extensionsFolder) {
        try {
            Path gmepgFolder = getGMEPGFolder(extensionsFolder);
            if (gmepgFolder == null || !Files.exists(gmepgFolder)) {
                log.warn(ANSI_RED + "Cannot create cleanup marker: GeyserModelEngineExtension folder not found" + ANSI_RESET);
                return;
            }

            Path markerFile = gmepgFolder.resolve(".cleanup-pending");
            if (!Files.exists(markerFile)) {
                Files.createFile(markerFile);
                log.info(ANSI_GREEN + "Created cleanup marker - will clean on server shutdown" + ANSI_RESET);
            }
        } catch (IOException e) {
            log.warn(ANSI_RED + "Failed to create cleanup marker: " + e.getMessage() + ANSI_RESET);
        }
    }

    /**
     * Execute cleanup on server shutdown if marker exists
     * This should be called when the plugin is being disabled
     */
    public void executeCleanupOnShutdown(Platform platform, Path pluginsDir) {
        try {
            Path extensionsFolder = findGeyserExtensionsFolder(platform, pluginsDir);
            if (extensionsFolder == null) {
                return;
            }

            Path gmepgFolder = getGMEPGFolder(extensionsFolder);
            if (gmepgFolder == null || !Files.exists(gmepgFolder)) {
                return;
            }

            Path markerFile = gmepgFolder.resolve(".cleanup-pending");
            if (!Files.exists(markerFile)) {
                return; // No cleanup pending
            }

            log.info(ANSI_YELLOW + "Cleanup marker detected, cleaning GeyserModelEngineExtension folder on shutdown..." + ANSI_RESET);

            // Delete all files and folders except input/ and .jar files
            Files.list(gmepgFolder)
                .filter(path -> {
                    String name = path.getFileName().toString();
                    // Keep .jar files, .cleanup-pending marker (will be deleted at the end), and input/ folder
                    return !name.endsWith(".jar") && !name.equals(".cleanup-pending") && !name.equals("input");
                })
                .forEach(path -> {
                    try {
                        if (Files.isDirectory(path)) {
                            deleteDirectoryRecursively(path);
                            log.info(ANSI_GRAY + "Deleted folder: " + path.getFileName() + ANSI_RESET);
                        } else {
                            Files.delete(path);
                            log.info(ANSI_GRAY + "Deleted file: " + path.getFileName() + ANSI_RESET);
                        }
                    } catch (IOException e) {
                        log.warn(ANSI_RED + "Failed to delete " + path + ": " + e.getMessage() + ANSI_RESET);
                    }
                });

            // Delete the marker file
            Files.deleteIfExists(markerFile);
            log.info(ANSI_GREEN + "GeyserModelEngineExtension cleanup completed" + ANSI_RESET);

        } catch (IOException e) {
            log.warn(ANSI_RED + "Error during GeyserModelEngineExtension cleanup: " + e.getMessage() + ANSI_RESET);
        }
    }

    /**
     * Simulate a GeyserModelEngineExtension update by creating cleanup marker
     * This is used for testing purposes via the packtest command
     */
    public boolean simulateGMEPGUpdate(Platform platform, Path pluginsDir) {
        if (!cfg.targets.geyserExtensions.geyserModelEngineExtension.enabled) {
            log.warn(ANSI_RED + "GeyserModelEngineExtension is not enabled in config" + ANSI_RESET);
            return false;
        }

        if (!cfg.targets.geyserExtensions.geyserModelEngineExtension.cleanOnUpdate) {
            log.warn(ANSI_RED + "cleanOnUpdate is not enabled for GeyserModelEngineExtension" + ANSI_RESET);
            return false;
        }

        try {
            Path extensionsFolder = findGeyserExtensionsFolder(platform, pluginsDir);
            if (extensionsFolder == null) {
                log.warn(ANSI_RED + "Could not find Geyser extensions folder" + ANSI_RESET);
                return false;
            }

            log.info(ANSI_CYAN + "Found extensions folder: " + extensionsFolder + ANSI_RESET);

            Path gmepgFolder = getGMEPGFolder(extensionsFolder);
            if (gmepgFolder == null || !Files.exists(gmepgFolder)) {
                log.warn(ANSI_RED + "Could not find GeyserModelEngineExtension folder in extensions folder" + ANSI_RESET);
                // List available folders for debugging
                try {
                    log.info(ANSI_CYAN + "Available folders in extensions:" + ANSI_RESET);
                    Files.list(extensionsFolder)
                        .filter(Files::isDirectory)
                        .forEach(dir -> log.info(ANSI_GRAY + "  - " + dir.getFileName() + ANSI_RESET));
                } catch (IOException ex) {
                    log.warn(ANSI_RED + "Could not list extensions folder contents" + ANSI_RESET);
                }
                return false;
            }

            // Create cleanup marker - cleanup will happen on shutdown
            createCleanupMarker(extensionsFolder);
            log.info(ANSI_GREEN + "Created cleanup marker - cleanup will execute on server shutdown" + ANSI_RESET);
            return true;
        } catch (Exception e) {
            log.warn(ANSI_RED + "Failed to create cleanup marker: " + e.getMessage() + ANSI_RESET);
            return false;
        }
    }

    /**
     * Test Discord webhook by sending a test notification
     * This is used for testing purposes via the webhooktest command
     * @return true if the test was successful, false otherwise
     */
    public boolean testDiscordWebhook() {
        // Allow testing even if disabled - just check if URL is configured
        if (cfg.discordWebhook.webhookUrl == null || cfg.discordWebhook.webhookUrl.trim().isEmpty()) {
            log.warn(ANSI_YELLOW + "Discord webhook URL is not configured in config.yml" + ANSI_RESET);
            return false;
        }

        try {
            DiscordNotifier notifier = new DiscordNotifier(cfg, log);
            return notifier.sendTestNotification();
        } catch (Exception e) {
            log.warn(ANSI_RED + "Failed to send Discord webhook test: " + e.getMessage() + ANSI_RESET);
            return false;
        }
    }


    private Path getGMEPGFolder(Path extensionsFolder) {
        // Try different possible folder names (new and old names for backwards compatibility)
        String[] possibleNames = {
            "GeyserModelEngineExtension",
            "geysermodelengineextension",
            "GeyserModelEnginePackGenerator",  // Old name for backwards compatibility
            "geysermodelenginepackgenerator",
            "Geyser-ModelEnginePackGenerator"
        };

        for (String name : possibleNames) {
            Path folder = extensionsFolder.resolve(name);
            if (Files.exists(folder)) {
                return folder;
            }
        }

        // If not found by name, search for any folder containing "modelengine"
        try {
            return Files.list(extensionsFolder)
                .filter(Files::isDirectory)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.contains("modelengine");
                })
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private void deleteDirectoryRecursively(Path directory) throws IOException {
        Files.walk(directory)
            .sorted((a, b) -> -a.compareTo(b)) // Reverse order to delete files before directories
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    log.warn(ANSI_RED + "Failed to delete " + path + ": " + e.getMessage() + ANSI_RESET);
                }
            });
    }

    /**
     * Compare two version strings extracted from filenames
     * Returns: negative if version1 < version2, positive if version1 > version2, zero if equal
     *
     * Extracts version numbers from filenames like:
     * - "plugin-1.2.3.jar" -> [1, 2, 3]
     * - "Plugin-v2.0.jar" -> [2, 0]
     * - "item-nbt-api-plugin-2.15.0.jar" -> [2, 15, 0]
     */
    private int compareVersions(String filename1, String filename2) {
        try {
            int[] version1 = extractVersionNumbers(filename1);
            int[] version2 = extractVersionNumbers(filename2);

            // Compare each version component
            int maxLength = Math.max(version1.length, version2.length);
            for (int i = 0; i < maxLength; i++) {
                int v1 = i < version1.length ? version1[i] : 0;
                int v2 = i < version2.length ? version2[i] : 0;

                if (v1 != v2) {
                    return Integer.compare(v1, v2);
                }
            }

            return 0; // Versions are equal
        } catch (Exception e) {
            // If version extraction fails, treat as equal (fallback to filename comparison)
            log.warn(ANSI_YELLOW + "Failed to compare versions for: " + filename1 + " vs " + filename2 + " - " + e.getMessage() + ANSI_RESET);
            return 0;
        }
    }

    /**
     * Extract version numbers from a filename
     * Examples:
     * - "PlaceholderAPI-2.11.6.jar" -> [2, 11, 6]
     * - "item-nbt-api-plugin-2.15.0.jar" -> [2, 15, 0]
     * - "Geyser-Spigot.jar" -> [] (no version)
     */
    private int[] extractVersionNumbers(String filename) {
        // Remove .jar extension
        String nameWithoutExt = filename.replace(".jar", "");

        // Find version pattern: sequences of digits separated by dots
        // Match patterns like: 1.2.3, 2.0, 10.15.3
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");
        java.util.regex.Matcher matcher = pattern.matcher(nameWithoutExt);

        if (matcher.find()) {
            java.util.List<Integer> versions = new java.util.ArrayList<>();
            for (int i = 1; i <= matcher.groupCount(); i++) {
                String group = matcher.group(i);
                if (group != null) {
                    versions.add(Integer.parseInt(group));
                }
            }
            return versions.stream().mapToInt(Integer::intValue).toArray();
        }

        // No version found
        return new int[0];
    }

    /**
     * Create an HTTP request builder with standard headers
     */
    private HttpRequest.Builder createRequestBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT);

        // Add GitHub token if configured (for GitHub API requests)
        if (cfg.githubToken != null && !cfg.githubToken.trim().isEmpty() && url.contains("api.github.com")) {
            builder.header("Authorization", "Bearer " + cfg.githubToken.trim());
        }

        return builder;
    }

    private void downloadTo(String url, Path target) throws IOException {
        HttpRequest req = createRequestBuilder(url)
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        try {
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                try (InputStream in = resp.body()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                throw new IOException("HTTP " + resp.statusCode() + " when downloading " + url);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private Path findExistingJar(Project project, Path pluginsDir) throws IOException {
        if (!Files.exists(pluginsDir)) return null;
        try {
            String fileHintLower = project.fileHint().toLowerCase(Locale.ROOT);
            List<Path> matches = Files.list(pluginsDir)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (!name.endsWith(".jar")) return false;

                        // Special case for Geyser to avoid matching GeyserUtils/GeyserModelEngine
                        if (project == Project.GEYSER) {
                            // Must start with "geyser-" (case insensitive) to match Geyser-Spigot.jar, Geyser-BungeeCord.jar, etc.
                            // This excludes geyserutils and geysermodelengine
                            return name.startsWith("geyser-");
                        }

                        // Special case for ViaRewind to avoid matching ViaRewind-Legacy-Support
                        if (project == Project.VIAREWIND) {
                            // Must contain "viarewind" but NOT "legacy"
                            return name.contains("viarewind") && !name.contains("legacy");
                        }

                        // Special case for ViaRewind-Legacy to only match legacy versions
                        if (project == Project.VIAREWIND_LEGACY) {
                            // Must contain both "viarewind" and "legacy"
                            return name.contains("viarewind") && name.contains("legacy");
                        }

                        return name.contains(fileHintLower);
                    })
                    .collect(Collectors.toList());
            if (matches.isEmpty()) return null;
            // Prefer jars that also contain platform hint words, but fallback to first
            Optional<Path> preferred = matches.stream().filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.contains("spigot") || n.contains("paper") || n.contains("bungee") || n.contains("velocity");
            }).findFirst();
            return preferred.orElse(matches.get(0));
        } catch (IOException e) {
            throw e;
        }
    }

    /**
     * Get the published_at timestamp of the latest GitHub release for a project
     * Returns timestamp in milliseconds, or null if not available
     */
    private Long getGitHubReleaseTimestamp(Project project) throws IOException {
        String apiUrl;

        switch (project) {
            case GEYSERUTILS_EXTENSION:
            case GEYSERUTILS_PLUGIN:
                apiUrl = GEYSERUTILS_GITHUB_API;
                break;
            case GEYSERMODELENGINE_EXTENSION:
            case GEYSERMODELENGINE_PLUGIN:
                apiUrl = GEYSERMODELENGINE_GITHUB_API;
                break;
            default:
                return null; // Not a GitHub release project
        }

        HttpRequest req = createRequestBuilder(apiUrl)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return extractPublishedTimestamp(resp.body());
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    /**
     * Extract published_at timestamp from GitHub API JSON response
     * Format: "published_at":"2025-10-15T04:47:55Z"
     */
    private Long extractPublishedTimestamp(String json) {
        String searchPattern = "\"published_at\":\"";
        int startPos = json.indexOf(searchPattern);

        if (startPos != -1) {
            int dateStart = startPos + searchPattern.length();
            int dateEnd = json.indexOf("\"", dateStart);
            if (dateEnd != -1) {
                String dateStr = json.substring(dateStart, dateEnd);
                try {
                    // Parse ISO 8601 timestamp: "2025-10-15T04:47:55Z"
                    return java.time.Instant.parse(dateStr).toEpochMilli();
                } catch (Exception e) {
                    log.warn(ANSI_YELLOW + "Failed to parse GitHub timestamp: " + dateStr + ANSI_RESET);
                }
            }
        }

        return null;
    }

    /**
     * Normalize filename by removing browser download suffixes like (1), (2), etc.
     * Examples:
     * - "Geyser-BungeeCord (11).jar" -> "Geyser-BungeeCord.jar"
     * - "geyserutils-geyser-1.0-SNAPSHOT (10).jar" -> "geyserutils-geyser-1.0-SNAPSHOT.jar"
     * - "plugin-1.2.3.jar" -> "plugin-1.2.3.jar" (no change)
     */
    private String normalizeFilename(String filename) {
        // Pattern: " (N).jar" where N is one or more digits
        // Remove this pattern if it exists at the end of the filename
        return filename.replaceAll("\\s+\\(\\d+\\)\\.jar$", ".jar");
    }

    private String extractFilenameFromUrl(String url) {
        // Extract filename from URL (everything after the last /)
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash != -1 && lastSlash < url.length() - 1) {
            return url.substring(lastSlash + 1);
        }
        // Fallback to default if URL parsing fails
        return "plugin.jar";
    }

    private String getDefaultFilename(Project project, Platform platform) {
        String filename;
        switch (project) {
            case GEYSER:
                switch (platform) {
                    case SPIGOT: filename = "Geyser-Spigot.jar"; break;
                    case BUNGEECORD: filename = "Geyser-BungeeCord.jar"; break;
                    case VELOCITY: filename = "Geyser-Velocity.jar"; break;
                    default: filename = "Geyser.jar";
                }
                break;
            case FLOODGATE:
                switch (platform) {
                    case SPIGOT: filename = "floodgate-spigot.jar"; break;
                    case BUNGEECORD: filename = "floodgate-bungee.jar"; break;
                    case VELOCITY: filename = "floodgate-velocity.jar"; break;
                    default: filename = "floodgate.jar";
                }
                break;
            case LUCKPERMS:
                switch (platform) {
                    case SPIGOT: filename = "LuckPerms-Bukkit.jar"; break;
                    case BUNGEECORD: filename = "LuckPerms-Bungee.jar"; break;
                    case VELOCITY: filename = "LuckPerms-Velocity.jar"; break;
                    default: filename = "LuckPerms.jar";
                }
                break;
            case PACKETEVENTS:
                switch (platform) {
                    case SPIGOT: filename = "packetevents-spigot.jar"; break;
                    case BUNGEECORD: filename = "packetevents-bungeecord.jar"; break;
                    case VELOCITY: filename = "packetevents-velocity.jar"; break;
                    default: filename = "packetevents.jar";
                }
                break;
            case PROTOCOLLIB:
                filename = "ProtocolLib.jar";
                break;
            case VIAVERSION:
                filename = "ViaVersion.jar";
                break;
            case VIABACKWARDS:
                filename = "ViaBackwards.jar";
                break;
            case VIAREWIND:
                filename = "ViaRewind.jar";
                break;
            case VIAREWIND_LEGACY:
                filename = "ViaRewind-Legacy-Support.jar";
                break;
            case GEYSERUTILS_EXTENSION:
                filename = "geyserutils-geyser.jar";
                break;
            case GEYSERUTILS_PLUGIN:
                switch (platform) {
                    case SPIGOT: filename = "geyserutils-spigot.jar"; break;
                    case BUNGEECORD: filename = "geyserutils-bungee.jar"; break;
                    case VELOCITY: filename = "geyserutils-velocity.jar"; break;
                    default: filename = "geyserutils.jar";
                }
                break;
            case GEYSERMODELENGINE_EXTENSION:
                filename = "GeyserModelEngine-Extension.jar";
                break;
            case THIRDPARTYCOSMETICS_EXTENSION:
                filename = "thirdpartycosmetics.jar";
                break;
            case EMOTEOFFHAND_EXTENSION:
                filename = "emoteoffhand.jar";
                break;
            case GEYSERMODELENGINE_PLUGIN:
                filename = "GeyserModelEngine-Plugin.jar";
                break;
            case FAWE:
                filename = "FastAsyncWorldEdit.jar";
                break;
            case PLACEHOLDERAPI:
                filename = "PlaceholderAPI.jar";
                break;
            default:
                filename = "plugin.jar";
        }
        return filename;
    }

    private Path defaultDestination(Project project, Platform platform, Path pluginsDir) {
        String filename = getDefaultFilename(project, platform);
        return pluginsDir.resolve(filename);
    }
}