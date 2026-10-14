import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV input/output for the maze.
 *
 * Input : one grid row per line, cells separated by commas (e.g. "S,.,#,a").
 *         A line without commas is read character by character (e.g. "S.#a").
 * Output: the same grid with the shortest path marked:
 *           x      open cell on the path
 *           x:a    key 'a' picked up on the path
 *           x:A    door 'A' opened on the path (door e -> x:DE)
 *           S, E   start and exit stay as they are
 *         Keys/doors not on the path keep their letter, so every key and door stays identifiable.
 */
public final class MazeCsv {
    private MazeCsv() { }

    public static Maze read(Path file, int types) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<int[]> rows = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.replace("﻿", "").trim();
            if (line.isEmpty()) continue;
            String[] tokens;
            if (line.contains(",")) {
                tokens = line.split(",", -1);
            } else {
                tokens = new String[line.length()];
                for (int i = 0; i < line.length(); i++) tokens[i] = String.valueOf(line.charAt(i));
            }
            int[] row = new int[tokens.length];
            for (int c = 0; c < tokens.length; c++) {
                if (tokens[c].trim().isEmpty()) {
                    throw new IllegalArgumentException("Empty cell at row " + (rows.size() + 1) + ", col " + (c + 1));
                }
                row[c] = Maze.parseToken(tokens[c], rows.size() + 1, c + 1);
            }
            rows.add(row);
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("CSV file " + file + " contains no grid");
        return new Maze(rows.toArray(new int[0][]), types);
    }

    public static void writeMaze(Maze m, Path file) throws IOException {
        int n = m.size();
        String[][] out = new String[n][n];
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) out[r][c] = Maze.token(m.cell(r, c));
        write(out, file);
    }

    /** Grid with the shortest path marked by 'x' (see class comment). */
    public static String[][] markedGrid(Maze m, MazeSolver.Result res) {
        int n = m.size();
        String[][] out = new String[n][n];
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) out[r][c] = Maze.token(m.cell(r, c));
        for (MazeSolver.Step s : res.steps) {
            int code = s.code;
            if (code == Maze.OPEN) out[s.row][s.col] = "x";
            else if (Maze.isKey(code) || Maze.isDoor(code)) out[s.row][s.col] = "x:" + Maze.token(code);
        }
        return out;
    }

    public static void writePath(Maze m, MazeSolver.Result res, Path file) throws IOException {
        write(markedGrid(m, res), file);
    }

    /** Step-by-step path: step,row,col,cell,event,inventory (rows/cols are 1-based). */
    public static void writeSteps(MazeSolver.Result res, Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("step,row,col,cell,event,inventory");
        for (int i = 0; i < res.steps.size(); i++) {
            MazeSolver.Step s = res.steps.get(i);
            lines.add(i + "," + (s.row + 1) + "," + (s.col + 1) + "," + Maze.token(s.code) + ","
                    + s.event + ",\"" + MazeSolver.inventory(s.mask) + "\"");
        }
        ensureParent(file);
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static void write(String[][] grid, Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String[] row : grid) lines.add(String.join(",", row));
        ensureParent(file);
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    static void ensureParent(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
    }
}
