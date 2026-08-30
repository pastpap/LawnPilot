package org.lawnpilot.plugin.registry;

import org.junit.jupiter.api.Test;
import org.lawnpilot.plugin.PluginMetadata;
import org.lawnpilot.plugin.PluginType;
import org.lawnpilot.plugin.collision.CollisionPolicy;
import org.lawnpilot.plugin.decision.AutonomousDecisionStrategyStub;
import org.lawnpilot.plugin.output.JsonOutputFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginRegistryTest {

    @Test
    void defaultContractsAreEnabled() {
        PluginRegistry registry = PluginRegistry.withDefaults();

        assertEquals("instruction-decision-strategy", registry.decisionStrategy().metadata().name());
        assertEquals("boundary-collision-policy", registry.collisionPolicy().metadata().name());
        assertEquals("text-output-formatter", registry.outputFormatter().metadata().name());
    }

    @Test
    void registerEnableDisableLifecycleHooksWork() {
        PluginRegistry registry = PluginRegistry.withDefaults();

        registry.registerDecisionStrategy("autonomous-stub", new AutonomousDecisionStrategyStub());
        registry.enable(PluginType.DECISION_STRATEGY, "autonomous-stub");
        assertEquals("autonomous-decision-strategy-stub", registry.decisionStrategy().metadata().name());

        registry.disable(PluginType.DECISION_STRATEGY, "autonomous-stub");
        assertEquals("instruction-decision-strategy", registry.decisionStrategy().metadata().name());
    }

    @Test
    void contractMismatchIsRejected() {
        PluginRegistry registry = PluginRegistry.withDefaults();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> registry.registerCollisionPolicy("wrong-contract", new IncompatibleCollisionPolicy()));

        assertTrue(exception.getMessage().contains("contract mismatch"));
    }

    @Test
    void disablingBuiltInDefaultIsRejected() {
        PluginRegistry registry = PluginRegistry.withDefaults();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> registry.disable(PluginType.OUTPUT_FORMATTER, PluginRegistry.DEFAULT_OUTPUT_FORMATTER_ID));

        assertTrue(exception.getMessage().contains("cannot be disabled"));
    }

    @Test
    void outputFormatterAlternativeCanBeRegistered() {
        PluginRegistry registry = PluginRegistry.withDefaults();
        registry.registerOutputFormatter("json", new JsonOutputFormatter());
        registry.enable(PluginType.OUTPUT_FORMATTER, "json");

        assertEquals("json-output-formatter", registry.outputFormatter().metadata().name());
    }

    private static final class IncompatibleCollisionPolicy implements CollisionPolicy {
        @Override
        public boolean canMove(org.lawnpilot.model.Mower mower,
                org.lawnpilot.model.Position nextPosition,
                org.lawnpilot.model.Lawn lawn,
                java.util.List<org.lawnpilot.model.Mower> allMowers) {
            return true;
        }

        @Override
        public PluginMetadata metadata() {
            return new PluginMetadata("incompatible-collision-policy", "1.0.0", "2.0");
        }
    }
}
