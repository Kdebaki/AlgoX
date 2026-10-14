import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

/**
 * Automated tests for Problem 1 (no external libraries needed).
 * Run: java -cp java/Problem1-Maze/out MazeTests
 */
public final class MazeTests {
    private static int passed = 0, failed = 0;

    public static void main(String[] args) throws IOException {
        // ---- normal cases ----
        expect("open 3x3 grid", 4, "S..", "...", "..E");
        expect("S adjacent to E", 1, "SE.", "...", "...");
        expect("key then door in a corridor", 4, "Sa.AE", "#####", "#####", "#####", "#####");
        expect("detour back for key behind start", 8, "S.A.E", ".####", "a####", "#####", "#####");
        expect("two keys collected in order", 5, "SaAbB", "####E", "#####", "#####", "#####");
        expect("door route and open route tie", 8, "S.A.E", ".###.", "a....", "#####", "#####");
        expect("door of type e written DE, exit E", 4, "S,e,DE,.,E", "#,#,#,#,#", "#,#,#,#,#", "#,#,#,#,#", "#,#,#,#,#");
        expect("key opens many doors (keys never used up)", 6, "SaA.A", "####A", "####E", "#####", "#####");
        // ---- boundary cases ----
        expect("2x2 grid", 1, "S.", "E.");
        expect("start and exit in opposite corners", 6, "S...", "....", "....", "...E");
        expect("snake corridor along the border", 9,"S...", "###.", "....", "E###");
        // ---- failure cases (-1) ----
        expect("start enclosed by walls", -1, "S#E", "##.", "...");
        expect("locked door, no key anywhere", -1, "S.A.E", "#####", "#####", "#####", "#####");
        expect("key is behind its own door", -1, "SAa.E", "#####", "#####", "#####", "#####");
        expect("wrong key for the door", -1, "SbA.E", "#####", "#####", "#####", "#####");
        expect("exit walled off", -1, "S..", ".##", ".#E");
        // ---- invalid input ----
        expectError("non-square grid", "S.E", "...");
        expectError("two starts", "SSE", "...", "...");
        expectError("no exit", "S..", "...", "...");
        expectError("invalid character", "S.z", "...", "..E");
        expectError("1x1 grid", "S");
        expectErrorT("key type exceeds T", 1, "Sb.", "...", "..E");

        randomGridsAgainstReference();
        generatorProducesSolvableMazes();
        csvRoundTripAndMarking();

        System.out.println();
        System.out.println("Problem 1 tests: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ helpers

    static Maze maze(int types, String... rows) {
        int[][] g = new int[rows.length][];
        for (int r = 0; r < rows.length; r++) {
            String[] tok = rows[r].contains(",") ? rows[r].split(",") : rows[r].split("");
            g[r] = new int[tok.length];
            for (int c = 0; c < tok.length; c++) g[r][c] = Maze.parseToken(tok[c], r + 1, c + 1);
        }
        return new Maze(g, types);
    }

    static void expect(String name, int expected, String... rows) {
        try {
            Maze m = maze(0, rows);
            MazeSolver.Result r = MazeSolver.solve(m);
            check(name, r.length == expected && validPath(m, r) && reference(m) == expected,
                    "expected " + expected + ", got " + r.length + " (reference " + reference(m) + ")");
        } catch (RuntimeException e) {
            check(name, false, "threw " + e);
        }
    }

    static void expectError(String name, String... rows) { expectErrorT(name, 0, rows); }

    static void expectErrorT(String name, int types, String... rows) {
        try {
            maze(types, rows);
            check(name + " is rejected", false, "no exception thrown");
        } catch (IllegalArgumentException e) {
            check(name + " is rejected (" + e.getMessage() + ")", true, "");
        }
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) { passed++; System.out.println("PASS  " + name); }
        else { failed++; System.out.println("FAIL  " + name + " -> " + detail); }
    }

    /** Verifies the returned path is a legal walk whose length equals the reported length. */
    static boolean validPath(Maze m, MazeSolver.Result r) {
        if (!r.reachable()) return r.steps.isEmpty();
        if (r.steps.size() != r.length + 1) return false;
        MazeSolver.Step first = r.steps.get(0), last = r.steps.get(r.steps.size() - 1);
        if (first.row != m.startRow() || first.col != m.startCol() || first.mask != 0) return false;
        if (last.row != m.exitRow() || last.col != m.exitCol()) return false;
        int mask = 0;
        for (int i = 1; i < r.steps.size(); i++) {
            MazeSolver.Step a = r.steps.get(i - 1), b = r.steps.get(i);
            if (Math.abs(a.row - b.row) + Math.abs(a.col - b.col) != 1) return false;
            int code = m.cell(b.row, b.col);
            if (code == Maze.WALL) return false;
            if (Maze.isDoor(code) && (mask & (1 << Maze.typeOf(code))) == 0) return false;
            if (Maze.isKey(code)) mask |= 1 << Maze.typeOf(code);
            if (b.mask != mask) return false;
        }
        return true;
    }

    /** Independent reference: Bellman-Ford style relaxation over (cell, mask) until a fixpoint. */
    static int reference(Maze m) {
        int n = m.size(), masks = 1 << m.types();
        int inf = Integer.MAX_VALUE / 2;
        int[][][] d = new int[n][n][masks];
        for (int[][] a : d) for (int[] b : a) Arrays.fill(b, inf);
        d[m.startRow()][m.startCol()][0] = 0;
        int[] dr = {1, -1, 0, 0}, dc = {0, 0, 1, -1};
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) for (int k = 0; k < masks; k++) {
                if (d[r][c][k] >= inf) continue;
                if (r == m.exitRow() && c == m.exitCol()) continue; // exit is terminal
                for (int q = 0; q < 4; q++) {
                    int nr = r + dr[q], nc = c + dc[q];
                    if (nr < 0 || nc < 0 || nr >= n || nc >= n) continue;
                    int code = m.cell(nr, nc);
                    if (code == Maze.WALL) continue;
                    if (Maze.isDoor(code) && (k & (1 << Maze.typeOf(code))) == 0) continue;
                    int nk = Maze.isKey(code) ? k | (1 << Maze.typeOf(code)) : k;
                    if (d[r][c][k] + 1 < d[nr][nc][nk]) { d[nr][nc][nk] = d[r][c][k] + 1; changed = true; }
                }
            }
        }
        int best = inf;
        for (int k = 0; k < masks; k++) best = Math.min(best, d[m.exitRow()][m.exitCol()][k]);
        return best >= inf ? -1 : best;
    }

    static void randomGridsAgainstReference() {
        Random rnd = new Random(2026);
        int ok = 0, reachable = 0, trials = 400;
        String detail = "";
        for (int t = 0; t < trials; t++) {
            int n = 3 + rnd.nextInt(5), types = 1 + rnd.nextInt(3);
            int[][] g = new int[n][n];
            for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) {
                double x = rnd.nextDouble();
                g[r][c] = x < 0.25 ? Maze.WALL : x < 0.33 ? Maze.KEY_BASE + rnd.nextInt(types)
                        : x < 0.41 ? Maze.DOOR_BASE + rnd.nextInt(types) : Maze.OPEN;
            }
            int s = rnd.nextInt(n * n), e;
            do { e = rnd.nextInt(n * n); } while (e == s);
            g[s / n][s % n] = Maze.START;
            g[e / n][e % n] = Maze.EXIT;
            Maze m = new Maze(g, types);
            MazeSolver.Result r = MazeSolver.solve(m);
            if (r.length == reference(m) && validPath(m, r)) ok++;
            else if (detail.isEmpty()) detail = "mismatch on\n" + m;
            if (r.reachable()) reachable++;
        }
        check("BFS equals reference on " + trials + " random grids (" + reachable + " solvable, "
                + (trials - reachable) + " unsolvable)", ok == trials, detail);
    }

    static void generatorProducesSolvableMazes() {
        int ok = 0, total = 0;
        String detail = "";
        for (int n = 7; n <= 31; n += 4) {
            for (int types = 1; types <= 6; types++) {
                for (long seed = 1; seed <= 4; seed++) {
                    total++;
                    try {
                        Maze m = MazeGenerator.generate(n, types, seed);
                        MazeSolver.Result r = MazeSolver.solve(m);
                        boolean good = r.reachable() && validPath(m, r) && m.countKeys() == types
                                && m.countDoors() == types && m.size() == n && r.length == reference(m)
                                && MazeGenerator.generate(n, types, seed).equals(m); // reproducible
                        if (good) ok++; else if (detail.isEmpty()) detail = "bad maze n=" + n + " T=" + types + "\n" + m;
                    } catch (RuntimeException ex) {
                        if (detail.isEmpty()) detail = "n=" + n + " T=" + types + ": " + ex.getMessage();
                    }
                }
            }
        }
        check("random generator: " + total + " mazes solvable, exactly T keys and T doors, reproducible", ok == total, detail);
        try {
            MazeGenerator.generate(3, 1, 1);
            check("generator rejects N < 5", false, "no exception");
        } catch (IllegalArgumentException ex) {
            check("generator rejects N < 5", true, "");
        }
    }

    static void csvRoundTripAndMarking() throws IOException {
        Path dir = Files.createTempDirectory("maze-test");
        Maze m = MazeGenerator.generate(15, 5, 99);
        Path f = dir.resolve("maze.csv");
        MazeCsv.writeMaze(m, f);
        Maze back = MazeCsv.read(f, 5);
        check("CSV round trip preserves the maze (incl. door e as DE)", back.equals(m), "differs");

        Maze simple = maze(0, "S.A.E", ".####", "a####", "#####", "#####");
        MazeSolver.Result r = MazeSolver.solve(simple);
        String[][] marked = MazeCsv.markedGrid(simple, r);
        boolean good = marked[0][0].equals("S") && marked[0][4].equals("E") && marked[0][2].equals("x:A")
                && marked[2][0].equals("x:a") && marked[1][0].equals("x") && marked[0][1].equals("x")
                && marked[0][3].equals("x") && marked[1][1].equals("#");
        check("path CSV marks path cells x and keeps key/door identity (x:a, x:A)", good, Arrays.deepToString(marked));

        Path p = dir.resolve("path.csv");
        MazeCsv.writePath(simple, r, p);
        String text = String.join("\n", Files.readAllLines(p));
        check("path CSV written to disk", text.startsWith("S,x,x:A,x,E"), text);
        Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(q -> q.toFile().delete());
    }
}
