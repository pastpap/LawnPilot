package org.lawnpilot.command;

import org.lawnpilot.model.Lawn;
import org.lawnpilot.model.Mower;

import java.util.Optional;

public enum InstructionCommand implements MowerCommand {
    L {
        @Override
        public void apply(Mower mower, Lawn lawn) {
            mower.turnLeft();
        }
    },
    R {
        @Override
        public void apply(Mower mower, Lawn lawn) {
            mower.turnRight();
        }
    },
    F {
        @Override
        public void apply(Mower mower, Lawn lawn) {
            mower.moveForward(lawn);
        }
    };

    public static Optional<InstructionCommand> tryParse(char token) {
        if (token == 'L') {
            return Optional.of(L);
        }
        if (token == 'R') {
            return Optional.of(R);
        }
        if (token == 'F') {
            return Optional.of(F);
        }
        return Optional.empty();
    }
}