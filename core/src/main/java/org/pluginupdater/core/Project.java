package org.pluginupdater.core;

public enum Project {
    GEYSER("geyser", "geyser"),
    FLOODGATE("floodgate", "floodgate"),
    LUCKPERMS("luckperms", "luckperms"),
    PACKETEVENTS("packetevents", "packetevents"),
    PROTOCOLLIB("protocollib", "ProtocolLib");

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
}