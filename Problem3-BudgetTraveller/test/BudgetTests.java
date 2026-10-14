import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/**
 * Automated tests for Problem 3 (no external libraries needed).
 * Run: java -cp java/Problem3-BudgetTraveller/out BudgetTests
 */
public final class BudgetTests {
    private static int passed = 0, failed = 0;
    private static final String SAMPLE = "4 5\n%d %d\n1 2 100\n2 4 100\n1 4 500\n1 3 50\n3 4 300\n";

    public static void main(String[] args) throws IOException {
        // ---- normal cases on the sample graph ----
        expect("sample K=1 C=1 (1->2->4, one coupon)", 150, String.format(SAMPLE, 1, 1));
        expect("sample K=1 C=2 (both flights halved)", 100, String.format(SAMPLE, 1, 2));
        expect("sample K=1 C=0 (no coupons)", 200, String.format(SAMPLE, 1, 0));
        expect("sample K=0 C=1 (zero stops: direct flight halved)", 250, String.format(SAMPLE, 0, 1));
        expect("sample K=0 C=0 (zero stops, zero coupons)", 500, String.format(SAMPLE, 0, 0));
        expect("coupons change the best route (C=2 -> direct 150/2)", 75, "3 3\n1 2\n1 2 100\n2 3 100\n1 3 150\n");
        expect("odd price rounds down (7 -> 3)", 3, "2 1\n0 1\n1 2 7\n");
        expect("price 1 with coupon costs 0", 0, "2 1\n0 1\n1 2 1\n");
        expect("zero-price flight", 0, "3 2\n1 0\n1 2 0\n2 3 0\n");
        expect("more coupons than flights", 15, "3 2\n5 10\n1 2 10\n2 3 20\n");
        expect("parallel flights: cheaper one chosen", 3, "2 2\n0 0\n1 2 10\n1 2 3\n");
        expect("cycle 1<->2 is not used", 55, "3 3\n5 1\n1 2 5\n2 1 5\n2 3 100\n");
        expect("self loop ignored", 5, "2 2\n0 1\n1 1 1\n1 2 10\n");
        expect("cheap long route exceeds K, pay the direct flight", 100, "5 5\n1 0\n1 2 1\n2 3 1\n3 4 1\n4 5 1\n1 5 100\n");
        expect("cheap long route allowed when K=3", 4, "5 5\n3 0\n1 2 1\n2 3 1\n3 4 1\n4 5 1\n1 5 100\n");
        expect("coupon goes to the most expensive leg", 60, "3 2\n1 1\n1 2 20\n2 3 80\n");
        // ---- boundary cases ----
        expect("N = 1: already at destination", 0, "1 0\n0 0\n");
        expect("N = 1 with flights", 0, "1 1\n3 2\n1 1 9\n");
        expect("large K is capped safely", 30, "4 3\n1000000 0\n1 2 10\n2 3 10\n3 4 10\n");
        expect("large prices (long arithmetic)", 3000000000L, "3 2\n1 0\n1 2 1500000000\n2 3 1500000000\n");
        // ---- failure cases (-1) ----
        expect("destination unreachable", -1, "3 1\n2 2\n1 2 10\n");
        expect("no flights at all", -1, "2 0\n0 0\n");
        expect("needs 3 flights but K = 1", -1, "4 3\n1 5\n1 2 10\n2 3 10\n3 4 10\n");
        expect("only flights into city 1", -1, "3 2\n2 2\n2 1 5\n3 1 5\n");
        // ---- invalid input ----
        expectError("negative price", "2 1\n0 0\n1 2 -5\n");
        expectError("city out of range", "2 1\n0 0\n1 3 5\n");
        expectError("wrong number of flights", "3 2\n0 0\n1 2 5\n");
        expectError("negative K", "2 1\n-1 0\n1 2 5\n");
        expectError("negative C", "2 1\n0 -2\n1 2 5\n");
        expectError("N = 0", "0 0\n0 0\n");
        expectError("non-numeric token", "2 1\n0 0\n1 two 5\n");

        randomAgainstBruteForce();
        largeRandomDpEqualsDijkstra();
        csvInputAndOutput();
        generatorAlwaysValid();

        System.out.println();
        System.out.println("Problem 3 tests: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static void expect(String name, long expected, String text) {
        try {
            GraphIO.Instance in = GraphIO.parseText(text);
            BudgetSolver.Result dp = BudgetSolver.solveDP(in.graph, in.k, in.c);
            BudgetSolver.Result dj = BudgetSolver.solveDijkstra(in.graph, in.k, in.c);
            BudgetSolver.Result bf = BudgetSolver.solveBruteForce(in.graph, in.k, in.c);
            String e1 = BudgetSolver.verify(in.graph, in.k, in.c, dp), e2 = BudgetSolver.verify(in.graph, in.k, in.c, dj);
            boolean ok = dp.cost == expected && dj.cost == expected && bf.cost == expected && e1 == null && e2 == null;
            check(name, ok, "expected " + expected + ", DP " + dp.cost + ", Dijkstra " + dj.cost + ", brute " + bf.cost
                    + (e1 != null ? ", DP route: " + e1 : "") + (e2 != null ? ", Dijkstra route: " + e2 : ""));
        } catch (RuntimeException ex) {
            check(name, false, "threw " + ex);
        }
    }

    static void expectError(String name, String text) {
        try {
            GraphIO.Instance in = GraphIO.parseText(text);
            BudgetSolver.solveDP(in.graph, in.k, in.c);
            check(name + " is rejected", false, "no exception");
        } catch (IllegalArgumentException ex) {
            check(name + " is rejected (" + ex.getMessage() + ")", true, "");
        }
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) { passed++; System.out.println("PASS  " + name); }
        else { failed++; System.out.println("FAIL  " + name + " -> " + detail); }
    }

    static void randomAgainstBruteForce() {
        Random rnd = new Random(7);
        int trials = 1500, ok = 0, feasible = 0;
        String detail = "";
        for (int t = 0; t < trials; t++) {
            int n = 1 + rnd.nextInt(7), m = rnd.nextInt(16), k = rnd.nextInt(5), c = rnd.nextInt(4);
            FlightGraph g = new FlightGraph(n);
            for (int i = 0; i < m; i++) g.addFlight(1 + rnd.nextInt(n), 1 + rnd.nextInt(n), rnd.nextInt(60));
            BudgetSolver.Result dp = BudgetSolver.solveDP(g, k, c), dj = BudgetSolver.solveDijkstra(g, k, c),
                    bf = BudgetSolver.solveBruteForce(g, k, c);
            boolean good = dp.cost == bf.cost && dj.cost == bf.cost && BudgetSolver.verify(g, k, c, dp) == null
                    && BudgetSolver.verify(g, k, c, dj) == null && BudgetSolver.verify(g, k, c, bf) == null;
            if (good) ok++; else if (detail.isEmpty()) detail = "n=" + n + " k=" + k + " c=" + c + " dp=" + dp.cost + " dj=" + dj.cost + " bf=" + bf.cost;
            if (dp.feasible()) feasible++;
        }
        check("DP = Dijkstra = brute force on " + trials + " random graphs (" + feasible + " feasible); every route's cost, "
                + "flight count <= K+1 and coupon count <= C re-verified", ok == trials, detail);
    }

    static void largeRandomDpEqualsDijkstra() {
        int ok = 0, total = 0;
        for (int n = 20; n <= 320; n *= 2) {
            for (int k = 0; k <= 8; k += 4) {
                for (int c = 0; c <= 3; c++) {
                    total++;
                    FlightGraph g = FlightGraph.random(n, 4 * n, k, 1000, n * 31 + k * 7 + c);
                    BudgetSolver.Result dp = BudgetSolver.solveDP(g, k, c), dj = BudgetSolver.solveDijkstra(g, k, c);
                    if (dp.cost == dj.cost && dp.feasible() && BudgetSolver.verify(g, k, c, dp) == null
                            && BudgetSolver.verify(g, k, c, dj) == null) ok++;
                }
            }
        }
        check("large random graphs: DP = Dijkstra and routes valid (" + total + " instances)", ok == total, "");
    }

    static void csvInputAndOutput() throws IOException {
        Path dir = Files.createTempDirectory("p3-test");
        Path csv = dir.resolve("g.csv");
        Files.write(csv, List.of("from,to,price", "1,2,100", "2,4,100", "1,4,500", "1,3,50", "3,4,300"), StandardCharsets.UTF_8);
        FlightGraph g = GraphIO.readCsv(csv, 0);
        BudgetSolver.Result r = BudgetSolver.solveDP(g, 1, 1);
        check("CSV with header: N inferred = 4, cost 150", g.cities() == 4 && r.cost == 150, "N=" + g.cities() + " cost=" + r.cost);
        Files.write(csv, List.of("1,2,100", "2,4,100"), StandardCharsets.UTF_8);
        check("CSV without header", GraphIO.readCsv(csv, 0).flights().size() == 2, "");
        check("CSV with explicit larger N -> destination isolated -> -1",
                BudgetSolver.solveDP(GraphIO.readCsv(csv, 5), 3, 1).cost == -1, "");

        Path out = dir.resolve("out.csv");
        GraphIO.writeResultCsv(g, r, out);
        List<String> lines = Files.readAllLines(out);
        boolean marked = lines.get(0).equals("from,to,price,on_path,leg_order,coupon_used,paid_price")
                && lines.get(1).startsWith("1,2,100,x,1,") && lines.get(2).startsWith("2,4,100,x,2,")
                && lines.get(3).equals("1,4,500,,,,") && lines.contains("# minimum_cost,150");
        int coupons = 0;
        for (String l : lines.subList(1, 6)) if (l.split(",", -1)[5].equals("x")) coupons++;
        check("result CSV marks path flights with x and exactly one coupon flight", marked && coupons == 1, String.join(" | ", lines));
        Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }

    static void generatorAlwaysValid() {
        int ok = 0, total = 0;
        for (int n = 2; n <= 30; n += 4) for (int k = 0; k <= 5; k++) for (long s = 1; s <= 3; s++) {
            total++;
            int m = Math.min(n * (n - 1), 3 * n);
            FlightGraph g = FlightGraph.random(n, m, k, 300, s);
            BudgetSolver.Result r = BudgetSolver.solveDP(g, k, 1);
            if (g.flights().size() == m && r.feasible() && BudgetSolver.verify(g, k, 1, r) == null) ok++;
        }
        check("random generator: " + total + " graphs have exactly M flights and a valid route within K stops", ok == total, "");
    }
}
