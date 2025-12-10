package org.pluginupdater.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.bstats.velocity.Metrics;
import org.pluginupdater.core.Config;
import org.pluginupdater.core.ConfigManager;
import org.pluginupdater.core.Platform;
import org.pluginupdater.core.UpdaterService;
import org.pluginupdater.core.logging.LogAdapter;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Plugin(
        id = "pluginupdater",
        name = "PluginUpdater",
        version = "1.0.0",
        description = "Automatically downloads and updates plugins from official sources"
)
public class VelocityPluginUpdaterPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;
    private final Metrics.Factory metricsFactory;

    private ConfigManager cfgMgr;
    private Config cfg;

    @Inject
    public VelocityPluginUpdaterPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir, Metrics.Factory metricsFactory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialize(com.velocitypowered.api.event.proxy.ProxyInitializeEvent e) {
        try {
            java.nio.file.Files.createDirectories(dataDir);
        } catch (Exception ex) {
            // Note: cfg not loaded yet, so we can't use cfg.messages here
            logger.severe("Could not create data directory: " + ex.getMessage());
        }
        this.cfgMgr = new ConfigManager(dataDir);
        this.cfg = cfgMgr.loadOrCreateDefault();

        // Execute migration
        migrateNestedPluginsIfNeeded(dataDir.getParent());

        // Register command
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("zpluginupdate-velocity").build(),
                new UpdateCommand()
        );

        // Note: Main instance is automatically registered for events, explicit register not needed
        // Example: do not call proxy.getEventManager().register(this, this);

        if (!cfg.enabled) {
            logger.info(cfg.messages.pluginDisabled);
            return;
        }

        // Initialize bStats
        int pluginId = 28288;
        Metrics metrics = metricsFactory.make(this, pluginId);

        if (cfg.checkOnStartup) {
            logger.info(cfg.messages.startUpCheck);
            runAsyncCheck(false, null);
        }
        if (cfg.periodic.enabled && cfg.periodic.intervalHours > 0) {
            logger.info(cfg.messages.periodicCheck.replace("{hours}", String.valueOf(cfg.periodic.intervalHours)));
            proxy.getScheduler().buildTask(this, () -> runAsyncCheck(false, null))
                    .delay(5, TimeUnit.MINUTES)
                    .repeat(cfg.periodic.intervalHours, TimeUnit.HOURS)
                    .schedule();
        }
    }

    @Subscribe
    public void onProxyShutdown(com.velocitypowered.api.event.proxy.ProxyShutdownEvent event) {
        // Execute cleanup on shutdown if marker exists
        Path pluginsDir = dataDir.getParent();
        UpdaterService cleanupService = new UpdaterService(new VelocityLogger(), cfg);
        cleanupService.executeCleanupOnShutdown(Platform.VELOCITY, pluginsDir);
    }

    private void runAsyncCheck(boolean manual, CommandSource sender) {
        proxy.getScheduler().buildTask(this, () -> {
            UpdaterService service = new UpdaterService(new VelocityLogger(), cfg);
            if (manual) {
                send(sender, cfg.messages.prefix + cfg.messages.manualTriggered);
            } else {
                logger.info(cfg.messages.checking);
            }
            Path pluginsDir = dataDir.getParent(); // This is directly under plugins
            List<UpdaterService.UpdateOutcome> results = service.checkAndUpdate(Platform.VELOCITY, pluginsDir);

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
                logger.info(cfg.messages.promptRestart);
                if (cfg.postUpdate.runRestartCommand && cfg.postUpdate.restartCommand != null && !cfg.postUpdate.restartCommand.isBlank()) {
                    proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), cfg.postUpdate.restartCommand);
                }
            }
            msg(sender, cfg.messages.done);
        }).schedule();
    }

    private void runVersionCheck(CommandSource sender) {
        proxy.getScheduler().buildTask(this, () -> {
            send(sender, cfg.messages.prefix + cfg.messages.versionCheckFetching);

            UpdaterService service = new UpdaterService(new VelocityLogger(), cfg);
            Path pluginsDir = dataDir.getParent();
            List<org.pluginupdater.core.VersionInfo> versions = service.checkVersions(Platform.VELOCITY, pluginsDir);

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
        }).schedule();
    }

    private class UpdateCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource src = invocation.source();
            if (!src.hasPermission("zpluginupdater.command")) {
                send(src, cfg.messages.prefix + cfg.messages.noPermission);
                return;
            }

            // Check for subcommands
            String[] args = invocation.arguments();
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("reload")) {
                    if (!src.hasPermission("zpluginupdater.command.reload")) {
                        send(src, cfg.messages.prefix + cfg.messages.noPermission);
                        return;
                    }
                    try {
                        cfg = cfgMgr.loadOrCreateDefault();
                        send(src, cfg.messages.prefix + cfg.messages.reloadSuccess);
                    } catch (Exception e) {
                        send(src, cfg.messages.prefix + cfg.messages.reloadFailed.replace("{error}", e.getMessage()));
                    }
                    return;
                } else if (args[0].equalsIgnoreCase("check")) {
                    if (!src.hasPermission("zpluginupdater.command.check")) {
                        send(src, cfg.messages.prefix + cfg.messages.noPermission);
                        return;
                    }
                    runVersionCheck(src);
                    return;
                } else if (args[0].equalsIgnoreCase("packtest")) {
                    if (!src.hasPermission("zpluginupdater.command.packtest")) {
                        send(src, cfg.messages.prefix + cfg.messages.noPermission);
                        return;
                    }
                    proxy.getScheduler().buildTask(VelocityPluginUpdaterPlugin.this, () -> {
                        UpdaterService service = new UpdaterService(new VelocityLogger(), cfg);
                        Path pluginsDir = dataDir.getParent();
                        boolean success = service.simulateGMEPGUpdate(Platform.VELOCITY, pluginsDir);
                        if (success) {
                            send(src, cfg.messages.prefix + cfg.messages.geyserModelEngineCleanupSuccess);
                        } else {
                            send(src, cfg.messages.prefix + cfg.messages.geyserModelEngineCleanupFailed);
                        }
                    }).schedule();
                    return;
                } else if (args[0].equalsIgnoreCase("webhooktest")) {
                    if (!src.hasPermission("zpluginupdater.command.webhooktest")) {
                        send(src, cfg.messages.prefix + cfg.messages.noPermission);
                        return;
                    }
                    proxy.getScheduler().buildTask(VelocityPluginUpdaterPlugin.this, () -> {
                        UpdaterService service = new UpdaterService(new VelocityLogger(), cfg);
                        send(src, cfg.messages.prefix + "§7Sending Discord webhook test...");
                        boolean success = service.testDiscordWebhook();
                        if (success) {
                            send(src, cfg.messages.prefix + "§aDiscord webhook test sent successfully! Check your Discord channel.");
                        } else {
                            send(src, cfg.messages.prefix + "§cFailed to send Discord webhook test. Check console for details.");
                        }
                    }).schedule();
                    return;
                }
            }

            // Default: run update (requires update permission)
            if (!src.hasPermission("zpluginupdater.command.update")) {
                send(src, cfg.messages.prefix + cfg.messages.noPermission);
                return;
            }
            runAsyncCheck(true, src);
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            if (!invocation.source().hasPermission("zpluginupdater.command")) {
                return List.of();
            }

            String[] args = invocation.arguments();
            if (args.length == 0 || args.length == 1) {
                String input = args.length == 0 ? "" : args[0].toLowerCase();
                List<String> completions = new java.util.ArrayList<>();

                if ("check".startsWith(input)) completions.add("check");
                if ("reload".startsWith(input)) completions.add("reload");
                if ("packtest".startsWith(input)) completions.add("packtest");
                if ("webhooktest".startsWith(input)) completions.add("webhooktest");

                return completions;
            }

            return List.of();
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("zpluginupdater.command");
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent e) {
        if (!cfg.enabled || !cfg.adminLogin.enabled) return;
        if (e.getPlayer().hasPermission(cfg.adminLogin.permission)) {
            logger.info(cfg.messages.adminLoginCheck);
            runAsyncCheck(false, e.getPlayer());
        }
    }

    private void msg(CommandSource sender, String msg) {
        if (sender != null) send(sender, cfg.messages.prefix + msg);
        else logger.info(msg);
    }

    private void send(CommandSource sender, String msg) {
        if (sender != null) sender.sendMessage(Component.text(msg));
        else logger.info(msg);
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
                    logger.warning(cfg.messages.migrationFailed
                        .replace("{file}", p.toString())
                        .replace("{error}", ex.getMessage()));
                }
            });
        } catch (Exception ex) {
            logger.warning(cfg.messages.migrationScanFailed.replace("{error}", ex.getMessage()));
        }
    }

    private class VelocityLogger implements LogAdapter {
        @Override public void info(String msg) { logger.info(msg); }
        @Override public void warn(String msg) { logger.warning(msg); }
        @Override public void error(String msg, Throwable t) { logger.severe(msg + " : " + t.getMessage()); }
    }
}
