package org.lawnpilot.plugin;

public enum PluginType {
    DECISION_STRATEGY("1.0"),
    COLLISION_POLICY("1.0"),
    OUTPUT_FORMATTER("1.0");

    private final String contractVersion;

    PluginType(String contractVersion) {
        this.contractVersion = contractVersion;
    }

    public String contractVersion() {
        return contractVersion;
    }

    public boolean isCompatible(String version) {
        return contractVersion.equals(version);
    }
}
