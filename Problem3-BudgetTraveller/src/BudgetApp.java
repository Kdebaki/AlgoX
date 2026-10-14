import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Problem 3 - Budget Traveller with Coupons (command-line entry point).
 *
 * Run from the project root, e.g.
 *   java -cp java/Problem3-BudgetTraveller/out BudgetApp                                   (interactive menu)
 *   java -cp java/Problem3-BudgetTraveller/out BudgetApp --input input/p3_sample.txt --gui  (Option 1 file)
 *   java -cp java/Problem3-BudgetTraveller/out BudgetApp --stdin < input/p3_sample.txt      (Option 1 stdin)
 *   java -cp java/Problem3-BudgetTraveller/out BudgetApp --csv input/p3_graph.csv --k 2 --c 1 (Option 2)
 *   java -cp java/Problem3-BudgetTraveller/out BudgetApp --random 10 25 --k 3 --c 2 --seed 5 (Option 3)
 *   java -cp java/Problem3-BudgetTraveller/out BudgetApp --benchmark
 */
public final class BudgetApp {
    private BudgetApp() { }

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
        System.out.println("Option 1: type the instance (N M / K C / M lines u v p)");
        System.out.println("Option 2: enter K C, then read the graph from a CSV file");
        System.out.println("Option 3: enter K C, then generate a random valid graph");
        System.out.print("Choose option (1/2/3): ");
        int opt = in.nextInt();
        List<String> a = new ArrayList<>();
        if (opt == 1) {
            System.out.println("Enter N M, then K C, then M lines 'u v p':");
            int n = in.nextInt(), m = in.nextInt(), k = in.nextInt(), c = in.nextInt();
            StringBuilder sb = new StringBuilder(n + " " + m + "\n" + k + " " + c + "\n");
            for (int i = 0; i < m; i++) sb.append(in.nextLong()).append(' ').append(in.nextLong()).append(' ').append(in.nextLong()).append('\n');
            GraphIO.Instance inst = GraphIO.parseText(sb.toString());
            Path tmp = Paths.get("output_csv/p3_interactive_input.txt");
            GraphIO.writeText(inst.graph, inst.k, inst.c, tmp);
            a.add("--input"); a.add(tmp.toString());
        } else if (opt == 2 || opt == 3) {
            System.out.print("Enter K C (maximum stops, coupons): ");
            String k = String.valueOf(in.nextInt()), c = String.valueOf(in.nextInt());
            if (opt == 2) {
                System.out.print("CSV file path (e.g. input/p3_graph.csv): ");
                a.add("--csv"); a.add(in.next());
            } else {
                System.out.print("Number of cities N and flights M: ");
                a.add("--random"); a.add(String.valueOf(in.nextInt())); a.add(String.valueOf(in.nextInt()));
                System.out.print("Random seed: ");
                a.add("--seed"); a.add(String.valueOf(in.nextLong()));
            }
            a.add("--k"); a.add(k); a.add("--c"); a.add(c);
        } else {
            throw new IllegalArgumentException("Option must be 1, 2 or 3");
        }
        System.out.print("Open the animated visualisation? (y/n): ");
        if (in.next().toLowerCase().startsWith("y")) a.add("--gui");
        run(a.toArray(new String[0]));
    }

    private static void run(String[] args) throws IOException {
        String input = null, csv = null, out = null, snapshot = null, saveGraph = null;
        boolean stdin = false, gui = false, bench = false;
        int randN = -1, randM = -1, k = -1, c = -1, csvN = 0;
        long seed = 42, maxPrice = 500;
        double snapAt = -1;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input": input = need(args, ++i); break;
                case "--stdin": stdin = true; break;
                case "--csv": csv = need(args, ++i); break;
                case "--n": csvN = Integer.parseInt(need(args, ++i)); break;
                case "--random": randN = Integer.parseInt(need(args, ++i)); randM = Integer.parseInt(need(args, ++i)); break;
                case "--k": k = Integer.parseInt(need(args, ++i)); break;
                case "--c": c = Integer.parseInt(need(args, ++i)); break;
                case "--seed": seed = Long.parseLong(need(args, ++i)); break;
                case "--max-price": maxPrice = Long.parseLong(need(args, ++i)); break;
                case "--out": out = need(args, ++i); break;
                case "--save-graph": saveGraph = need(args, ++i); break;
                case "--snapshot": snapshot = need(args, ++i); break;
                case "--snapshot-at": snapAt = Double.parseDouble(need(args, ++i)); break;
                case "--gui": gui = true; break;
                case "--benchmark": bench = true; break;
                case "--help": usage(); return;
                default: throw new IllegalArgumentException("Unknown argument: " + args[i] + " (use --help)");
            }
        }
        if (bench) { benchmark(); return; }
        int modes = (input != null ? 1 : 0) + (stdin ? 1 : 0) + (csv != null ? 1 : 0) + (randN >= 0 ? 1 : 0);
        if (modes != 1) throw new IllegalArgumentException("Give exactly one of --input, --stdin, --csv or --random (use --help)");

        FlightGraph g;
        String name;
        if (input != null || stdin) {
            GraphIO.Instance inst = stdin ? GraphIO.parseText(readAll(System.in)) : GraphIO.readText(Paths.get(input));
            g = inst.graph; k = inst.k; c = inst.c;
            name = stdin ? "p3_stdin" : Paths.get(input).getFileName().toString().replaceFirst("\\.[^.]*$", "");
        } else if (csv != null) {
            if (k < 0 || c < 0) { // Option 2: first line "K C" on standard input
                System.out.print("Enter K C: ");
                Scanner sc = new Scanner(System.in).useDelimiter("[\\s\\x{FEFF}]+"); // BOM-safe
                k = sc.nextInt(); c = sc.nextInt();
            }
            g = GraphIO.readCsv(Paths.get(csv), csvN);
            name = Paths.get(csv).getFileName().toString().replaceFirst("\\.csv$", "");
        } else {
            if (k < 0 || c < 0) throw new IllegalArgumentException("--random needs --k K and --c C");
            g = FlightGraph.random(randN, randM, k, maxPrice, seed);
            name = "p3_random_n" + randN + "_m" + randM + "_k" + k + "_c" + c + "_seed" + seed;
            Path gf = Paths.get(saveGraph != null ? saveGraph : "output_csv/" + name + "_graph.csv");
            GraphIO.writeGraphCsv(g, gf);
            System.out.println("Generated graph saved to " + gf);
        }

        System.out.println("Cities N = " + g.cities() + ", flights M = " + g.flights().size() + ", K = " + k + ", C = " + c);
        BudgetSolver.Result dp = BudgetSolver.solveDP(g, k, c);
        String err = BudgetSolver.verify(g, k, c, dp);
        if (err != null) throw new IllegalStateException("Internal check failed: " + err);

        System.out.println("=== Output 1: minimum cost ===");
        System.out.println(dp.cost);
        if (dp.feasible()) {
            System.out.println("Route: " + dp.routeString());
            System.out.println("Flights used = " + dp.flightsUsed() + " (limit K+1 = " + (k + 1) + "), stops = " + dp.stops()
                    + " (limit K = " + k + "), coupons used = " + dp.couponsUsed() + " (limit C = " + c + ")");
            System.out.printf("%-5s %-10s %-10s %-8s %-10s %-12s%n", "Leg", "Flight", "Original", "Coupon", "Paid", "Running");
            for (int i = 0; i < dp.legs.size(); i++) {
                BudgetSolver.Leg l = dp.legs.get(i);
                System.out.printf("%-5d %-10s %-10d %-8s %-10d %-12d%n", i + 1, l.flight.from + "->" + l.flight.to,
                        l.flight.price, l.coupon ? "yes" : "", l.paid, l.runningCost);
            }
        } else {
            System.out.println("No route from city 1 to city " + g.cities() + " with at most " + k + " stops.");
        }

        Path outFile = Paths.get(out != null ? out : "output_csv/" + name + "_result.csv");
        Path routeFile = Paths.get(outFile.toString().replaceFirst("\\.csv$", "") + "_route.csv");
        GraphIO.writeResultCsv(g, dp, outFile);
        GraphIO.writeRouteCsv(dp, routeFile);
        System.out.println("=== Output 2: CSV ===");
        System.out.println("Graph with path flights marked x and coupon flights marked: " + outFile);
        System.out.println("Route legs with running cost: " + routeFile);

        // Comparative analysis: the same answer from an independent algorithm.
        BudgetSolver.Result dj = BudgetSolver.solveDijkstra(g, k, c);
        System.out.println("=== Comparison ===");
        System.out.printf("%-20s cost=%-8d time=%.3f ms  work=%d%n", dp.method, dp.cost, dp.millis, dp.work);
        System.out.printf("%-20s cost=%-8d time=%.3f ms  work=%d%n", dj.method, dj.cost, dj.millis, dj.work);
        if (smallEnoughForBruteForce(g, k)) {
            BudgetSolver.Result bf = BudgetSolver.solveBruteForce(g, k, c);
            System.out.printf("%-20s cost=%-8d time=%.3f ms  walks=%d%n", bf.method, bf.cost, bf.millis, bf.work);
            System.out.println("All methods agree: " + (dp.cost == dj.cost && dp.cost == bf.cost ? "YES" : "NO"));
        } else {
            System.out.println("DP and Dijkstra agree: " + (dp.cost == dj.cost ? "YES" : "NO") + " (brute force skipped: too large)");
        }

        if (snapshot != null) {
            double pos = snapAt >= 0 ? Math.min(snapAt, dp.legs.size()) : dp.legs.size();
            BudgetVisualizer.saveSnapshot(g, dp, k, c, name, pos, new File(snapshot), 1180, 640);
            System.out.println("Snapshot saved to " + snapshot);
        }
        if (gui) BudgetVisualizer.show(g, dp, k, c, name);
    }

    static boolean smallEnoughForBruteForce(FlightGraph g, int k) {
        int[] outDeg = new int[g.cities() + 1];
        int max = 1;
        for (FlightGraph.Flight f : g.flights()) max = Math.max(max, ++outDeg[f.from]);
        return (k + 1) * Math.log(max) <= Math.log(2e6);
    }

    /** DP vs Dijkstra running time as N grows (M = 5N, K = 10, C = 3). */
    private static void benchmark() throws IOException {
        int[] sizes = {50, 100, 200, 400, 800, 1600, 3200};
        int k = 10, c = 3;
        List<String> lines = new ArrayList<>();
        lines.add("N,M,K,C,cost,dp_ms,dijkstra_ms,dp_relaxations,dijkstra_settled,agree");
        System.out.printf("%6s %7s %8s %10s %13s %10s%n", "N", "M", "cost", "dp_ms", "dijkstra_ms", "agree");
        for (int n : sizes) {
            FlightGraph g = FlightGraph.random(n, 5 * n, k, 1000, n);
            for (int w = 0; w < 3; w++) { BudgetSolver.solveDP(g, k, c); BudgetSolver.solveDijkstra(g, k, c); }
            int reps = 5;
            double dpMs = 0, djMs = 0;
            BudgetSolver.Result a = null, b = null;
            for (int i = 0; i < reps; i++) {
                a = BudgetSolver.solveDP(g, k, c); dpMs += a.millis;
                b = BudgetSolver.solveDijkstra(g, k, c); djMs += b.millis;
            }
            dpMs /= reps; djMs /= reps;
            boolean agree = a.cost == b.cost;
            System.out.printf("%6d %7d %8d %10.3f %13.3f %10s%n", n, 5 * n, a.cost, dpMs, djMs, agree);
            lines.add(n + "," + 5 * n + "," + k + "," + c + "," + a.cost + "," + String.format("%.3f", dpMs) + ","
                    + String.format("%.3f", djMs) + "," + a.work + "," + b.work + "," + agree);
        }
        Path file = Paths.get("reports/benchmarks/p3_dp_vs_dijkstra.csv");
        GraphIO.write(file, lines);
        System.out.println("Benchmark written to " + file);
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) bos.write(buf, 0, r);
        return bos.toString(StandardCharsets.UTF_8.name());
    }

    private static String need(String[] args, int i) {
        if (i >= args.length) throw new IllegalArgumentException("Missing value after " + args[i - 1]);
        return args[i];
    }

    private static void usage() {
        String p = "  java -cp java/Problem3-BudgetTraveller/out BudgetApp ";
        System.out.println("Usage (run from the project root):");
        System.out.println(p + "                                  interactive menu (options 1/2/3)");
        System.out.println(p + "--input <file.txt>                Option 1 from a file (N M / K C / u v p)");
        System.out.println(p + "--stdin                           Option 1 from standard input");
        System.out.println(p + "--csv <graph.csv> [--k K --c C] [--n N]   Option 2 (K C read from stdin if not given)");
        System.out.println(p + "--random <N> <M> --k K --c C [--seed S] [--max-price P] [--save-graph <csv>]   Option 3");
        System.out.println("  common: [--out <result.csv>] [--gui] [--snapshot <png> [--snapshot-at <leg>]]");
        System.out.println(p + "--benchmark                       DP vs Dijkstra timing");
    }
}
