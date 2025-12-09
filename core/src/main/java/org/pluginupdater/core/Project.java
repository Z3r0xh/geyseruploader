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
    VIAREWIND_LEGACY("viarewind-legacy", "ViaRewind-Legacy-Support");

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
}