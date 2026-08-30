package org.lawnpilot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            List<String> data = readInput(args);

            InputParser ip = new InputParser();
            Lawn lawn = ip.parseLawn(data);
            List<InputParser.MowerData> mowers = ip.parseMowers(data.subList(1, data.size()), lawn);

            for (InputParser.MowerData mowerData : mowers) {
                mowerData.getMower().execute(mowerData.getInstructions(), lawn);
                System.out.println(mowerData.getMower());
            }
        } catch (InvalidInputException | IOException ex) {
            System.err.println("Input error: " + ex.getMessage());
            System.exit(1);
        }
    }

    private static List<String> readInput(String[] args) throws IOException {
        if (args.length > 0) {
            return Files.readAllLines(Path.of(args[0]));
        }

        if (System.in.available() > 0) {
            return readStdinLines();
        }

        return List.of("5 5", "1 2 N", "LFLFLFLFF", "3 3 E", "FFRFFRFRRF");
    }

    private static List<String> readStdinLines() throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line.trim());
                }
            }
        }
        return lines;
    }
}