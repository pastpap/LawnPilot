package org.lawnpilot;

final class ListLineTokenizer {

    String[] tokenize(String line) {
        if (line == null || line.isBlank()) {
            return new String[0];
        }
        return line.trim().split("\\s+");
    }
}