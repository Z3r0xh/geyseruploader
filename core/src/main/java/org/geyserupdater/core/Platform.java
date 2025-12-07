package org.geyserupdater.core;

public enum Platform {
    SPIGOT("spigot"),
    BUNGEECORD("bungee"),
    VELOCITY("velocity");

    private final String apiName;

    Platform(String apiName) {
        this.apiName = apiName;
    }

    public String apiName() {
        return apiName;
    }
}