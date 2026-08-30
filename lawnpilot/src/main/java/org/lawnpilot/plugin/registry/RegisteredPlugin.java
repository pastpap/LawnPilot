package org.lawnpilot.plugin.registry;

import org.lawnpilot.plugin.RuntimePlugin;

record RegisteredPlugin<T extends RuntimePlugin>(String id, T plugin, boolean enabled, boolean builtInDefault) {
    RegisteredPlugin<T> withEnabled(boolean newEnabled) {
        return new RegisteredPlugin<>(id, plugin, newEnabled, builtInDefault);
    }
}
