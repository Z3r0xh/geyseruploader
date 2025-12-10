package org.pluginupdater.core;

public class Config {
    public static final int CURRENT_CONFIG_VERSION = 1;

    public int configVersion = CURRENT_CONFIG_VERSION;

    public boolean enabled = true;

    public String language = "en";

    // Optional GitHub token for increased API rate limits (5000/hour vs 60/hour)
    // Leave empty to use unauthenticated requests
    public String githubToken = "";

    public boolean checkOnStartup = true;

    public Periodic periodic = new Periodic();
    public static class Periodic {
        public boolean enabled = true;
        public int intervalHours = 12;
    }

    public AdminLogin adminLogin = new AdminLogin();
    public static class AdminLogin {
        public boolean enabled = true;
        public String permission = "pluginupdater.admin";
    }

    public Targets targets = new Targets();
    public static class Targets {
        public boolean geyser = false;  // Default false, opt-in
        public boolean floodgate = false;  // Default false, opt-in
        public boolean luckperms = false;  // Default false, opt-in
        public boolean fawe = false;  // Default false, opt-in (Spigot only)
        public boolean placeholderapi = false;  // Default false, opt-in (Spigot only)
        public boolean itemnbtapi = false;  // Default false, opt-in (Spigot only)
        public PacketEventsConfig packetevents = new PacketEventsConfig();
        public ProtocolLibConfig protocollib = new ProtocolLibConfig();
        public ViaPluginsConfig viaPlugins = new ViaPluginsConfig();
        public GeyserExtensionsConfig geyserExtensions = new GeyserExtensionsConfig();
    }

    public static class PacketEventsConfig {
        public boolean enabled = false;  // Default false, opt-in
        public boolean useDevBuilds = false;  // Default false = stable releases from GitHub, true = dev builds from Jenkins
    }

    public static class ProtocolLibConfig {
        public boolean enabled = false;  // Default false, opt-in (Spigot only)
        public boolean useDevBuilds = false;  // Default false = stable releases from GitHub, true = dev builds from "dev-build" tag
    }

    public static class ViaPluginsConfig {
        public boolean viaVersion = false;  // Default false, opt-in (Spigot only)
        public boolean viaBackwards = false;  // Default false, opt-in (Spigot only)
        public boolean viaRewind = false;  // Default false, opt-in (Spigot only)
        public boolean viaRewindLegacy = false;  // Default false, opt-in (Spigot only)
    }

    public static class GeyserExtensionsConfig {
        public boolean geyserUtils = false;  // Default false, opt-in (downloads both extension + plugin)
        public GeyserModelEngineExtensionConfig geyserModelEngineExtension = new GeyserModelEngineExtensionConfig();
        public boolean geyserModelEnginePlugin = false;  // Default false, opt-in (downloads plugin for Spigot only)
    }

    public static class GeyserModelEngineExtensionConfig {
        public boolean enabled = false;  // Default false, opt-in (downloads GeyserModelEngineExtension)
        public boolean cleanOnUpdate = true;  // Clean extension folder on update (except input/)
    }

    public PostUpdate postUpdate = new PostUpdate();
    public static class PostUpdate {
        public boolean notifyConsole = true;
        public boolean notifyPlayersWithPermission = true;
        public boolean runRestartCommand = false;
        public String restartCommand = "restart";
    }

    public Messages messages = new Messages();
    public static class Messages {
        public String prefix = "§a[PluginUpdater]§r ";
        public String checking = "§7Checking for updates...";
        public String upToDate = "§a{project}§r is up to date.";
        public String updated = "§e{project}§r has been updated to the latest build.";
        public String noTarget = "§eNo target projects are enabled.";
        public String failed = "§cFailed to update {project}: {error}";
        public String promptRestart = "§eUpdate applied. Server restart required.";
        public String startUpCheck = "§7Starting automatic update check on startup.";
        public String periodicCheck = "§7Scheduled periodic update check (every {hours} hours).";
        public String adminLoginCheck = "§7Admin login detected, executing update check.";
        public String manualTriggered = "§7Starting manual update check.";
        public String nothingToDo = "§eNo valid targets. Please check config.yml.";
        public String done = "§aUpdate check completed.";
        public String noPermission = "§cYou do not have permission.";
        public String pluginDisabled = "§c[PluginUpdater] disabled by config";
        public String downloadFailed = "§cDownload failed: {error}";
        public String hashComparisonFailed = "§eHash comparison failed. Continuing with overwrite update: {error}";
        public String migrationFailed = "§cFailed to move {file}: {error}";
        public String migrationScanFailed = "§cMigration scan failed: {error}";
        public String dataDirectoryError = "§cCould not create data directory: {error}";
        public String reloadSuccess = "§aConfiguration reloaded successfully.";
        public String reloadFailed = "§cFailed to reload configuration: {error}";
        public String geyserModelEngineCleanupSuccess = "§aGeyserModelEngineExtension cleanup marker created. The extension folder will be cleaned on next restart.";
        public String geyserModelEngineCleanupFailed = "§cFailed to create cleanup marker. Make sure GeyserModelEngineExtension is installed and cleanOnUpdate is enabled.";

        // Version check messages
        public String versionCheckHeader = "§e§l═══════════════════════════════════";
        public String versionCheckTitle = "§a§lPluginUpdater §7Version Check";
        public String versionCheckFooter = "§e§l═══════════════════════════════════";
        public String versionCheckColumnHeaders = "";  // No column headers for compact format
        public String versionCheckSeparator = "";  // No separator for compact format
        public String versionCheckDisabled = "§8▪ §7{project} §8[DISABLED]";
        public String versionCheckUpToDate = "§a✓ §f{project} §7- §aUp to date";
        public String versionCheckUpdateAvailable = "§e⬆ §f{project} §7- §eUpdate available";
        public String versionCheckError = "§c✗ §f{project} §7- §c{error}";
        public String versionCheckNotFound = "§e○ §f{project} §7- §eNot installed";
        public String versionCheckSummary = "§f{enabled} §7enabled §8|§r §e{updates} §7update(s) available";
        public String versionCheckFetching = "§7Fetching version information...";
    }
}