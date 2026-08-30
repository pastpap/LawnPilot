package org.lawnpilot;

import lombok.Getter;

import java.util.List;

@Getter
public class Mower {

    private Position position;
    private Direction direction;

    public Mower(int x, int y, Direction direction) {
        this(new Position(x, y), direction);
    }

    public Mower(Position position, Direction direction) {
        this.position = position;
        this.direction = direction;
    }

    public void execute(String instructions, Lawn lawn) {
        for (char c : instructions.toCharArray()) {
            InstructionCommand.tryParse(c).ifPresent(command -> command.apply(this, lawn));
        }
    }

    public void execute(List<MowerCommand> commands, Lawn lawn) {
        for (MowerCommand command : commands) {
            command.apply(this, lawn);
        }
    }

    void turnLeft() {
        direction = direction.turnLeft();
    }

    void turnRight() {
        direction = direction.turnRight();
    }

    void moveForward(Lawn lawn) {
        Position nextPosition = direction.moveForward(position);
        if (lawn.isInside(nextPosition)) {
            position = nextPosition;
        }
    }

    public int getX() {
        return position.getX();
    }

    public int getY() {
        return position.getY();
    }

    @Override
    public String toString() {
        return getX() + " " + getY() + " " + direction;
    }
}
