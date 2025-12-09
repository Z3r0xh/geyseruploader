package org.pluginupdater.core;

public class Config {
    public boolean enabled = true;

    public String language = "en";

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
        public PacketEventsConfig packetevents = new PacketEventsConfig();
        public ProtocolLibConfig protocollib = new ProtocolLibConfig();
    }

    public static class PacketEventsConfig {
        public boolean enabled = false;  // Default false, opt-in
        public boolean useDevBuilds = false;  // Default false = stable releases from GitHub, true = dev builds from Jenkins
    }

    public static class ProtocolLibConfig {
        public boolean enabled = false;  // Default false, opt-in (Spigot only)
        public boolean useDevBuilds = false;  // Default false = stable releases from GitHub, true = dev builds from "dev-build" tag
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
        public String checking = "Checking for updates...";
        public String upToDate = "§a{project}§r is up to date.";
        public String updated = "§e{project}§r has been updated to the latest build.";
        public String noTarget = "No target projects are enabled.";
        public String failed = "§cFailed to update {project}: {error}";
        public String promptRestart = "§eUpdate applied. Server restart required.";
        public String startUpCheck = "Starting automatic update check on startup.";
        public String periodicCheck = "Scheduled periodic update check (every {hours} hours).";
        public String adminLoginCheck = "Admin login detected, executing update check.";
        public String manualTriggered = "Starting manual update check.";
        public String nothingToDo = "No valid targets. Please check config.yml.";
        public String done = "§aUpdate check completed.";
        public String noPermission = "§cYou do not have permission.";
        public String pluginDisabled = "[PluginUpdater] disabled by config";
        public String downloadFailed = "Download failed: {error}";
        public String hashComparisonFailed = "Hash comparison failed. Continuing with overwrite update: {error}";
        public String migrationFailed = "Failed to move {file}: {error}";
        public String migrationScanFailed = "Migration scan failed: {error}";
        public String dataDirectoryError = "Could not create data directory: {error}";
        public String reloadSuccess = "§aConfiguration reloaded successfully.";
        public String reloadFailed = "§cFailed to reload configuration: {error}";

        // Version check messages
        public String versionCheckHeader = "§e§l══════════════════════════════════════════════";
        public String versionCheckTitle = "§a§lPluginUpdater §7- §fVersion Check";
        public String versionCheckFooter = "§e§l══════════════════════════════════════════════";
        public String versionCheckColumnHeaders = "§7Plugin          Status      Installed                         Latest Available";
        public String versionCheckSeparator = "§7─────────────────────────────────────────────────────────────────────────────────────────";
        public String versionCheckDisabled = "§7{project}§r          §7DISABLED§r    §7-§r                                 §7-§r";
        public String versionCheckUpToDate = "§a{project}§r          §aENABLED§r     §f{installed}§r     §a{latest} ✓§r";
        public String versionCheckUpdateAvailable = "§e{project}§r          §eENABLED§r     §f{installed}§r     §e{latest} ⬆§r";
        public String versionCheckError = "§c{project}§r          §cERROR§r       §7-§r                                 §c{error}§r";
        public String versionCheckNotFound = "§7{project}§r          §eENABLED§r     §7Not found§r                          §e{latest}§r";
        public String versionCheckSummary = "§f{enabled} §7plugin(s) enabled §8|§r §e{updates} §7update(s) available";
        public String versionCheckFetching = "§7Fetching version information...";
    }
}