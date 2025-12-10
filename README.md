# PluginUpdater

**Fork of GeyserUpdater** - Modified to support automatic updates for multiple plugins beyond just Geyser and Floodgate.

Auto-update plugin for Minecraft servers. Supports Spigot (Paper, etc.), BungeeCord, and Velocity.

## Supported Plugins

### Core Plugins
- **Geyser** - Bedrock crossplay bridge
- **Floodgate** - Bedrock authentication
- **LuckPerms** - Permissions manager
- **PacketEvents** - Packet manipulation API
- **ProtocolLib** - Protocol manipulation (Spigot only)
- **ViaVersion, ViaBackwards, ViaRewind, ViaRewind-Legacy** - Multi-version support
- **FastAsyncWorldEdit (FAWE)** - World editing
- **PlaceholderAPI** - Placeholder system (Spigot only)
- **Item-NBT-API** - NBT manipulation (Spigot only)

### Geyser Extensions
- **GeyserUtils Extension** - Utility extension for Geyser
- **GeyserModelEngineExtension Extension** - Model engine resource pack generator

### Geyser-Related Plugins
- **GeyserUtils Plugin** - Geyser utilities
- **GeyserModelEngine Plugin** - Model engine integration

## Features
- Fetches the latest stable versions from official sources (Jenkins, GitHub, Download APIs)
- Automatic update checks:
  - On server startup
  - At specified intervals (e.g., every 12 hours)
  - When a player with specified permissions logs in (configurable)
- Manual commands with subcommands:
  - Update plugins: `/pluginupdate-{platform}`
  - Check versions: `/pluginupdate-{platform} check`
  - Reload config: `/pluginupdate-{platform} reload`
  - Test pack cleanup: `/pluginupdate-{platform} packtest`
- Semantic version comparison to prevent downgrades
- Special cleanup handling for GeyserModelEngineExtension extension updates
- Configurable messages, targets, intervals, and restart commands
- GitHub token support for API rate limit mitigation
- bStats metrics integration

## Build
- Prerequisites: Java 17, Maven 3.8+
- Steps:
  - Run `mvn package` in the cloned folder
  - Generated artifacts:
    - spigot/target/PluginUpdater-spigot-1.0.0.jar
    - bungee/target/PluginUpdater-bungee-1.0.0.jar
    - velocity/target/PluginUpdater-velocity-1.0.0.jar

## Installation
- Place the appropriate JAR for your server type in the plugins folder
- On startup, a config.yml will be generated with default values
- Existing plugin JARs are automatically detected by filename patterns
- The plugin loads at STARTUP phase (before other plugins) to ensure proper initialization order

## Commands & Permissions

### Spigot
- `/pluginupdate-spigot` - Execute update check (permission: pluginupdater.admin)
- `/pluginupdate-spigot check` - Display version information (permission: pluginupdater.check)
- `/pluginupdate-spigot reload` - Reload configuration (permission: pluginupdater.reload)
- `/pluginupdate-spigot packtest` - Simulate GMEPG update (permission: pluginupdater.packtest)

### BungeeCord
- `/pluginupdate-bungee` - Execute update check (permission: pluginupdater.admin)
- `/pluginupdate-bungee check` - Display version information (permission: pluginupdater.check)
- `/pluginupdate-bungee reload` - Reload configuration (permission: pluginupdater.reload)
- `/pluginupdate-bungee packtest` - Simulate GMEPG update (permission: pluginupdater.packtest)

### Velocity
- `/pluginupdate-velocity` - Execute update check (permission: pluginupdater.admin)
- `/pluginupdate-velocity check` - Display version information (permission: pluginupdater.check)
- `/pluginupdate-velocity reload` - Reload configuration (permission: pluginupdater.reload)
- `/pluginupdate-velocity packtest` - Simulate GMEPG update (permission: pluginupdater.packtest)

## Configuration File (config.yml)

### Main Settings
- `enabled` - Enable/disable the plugin
- `checkOnStartup` - Enable/disable check on startup
- `periodic.enabled` - Enable/disable periodic checks
- `periodic.intervalHours` - Check interval (in hours)
- `adminLogin.enabled` - Enable/disable check when permission holder logs in
- `adminLogin.permission` - Permission that triggers the check

### Target Selection
Enable/disable specific plugins to update:
```yaml
targets:
  geyser: true
  floodgate: true
  luckperms: true
  packetevents: true
  protocollib: true  # Spigot only
  viaversion: true
  viabackwards: true
  viarewind: true
  viarewind_legacy: true
  fawe: true
  placeholderapi: true  # Spigot only
  itemnbtapi: false  # Spigot only, opt-in
  geyserutils_extension: true
  geyserutils_plugin: true
  geysermodelengine_extension: true
  geysermodelengine_plugin: true
```

### GeyserModelEngineExtension Cleanup
- `GeyserModelEngineExtension.cleanOnUpdate` - Clean extension folder on update (keeps input/ folder)

### Post-Update Actions
- `postUpdate.runRestartCommand` - Automatically run restart command after update
- `postUpdate.restartCommand` - Restart command to execute (e.g., restart / end)

### GitHub API (Optional)
- `github.token` - GitHub personal access token to increase API rate limits

### Messages
All messages are customizable in the config file.

## How It Works

### Download Sources
- **Geyser/Floodgate**: Official download API (https://download.geysermc.org)
- **LuckPerms**: Jenkins CI (https://ci.lucko.me)
- **PacketEvents**: Jenkins CI (https://ci.codemc.io)
- **ProtocolLib**: SpigotMC resources
- **Via Plugins**: HangarMC API
- **FAWE**: Jenkins CI
- **PlaceholderAPI**: HangarMC API
- **Item-NBT-API**: GitHub releases
- **Geyser Extensions/Plugins**: GitHub releases

### Update Process
1. Downloads the latest version to a temporary file
2. Compares filenames using semantic version comparison
3. If newer version available:
   - Downloads and replaces the existing JAR atomically
   - For GeyserModelEngineExtension: Creates cleanup marker
4. On next server restart:
   - Cleans GMEPG extension folder (if marker exists)
   - New plugins load automatically

### Version Comparison
- Extracts semantic version numbers (e.g., 2.15.0 vs 2.14.0)
- Prevents downgrades by comparing major.minor.patch components
- Only updates if new version is higher

### Special Handling
- **Geyser Extensions**: Installed in `Geyser-{Platform}/extensions/` directory
- **GMEPG Cleanup**: Preserves `input/` folder, deletes `generated_pack.zip` and other files
- **Load Order**: Uses `softdepend` to load before other plugins
- **Platform Detection**: Automatically selects correct download variant per platform

## Notes / Known Limitations
- If download fails due to network issues, existing files are not affected
- Plugins must have recognizable filename patterns for detection
- Server/proxy restart required after updates for changes to take effect
- Some plugins are platform-specific (check target configuration)
- GitHub API has rate limits (60 requests/hour without token, 5000 with token)

## Original Project
This is a fork of [GeyserUpdater](https://github.com/grampr/geyseruploader) (replace with actual URL if known), expanded to support multiple plugins beyond Geyser and Floodgate.

## License
MIT License (or specify as appropriate)
