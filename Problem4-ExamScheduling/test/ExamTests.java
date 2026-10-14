import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/**
 * Automated tests for Problem 4 (no external libraries needed).
 * Run: java -cp java/Problem4-ExamScheduling/out ExamTests
 */
public final class ExamTests {
    private static int passed = 0, failed = 0;

    public static void main(String[] args) throws IOException {
        // ---- normal and corner cases: expected optimum ----
        expect("isolated courses, R = N", 1, "5 0 5");
        expect("isolated courses, R = 2 (capacity forces ceil(5/2))", 3, "5 0 2");
        expect("isolated courses, R = 1", 5, "5 0 1");
        expect("complete graph K5", 5, complete(5, 5));
        expect("complete graph K5 with R = 2", 5, complete(5, 2));
        expect("disconnected: two triangles", 3, "6 6 6\n1 2\n2 3\n1 3\n4 5\n5 6\n4 6");
        expect("disconnected: two triangles, R = 1", 6, "6 6 1\n1 2\n2 3\n1 3\n4 5\n5 6\n4 6");
        expect("even cycle C6", 2, "6 6 6\n1 2\n2 3\n3 4\n4 5\n5 6\n6 1");
        expect("even cycle C6, R = 2", 3, "6 6 2\n1 2\n2 3\n3 4\n4 5\n5 6\n6 1");
        expect("odd cycle C5", 3, "5 5 5\n1 2\n2 3\n3 4\n4 5\n5 1");
        expect("star, R = 2 (centre alone + leaves in pairs)", 4, "7 6 2\n1 2\n1 3\n1 4\n1 5\n1 6\n1 7");
        expect("star, R = 7", 2, "7 6 7\n1 2\n1 3\n1 4\n1 5\n1 6\n1 7");
        expect("K3,3 with R = 2 (ceil(N/R) = 3 is not tight)", 4, "6 9 2\n1 4\n1 5\n1 6\n2 4\n2 5\n2 6\n3 4\n3 5\n3 6");
        expect("duplicate conflict lines are ignored", 2, "3 3 3\n1 2\n2 1\n1 2");
        expect("single course", 1, "1 0 1");
        expect("no courses", 0, "0 0 3");
        // ---- infeasible / invalid ----
        infeasible("R = 0 with courses", "3 1 0\n1 2");
        expectError("self-loop conflict", "3 1 2\n2 2");
        expectError("course out of range", "3 1 2\n1 4");
        expectError("wrong number of conflict lines", "3 2 2\n1 2");
        expectError("negative N", "-1 0 2");
        expectError("non-numeric token", "3 1 2\n1 x");

        exactAgainstBruteForce();
        heuristicsAlwaysValid();
        timeLimitNeverClaimsOptimality();
        heuristicLabelling();
        csvOutput();

        System.out.println();
        System.out.println("Problem 4 tests: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static String complete(int n, int r) {
        StringBuilder sb = new StringBuilder();
        int m = n * (n - 1) / 2;
        sb.append(n).append(' ').append(m).append(' ').append(r).append('\n');
        for (int u = 1; u <= n; u++) for (int v = u + 1; v <= n; v++) sb.append(u).append(' ').append(v).append('\n');
        return sb.toString();
    }

    static void expect(String name, int optimum, String text) {
        try {
            TimetableIO.Instance in = TimetableIO.parseText(text);
            ConflictGraph g = in.graph;
            Schedule ds = Schedulers.dsatur(g, in.r), wp = Schedulers.welshPowell(g, in.r), ex = Schedulers.exact(g, in.r, 10_000);
            String err = firstError(g, in.r, ds, wp, ex);
            boolean ok = err == null && ex.slots == optimum && ex.provenOptimal && ds.slots >= optimum && wp.slots >= optimum
                    && bruteForce(g, in.r) == optimum;
            check(name + " -> " + optimum + " slots (DSatur " + ds.slots + ", WP " + wp.slots + ")", ok,
                    "exact " + ex.slots + " proven=" + ex.provenOptimal + (err != null ? " invalid: " + err : ""));
        } catch (RuntimeException e) {
            check(name, false, "threw " + e);
        }
    }

    static String firstError(ConflictGraph g, int r, Schedule... list) {
        for (Schedule s : list) {
            String e = s.validate(g, r);
            if (e != null) return s.algorithm + ": " + e;
        }
        return null;
    }

    static void infeasible(String name, String text) {
        TimetableIO.Instance in = TimetableIO.parseText(text);
        Schedule ds = Schedulers.dsatur(in.graph, in.r), ex = Schedulers.exact(in.graph, in.r, 1000);
        check(name + " is reported infeasible", !ds.feasible() && !ex.feasible(), "");
    }

    static void expectError(String name, String text) {
        try {
            TimetableIO.parseText(text);
            check(name + " is rejected", false, "no exception");
        } catch (IllegalArgumentException e) {
            check(name + " is rejected (" + e.getMessage() + ")", true, "");
        }
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) { passed++; System.out.println("PASS  " + name); }
        else { failed++; System.out.println("FAIL  " + name + " -> " + detail); }
    }

    /** Exhaustive search over restricted-growth slot assignments (tiny N only). */
    static int bruteForce(ConflictGraph g, int r) {
        int n = g.size();
        if (n == 0) return 0;
        int[] slot = new int[n], count = new int[n + 1];
        int[] best = {Integer.MAX_VALUE};
        rec(g, r, 0, 0, slot, count, best);
        return best[0];
    }

    private static void rec(ConflictGraph g, int r, int v, int used, int[] slot, int[] count, int[] best) {
        if (used >= best[0]) return;
        if (v == g.size()) { best[0] = used; return; }
        for (int s = 0; s <= used; s++) {
            if (count[s] >= r) continue;
            boolean ok = true;
            for (int u = 0; u < v && ok; u++) if (slot[u] == s && g.conflict(u, v)) ok = false;
            if (!ok) continue;
            slot[v] = s;
            count[s]++;
            rec(g, r, v + 1, Math.max(used, s + 1), slot, count, best);
            count[s]--;
        }
    }

    static void exactAgainstBruteForce() {
        Random rnd = new Random(11);
        int trials = 400, ok = 0, dsWorse = 0, wpWorse = 0;
        String detail = "";
        for (int t = 0; t < trials; t++) {
            int n = 1 + rnd.nextInt(9), r = 1 + rnd.nextInt(4);
            ConflictGraph g = ConflictGraph.random(n, rnd.nextDouble(), rnd.nextLong());
            Schedule ds = Schedulers.dsatur(g, r), wp = Schedulers.welshPowell(g, r), ex = Schedulers.exact(g, r, 10_000);
            int opt = bruteForce(g, r);
            boolean good = ex.provenOptimal && ex.slots == opt && firstError(g, r, ds, wp, ex) == null && ds.slots >= opt && wp.slots >= opt;
            if (good) ok++; else if (detail.isEmpty()) detail = "n=" + n + " r=" + r + " exact=" + ex.slots + " brute=" + opt;
            if (ds.slots > opt) dsWorse++;
            if (wp.slots > opt) wpWorse++;
        }
        check("exact B&B = brute-force optimum on " + trials + " random instances (DSatur sub-optimal on " + dsWorse
                + ", Welsh-Powell on " + wpWorse + ")", ok == trials, detail);
    }

    static void heuristicsAlwaysValid() {
        Random rnd = new Random(5);
        int total = 0, ok = 0;
        for (int n : new int[]{10, 50, 120, 300}) {
            for (double p : new double[]{0.0, 0.1, 0.5, 0.9, 1.0}) {
                for (int r : new int[]{1, 2, 5, 1000}) {
                    total++;
                    ConflictGraph g = ConflictGraph.random(n, p, rnd.nextLong());
                    Schedule ds = Schedulers.dsatur(g, r), wp = Schedulers.welshPowell(g, r);
                    int lb = Schedulers.lowerBound(g, r);
                    if (firstError(g, r, ds, wp) == null && ds.slots >= lb && wp.slots >= lb) ok++;
                }
            }
        }
        check("DSatur and Welsh-Powell timetables are conflict-free and within capacity on " + total + " graphs (N up to 300)", ok == total, "");
    }

    static void timeLimitNeverClaimsOptimality() {
        ConflictGraph g = ConflictGraph.random(90, 0.5, 3);
        Schedule ex = Schedulers.exact(g, 6, 1);
        boolean ok = ex.validate(g, 6) == null
                && (ex.provenOptimal ? ex.slots == Schedulers.lowerBound(g, 6) || ex.proof.contains("explored the whole")
                                     : ex.proof.startsWith("NOT proven"));
        check("exact search with a 1 ms limit returns a valid timetable and does not claim optimality without proof (" + ex.proof + ")", ok, ex.proof);
    }

    static void heuristicLabelling() {
        // A heuristic result is only called optimal with a proof.
        ConflictGraph g = TimetableIO.parseText("6 9 2\n1 4\n1 5\n1 6\n2 4\n2 5\n2 6\n3 4\n3 5\n3 6").graph;
        Schedule ds = Schedulers.dsatur(g, 2);
        boolean before = !ds.provenOptimal && ds.proof.contains("not proven");
        Schedule ex = Schedulers.exact(g, 2, 1000);
        Schedulers.markHeuristicOptimality(ds, ex, Schedulers.lowerBound(g, 2));
        boolean after = ds.slots == ex.slots ? ds.provenOptimal : !ds.provenOptimal;
        check("heuristic result is 'not proven' until certified by the exact search or the lower bound", before && after, ds.proof);
    }

    static void csvOutput() throws IOException {
        TimetableIO.Instance in = TimetableIO.parseText("5 4 2\n1 2\n2 3\n3 4\n4 5");
        Schedule ex = Schedulers.exact(in.graph, 2, 1000);
        Path dir = Files.createTempDirectory("p4-test");
        Path f = dir.resolve("t.csv");
        TimetableIO.writeTimetable(ex, 2, f);
        List<String> lines = Files.readAllLines(f);
        boolean ok = lines.get(0).equals("Slot,Hall 1,Hall 2") && lines.size() == 1 + ex.slots;
        int seen = 0;
        for (String l : lines.subList(1, lines.size())) {
            String[] c = l.split(",", -1);
            ok &= c.length == 3;
            for (int i = 1; i < c.length; i++) if (!c[i].isEmpty()) seen++;
        }
        check("timetable CSV: rows = slots, columns = halls, every course listed once", ok && seen == 5, String.join(" | ", lines));
        Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }
}
