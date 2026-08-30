package org.lawnpilot.command;

import org.lawnpilot.model.Lawn;
import org.lawnpilot.model.Mower;

public interface MowerCommand {
    void apply(Mower mower, Lawn lawn);
}