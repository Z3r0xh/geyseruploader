package org.pluginupdater.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.bstats.bungeecord.Metrics;
import org.pluginupdater.core.Config;
import org.pluginupdater.core.ConfigManager;
import org.pluginupdater.core.Platform;
import org.pluginupdater.core.UpdaterService;
import org.pluginupdater.core.logging.LogAdapter;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BungeePluginUpdaterPlugin extends Plugin implements Listener {
    private ConfigManager cfgMgr;
    private Config cfg;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        this.cfgMgr = new ConfigManager(getDataFolder().toPath());
        this.cfg = cfgMgr.loadOrCreateDefault();

        // Execute migration
        migrateNestedPluginsIfNeeded(getDataFolder().toPath().getParent());

        // Execute GeyserModelEnginePackGenerator cleanup if pending
        Path pluginsDir = getDataFolder().toPath().getParent();
        UpdaterService cleanupService = new UpdaterService(new BungeeLogger(), cfg);
        cleanupService.executeCleanupIfPending(Platform.BUNGEECORD, pluginsDir);

        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new UpdateCommand());

        if (!cfg.enabled) {
            getLogger().info(cfg.messages.pluginDisabled);
            return;
        }

        // Initialize bStats
        int pluginId = 28287;
        Metrics metrics = new Metrics(this, pluginId);

        if (cfg.checkOnStartup) {
            info(cfg.messages.startUpCheck);
            runAsyncCheck(false, null);
        }

        if (cfg.periodic.enabled && cfg.periodic.intervalHours > 0) {
            info(cfg.messages.periodicCheck.replace("{hours}", String.valueOf(cfg.periodic.intervalHours)));
            long initialDelay = TimeUnit.MINUTES.toSeconds(5);
            long interval = TimeUnit.HOURS.toSeconds(cfg.periodic.intervalHours);
            getProxy().getScheduler().schedule(this, () -> runAsyncCheck(false, null), initialDelay, interval, TimeUnit.SECONDS);
        }
    }

    private void runAsyncCheck(boolean manual, CommandSender sender) {
        ProxyServer.getInstance().getScheduler().runAsync(this, () -> {
            UpdaterService service = new UpdaterService(new BungeeLogger(), cfg);
            if (manual) {
                send(sender, cfg.messages.prefix + cfg.messages.manualTriggered);
            } else {
                info(cfg.messages.checking);
            }
            Path pluginsDir = getDataFolder().toPath().getParent(); // This is directly under plugins
            List<UpdaterService.UpdateOutcome> results = service.checkAndUpdate(Platform.BUNGEECORD, pluginsDir);

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
                    ProxyServer.getInstance().getScheduler().runAsync(this, () -> ProxyServer.getInstance().getPluginManager()
                            .dispatchCommand(ProxyServer.getInstance().getConsole(), cfg.postUpdate.restartCommand));
                }
            }
            msg(sender, cfg.messages.done);
        });
    }

    private void runVersionCheck(CommandSender sender) {
        ProxyServer.getInstance().getScheduler().runAsync(this, () -> {
            send(sender, cfg.messages.prefix + cfg.messages.versionCheckFetching);

            UpdaterService service = new UpdaterService(new BungeeLogger(), cfg);
            Path pluginsDir = getDataFolder().toPath().getParent();
            List<org.pluginupdater.core.VersionInfo> versions = service.checkVersions(Platform.BUNGEECORD, pluginsDir);

            // Display header
            send(sender, "");
            send(sender, cfg.messages.versionCheckHeader);
            send(sender, cfg.messages.versionCheckTitle);
            send(sender, cfg.messages.versionCheckHeader);

            int enabledCount = 0;
            int updatesCount = 0;

            // Display each project
            for (org.pluginupdater.core.VersionInfo info : versions) {
                String projectName = info.project.name();

                if (!info.enabled) {
                    send(sender, cfg.messages.versionCheckDisabled.replace("{project}", projectName));
                } else {
                    enabledCount++;
                    if (info.error.isPresent()) {
                        String errorMsg = info.error.get();
                        if (errorMsg.length() > 40) errorMsg = errorMsg.substring(0, 37) + "...";
                        send(sender, cfg.messages.versionCheckError
                                .replace("{project}", projectName)
                                .replace("{error}", errorMsg));
                    } else if (!info.installedVersion.isPresent()) {
                        updatesCount++;
                        send(sender, cfg.messages.versionCheckNotFound
                                .replace("{project}", projectName));
                    } else if (info.updateAvailable) {
                        updatesCount++;
                        send(sender, cfg.messages.versionCheckUpdateAvailable
                                .replace("{project}", projectName));
                    } else {
                        send(sender, cfg.messages.versionCheckUpToDate
                                .replace("{project}", projectName));
                    }
                }
            }

            // Display footer
            send(sender, cfg.messages.versionCheckSummary
                    .replace("{enabled}", String.valueOf(enabledCount))
                    .replace("{updates}", String.valueOf(updatesCount)));
            send(sender, cfg.messages.versionCheckFooter);
            send(sender, "");
        });
    }

    private class UpdateCommand extends Command implements net.md_5.bungee.api.plugin.TabExecutor {
        public UpdateCommand() {
            super("pluginupdate-bungee", "pluginupdater.admin", new String[0]);
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            // Check for subcommands
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("reload")) {
                    if (!sender.hasPermission("pluginupdater.reload")) {
                        sender.sendMessage(new TextComponent(cfg.messages.prefix + cfg.messages.noPermission));
                        return;
                    }
                    try {
                        cfg = cfgMgr.loadOrCreateDefault();
                        sender.sendMessage(new TextComponent(cfg.messages.prefix + cfg.messages.reloadSuccess));
                    } catch (Exception e) {
                        sender.sendMessage(new TextComponent(cfg.messages.prefix + cfg.messages.reloadFailed.replace("{error}", e.getMessage())));
                    }
                    return;
                } else if (args[0].equalsIgnoreCase("check")) {
                    if (!sender.hasPermission("pluginupdater.check")) {
                        sender.sendMessage(new TextComponent(cfg.messages.prefix + cfg.messages.noPermission));
                        return;
                    }
                    runVersionCheck(sender);
                    return;
                } else if (args[0].equalsIgnoreCase("packtest")) {
                    if (!sender.hasPermission("pluginupdater.packtest")) {
                        sender.sendMessage(new TextComponent(cfg.messages.prefix + cfg.messages.noPermission));
                        return;
                    }
                    ProxyServer.getInstance().getScheduler().runAsync(BungeePluginUpdaterPlugin.this, () -> {
                        UpdaterService service = new UpdaterService(new BungeeLogger(), cfg);
                        Path pluginsDir = getDataFolder().toPath().getParent();
                        boolean success = service.simulateGMEPGUpdate(Platform.BUNGEECORD, pluginsDir);
                        if (success) {
                            sender.sendMessage(new TextComponent(cfg.messages.prefix + "§aGeyserModelEnginePackGenerator cleanup marker created. The extension folder will be cleaned on next restart."));
                        } else {
                            sender.sendMessage(new TextComponent(cfg.messages.prefix + "§cFailed to create cleanup marker. Make sure GeyserModelEnginePackGenerator is installed and cleanOnUpdate is enabled."));
                        }
                    });
                    return;
                }
            }

            runAsyncCheck(true, sender);
        }

        @Override
        public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
            if (!sender.hasPermission("pluginupdater.admin")) {
                return new java.util.ArrayList<>();
            }

            if (args.length == 1) {
                java.util.List<String> completions = new java.util.ArrayList<>();
                String input = args[0].toLowerCase();

                if ("check".startsWith(input)) completions.add("check");
                if ("reload".startsWith(input)) completions.add("reload");
                if ("packtest".startsWith(input)) completions.add("packtest");

                return completions;
            }

            return new java.util.ArrayList<>();
        }
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent e) {
        if (!cfg.enabled || !cfg.adminLogin.enabled) return;
        ProxiedPlayer p = e.getPlayer();
        if (p.hasPermission(cfg.adminLogin.permission)) {
            info(cfg.messages.adminLoginCheck);
            runAsyncCheck(false, p);
        }
    }

    private void msg(CommandSender sender, String msg) {
        if (sender != null) send(sender, cfg.messages.prefix + msg);
        else info(msg);
    }

    private void send(CommandSender sender, String msg) {
        if (sender != null) sender.sendMessage(new TextComponent(msg));
        else info(msg);
    }

        private void info(String msg) {

            getLogger().info(msg);

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

    

        private class BungeeLogger implements LogAdapter {

            @Override public void info(String msg) { getLogger().info(msg); }

            @Override public void warn(String msg) { getLogger().warning(msg); }

            @Override public void error(String msg, Throwable t) { getLogger().severe(msg + " : " + t.getMessage()); }

        }

    }

    