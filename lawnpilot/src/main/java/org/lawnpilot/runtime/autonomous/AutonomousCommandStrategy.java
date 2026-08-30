package org.lawnpilot.runtime.autonomous;

import org.lawnpilot.command.MowerCommand;

public interface AutonomousCommandStrategy {
    MowerCommand decide(AutonomousDecisionContext context);
}