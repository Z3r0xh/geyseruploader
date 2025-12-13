package org.pluginupdater.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Manages logging of update history to a persistent log file
 */
public class UpdateHistoryLogger {
    private final Path historyFile;
    private final Config config;
    private final SimpleDateFormat dateFormat;

    public UpdateHistoryLogger(Path dataDirectory, Config config) {
        this.historyFile = dataDirectory.resolve("history.log");
        this.config = config;

        // Use the configured date format, or fall back to default if invalid
        SimpleDateFormat df;
        try {
            df = new SimpleDateFormat(config.dateFormat);
        } catch (IllegalArgumentException e) {
            df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }
        this.dateFormat = df;
    }

    /**
     * Log a successful update check
     */
    public void logCheck(String message) {
        if (config.updateHistory.enabled && config.updateHistory.logChecks) {
            writeLog("CHECK", message);
        }
    }

    /**
     * Log a successful plugin update
     */
    public void logUpdate(Project project, String oldVersion, String newVersion) {
        if (config.updateHistory.enabled && config.updateHistory.logUpdates) {
            String message = String.format("%s updated: %s -> %s",
                project.apiName(),
                oldVersion != null ? oldVersion : "not installed",
                newVersion);
            writeLog("UPDATE", message);
        }
    }

    /**
     * Log an error during update process
     */
    public void logError(Project project, String error) {
        if (config.updateHistory.enabled && config.updateHistory.logErrors) {
            String message = String.format("%s error: %s", project.apiName(), error);
            writeLog("ERROR", message);
        }
    }

    /**
     * Log a general informational message
     */
    public void logInfo(String message) {
        if (config.updateHistory.enabled) {
            writeLog("INFO", message);
        }
    }

    /**
     * Write a log entry to the history file
     */
    private void writeLog(String level, String message) {
        try {
            // Check file size and rotate if needed
            rotateIfNeeded();

            // Format: [2025-12-12 16:30:45] [UPDATE] geyser updated: 2.4.0 -> 2.4.1
            String timestamp = dateFormat.format(new Date());
            String logEntry = String.format("[%s] [%s] %s%n", timestamp, level, message);

            // Append to file
            Files.writeString(historyFile, logEntry,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);

        } catch (IOException e) {
            // Silently fail - don't interrupt update process if logging fails
            System.err.println("Failed to write to update history log: " + e.getMessage());
        }
    }

    /**
     * Rotate log file if it exceeds the maximum size
     */
    private void rotateIfNeeded() throws IOException {
        if (!Files.exists(historyFile)) {
            return;
        }

        long maxBytes = config.updateHistory.maxFileSizeMB * 1024L * 1024L;
        long currentSize = Files.size(historyFile);

        if (currentSize >= maxBytes) {
            // Rotate: history.log -> history.log.old (overwrite previous .old)
            Path oldFile = historyFile.getParent().resolve("history.log.old");
            Files.move(historyFile, oldFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Get the path to the history log file
     */
    public Path getHistoryFile() {
        return historyFile;
    }
}
