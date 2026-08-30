package org.lawnpilot.plugin.output;

import org.lawnpilot.model.Mower;
import org.lawnpilot.plugin.RuntimePlugin;

public interface OutputFormatter extends RuntimePlugin {
    String format(Mower mower);
}
