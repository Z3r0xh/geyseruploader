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
        public boolean geyser = true;
        public boolean floodgate = true;
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
        public String upToDate = "{project} is up to date.";
        public String updated = "{project} has been updated to the latest build.";
        public String noTarget = "No target projects are enabled.";
        public String failed = "Failed to update {project}: {error}";
        public String promptRestart = "Update applied. Server restart required.";
        public String startUpCheck = "Starting automatic update check on startup.";
        public String periodicCheck = "Scheduled periodic update check (every {hours} hours).";
        public String adminLoginCheck = "Admin login detected, executing update check.";
        public String manualTriggered = "Starting manual update check.";
        public String nothingToDo = "No valid targets. Please check config.yml.";
        public String done = "Update check completed.";
        public String noPermission = "You do not have permission.";
        public String pluginDisabled = "[PluginUpdater] disabled by config";
        public String downloadFailed = "Download failed: {error}";
        public String hashComparisonFailed = "Hash comparison failed. Continuing with overwrite update: {error}";
        public String migrationFailed = "Failed to move {file}: {error}";
        public String migrationScanFailed = "Migration scan failed: {error}";
        public String dataDirectoryError = "Could not create data directory: {error}";
        public String reloadSuccess = "Configuration reloaded successfully.";
        public String reloadFailed = "Failed to reload configuration: {error}";
    }
}