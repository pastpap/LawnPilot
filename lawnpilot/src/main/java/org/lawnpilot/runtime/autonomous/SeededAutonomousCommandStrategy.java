package org.lawnpilot.runtime.autonomous;

import org.lawnpilot.command.InstructionCommand;
import org.lawnpilot.command.MowerCommand;

public final class SeededAutonomousCommandStrategy implements AutonomousCommandStrategy {

    @Override
    public MowerCommand decide(AutonomousDecisionContext context) {
        AutonomousSensorSnapshot sensor = context.getSensorSnapshot();
        if (sensor.isBoundaryAhead() || sensor.isMowerAhead()) {
            return context.getRandom().nextBoolean() ? InstructionCommand.L : InstructionCommand.R;
        }

        int roll = context.getRandom().nextInt(10);
        if (roll < 6) {
            return InstructionCommand.F;
        }
        if (roll < 8) {
            return InstructionCommand.L;
        }
        return InstructionCommand.R;
    }
}