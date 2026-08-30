package org.lawnpilot.plugin.output;

import org.lawnpilot.model.Mower;
import org.lawnpilot.plugin.PluginMetadata;

public final class TextOutputFormatter implements OutputFormatter {

    private static final PluginMetadata METADATA = new PluginMetadata(
            "text-output-formatter",
            "1.0.0",
            "1.0");

    @Override
    public String format(Mower mower) {
        return mower.toString();
    }

    @Override
    public PluginMetadata metadata() {
        return METADATA;
    }
}
