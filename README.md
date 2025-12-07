# GeyserUpdater

Auto-update plugin for GeyserMC / Floodgate. Supports Spigot (Paper, etc.), BungeeCord, and Velocity.

## Features
- Fetches the latest stable version of GeyserMC and Floodgate from Jenkins/Download API and overwrites existing JARs in the plugins directory
- Automatic update checks:
  - On server startup
  - At specified intervals (e.g., every 12 hours)
  - When a player with specified permissions logs in (can be toggled ON/OFF)
- Manual command: /geyserupdate (permission: geyserupdater.admin)
- Configurable messages, targets, intervals, and restart commands

## Build
- Prerequisites: Java 17, Maven 3.8+
- Steps:
  - Run `mvn package` in the cloned folder
  - Generated artifacts:
    - spigot/target/GeyserUpdater-spigot-1.0.2.jar
    - bungee/target/GeyserUpdater-bungee-1.0.2.jar
    - velocity/target/GeyserUpdater-velocity-1.0.2.jar

## Installation
- Place the appropriate JAR for your server type in the plugins folder
- On startup, a config.yml will be generated (equivalent to the default values included in this README)
- Existing Geyser/Floodgate JARs are automatically detected if their filenames contain "geyser" or "floodgate" in the same folder
  - If not found, a new file will be created with the standard name (e.g., Geyser-Spigot.jar, floodgate-velocity.jar)

## Commands & Permissions
- /geyserupdate
  - Description: Execute an immediate update check
  - Permission: geyserupdater.admin

## Configuration File (config.yml)
- enabled: Enable/disable the plugin
- checkOnStartup: Enable/disable check on startup
- periodic.enabled: Enable/disable periodic checks
- periodic.intervalHours: Check interval (in hours)
- adminLogin.enabled: Enable/disable check when permission holder logs in
- adminLogin.permission: Permission that triggers the check (default: geyserupdater.admin)
- targets.geyser | targets.floodgate: Select update targets
- postUpdate.notifyConsole: Notify console
- postUpdate.notifyPlayersWithPermission: Notify players with permission via chat
- postUpdate.runRestartCommand: Automatically run restart command after update
- postUpdate.restartCommand: Restart command to execute (e.g., restart / end)
- messages.*: Customize messages

## How It Works
- Download URLs:
  - Geyser: https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/{platform}
  - Floodgate: https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/{platform}
  - {platform} is spigot | bungeecord | velocity
- Downloads the latest JAR to a temporary file and compares it with the existing JAR using SHA-256
  - If identical, no overwrite is performed and the plugin reports "up to date"
  - If different, overwrites atomically
- After overwriting, a server/proxy restart is required
  - To enable automatic restart, set postUpdate.runRestartCommand to true and configure restartCommand according to your environment

## Notes / Known Limitations
- If download fails due to network issues, etc., existing files are not affected
- If Geyser/Floodgate filenames are unusual or located outside the plugins directory, they cannot be detected (assumes *.jar search directly under plugins)
- Version numbers are not displayed (update determination is based on hash comparison)

## License
- Can be set upon request (e.g., MIT)

