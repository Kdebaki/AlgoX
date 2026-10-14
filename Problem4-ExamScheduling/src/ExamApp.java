import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Problem 4 - Exam Timetable Scheduling (command-line entry point).
 *
 * Run from the project root, e.g.
 *   java -cp java/Problem4-ExamScheduling/out ExamApp                                 (interactive menu)
 *   java -cp java/Problem4-ExamScheduling/out ExamApp --input input/p4_sample.txt --gui
 *   java -cp java/Problem4-ExamScheduling/out ExamApp --stdin < input/p4_sample.txt
 *   java -cp java/Problem4-ExamScheduling/out ExamApp --random 20 4 --density 0.3 --seed 1
 *   java -cp java/Problem4-ExamScheduling/out ExamApp --compare --density 0.3 --r 5
 */
public final class ExamApp {
    private ExamApp() { }

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
        System.out.println("Option 1: enter N M R, then M lines 'u v' (or give a file)");
        System.out.println("Option 2: enter N R and an edge density -> random conflict graph");
        System.out.print("Choose option (1/2): ");
        int opt = in.nextInt();
        List<String> a = new ArrayList<>();
        if (opt == 1) {
            System.out.print("Type 'file' to read a file, or 'type' to enter the data now: ");
            if (in.next().toLowerCase().startsWith("f")) {
                System.out.print("File path (e.g. input/p4_sample.txt): ");
                a.add("--input"); a.add(in.next());
            } else {
                System.out.println("Enter N M R, then M lines 'u v':");
                int n = in.nextInt(), m = in.nextInt(), r = in.nextInt();
                StringBuilder sb = new StringBuilder(n + " " + m + " " + r + "\n");
                for (int i = 0; i < m; i++) sb.append(in.nextInt()).append(' ').append(in.nextInt()).append('\n');
                TimetableIO.Instance inst = TimetableIO.parseText(sb.toString());
                Path tmp = Paths.get("output_csv/p4_interactive_input.txt");
                TimetableIO.writeText(inst.graph, inst.r, tmp);
                a.add("--input"); a.add(tmp.toString());
            }
        } else if (opt == 2) {
            System.out.print("Enter N R: ");
            a.add("--random"); a.add(String.valueOf(in.nextInt())); a.add(String.valueOf(in.nextInt()));
            System.out.print("Edge density (0..1, e.g. 0.3): ");
            a.add("--density"); a.add(in.next());
            System.out.print("Random seed: ");
            a.add("--seed"); a.add(String.valueOf(in.nextLong()));
        } else {
            throw new IllegalArgumentException("Option must be 1 or 2");
        }
        System.out.print("Open the visualisation? (y/n): ");
        if (in.next().toLowerCase().startsWith("y")) a.add("--gui");
        run(a.toArray(new String[0]));
    }

    private static void run(String[] args) throws IOException {
        String input = null, out = null, snapshot = null;
        boolean stdin = false, gui = false, compare = false;
        int randN = -1, randR = -1, maxSlots = -1, compareR = 5;
        double density = 0.3;
        long seed = 1, timeLimit = 10_000;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input": input = need(args, ++i); break;
                case "--stdin": stdin = true; break;
                case "--random": randN = Integer.parseInt(need(args, ++i)); randR = Integer.parseInt(need(args, ++i)); break;
                case "--density": density = Double.parseDouble(need(args, ++i)); break;
                case "--seed": seed = Long.parseLong(need(args, ++i)); break;
                case "--max-slots": maxSlots = Integer.parseInt(need(args, ++i)); break;
                case "--time-limit": timeLimit = Long.parseLong(need(args, ++i)); break;
                case "--r": compareR = Integer.parseInt(need(args, ++i)); break;
                case "--out": out = need(args, ++i); break;
                case "--snapshot": snapshot = need(args, ++i); break;
                case "--gui": gui = true; break;
                case "--compare": compare = true; break;
                case "--help": usage(); return;
                default: throw new IllegalArgumentException("Unknown argument: " + args[i] + " (use --help)");
            }
        }
        if (compare) { compare(density, compareR, timeLimit); return; }
        int modes = (input != null ? 1 : 0) + (stdin ? 1 : 0) + (randN >= 0 ? 1 : 0);
        if (modes != 1) throw new IllegalArgumentException("Give exactly one of --input, --stdin or --random (use --help)");

        ConflictGraph g;
        int r;
        String name;
        if (randN >= 0) {
            g = ConflictGraph.random(randN, density, seed);
            r = randR;
            name = String.format(Locale.US, "p4_random_n%d_r%d_p%.2f_seed%d", randN, randR, density, seed);
            Path f = Paths.get("output_csv/" + name + "_input.txt");
            TimetableIO.writeText(g, r, f);
            System.out.println("Generated conflict graph saved to " + f);
        } else {
            TimetableIO.Instance inst = stdin ? TimetableIO.parseText(readAll(System.in)) : TimetableIO.readText(Paths.get(input));
            g = inst.graph;
            r = inst.r;
            name = stdin ? "p4_stdin" : Paths.get(input).getFileName().toString().replaceFirst("\\.[^.]*$", "");
        }
        System.out.println("Courses N = " + g.size() + ", conflicts M = " + g.edgeCount() + ", R = " + r + " exams per slot"
                + (maxSlots > 0 ? ", available slots = " + maxSlots : ""));

        Schedule ds = Schedulers.dsatur(g, r), wp = Schedulers.welshPowell(g, r), ex = Schedulers.exact(g, r, timeLimit);
        List<Schedule> all = Arrays.asList(ds, wp, ex);
        for (Schedule s : all) {
            String err = s.validate(g, r);
            if (err != null) throw new IllegalStateException(s.algorithm + " produced an invalid timetable: " + err);
        }
        int lb = (g.size() > 0 && r >= 1) ? Schedulers.lowerBound(g, r) : 0;
        Schedulers.markHeuristicOptimality(ds, ex, lb);
        Schedulers.markHeuristicOptimality(wp, ex, lb);

        System.out.println("=== Output 1: minimum number of slots (each algorithm) ===");
        for (Schedule s : all) {
            if (!s.feasible()) System.out.printf("%-13s: INFEASIBLE - %s%n", s.algorithm, s.infeasibleReason);
            else System.out.printf("%-13s: %d slots  [%s]  time %.3f ms, %d steps/nodes%n", s.algorithm, s.slots, s.proof, s.millis, s.nodes);
        }
        if (ex.feasible()) System.out.println("Lower bound (max(greedy clique, ceil(N/R))): " + lb);

        Schedule best = ex.feasible() ? ex : ds;
        if (best.feasible()) {
            System.out.println("=== Output 2: slot-wise timetable (" + best.algorithm + ") ===");
            List<List<Integer>> lists = best.slotLists();
            for (int i = 0; i < lists.size(); i++) {
                System.out.println("Slot " + (i + 1) + ": " + lists.get(i).stream().map(String::valueOf).collect(Collectors.joining(", ")));
            }
            if (!ds.feasible() || ds.slots != best.slots) {
                System.out.println("DSatur timetable:");
                List<List<Integer>> dl = ds.slotLists();
                for (int i = 0; i < dl.size(); i++) System.out.println("  Slot " + (i + 1) + ": " + dl.get(i).stream().map(String::valueOf).collect(Collectors.joining(", ")));
            }
        }
        if (maxSlots > 0 && best.feasible()) {
            System.out.println("=== Feasibility with " + maxSlots + " available slots ===");
            if (best.slots <= maxSlots) System.out.println("FEASIBLE: timetable uses " + best.slots + " <= " + maxSlots + " slots");
            else if (best.provenOptimal) System.out.println("INFEASIBLE (proven): at least " + best.slots + " slots are required");
            else if (lb > maxSlots) System.out.println("INFEASIBLE (proven by lower bound): at least " + lb + " slots are required");
            else System.out.println("UNKNOWN: best found needs " + best.slots + " slots, lower bound " + lb + " (search hit the time limit)");
        }

        Path outFile = Paths.get(out != null ? out : "output_csv/" + name + "_timetable.csv");
        TimetableIO.writeTimetable(best, r, outFile);
        String base = outFile.toString().replaceFirst("\\.csv$", "");
        TimetableIO.writeTimetable(ds, r, Paths.get(base + "_dsatur.csv"));
        TimetableIO.writeTimetable(wp, r, Paths.get(base + "_welsh_powell.csv"));
        if (best.feasible()) TimetableIO.writeAssignment(best, Paths.get(base + "_assignment.csv"));
        System.out.println("=== Output 3: CSV (rows = slots, columns = halls) ===");
        System.out.println(outFile + "   (also _dsatur.csv, _welsh_powell.csv, _assignment.csv)");

        if (snapshot != null) {
            String p = snapshot.replaceFirst("\\.png$", "");
            ExamVisualizer.saveScene(g, ds, r, lb, name, g.size(), new File(p + "_dsatur.png"));
            ExamVisualizer.saveScene(g, ex, r, lb, name, g.size(), new File(p + "_exact.png"));
            ExamVisualizer.saveScene(g, ex, r, lb, name, Math.max(1, g.size() / 2), new File(p + "_exact_animation_midway.png"));
            System.out.println("Snapshots saved to " + p + "_dsatur.png, " + p + "_exact.png, " + p + "_exact_animation_midway.png");
        }
        if (gui) ExamVisualizer.show(g, all, r, lb, name, Math.max(0.05, density), null);
    }

    private static void compare(double density, int r, long timeLimit) throws IOException {
        int[] sizes = {5, 10, 15, 20, 25, 30, 35, 40, 45, 50};
        System.out.printf("Comparison on random G(N, p = %.2f), R = %d, 3 seeds per N, exact time limit %d ms%n", density, r, timeLimit);
        List<ExamComparison.Row> rows = ExamComparison.run(sizes, density, r, 3, timeLimit);
        ExamComparison.print(rows);
        String tag = String.format(Locale.US, "p%.2f_r%d", density, r);
        Path csv = Paths.get("reports/benchmarks/p4_comparison_" + tag + ".csv");
        ExamComparison.writeCsv(rows, density, r, csv);
        File png = new File("reports/screenshots/p4_comparison_" + tag + ".png");
        ExamVisualizer.saveComparison(rows, String.format(Locale.US, "density p = %.2f, R = %d", density, r), png);
        System.out.println("Written " + csv + " and " + png);
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toString(StandardCharsets.UTF_8.name());
    }

    private static String need(String[] args, int i) {
        if (i >= args.length) throw new IllegalArgumentException("Missing value after " + args[i - 1]);
        return args[i];
    }

    private static void usage() {
        String p = "  java -cp java/Problem4-ExamScheduling/out ExamApp ";
        System.out.println("Usage (run from the project root):");
        System.out.println(p + "                                   interactive menu");
        System.out.println(p + "--input <file>                     Option 1 file: N M R then M lines u v");
        System.out.println(p + "--stdin                            Option 1 from standard input");
        System.out.println(p + "--random <N> <R> --density p [--seed S]   Option 2");
        System.out.println("  common: [--max-slots S] [--time-limit ms] [--out <csv>] [--snapshot <prefix.png>] [--gui]");
        System.out.println(p + "--compare [--density p] [--r R] [--time-limit ms]   slots/time vs N chart");
    }
}
