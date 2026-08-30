package org.lawnpilot.plugin.registry;

import org.lawnpilot.plugin.PluginType;
import org.lawnpilot.plugin.RuntimePlugin;
import org.lawnpilot.plugin.collision.BoundaryCollisionPolicy;
import org.lawnpilot.plugin.collision.CollisionPolicy;
import org.lawnpilot.plugin.decision.DecisionStrategy;
import org.lawnpilot.plugin.decision.InstructionDecisionStrategy;
import org.lawnpilot.plugin.output.OutputFormatter;
import org.lawnpilot.plugin.output.TextOutputFormatter;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public final class PluginRegistry {

    public static final String DEFAULT_DECISION_STRATEGY_ID = "default-decision-strategy";
    public static final String DEFAULT_COLLISION_POLICY_ID = "default-collision-policy";
    public static final String DEFAULT_OUTPUT_FORMATTER_ID = "default-output-formatter";

    private final Map<PluginType, Map<String, RegisteredPlugin<? extends RuntimePlugin>>> pluginsByType = new EnumMap<>(
            PluginType.class);
    private final Map<PluginType, String> activePluginByType = new EnumMap<>(PluginType.class);

    public static PluginRegistry withDefaults() {
        PluginRegistry registry = new PluginRegistry();
        registry.registerBuiltInDefault(PluginType.DECISION_STRATEGY,
                DEFAULT_DECISION_STRATEGY_ID,
                new InstructionDecisionStrategy());
        registry.registerBuiltInDefault(PluginType.COLLISION_POLICY,
                DEFAULT_COLLISION_POLICY_ID,
                new BoundaryCollisionPolicy());
        registry.registerBuiltInDefault(PluginType.OUTPUT_FORMATTER,
                DEFAULT_OUTPUT_FORMATTER_ID,
                new TextOutputFormatter());
        return registry;
    }

    public void registerDecisionStrategy(String id, DecisionStrategy strategy) {
        registerPlugin(PluginType.DECISION_STRATEGY, id, strategy, false);
    }

    public void registerCollisionPolicy(String id, CollisionPolicy policy) {
        registerPlugin(PluginType.COLLISION_POLICY, id, policy, false);
    }

    public void registerOutputFormatter(String id, OutputFormatter formatter) {
        registerPlugin(PluginType.OUTPUT_FORMATTER, id, formatter, false);
    }

    public void enable(PluginType type, String id) {
        RegisteredPlugin<? extends RuntimePlugin> plugin = getRegistered(type, id);
        put(type, id, plugin.withEnabled(true));
        activePluginByType.put(type, id);
    }

    public void disable(PluginType type, String id) {
        RegisteredPlugin<? extends RuntimePlugin> plugin = getRegistered(type, id);
        if (plugin.builtInDefault()) {
            throw new IllegalArgumentException("Built-in default plugin cannot be disabled: " + id);
        }
        put(type, id, plugin.withEnabled(false));
        if (id.equals(activePluginByType.get(type))) {
            activePluginByType.put(type, defaultId(type));
        }
    }

    public DecisionStrategy decisionStrategy() {
        return (DecisionStrategy) resolveActive(PluginType.DECISION_STRATEGY);
    }

    public CollisionPolicy collisionPolicy() {
        return (CollisionPolicy) resolveActive(PluginType.COLLISION_POLICY);
    }

    public OutputFormatter outputFormatter() {
        return (OutputFormatter) resolveActive(PluginType.OUTPUT_FORMATTER);
    }

    private void registerBuiltInDefault(PluginType type, String id, RuntimePlugin plugin) {
        registerPlugin(type, id, plugin, true);
        activePluginByType.put(type, id);
    }

    private void registerPlugin(PluginType type, String id, RuntimePlugin plugin, boolean builtInDefault) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Plugin id must not be blank.");
        }
        validateTypeCompatibility(type, plugin);
        if (!type.isCompatible(plugin.metadata().contractVersion())) {
            throw new IllegalArgumentException("Plugin contract mismatch for " + id
                    + ": expected " + type.contractVersion()
                    + " but was " + plugin.metadata().contractVersion());
        }

        Map<String, RegisteredPlugin<? extends RuntimePlugin>> plugins = byType(type);
        plugins.put(id, new RegisteredPlugin<>(id, plugin, true, builtInDefault));
    }

    private RuntimePlugin resolveActive(PluginType type) {
        String activeId = activePluginByType.get(type);
        RegisteredPlugin<? extends RuntimePlugin> active = getRegistered(type, activeId);
        if (!active.enabled()) {
            activeId = defaultId(type);
            active = getRegistered(type, activeId);
        }
        return active.plugin();
    }

    private RegisteredPlugin<? extends RuntimePlugin> getRegistered(PluginType type, String id) {
        Map<String, RegisteredPlugin<? extends RuntimePlugin>> plugins = byType(type);
        RegisteredPlugin<? extends RuntimePlugin> plugin = plugins.get(id);
        if (plugin == null) {
            throw new NoSuchElementException("Plugin not found: " + type + "/" + id);
        }
        return plugin;
    }

    private Map<String, RegisteredPlugin<? extends RuntimePlugin>> byType(PluginType type) {
        return pluginsByType.computeIfAbsent(type, ignored -> new LinkedHashMap<>());
    }

    private void put(PluginType type, String id, RegisteredPlugin<? extends RuntimePlugin> plugin) {
        byType(type).put(id, plugin);
    }

    private String defaultId(PluginType type) {
        return switch (type) {
            case DECISION_STRATEGY -> DEFAULT_DECISION_STRATEGY_ID;
            case COLLISION_POLICY -> DEFAULT_COLLISION_POLICY_ID;
            case OUTPUT_FORMATTER -> DEFAULT_OUTPUT_FORMATTER_ID;
        };
    }

    private void validateTypeCompatibility(PluginType type, RuntimePlugin plugin) {
        boolean compatible = switch (type) {
            case DECISION_STRATEGY -> plugin instanceof DecisionStrategy;
            case COLLISION_POLICY -> plugin instanceof CollisionPolicy;
            case OUTPUT_FORMATTER -> plugin instanceof OutputFormatter;
        };
        if (!compatible) {
            throw new IllegalArgumentException("Plugin type mismatch for " + type + ".");
        }
    }
}
