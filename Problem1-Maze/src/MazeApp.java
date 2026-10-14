import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Problem 1 - Maze with Keys and Doors (command-line entry point).
 *
 * Run from the project root, e.g.
 *   java -cp java/Problem1-Maze/out MazeApp                         (interactive: reads T, then option 1/2)
 *   java -cp java/Problem1-Maze/out MazeApp --csv input/p1_maze_sample.csv --t 3 --gui
 *   java -cp java/Problem1-Maze/out MazeApp --random 21 --t 4 --seed 7 --gui
 *   java -cp java/Problem1-Maze/out MazeApp --benchmark
 */
public final class MazeApp {
    private MazeApp() { }

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        try {
            if (args.length == 0) interactive();
            else run(args);
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void interactive() throws IOException {
        Scanner in = new Scanner(System.in).useDelimiter("[\\s\\x{FEFF}]+"); // BOM-safe when input is piped
        System.out.print("Enter T (number of distinct key/door types, 1..6): ");
        int t = readInt(in);
        if (t < 1 || t > 6) throw new IllegalArgumentException("T must be between 1 and 6");
        System.out.println("Option 1: read the N x N grid from a CSV file");
        System.out.println("Option 2: generate a random solvable N x N maze");
        System.out.print("Choose option (1/2): ");
        int opt = readInt(in);
        List<String> a = new ArrayList<>();
        if (opt == 1) {
            System.out.print("CSV file path (e.g. input/p1_maze_sample.csv): ");
            a.add("--csv"); a.add(in.next());
        } else if (opt == 2) {
            System.out.print("Grid size N (>= 5): ");
            a.add("--random"); a.add(String.valueOf(readInt(in)));
            System.out.print("Random seed (integer): ");
            a.add("--seed"); a.add(String.valueOf(readInt(in)));
        } else {
            throw new IllegalArgumentException("Option must be 1 or 2");
        }
        a.add("--t"); a.add(String.valueOf(t));
        System.out.print("Open the animated visualisation? (y/n): ");
        if (in.next().trim().toLowerCase().startsWith("y")) a.add("--gui");
        run(a.toArray(new String[0]));
    }

    private static int readInt(Scanner in) {
        if (!in.hasNextInt()) throw new IllegalArgumentException("Expected an integer");
        return in.nextInt();
    }

    private static void run(String[] args) throws IOException {
        String csv = null, out = null, snapshot = null, saveMaze = null;
        double snapshotAt = -1;
        int randomN = -1, t = 0;
        long seed = 42;
        boolean gui = false, benchmark = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--csv": csv = need(args, ++i); break;
                case "--random": randomN = Integer.parseInt(need(args, ++i)); break;
                case "--t": t = Integer.parseInt(need(args, ++i)); break;
                case "--seed": seed = Long.parseLong(need(args, ++i)); break;
                case "--out": out = need(args, ++i); break;
                case "--save-maze": saveMaze = need(args, ++i); break;
                case "--snapshot": snapshot = need(args, ++i); break;
                case "--snapshot-at": snapshotAt = Double.parseDouble(need(args, ++i)); break;
                case "--gui": gui = true; break;
                case "--benchmark": benchmark = true; break;
                case "--help": usage(); return;
                default: throw new IllegalArgumentException("Unknown argument: " + args[i] + " (use --help)");
            }
        }
        if (benchmark) { benchmark(); return; }
        if ((csv == null) == (randomN < 0)) throw new IllegalArgumentException("Give exactly one of --csv <file> or --random <N>");

        Maze maze;
        String name;
        if (csv != null) {
            maze = MazeCsv.read(Paths.get(csv), t);
            name = Paths.get(csv).getFileName().toString().replaceFirst("\\.csv$", "");
        } else {
            if (t == 0) throw new IllegalArgumentException("--random needs --t T");
            maze = MazeGenerator.generate(randomN, t, seed);
            name = "p1_random_n" + randomN + "_t" + t + "_seed" + seed;
            Path mazeFile = Paths.get(saveMaze != null ? saveMaze : "output_csv/" + name + "_maze.csv");
            MazeCsv.writeMaze(maze, mazeFile);
            System.out.println("Generated maze saved to " + mazeFile);
        }

        System.out.println("N = " + maze.size() + ", T = " + maze.types() + ", keys = " + maze.countKeys()
                + ", doors = " + maze.countDoors());
        System.out.println(maze);
        MazeSolver.Result res = MazeSolver.solve(maze);

        System.out.println("=== Output 1: shortest path length ===");
        System.out.println(res.length);
        Path outFile = Paths.get(out != null ? out : "output_csv/" + name + "_path.csv");
        Path stepsFile = Paths.get(outFile.toString().replaceFirst("\\.csv$", "") + "_steps.csv");
        MazeCsv.writePath(maze, res, outFile);
        MazeCsv.writeSteps(res, stepsFile);
        System.out.println("=== Output 2: path CSV ===");
        System.out.println("Grid with path marked (x = path, x:a = key picked up, x:A = door opened): " + outFile);
        System.out.println("Step-by-step path with key inventory: " + stepsFile);
        if (res.reachable()) {
            for (String[] row : MazeCsv.markedGrid(maze, res)) {
                StringBuilder sb = new StringBuilder();
                for (String cell : row) sb.append(String.format("%-5s", cell));
                System.out.println(sb.toString().replaceAll("\\s+$", ""));
            }
            System.out.println("Events along the path:");
            for (int i = 0; i < res.steps.size(); i++) {
                MazeSolver.Step s = res.steps.get(i);
                if (s.event.startsWith("picked") || s.event.startsWith("unlocked")) {
                    System.out.println("  step " + i + " (row " + (s.row + 1) + ", col " + (s.col + 1) + "): "
                            + s.event + "  inventory=" + MazeSolver.inventory(s.mask));
                }
            }
        } else {
            System.out.println("No path from S to E exists (all reachable states were explored).");
        }
        System.out.printf("BFS explored %d states in %.3f ms%n", res.statesExplored, res.millis);

        if (snapshot != null) {
            int[] size = MazeVisualizer.preferredSize(maze);
            double pos = snapshotAt >= 0 ? Math.min(snapshotAt, Math.max(0, res.length)) : Math.max(0, res.length);
            MazeVisualizer.saveSnapshot(maze, res, name, pos, new File(snapshot), size[0], size[1]);
            System.out.println("Snapshot saved to " + snapshot);
        }
        if (gui) MazeVisualizer.show(maze, res, name);
    }

    /** Running time of the BFS as N grows (T = 6, random solvable mazes). */
    private static void benchmark() throws IOException {
        int[] sizes = {11, 21, 41, 61, 81, 101, 151, 201};
        List<String> lines = new ArrayList<>();
        lines.add("N,T,path_length,states_explored,state_space_N2_2T,avg_ms");
        System.out.printf("%6s %3s %12s %16s %18s %10s%n", "N", "T", "path_length", "states_explored", "N^2*2^T", "avg_ms");
        for (int n : sizes) {
            Maze m = MazeGenerator.generate(n, 6, 1000 + n);
            for (int w = 0; w < 3; w++) MazeSolver.solve(m); // JIT warm-up
            int reps = 7;
            double total = 0;
            MazeSolver.Result r = null;
            for (int i = 0; i < reps; i++) { r = MazeSolver.solve(m); total += r.millis; }
            double avg = total / reps;
            long space = (long) n * n * 64;
            System.out.printf("%6d %3d %12d %16d %18d %10.3f%n", n, 6, r.length, r.statesExplored, space, avg);
            lines.add(n + ",6," + r.length + "," + r.statesExplored + "," + space + "," + String.format("%.3f", avg));
        }
        Path file = Paths.get("reports/benchmarks/p1_bfs_benchmark.csv");
        MazeCsv.ensureParent(file);
        Files.write(file, lines, StandardCharsets.UTF_8);
        System.out.println("Benchmark written to " + file);
    }

    private static String need(String[] args, int i) {
        if (i >= args.length) throw new IllegalArgumentException("Missing value after " + args[i - 1]);
        return args[i];
    }

    private static void usage() {
        System.out.println("Usage (run from the project root):");
        System.out.println("  java -cp java/Problem1-Maze/out MazeApp                       interactive mode (reads T from stdin)");
        System.out.println("  java -cp java/Problem1-Maze/out MazeApp --csv <file> [--t T] [--out <csv>] [--gui] [--snapshot <png> [--snapshot-at <step>]]");
        System.out.println("  java -cp java/Problem1-Maze/out MazeApp --random <N> --t <T> [--seed S] [--save-maze <csv>] [--out <csv>] [--gui] [--snapshot <png> [--snapshot-at <step>]]");
        System.out.println("  java -cp java/Problem1-Maze/out MazeApp --benchmark");
    }
}
