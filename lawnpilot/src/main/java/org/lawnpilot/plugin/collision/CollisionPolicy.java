package org.lawnpilot.plugin.collision;

import org.lawnpilot.model.Lawn;
import org.lawnpilot.model.Mower;
import org.lawnpilot.model.Position;
import org.lawnpilot.plugin.RuntimePlugin;

import java.util.List;

public interface CollisionPolicy extends RuntimePlugin {
    boolean canMove(Mower mower, Position nextPosition, Lawn lawn, List<Mower> allMowers);
}
