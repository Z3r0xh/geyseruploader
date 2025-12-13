package org.pluginupdater.core;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;

public class ConfigManager {
    private final Path dataFolder;
    private final Path configPath;
    private final Path messagesFolder;

    public ConfigManager(Path dataFolder) {
        this.dataFolder = dataFolder;
        this.configPath = dataFolder.resolve("config.yml");
        this.messagesFolder = dataFolder.resolve("messages");
    }

    public Config loadOrCreateDefault() {
        try {
            if (!Files.exists(dataFolder)) Files.createDirectories(dataFolder);
            if (!Files.exists(configPath)) {
                // First time: copy resources/config.yml (without type tags)
                try (InputStream in = ConfigManager.class.getClassLoader().getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configPath);
                    } else {
                        Files.writeString(configPath, "enabled: true\n", StandardCharsets.UTF_8);
                    }
                }
            }

            String content = Files.readString(configPath, StandardCharsets.UTF_8);
            // Remove type tag if present from old version
            if (content.startsWith("!!org.geyserupdater.core.Config")) {
                int idx = content.indexOf('\n');
                content = (idx >= 0) ? content.substring(idx + 1) : "";
                Files.writeString(configPath, content, StandardCharsets.UTF_8);
            }

            LoaderOptions options = new LoaderOptions();
            Yaml yaml = new Yaml(new SafeConstructor(options));

            Object obj = yaml.load(content);
            Config cfg = new Config();
            if (!(obj instanceof Map<?, ?> map)) {
                return cfg; // default values
            }

            // Read config version
            int loadedVersion = asInt(map, "configVersion", 0);
            cfg.configVersion = loadedVersion;

            // Perform migration if needed
            if (loadedVersion < Config.CURRENT_CONFIG_VERSION) {
                migrateConfig(loadedVersion, cfg, map);
            }

            // Level 1
            cfg.enabled = asBool(map, "enabled", cfg.enabled);
            cfg.language = asStr(map, "language", cfg.language);
            cfg.githubToken = asStr(map, "githubToken", cfg.githubToken);
            cfg.checkOnStartup = asBool(map, "checkOnStartup", cfg.checkOnStartup);

            // periodic
            Map<String, Object> periodic = asMap(map, "periodic");
            cfg.periodic.enabled = asBool(periodic, "enabled", cfg.periodic.enabled);
            cfg.periodic.intervalHours = asInt(periodic, "intervalHours", cfg.periodic.intervalHours);

            // adminLogin
            Map<String, Object> adminLogin = asMap(map, "adminLogin");
            cfg.adminLogin.enabled = asBool(adminLogin, "enabled", cfg.adminLogin.enabled);
            cfg.adminLogin.permission = asStr(adminLogin, "permission", cfg.adminLogin.permission);

            // permissions
            Map<String, Object> permissions = asMap(map, "permissions");
            loadPermissionNode(permissions, "command", cfg.permissions.command);
            loadPermissionNode(permissions, "update", cfg.permissions.update);
            loadPermissionNode(permissions, "check", cfg.permissions.check);
            loadPermissionNode(permissions, "reload", cfg.permissions.reload);
            loadPermissionNode(permissions, "packtest", cfg.permissions.packtest);
            loadPermissionNode(permissions, "webhooktest", cfg.permissions.webhooktest);
            loadPermissionNode(permissions, "summary", cfg.permissions.summary);

            // targets
            Map<String, Object> targets = asMap(map, "targets");
            cfg.targets.geyser = asBool(targets, "geyser", cfg.targets.geyser);
            cfg.targets.floodgate = asBool(targets, "floodgate", cfg.targets.floodgate);
            cfg.targets.luckperms = asBool(targets, "luckperms", cfg.targets.luckperms);
            cfg.targets.fawe = asBool(targets, "fawe", cfg.targets.fawe);
            cfg.targets.placeholderapi = asBool(targets, "placeholderapi", cfg.targets.placeholderapi);
            cfg.targets.itemnbtapi = asBool(targets, "itemnbtapi", cfg.targets.itemnbtapi);

            // packetevents nested config
            Map<String, Object> packetevents = asMap(targets, "packetevents");
            cfg.targets.packetevents.enabled = asBool(packetevents, "enabled", cfg.targets.packetevents.enabled);
            cfg.targets.packetevents.useDevBuilds = asBool(packetevents, "useDevBuilds", cfg.targets.packetevents.useDevBuilds);

            // protocollib nested config
            Map<String, Object> protocollib = asMap(targets, "protocollib");
            cfg.targets.protocollib.enabled = asBool(protocollib, "enabled", cfg.targets.protocollib.enabled);
            cfg.targets.protocollib.useDevBuilds = asBool(protocollib, "useDevBuilds", cfg.targets.protocollib.useDevBuilds);

            // viaPlugins nested config
            Map<String, Object> viaPlugins = asMap(targets, "viaPlugins");
            cfg.targets.viaPlugins.viaVersion = asBool(viaPlugins, "viaVersion", cfg.targets.viaPlugins.viaVersion);
            cfg.targets.viaPlugins.viaBackwards = asBool(viaPlugins, "viaBackwards", cfg.targets.viaPlugins.viaBackwards);
            cfg.targets.viaPlugins.viaRewind = asBool(viaPlugins, "viaRewind", cfg.targets.viaPlugins.viaRewind);
            cfg.targets.viaPlugins.viaRewindLegacy = asBool(viaPlugins, "viaRewindLegacy", cfg.targets.viaPlugins.viaRewindLegacy);

            // geyserExtensions nested config
            Map<String, Object> geyserExtensions = asMap(targets, "geyserExtensions");

            // geyserUtils can be boolean (old format) or object (new format)
            Object geyserUtils = geyserExtensions.get("geyserUtils");
            if (geyserUtils instanceof Boolean) {
                // Legacy boolean format - enable both extension and plugin
                boolean enabled = (Boolean) geyserUtils;
                cfg.targets.geyserExtensions.geyserUtils.extension = enabled;
                cfg.targets.geyserExtensions.geyserUtils.plugin = enabled;
            } else if (geyserUtils instanceof Map) {
                // New object format
                Map<String, Object> geyserUtilsMap = (Map<String, Object>) geyserUtils;
                cfg.targets.geyserExtensions.geyserUtils.extension =
                    asBool(geyserUtilsMap, "extension", cfg.targets.geyserExtensions.geyserUtils.extension);
                cfg.targets.geyserExtensions.geyserUtils.plugin =
                    asBool(geyserUtilsMap, "plugin", cfg.targets.geyserExtensions.geyserUtils.plugin);
            }

            // geyserModelEngineExtension can be boolean (old name: geyserModelEnginePackGenerator) or object
            Object gmepg = geyserExtensions.get("geyserModelEngineExtension");
            if (gmepg == null) {
                // Try old name for backwards compatibility
                gmepg = geyserExtensions.get("geyserModelEnginePackGenerator");
            }
            if (gmepg instanceof Boolean) {
                // Legacy boolean format
                cfg.targets.geyserExtensions.geyserModelEngineExtension.enabled = (Boolean) gmepg;
            } else if (gmepg instanceof Map) {
                // New object format
                Map<String, Object> gmepgMap = (Map<String, Object>) gmepg;
                cfg.targets.geyserExtensions.geyserModelEngineExtension.enabled =
                    asBool(gmepgMap, "enabled", cfg.targets.geyserExtensions.geyserModelEngineExtension.enabled);
                cfg.targets.geyserExtensions.geyserModelEngineExtension.cleanOnUpdate =
                    asBool(gmepgMap, "cleanOnUpdate", cfg.targets.geyserExtensions.geyserModelEngineExtension.cleanOnUpdate);
            }

            // thirdPartyCosmetics and emoteOffhand extensions
            cfg.targets.geyserExtensions.thirdPartyCosmetics = asBool(geyserExtensions, "thirdPartyCosmetics", cfg.targets.geyserExtensions.thirdPartyCosmetics);
            cfg.targets.geyserExtensions.emoteOffhand = asBool(geyserExtensions, "emoteOffhand", cfg.targets.geyserExtensions.emoteOffhand);

            // geyserModelEnginePlugin (old name: geyserModelEngine)
            cfg.targets.geyserExtensions.geyserModelEnginePlugin = asBool(geyserExtensions, "geyserModelEnginePlugin",
                asBool(geyserExtensions, "geyserModelEngine", cfg.targets.geyserExtensions.geyserModelEnginePlugin));

            // postUpdate
            Map<String, Object> postUpdate = asMap(map, "postUpdate");
            cfg.postUpdate.notifyConsole = asBool(postUpdate, "notifyConsole", cfg.postUpdate.notifyConsole);
            cfg.postUpdate.notifyPlayersWithPermission = asBool(postUpdate, "notifyPlayersWithPermission", cfg.postUpdate.notifyPlayersWithPermission);
            cfg.postUpdate.runRestartCommand = asBool(postUpdate, "runRestartCommand", cfg.postUpdate.runRestartCommand);
            cfg.postUpdate.restartCommand = asStr(postUpdate, "restartCommand", cfg.postUpdate.restartCommand);

            // discordWebhook
            Map<String, Object> discordWebhook = asMap(map, "discordWebhook");
            cfg.discordWebhook.enabled = asBool(discordWebhook, "enabled", cfg.discordWebhook.enabled);
            cfg.discordWebhook.webhookUrl = asStr(discordWebhook, "webhookUrl", cfg.discordWebhook.webhookUrl);
            cfg.discordWebhook.notifyOnUpdate = asBool(discordWebhook, "notifyOnUpdate", cfg.discordWebhook.notifyOnUpdate);
            cfg.discordWebhook.notifyOnError = asBool(discordWebhook, "notifyOnError", cfg.discordWebhook.notifyOnError);
            cfg.discordWebhook.notifyPeriodicSummary = asBool(discordWebhook, "notifyPeriodicSummary", cfg.discordWebhook.notifyPeriodicSummary);

            // Load messages from language file
            loadMessages(cfg);

            // If config was migrated, save the updated version
            if (loadedVersion < Config.CURRENT_CONFIG_VERSION) {
                saveConfig(cfg);
            }

            return cfg;
        } catch (IOException e) {
            e.printStackTrace();
            return new Config();
        }
    }

    private static Map<String, Object> asMap(Map<?, ?> map, String key) {
        Object o = map.get(key);
        if (o instanceof Map<?, ?> m) {
            // Allow unsafe cast (YAML is assumed to have String keys)
            return (Map<String, Object>) (Map<?, ?>) m;
        }
        return Collections.emptyMap();
    }

    private static String asStr(Map<?, ?> map, String key, String def) {
        Object o = map.get(key);
        return o == null ? def : String.valueOf(o);
    }

    private static boolean asBool(Map<?, ?> map, String key, boolean def) {
        Object o = map.get(key);
        if (o instanceof Boolean b) return b;
        if (o == null) return def;
        return Boolean.parseBoolean(String.valueOf(o));
    }

    private static int asInt(Map<?, ?> map, String key, int def) {
        Object o = map.get(key);
        if (o instanceof Number n) return n.intValue();
        if (o == null) return def;
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private static void loadPermissionNode(Map<String, Object> map, String key, Config.PermissionNode node) {
        Object o = map.get(key);
        if (o instanceof Map<?, ?> permMap) {
            node.permission = asStr(permMap, "permission", node.permission);
            node.defaultOp = asBool(permMap, "defaultOp", node.defaultOp);
        }
    }

    public Path getConfigPath() {
        return configPath;
    }

    private void loadMessages(Config cfg) {
        try {
            // Create messages folder if it doesn't exist
            if (!Files.exists(messagesFolder)) {
                Files.createDirectories(messagesFolder);
                // Copy all default language files from resources
                copyDefaultLanguageFiles();
            }

            String languageFile = "messages_" + cfg.language + ".yml";
            Path externalFile = messagesFolder.resolve(languageFile);

            // If the specific language file doesn't exist externally, try to copy it from resources
            if (!Files.exists(externalFile)) {
                copyLanguageFileFromResources(languageFile);
            }

            // Load from external file if it exists
            if (Files.exists(externalFile)) {
                String content = Files.readString(externalFile, StandardCharsets.UTF_8);
                loadMessagesFromString(content, cfg);
            } else {
                // Fallback to English from external or resources
                Path englishFile = messagesFolder.resolve("messages_en.yml");
                if (Files.exists(englishFile)) {
                    String content = Files.readString(englishFile, StandardCharsets.UTF_8);
                    loadMessagesFromString(content, cfg);
                } else {
                    // Last resort: load from resources
                    try (InputStream fallback = ConfigManager.class.getClassLoader().getResourceAsStream("messages_en.yml")) {
                        if (fallback != null) {
                            loadMessagesFromStream(fallback, cfg);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void copyDefaultLanguageFiles() {
        String[] languages = {"en", "es", "fr", "de", "ja", "zh"};
        for (String lang : languages) {
            copyLanguageFileFromResources("messages_" + lang + ".yml");
        }
    }

    private void copyLanguageFileFromResources(String fileName) {
        try (InputStream in = ConfigManager.class.getClassLoader().getResourceAsStream(fileName)) {
            if (in != null) {
                Path destination = messagesFolder.resolve(fileName);
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // Silently fail if resource doesn't exist or cannot be copied
        }
    }

    private void loadMessagesFromString(String content, Config cfg) {
        LoaderOptions options = new LoaderOptions();
        Yaml yaml = new Yaml(new SafeConstructor(options));

        Object obj = yaml.load(content);
        if (!(obj instanceof Map<?, ?> map)) {
            return;
        }

        applyMessagesFromMap(map, cfg);
    }

    private void loadMessagesFromStream(InputStream in, Config cfg) throws IOException {
        String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        loadMessagesFromString(content, cfg);
    }

    private void applyMessagesFromMap(Map<?, ?> map, Config cfg) {
        cfg.messages.prefix = asStr(map, "prefix", cfg.messages.prefix);
        cfg.messages.checking = asStr(map, "checking", cfg.messages.checking);
        cfg.messages.upToDate = asStr(map, "upToDate", cfg.messages.upToDate);
        cfg.messages.updated = asStr(map, "updated", cfg.messages.updated);
        cfg.messages.noTarget = asStr(map, "noTarget", cfg.messages.noTarget);
        cfg.messages.failed = asStr(map, "failed", cfg.messages.failed);
        cfg.messages.promptRestart = asStr(map, "promptRestart", cfg.messages.promptRestart);
        cfg.messages.startUpCheck = asStr(map, "startUpCheck", cfg.messages.startUpCheck);
        cfg.messages.periodicCheck = asStr(map, "periodicCheck", cfg.messages.periodicCheck);
        cfg.messages.adminLoginCheck = asStr(map, "adminLoginCheck", cfg.messages.adminLoginCheck);
        cfg.messages.manualTriggered = asStr(map, "manualTriggered", cfg.messages.manualTriggered);
        cfg.messages.nothingToDo = asStr(map, "nothingToDo", cfg.messages.nothingToDo);
        cfg.messages.done = asStr(map, "done", cfg.messages.done);
        cfg.messages.noPermission = asStr(map, "noPermission", cfg.messages.noPermission);
        cfg.messages.pluginDisabled = asStr(map, "pluginDisabled", cfg.messages.pluginDisabled);
        cfg.messages.downloadFailed = asStr(map, "downloadFailed", cfg.messages.downloadFailed);
        cfg.messages.hashComparisonFailed = asStr(map, "hashComparisonFailed", cfg.messages.hashComparisonFailed);
        cfg.messages.migrationFailed = asStr(map, "migrationFailed", cfg.messages.migrationFailed);
        cfg.messages.migrationScanFailed = asStr(map, "migrationScanFailed", cfg.messages.migrationScanFailed);
        cfg.messages.dataDirectoryError = asStr(map, "dataDirectoryError", cfg.messages.dataDirectoryError);
        cfg.messages.reloadSuccess = asStr(map, "reloadSuccess", cfg.messages.reloadSuccess);
        cfg.messages.reloadFailed = asStr(map, "reloadFailed", cfg.messages.reloadFailed);
        cfg.messages.geyserModelEngineCleanupSuccess = asStr(map, "geyserModelEngineCleanupSuccess", cfg.messages.geyserModelEngineCleanupSuccess);
        cfg.messages.geyserModelEngineCleanupFailed = asStr(map, "geyserModelEngineCleanupFailed", cfg.messages.geyserModelEngineCleanupFailed);
    }

    /**
     * Migrate configuration from older versions to current version
     */
    private void migrateConfig(int fromVersion, Config cfg, Map<?, ?> originalMap) {
        // Current version is 1 (baseline), no migrations needed yet
        // Future migrations would go here when we release version 2+
        // Example:
        // if (fromVersion < 2) {
        //     // Migrate from version 1 to 2
        //     cfg.configVersion = 2;
        // }
    }

    /**
     * Save configuration to file, preserving user comments where possible
     */
    private void saveConfig(Config cfg) {
        try {
            // Read existing config to preserve comments
            String existingContent = Files.exists(configPath) ? Files.readString(configPath, StandardCharsets.UTF_8) : "";

            // Build new config content
            StringBuilder sb = new StringBuilder();

            // Add/update configVersion at the top
            if (existingContent.contains("configVersion:")) {
                // Replace existing configVersion
                existingContent = existingContent.replaceFirst("configVersion:\\s*\\d+", "configVersion: " + cfg.configVersion);
            } else {
                // Add configVersion to the beginning
                existingContent = "configVersion: " + cfg.configVersion + "\n" + existingContent;
            }

            // Future version-specific migrations would go here
            // Example:
            // if (cfg.configVersion >= 2 && !existingContent.contains("newFeature:")) {
            //     existingContent += "\nnewFeature: true\n";
            // }

            Files.writeString(configPath, existingContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
