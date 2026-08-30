package org.lawnpilot.plugin;

public record PluginMetadata(String name, String pluginVersion, String contractVersion) {
    public PluginMetadata {
        if (isBlank(name)) {
            throw new IllegalArgumentException("Plugin name must not be blank.");
        }
        if (isBlank(pluginVersion)) {
            throw new IllegalArgumentException("Plugin version must not be blank.");
        }
        if (isBlank(contractVersion)) {
            throw new IllegalArgumentException("Contract version must not be blank.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
