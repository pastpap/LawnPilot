package org.lawnpilot.plugin.collision;

import org.lawnpilot.model.Lawn;
import org.lawnpilot.model.Mower;
import org.lawnpilot.model.Position;
import org.lawnpilot.plugin.PluginMetadata;

import java.util.List;

public final class BoundaryCollisionPolicy implements CollisionPolicy {

    private static final PluginMetadata METADATA = new PluginMetadata(
            "boundary-collision-policy",
            "1.0.0",
            "1.0");

    @Override
    public boolean canMove(Mower mower, Position nextPosition, Lawn lawn, List<Mower> allMowers) {
        return lawn.isInside(nextPosition);
    }

    @Override
    public PluginMetadata metadata() {
        return METADATA;
    }
}
