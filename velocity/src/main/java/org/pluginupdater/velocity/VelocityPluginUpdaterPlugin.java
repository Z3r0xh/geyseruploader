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
        description = "Auto-updater for Geyser, Floodgate and other plugins"
)
public class VelocityPluginUpdaterPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;

    private ConfigManager cfgMgr;
    private Config cfg;

    @Inject
    public VelocityGeyserUpdaterPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
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
                proxy.getCommandManager().metaBuilder("pluginupdate-velocity").build(),
                new UpdateCommand()
        );

        // Note: Main instance is automatically registered for events, explicit register not needed
        // Example: do not call proxy.getEventManager().register(this, this);

        if (!cfg.enabled) {
            logger.info(cfg.messages.pluginDisabled);
            return;
        }

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

    private class UpdateCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource src = invocation.source();
            if (!src.hasPermission("pluginupdater.admin")) {
                send(src, cfg.messages.prefix + cfg.messages.noPermission);
                return;
            }

            // Check for reload subcommand
            String[] args = invocation.arguments();
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!src.hasPermission("pluginupdater.reload")) {
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
            }

            runAsyncCheck(true, src);
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("pluginupdater.admin");
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
                return name.endsWith(".jar") && (name.contains("geyser") || name.contains("floodgate") || name.contains("luckperms"));
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
