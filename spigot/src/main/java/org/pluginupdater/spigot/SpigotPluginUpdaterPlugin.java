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
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> runAsyncCheck(false, null), delay, ticks);
        }
    }

    @Override
    public void onDisable() {
        // Execute cleanup on shutdown if marker exists
        Path pluginsDir = getDataFolder().toPath().getParent();
        UpdaterService cleanupService = new UpdaterService(new SpigotLogger(), cfg);
        cleanupService.executeCleanupOnShutdown(Platform.SPIGOT, pluginsDir);
    }

    private void saveDefaultConfigFile() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        // We use our own config manager; nothing to save here
    }

    private void runAsyncCheck(boolean manual, CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            UpdaterService service = new UpdaterService(new SpigotLogger(), cfg);
            if (manual) {
                sendTo(sender, cfg.messages.prefix + cfg.messages.manualTriggered);
            } else {
                info(cfg.messages.checking);
            }
            Path pluginsDir = getDataFolder().toPath().getParent(); // This is directly under plugins
            List<UpdaterService.UpdateOutcome> results =
                    service.checkAndUpdate(Platform.SPIGOT, pluginsDir);

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

            UpdaterService service = new UpdaterService(new SpigotLogger(), cfg);
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
        getLogger().info(m);
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
                    UpdaterService service = new UpdaterService(new SpigotLogger(), cfg);
                    Path pluginsDir = getDataFolder().toPath().getParent();
                    boolean success = service.simulateGMEPGUpdate(Platform.SPIGOT, pluginsDir);
                    if (success) {
                        sender.sendMessage(cfg.messages.prefix + cfg.messages.geyserModelEngineCleanupSuccess);
                    } else {
                        sender.sendMessage(cfg.messages.prefix + cfg.messages.geyserModelEngineCleanupFailed);
                    }
                });
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
        
            private class SpigotLogger implements LogAdapter {
                @Override public void info(String msg) { getLogger().info(msg); }
                @Override public void warn(String msg) { getLogger().warning(msg); }
                @Override public void error(String msg, Throwable t) { getLogger().severe(msg + " : " + t.getMessage()); }
            }
        }
        