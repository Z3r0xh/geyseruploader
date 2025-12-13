package org.pluginupdater.spigot;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.pluginupdater.core.Config;
import org.pluginupdater.core.ConfigManager;
import org.pluginupdater.core.Platform;
import org.pluginupdater.core.UpdaterService;
import org.pluginupdater.core.logging.LogAdapter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SpigotPluginUpdaterPlugin extends JavaPlugin implements Listener {
    private ConfigManager cfgMgr;
    private Config cfg;

    @Override
    public void onEnable() {
        saveDefaultConfigFile(); // ensure folder exists
        this.cfgMgr = new ConfigManager(getDataFolder().toPath());
        this.cfg = cfgMgr.loadOrCreateDefault();

        // Execute migration
        migrateNestedPluginsIfNeeded(getDataFolder().toPath().getParent());

        getServer().getPluginManager().registerEvents(this, this);

        if (!cfg.enabled) {
            getLogger().info(cfg.messages.pluginDisabled);
            return;
        }

        // Initialize bStats
        int pluginId = 28286;
        Metrics metrics = new Metrics(this, pluginId);

        if (cfg.checkOnStartup) {
            info(cfg.messages.startUpCheck);
            runAsyncCheck(false, null);
        }

        if (cfg.periodic.enabled && cfg.periodic.intervalHours > 0) {
            info(cfg.messages.periodicCheck.replace("{hours}", String.valueOf(cfg.periodic.intervalHours)));
            long ticks = TimeUnit.HOURS.toSeconds(cfg.periodic.intervalHours) * 20L;
            // initial delay = 5 minutes
            long delay = TimeUnit.MINUTES.toSeconds(5) * 20L;
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> runAsyncCheck(false, null, true), delay, ticks);
        }
    }

    @Override
    public void onDisable() {
        // Execute cleanup on shutdown if marker exists
        Path pluginsDir = getDataFolder().toPath().getParent();
        UpdaterService cleanupService = createUpdaterService();
        cleanupService.executeCleanupOnShutdown(Platform.SPIGOT, pluginsDir);
    }

    private void saveDefaultConfigFile() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        // We use our own config manager; nothing to save here
    }

    private void runAsyncCheck(boolean manual, CommandSender sender) {
        runAsyncCheck(manual, sender, false);
    }

    private void runAsyncCheck(boolean manual, CommandSender sender, boolean isPeriodic) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            UpdaterService service = createUpdaterService();
            if (manual) {
                sendTo(sender, cfg.messages.prefix + cfg.messages.manualTriggered);
            } else {
                info(cfg.messages.checking);
            }
            Path pluginsDir = getDataFolder().toPath().getParent(); // This is directly under plugins
            List<UpdaterService.UpdateOutcome> results =
                    service.checkAndUpdate(Platform.SPIGOT, pluginsDir, isPeriodic);

            boolean anyUpdated = false;
            for (UpdaterService.UpdateOutcome r : results) {
                if (r.error.isPresent()) {
                    msg(sender, cfg.messages.failed.replace("{project}", r.project.name().toLowerCase()).replace("{error}", r.error.get()));
                } else if (r.skippedNoChange) {
                    msg(sender, cfg.messages.upToDate.replace("{project}", r.project.name().toLowerCase()));
                } else if (r.updated) {
                    anyUpdated = true;
                    msg(sender, cfg.messages.updated.replace("{project}", r.project.name().toLowerCase()));
                }
            }
            if (anyUpdated) {
                info(cfg.messages.promptRestart);
                if (cfg.postUpdate.runRestartCommand && cfg.postUpdate.restartCommand != null && !cfg.postUpdate.restartCommand.isBlank()) {
                    Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cfg.postUpdate.restartCommand));
                }
            }
            msg(sender, cfg.messages.done);
        });
    }

    private void runVersionCheck(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            sendTo(sender, cfg.messages.prefix + cfg.messages.versionCheckFetching);

            UpdaterService service = createUpdaterService();
            Path pluginsDir = getDataFolder().toPath().getParent();
            List<org.pluginupdater.core.VersionInfo> versions = service.checkVersions(Platform.SPIGOT, pluginsDir);

            // Display header
            sendTo(sender, "");
            sendTo(sender, cfg.messages.versionCheckHeader);
            sendTo(sender, cfg.messages.versionCheckTitle);
            sendTo(sender, cfg.messages.versionCheckHeader);

            int enabledCount = 0;
            int updatesCount = 0;

            // Display each project
            for (org.pluginupdater.core.VersionInfo info : versions) {
                String projectName = info.project.name();

                if (!info.enabled) {
                    sendTo(sender, cfg.messages.versionCheckDisabled.replace("{project}", projectName));
                } else {
                    enabledCount++;
                    if (info.error.isPresent()) {
                        String errorMsg = info.error.get();
                        if (errorMsg.length() > 40) errorMsg = errorMsg.substring(0, 37) + "...";
                        sendTo(sender, cfg.messages.versionCheckError
                                .replace("{project}", projectName)
                                .replace("{error}", errorMsg));
                    } else if (!info.installedVersion.isPresent()) {
                        updatesCount++;
                        sendTo(sender, cfg.messages.versionCheckNotFound
                                .replace("{project}", projectName));
                    } else if (info.updateAvailable) {
                        updatesCount++;
                        sendTo(sender, cfg.messages.versionCheckUpdateAvailable
                                .replace("{project}", projectName));
                    } else {
                        sendTo(sender, cfg.messages.versionCheckUpToDate
                                .replace("{project}", projectName));
                    }
                }
            }

            // Display footer
            sendTo(sender, cfg.messages.versionCheckSummary
                    .replace("{enabled}", String.valueOf(enabledCount))
                    .replace("{updates}", String.valueOf(updatesCount)));
            sendTo(sender, cfg.messages.versionCheckFooter);
            sendTo(sender, "");
        });
    }

    private void runPluginInfo(CommandSender sender, String pluginName) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            // Try to find the project by name
            org.pluginupdater.core.Project project = null;
            for (org.pluginupdater.core.Project p : org.pluginupdater.core.Project.values()) {
                if (p.name().equalsIgnoreCase(pluginName) || p.fileHint().equalsIgnoreCase(pluginName)) {
                    project = p;
                    break;
                }
            }

            if (project == null) {
                sendTo(sender, cfg.messages.prefix + cfg.messages.infoNotFound.replace("{plugin}", pluginName));
                return;
            }

            // Get plugin info
            UpdaterService service = createUpdaterService();
            Path pluginsDir = getDataFolder().toPath().getParent();
            org.pluginupdater.core.PluginInfo info = service.getPluginInfo(project, Platform.SPIGOT, pluginsDir);

            // Display info
            sendTo(sender, "");
            sendTo(sender, cfg.messages.infoHeader);
            sendTo(sender, cfg.messages.infoTitle.replace("{plugin}", info.project.name()));
            sendTo(sender, cfg.messages.infoHeader);
            sendTo(sender, "");

            // Status
            String status;
            if (!info.enabled) {
                status = cfg.messages.infoStatusDisabled;
            } else if (info.error.isPresent()) {
                status = cfg.messages.infoStatusError.replace("{error}", info.error.get());
            } else if (!info.installedVersion.isPresent()) {
                status = cfg.messages.infoStatusNotInstalled;
            } else if (info.updateAvailable) {
                status = cfg.messages.infoStatusUpdateAvailable;
            } else {
                status = cfg.messages.infoStatusUpToDate;
            }
            sendTo(sender, cfg.messages.infoStatus.replace("{status}", status));

            // Versions
            if (info.installedVersion.isPresent()) {
                sendTo(sender, cfg.messages.infoInstalledVersion.replace("{version}", info.installedVersion.get()));
            }
            if (info.latestVersion.isPresent()) {
                sendTo(sender, cfg.messages.infoLatestVersion.replace("{version}", info.latestVersion.get()));
            }

            sendTo(sender, "");

            // Build information (if available)
            if (info.localFileModified.isPresent() || info.latestBuildTime.isPresent() || info.buildNumber.isPresent()) {
                sendTo(sender, "§7Build Information:");
                if (info.localFileModified.isPresent()) {
                    sendTo(sender, "  §8▪ §7Local file modified: §f" + info.localFileModified.get());
                }
                if (info.latestBuildTime.isPresent()) {
                    sendTo(sender, "  §8▪ §7Latest build time: §f" + info.latestBuildTime.get());
                }
                if (info.buildNumber.isPresent()) {
                    sendTo(sender, "  §8▪ §7Build number: §f" + info.buildNumber.get());
                }
                sendTo(sender, "");
            }

            // Download source
            sendTo(sender, cfg.messages.infoDownloadSource.replace("{source}", info.downloadSource));

            // Installation location
            sendTo(sender, cfg.messages.infoInstallLocation.replace("{location}", info.installLocation));

            // File search patterns
            if (!info.fileSearchPatterns.isEmpty()) {
                sendTo(sender, "");
                sendTo(sender, cfg.messages.infoSearchPatterns);
                for (String pattern : info.fileSearchPatterns) {
                    sendTo(sender, cfg.messages.infoSearchPattern.replace("{pattern}", pattern));
                }
            }

            // Special rules
            if (!info.specialRules.isEmpty()) {
                sendTo(sender, "");
                sendTo(sender, cfg.messages.infoSpecialRules);
                for (String rule : info.specialRules) {
                    sendTo(sender, cfg.messages.infoSpecialRule.replace("{rule}", rule));
                }
            }

            sendTo(sender, "");
            sendTo(sender, cfg.messages.infoFooter);
            sendTo(sender, "");
        });
    }

    private void msg(CommandSender sender, String message) {
        if (sender != null) {
            sender.sendMessage(cfg.messages.prefix + message);
        } else {
            info(message);
        }
    }

    private void sendTo(CommandSender sender, String message) {
        if (sender != null) {
            sender.sendMessage(message);
        } else {
            info(message);
        }
    }

    private void info(String m) {
        // Remove Minecraft color codes for console display
        getLogger().info(stripMinecraftColors(m));
    }

    /**
     * Remove Minecraft color codes (§x) from string for console display
     */
    private String stripMinecraftColors(String text) {
        if (text == null) return null;
        return text.replaceAll("§[0-9a-fklmnor]", "");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!cfg.enabled) return;
        if (!cfg.adminLogin.enabled) return;
        Player p = e.getPlayer();
        if (p.hasPermission(cfg.adminLogin.permission)) {
            info(cfg.messages.adminLoginCheck);
            runAsyncCheck(false, p);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("zpluginupdate-spigot")) return false;
        if (!sender.hasPermission("zpluginupdater.command")) {
            sender.sendMessage(cfg.messages.prefix + cfg.messages.noPermission);
            return true;
        }

        // Check for subcommands
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("zpluginupdater.command.reload")) {
                    sender.sendMessage(cfg.messages.prefix + cfg.messages.noPermission);
                    return true;
                }
                try {
                    this.cfg = cfgMgr.loadOrCreateDefault();
                    sender.sendMessage(cfg.messages.prefix + cfg.messages.reloadSuccess);
                } catch (Exception e) {
                    sender.sendMessage(cfg.messages.prefix + cfg.messages.reloadFailed.replace("{error}", e.getMessage()));
                }
                return true;
            } else if (args[0].equalsIgnoreCase("check")) {
                if (!sender.hasPermission("zpluginupdater.command.check")) {
                    sender.sendMessage(cfg.messages.prefix + cfg.messages.noPermission);
                    return true;
                }
                runVersionCheck(sender);
                return true;
            } else if (args[0].equalsIgnoreCase("packtest")) {
                if (!sender.hasPermission("zpluginupdater.command.packtest")) {
                    sender.sendMessage(cfg.messages.prefix + cfg.messages.noPermission);
                    return true;
                }
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    UpdaterService service = createUpdaterService();
                    Path pluginsDir = getDataFolder().toPath().getParent();
                    boolean success = service.simulateGMEPGUpdate(Platform.SPIGOT, pluginsDir);
                    if (success) {
                        sender.sendMessage(cfg.messages.prefix + cfg.messages.geyserModelEngineCleanupSuccess);
                    } else {
                        sender.sendMessage(cfg.messages.prefix + cfg.messages.geyserModelEngineCleanupFailed);
                    }
                });
                return true;
            } else if (args[0].equalsIgnoreCase("webhooktest")) {
                if (!sender.hasPermission("zpluginupdater.command.webhooktest")) {
                    sender.sendMessage(cfg.messages.prefix + cfg.messages.noPermission);
                    return true;
                }
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    UpdaterService service = createUpdaterService();
                    sender.sendMessage(cfg.messages.prefix + "§7Sending Discord webhook test...");
                    boolean success = service.testDiscordWebhook();
                    if (success) {
                        sender.sendMessage(cfg.messages.prefix + "§aDiscord webhook test sent successfully! Check your Discord channel.");
                    } else {
                        sender.sendMessage(cfg.messages.prefix + "§cFailed to send Discord webhook test. Check console for details.");
                    }
                });
                return true;
            } else if (args[0].equalsIgnoreCase("summary") || args[0].equalsIgnoreCase("webhooksummary")) {
                if (!sender.hasPermission("zpluginupdater.command.summary")) {
                    sender.sendMessage(cfg.messages.prefix + cfg.messages.noPermission);
                    return true;
                }
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    sender.sendMessage(cfg.messages.prefix + "§7Sending version summary to Discord webhook...");
                    UpdaterService service = createUpdaterService();
                    Path pluginsDir = getDataFolder().toPath().getParent();
                    List<org.pluginupdater.core.VersionInfo> versions = service.checkVersions(Platform.SPIGOT, pluginsDir);

                    org.pluginupdater.core.DiscordNotifier notifier = new org.pluginupdater.core.DiscordNotifier(cfg, new SpigotLogger());
                    notifier.notifyPeriodicCheck(versions);
                    sender.sendMessage(cfg.messages.prefix + "§aVersion summary sent to Discord! Check your Discord channel.");
                });
                return true;
            } else if (args[0].equalsIgnoreCase("info")) {
                if (!sender.hasPermission("zpluginupdater.command.info")) {
                    sender.sendMessage(cfg.messages.prefix + cfg.messages.noPermission);
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(cfg.messages.prefix + cfg.messages.infoUsage);
                    return true;
                }
                runPluginInfo(sender, args[1]);
                return true;
            }
        }

        // Default: run update (requires update permission)
        if (!sender.hasPermission("zpluginupdater.command.update")) {
            sender.sendMessage(cfg.messages.prefix + cfg.messages.noPermission);
            return true;
        }
        runAsyncCheck(true, sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("zpluginupdate-spigot")) return null;
        if (!sender.hasPermission("zpluginupdater.command")) return null;

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String input = args[0].toLowerCase();

            if ("check".startsWith(input)) completions.add("check");
            if ("reload".startsWith(input)) completions.add("reload");
            if ("packtest".startsWith(input)) completions.add("packtest");
            if ("webhooktest".startsWith(input)) completions.add("webhooktest");
            if ("summary".startsWith(input)) completions.add("summary");
            if ("info".startsWith(input)) completions.add("info");

            return completions;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            // Tab complete plugin names for info command
            List<String> completions = new ArrayList<>();
            String input = args[1].toLowerCase();

            for (org.pluginupdater.core.Project project : org.pluginupdater.core.Project.values()) {
                String name = project.name().toLowerCase();
                if (name.startsWith(input)) {
                    completions.add(project.name());
                }
            }

            return completions;
        }

        return null;
    }

    private void migrateNestedPluginsIfNeeded(Path correctPluginsDir) {
                Path nested = correctPluginsDir.resolve("plugins");
                if (!java.nio.file.Files.isDirectory(nested)) return;
                try (java.util.stream.Stream<java.nio.file.Path> s = java.nio.file.Files.list(nested)) {
                    s.filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".jar") && (name.contains("geyser") || name.contains("floodgate") || name.contains("luckperms") || name.contains("packetevents"));
                    }).forEach(p -> {
                        try {
                            java.nio.file.Path dest = correctPluginsDir.resolve(p.getFileName().toString());
                            java.nio.file.Files.move(p, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (Exception ex) {
                            getLogger().warning(cfg.messages.migrationFailed
                                .replace("{file}", p.toString())
                                .replace("{error}", ex.getMessage()));
                        }
                    });
                } catch (Exception ex) {
                    getLogger().warning(cfg.messages.migrationScanFailed.replace("{error}", ex.getMessage()));
                }
            }

    private UpdaterService createUpdaterService() {
        return new UpdaterService(new SpigotLogger(), cfg, getDataFolder().toPath());
    }

            private class SpigotLogger implements LogAdapter {
                @Override public void info(String msg) { getLogger().info(msg); }
                @Override public void warn(String msg) { getLogger().warning(msg); }
                @Override public void error(String msg, Throwable t) { getLogger().severe(msg + " : " + t.getMessage()); }
            }
        }
        