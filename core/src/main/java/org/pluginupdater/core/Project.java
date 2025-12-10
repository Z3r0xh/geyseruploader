package org.pluginupdater.core;

public enum Project {
    GEYSER("geyser", "geyser"),
    FLOODGATE("floodgate", "floodgate"),
    LUCKPERMS("luckperms", "luckperms"),
    PACKETEVENTS("packetevents", "packetevents"),
    PROTOCOLLIB("protocollib", "ProtocolLib"),
    VIAVERSION("viaversion", "ViaVersion"),
    VIABACKWARDS("viabackwards", "ViaBackwards"),
    VIAREWIND("viarewind", "ViaRewind"),
    VIAREWIND_LEGACY("viarewind-legacy", "ViaRewind-Legacy-Support"),
    FAWE("fawe", "FastAsyncWorldEdit"),
    PLACEHOLDERAPI("placeholderapi", "PlaceholderAPI"),
    ITEMNBTAPI("itemnbtapi", "item-nbt-api"),
    // Geyser Extensions (go in Geyser-{Platform}/extensions/)
    GEYSERUTILS_EXTENSION("geyserutils-extension", "geyserutils-geyser"),
    GEYSERMODELENGINE_EXTENSION("geysermodelengine-extension", "GeyserModelEngineExtension"),
    // Geyser-related Plugins (go in plugins/)
    GEYSERUTILS_PLUGIN("geyserutils-plugin", "geyserutils"),
    GEYSERMODELENGINE_PLUGIN("geysermodelengine-plugin", "GeyserModelEngine");

    private final String apiName;
    private final String fileHint;

    Project(String apiName, String fileHint) {
        this.apiName = apiName;
        this.fileHint = fileHint;
    }

    public String apiName() { return apiName; }

    public String fileHint() { return fileHint; }

    public boolean isLuckPerms() {
        return this == LUCKPERMS;
    }

    public boolean isPacketEvents() {
        return this == PACKETEVENTS;
    }

    public boolean isProtocolLib() {
        return this == PROTOCOLLIB;
    }

    public boolean isViaPlugin() {
        return this == VIAVERSION || this == VIABACKWARDS || this == VIAREWIND || this == VIAREWIND_LEGACY;
    }

    public boolean isGeyserExtension() {
        return this == GEYSERUTILS_EXTENSION || this == GEYSERMODELENGINE_EXTENSION;
    }

    public boolean isGeyserRelatedPlugin() {
        return this == GEYSERUTILS_PLUGIN || this == GEYSERMODELENGINE_PLUGIN;
    }

    public boolean isItemNBTAPI() {
        return this == ITEMNBTAPI;
    }
}