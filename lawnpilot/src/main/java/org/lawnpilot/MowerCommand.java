package org.lawnpilot;

public interface MowerCommand {
    void apply(Mower mower, Lawn lawn);
}