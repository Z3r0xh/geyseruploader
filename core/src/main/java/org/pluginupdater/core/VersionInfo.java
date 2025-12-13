package org.pluginupdater.core;

import java.util.Optional;

public class VersionInfo {
    public final Project project;
    public final boolean enabled;
    public final Optional<String> installedVersion;
    public final Optional<String> latestVersion;
    public final boolean updateAvailable;
    public final Optional<String> error;
    public final Optional<String> buildNumber;  // Build number for Jenkins builds

    public VersionInfo(Project project, boolean enabled, Optional<String> installedVersion,
                      Optional<String> latestVersion, boolean updateAvailable, Optional<String> error,
                      Optional<String> buildNumber) {
        this.project = project;
        this.enabled = enabled;
        this.installedVersion = installedVersion;
        this.latestVersion = latestVersion;
        this.updateAvailable = updateAvailable;
        this.error = error;
        this.buildNumber = buildNumber;
    }

    public static VersionInfo disabled(Project project) {
        return new VersionInfo(project, false, Optional.empty(), Optional.empty(), false, Optional.empty(), Optional.empty());
    }

    public static VersionInfo error(Project project, String error) {
        return new VersionInfo(project, true, Optional.empty(), Optional.empty(), false, Optional.of(error), Optional.empty());
    }

    public static VersionInfo upToDate(Project project, String installed, String latest) {
        return new VersionInfo(project, true, Optional.of(installed), Optional.of(latest), false, Optional.empty(), Optional.empty());
    }

    public static VersionInfo upToDate(Project project, String installed, String latest, String buildNumber) {
        return new VersionInfo(project, true, Optional.of(installed), Optional.of(latest), false, Optional.empty(), Optional.of(buildNumber));
    }

    public static VersionInfo updateAvailable(Project project, String installed, String latest) {
        return new VersionInfo(project, true, Optional.of(installed), Optional.of(latest), true, Optional.empty(), Optional.empty());
    }

    public static VersionInfo updateAvailable(Project project, String installed, String latest, String buildNumber) {
        return new VersionInfo(project, true, Optional.of(installed), Optional.of(latest), true, Optional.empty(), Optional.of(buildNumber));
    }

    public static VersionInfo notInstalled(Project project, String latest) {
        return new VersionInfo(project, true, Optional.empty(), Optional.of(latest), true, Optional.empty(), Optional.empty());
    }

    public static VersionInfo notInstalled(Project project, String latest, String buildNumber) {
        return new VersionInfo(project, true, Optional.empty(), Optional.of(latest), true, Optional.empty(), Optional.of(buildNumber));
    }
}
