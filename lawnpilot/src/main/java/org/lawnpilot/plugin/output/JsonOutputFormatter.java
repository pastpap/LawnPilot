package org.lawnpilot.plugin.output;

import org.lawnpilot.model.Mower;
import org.lawnpilot.plugin.PluginMetadata;

public final class JsonOutputFormatter implements OutputFormatter {

    private static final PluginMetadata METADATA = new PluginMetadata(
            "json-output-formatter",
            "1.0.0",
            "1.0");

    @Override
    public String format(Mower mower) {
        return "{\"x\":" + mower.getX()
                + ",\"y\":" + mower.getY()
                + ",\"direction\":\"" + mower.getDirection() + "\"}";
    }

    @Override
    public PluginMetadata metadata() {
        return METADATA;
    }
}
