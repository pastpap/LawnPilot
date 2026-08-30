package org.lawnpilot;

import lombok.Getter;

@Getter
public class Mower {

    private int x;
    private int y;
    private Direction direction;

    public Mower(int x, int y, Direction direction) {
        this.x = x;
        this.y = y;
        this.direction = direction;
    }

    public void execute(String instructions, Lawn lawn) {
        for (char c : instructions.toCharArray()) {
            if (c == 'L')
                turnLeft();
            else if (c == 'R')
                turnRight();
            else if (c == 'F')
                move(lawn);
        }
    }

    private void turnLeft() {
        switch (direction) {
            case N -> direction = Direction.W;
            case W -> direction = Direction.S;
            case E -> direction = Direction.N;
            case S -> direction = Direction.E;
        }
    }

    private void turnRight() {
        switch (direction) {
            case E -> direction = Direction.S;
            case N -> direction = Direction.E;
            case S -> direction = Direction.W;
            case W -> direction = Direction.N;
        }
    }

    private void move(Lawn lawn) {
        int nextX = x;
        int nextY = y;

        switch (direction) {
            case N -> nextY++;
            case W -> nextX--;
            case E -> nextX++;
            case S -> nextY--;
        }

        if (lawn.isInside(nextX, nextY)) {
            x = nextX;
            y = nextY;
        }
    }

    @Override
    public String toString() {
        return x + " " + y + " " + direction;
    }
}
