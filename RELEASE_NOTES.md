# 🎉 zPluginUpdater v1.0.0 - Complete Rebrand

## ⚡ Major Changes

### 🏷️ Plugin Rebrand
- **New Name:** `zPluginUpdater` (formerly PluginUpdater)
- **New Commands:** `/zpluginupdate-{platform}` with "z" prefix
- **New Permissions:** `zpluginupdater.*` hierarchical structure
- All platforms updated: Spigot, BungeeCord, and Velocity

### 🎨 Visual Improvements
- **Minecraft Color Codes:** All in-game messages now have color codes
  - 🟢 Green (§a) - Success messages
  - 🟡 Yellow (§e) - Warnings and updates
  - 🔴 Red (§c) - Errors
  - ⚪ Gray (§7) - Informational messages
- **Console Colors:** ANSI color codes for better console visibility
- **Multi-language Support:** Colors applied to all 6 language files (EN, ES, DE, FR, JA, ZH)

### 🔐 Permission System Overhaul
Implemented hierarchical permission structure:
- `zpluginupdater.*` - Grants all permissions
  - `zpluginupdater.command` - Access to all commands
    - `zpluginupdater.command.update` - Execute updates
    - `zpluginupdater.command.check` - Check versions
  - `zpluginupdater.command.reload` - Reload configuration
  - `zpluginupdater.command.packtest` - Test pack cleanup

## 📦 Downloads

Choose the appropriate JAR file for your server platform:

- **Spigot/Paper:** `zPluginUpdater-Spigot-1.0.0.jar`
- **BungeeCord/Waterfall:** `zPluginUpdater-BungeeCord-1.0.0.jar`
- **Velocity:** `zPluginUpdater-Velocity-1.0.0.jar`

## 🔧 Installation

1. Download the appropriate JAR for your platform
2. Place it in your `plugins` folder
3. Restart your server
4. Configure in `config.yml` (auto-generated on first run)

## 📝 Commands

### Spigot
- `/zpluginupdate-spigot` - Execute update check
- `/zpluginupdate-spigot check` - Display version information
- `/zpluginupdate-spigot reload` - Reload configuration
- `/zpluginupdate-spigot packtest` - Test GMEPG cleanup

### BungeeCord
- `/zpluginupdate-bungee [check|reload|packtest]`

### Velocity
- `/zpluginupdate-velocity [check|reload|packtest]`

## ✨ Features

- ✅ Automatic updates for 15+ popular plugins
- ✅ Multi-language support (EN, ES, DE, FR, JA, ZH)
- ✅ Semantic version comparison (prevents downgrades)
- ✅ Special cleanup handling for GeyserModelEngineExtension
- ✅ GitHub token support for rate limit mitigation
- ✅ Configurable update intervals and restart commands
- ✅ bStats metrics integration

## 📚 Supported Plugins

### Core Plugins
- Geyser, Floodgate, LuckPerms, PacketEvents
- ProtocolLib (Spigot only)
- ViaVersion, ViaBackwards, ViaRewind, ViaRewind-Legacy
- FastAsyncWorldEdit (FAWE)
- PlaceholderAPI (Spigot only)
- Item-NBT-API (Spigot only)

### Geyser Extensions & Plugins
- GeyserUtils Extension & Plugin
- GeyserModelEngineExtension Extension & Plugin

## 🔄 Migration from PluginUpdater

If you're upgrading from the previous "PluginUpdater" version:
1. Replace the old JAR with the new zPluginUpdater JAR
2. Update your permissions to use `zpluginupdater.*` instead of `pluginupdater.*`
3. Update any scripts/commands to use the new command names with "z" prefix
4. Configuration files will migrate automatically

## 🐛 Bug Fixes

- Fixed Geyser detection incorrectly matching GeyserUtils
- Fixed version detection for plugins with static filenames
- Improved file size comparison for update detection
- Fixed Floodgate download URL for BungeeCord (now uses "bungee" instead of "bungeecord")
- Fixed version comparison for files downloaded with browsers (removes "(N)" suffixes)
- Fixed SNAPSHOT version detection to update when file size changes
- Added protection against corrupted downloads (prevents updating with suspiciously small files)
- Updated User-Agent to reflect zPluginUpdater branding
- **Automatic SNAPSHOT update detection**: Now compares GitHub release timestamps to detect updates even when file size is the same

## 📖 Full Documentation

For complete documentation, configuration examples, and troubleshooting, see the [README.md](https://github.com/Z3r0xh/geyseruploader/blob/rebrand-to-pluginupdater/README.md)

## 🤝 Credits

Original project: [GeyserUpdater](https://github.com/grampr/geyseruploader)
Authors: neha, Z3r0xh

---

**Minimum Requirements:** Java 17, Spigot/Paper 1.19+, BungeeCord/Waterfall, or Velocity 3.0+
