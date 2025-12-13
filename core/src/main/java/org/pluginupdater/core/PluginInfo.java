package org.pluginupdater.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Holds detailed information about a plugin's update rules and current status
 */
public class PluginInfo {
    public final Project project;
    public final boolean enabled;
    public final Optional<String> installedVersion;
    public final Optional<String> latestVersion;
    public final boolean updateAvailable;
    public final Optional<String> error;

    // Update rule information
    public final String downloadSource;
    public final List<String> fileSearchPatterns;
    public final String installLocation;
    public final List<String> specialRules;

    // Build information
    public final Optional<String> localFileModified;
    public final Optional<String> latestBuildTime;
    public final Optional<String> buildNumber;

    public PluginInfo(
            Project project,
            boolean enabled,
            Optional<String> installedVersion,
            Optional<String> latestVersion,
            boolean updateAvailable,
            Optional<String> error,
            String downloadSource,
            List<String> fileSearchPatterns,
            String installLocation,
            List<String> specialRules,
            Optional<String> localFileModified,
            Optional<String> latestBuildTime,
            Optional<String> buildNumber
    ) {
        this.project = project;
        this.enabled = enabled;
        this.installedVersion = installedVersion;
        this.latestVersion = latestVersion;
        this.updateAvailable = updateAvailable;
        this.error = error;
        this.downloadSource = downloadSource;
        this.fileSearchPatterns = fileSearchPatterns;
        this.installLocation = installLocation;
        this.specialRules = specialRules;
        this.localFileModified = localFileModified;
        this.latestBuildTime = latestBuildTime;
        this.buildNumber = buildNumber;
    }

    public static class Builder {
        private Project project;
        private boolean enabled = true;
        private Optional<String> installedVersion = Optional.empty();
        private Optional<String> latestVersion = Optional.empty();
        private boolean updateAvailable = false;
        private Optional<String> error = Optional.empty();
        private String downloadSource = "";
        private List<String> fileSearchPatterns = new ArrayList<>();
        private String installLocation = "";
        private List<String> specialRules = new ArrayList<>();
        private Optional<String> localFileModified = Optional.empty();
        private Optional<String> latestBuildTime = Optional.empty();
        private Optional<String> buildNumber = Optional.empty();

        public Builder(Project project) {
            this.project = project;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder installedVersion(Optional<String> installedVersion) {
            this.installedVersion = installedVersion;
            return this;
        }

        public Builder latestVersion(Optional<String> latestVersion) {
            this.latestVersion = latestVersion;
            return this;
        }

        public Builder updateAvailable(boolean updateAvailable) {
            this.updateAvailable = updateAvailable;
            return this;
        }

        public Builder error(Optional<String> error) {
            this.error = error;
            return this;
        }

        public Builder downloadSource(String downloadSource) {
            this.downloadSource = downloadSource;
            return this;
        }

        public Builder addFileSearchPattern(String pattern) {
            this.fileSearchPatterns.add(pattern);
            return this;
        }

        public Builder installLocation(String installLocation) {
            this.installLocation = installLocation;
            return this;
        }

        public Builder addSpecialRule(String rule) {
            this.specialRules.add(rule);
            return this;
        }

        public Builder localFileModified(Optional<String> localFileModified) {
            this.localFileModified = localFileModified;
            return this;
        }

        public Builder latestBuildTime(Optional<String> latestBuildTime) {
            this.latestBuildTime = latestBuildTime;
            return this;
        }

        public Builder buildNumber(Optional<String> buildNumber) {
            this.buildNumber = buildNumber;
            return this;
        }

        public PluginInfo build() {
            return new PluginInfo(
                project, enabled, installedVersion, latestVersion,
                updateAvailable, error, downloadSource, fileSearchPatterns,
                installLocation, specialRules, localFileModified,
                latestBuildTime, buildNumber
            );
        }
    }
}
