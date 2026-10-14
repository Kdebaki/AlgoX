import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Minimum-cost route from city 1 to city N with at most K stops (K + 1 flights) and C coupons.
 *
 * State = (city v, flights used f, coupons used c).
 *
 * Main method - layered dynamic programming (Bellman-Ford limited to K+1 rounds):
 *   dp[f][v][c] = cheapest cost to stand in v after exactly f flights having used exactly c coupons
 *   dp[0][1][0] = 0
 *   for every flight u->v (price p):
 *     dp[f+1][v][c]   <- dp[f][u][c] + p          (pay full price)
 *     dp[f+1][v][c+1] <- dp[f][u][c] + floor(p/2)  (use one coupon on this flight)
 *   answer = min over f = 1..K+1, c = 0..C of dp[f][N][c]
 * Since f only grows by one per layer and the last layer is K+1, the stop limit can never be
 * exceeded. Time O(F * M * C'), space O(F * N * C') with F = min(K+1, N-1), C' = min(C, F) + 1.
 *
 * Alternative method (for comparison) - Dijkstra on the same state graph; brute force
 * enumeration (exponential) is used to verify both on small inputs.
 */
public final class BudgetSolver {
    private BudgetSolver() { }

    /** One flight of the chosen route. */
    public static final class Leg {
        public final FlightGraph.Flight flight;
        public final boolean coupon;
        public final long paid, runningCost;

        Leg(FlightGraph.Flight flight, boolean coupon, long runningCost) {
            this.flight = flight;
            this.coupon = coupon;
            this.paid = coupon ? flight.discounted() : flight.price;
            this.runningCost = runningCost;
        }
    }

    public static final class Result {
        public final long cost;        // -1 if impossible
        public final List<Leg> legs;   // chosen route (empty if impossible or N == 1)
        public final String method;
        public final long work;        // relaxations / state expansions performed
        public final double millis;

        Result(long cost, List<Leg> legs, String method, long work, double millis) {
            this.cost = cost; this.legs = legs; this.method = method; this.work = work; this.millis = millis;
        }

        public boolean feasible() { return cost >= 0; }
        public int flightsUsed() { return legs.size(); }
        public int stops() { return Math.max(0, legs.size() - 1); }
        public int couponsUsed() { int c = 0; for (Leg l : legs) if (l.coupon) c++; return c; }

        public String routeString() {
            if (!feasible()) return "(no route)";
            if (legs.isEmpty()) return "1 (already at destination)";
            StringBuilder sb = new StringBuilder().append(legs.get(0).flight.from);
            for (Leg l : legs) sb.append(" -> ").append(l.flight.to);
            return sb.toString();
        }
    }

    private static final long INF = Long.MAX_VALUE / 4;
    private static final long MAX_STATES = 40_000_000L;

    static void validate(FlightGraph g, int k, int c) {
        if (k < 0) throw new IllegalArgumentException("K (maximum stops) must be >= 0 (got " + k + ")");
        if (c < 0) throw new IllegalArgumentException("C (number of coupons) must be >= 0 (got " + c + ")");
    }

    /** Maximum useful number of flights: an optimal route never repeats a city (see report). */
    static int maxFlights(FlightGraph g, int k) {
        return (int) Math.min((long) k + 1, Math.max(1, g.cities() - 1));
    }

    // ------------------------------------------------------------------ layered DP (main)

    public static Result solveDP(FlightGraph g, int k, int c) {
        validate(g, k, c);
        long t0 = System.nanoTime();
        int n = g.cities();
        if (n == 1) return new Result(0, Collections.emptyList(), "Layered DP", 0, (System.nanoTime() - t0) / 1e6);
        int F = maxFlights(g, k);
        int cm = Math.min(c, F);
        checkSize(F, n, cm);
        long[][][] dp = new long[F + 1][n + 1][cm + 1];
        int[][][] parFlight = new int[F + 1][n + 1][cm + 1];
        for (long[][] a : dp) for (long[] b : a) Arrays.fill(b, INF);
        dp[0][1][0] = 0;
        long work = 0;
        List<FlightGraph.Flight> flights = g.flights();
        for (int f = 0; f < F; f++) {
            for (FlightGraph.Flight e : flights) {
                long[] from = dp[f][e.from];
                for (int used = 0; used <= cm; used++) {
                    long base = from[used];
                    if (base >= INF) continue;
                    work++;
                    long full = base + e.price;
                    if (full < dp[f + 1][e.to][used]) {
                        dp[f + 1][e.to][used] = full;
                        parFlight[f + 1][e.to][used] = e.index;
                    }
                    if (used < cm) {
                        long half = base + e.discounted();
                        if (half < dp[f + 1][e.to][used + 1]) {
                            dp[f + 1][e.to][used + 1] = half;
                            parFlight[f + 1][e.to][used + 1] = e.index;
                        }
                    }
                }
            }
        }
        long best = INF;
        int bf = -1, bc = -1;
        for (int f = 1; f <= F; f++) {
            for (int used = 0; used <= cm; used++) {
                if (dp[f][n][used] < best) { best = dp[f][n][used]; bf = f; bc = used; }
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        if (best >= INF) return new Result(-1, Collections.emptyList(), "Layered DP", work, ms);

        // Reconstruct: the coupon was used on the flight iff the coupon count changed.
        List<FlightGraph.Flight> route = new ArrayList<>();
        List<Boolean> coupons = new ArrayList<>();
        int v = n, used = bc;
        for (int f = bf; f > 0; f--) {
            FlightGraph.Flight e = flights.get(parFlight[f][v][used]);
            long prevFull = dp[f - 1][e.from][used];
            boolean coupon = !(prevFull < INF && prevFull + e.price == dp[f][v][used]);
            route.add(e);
            coupons.add(coupon);
            if (coupon) used--;
            v = e.from;
        }
        Collections.reverse(route);
        Collections.reverse(coupons);
        return new Result(best, buildLegs(route, coupons), "Layered DP", work, ms);
    }

    // ------------------------------------------------------------------ Dijkstra on states

    public static Result solveDijkstra(FlightGraph g, int k, int c) {
        validate(g, k, c);
        long t0 = System.nanoTime();
        int n = g.cities();
        if (n == 1) return new Result(0, Collections.emptyList(), "Dijkstra on states", 0, (System.nanoTime() - t0) / 1e6);
        int F = maxFlights(g, k);
        int cm = Math.min(c, F);
        checkSize(F, n, cm);
        List<List<FlightGraph.Flight>> out = new ArrayList<>();
        for (int i = 0; i <= n; i++) out.add(new ArrayList<>());
        for (FlightGraph.Flight e : g.flights()) out.get(e.from).add(e);

        int layer = (n + 1) * (cm + 1);
        int total = (F + 1) * layer;
        long[] dist = new long[total];
        int[] parState = new int[total], parFlight = new int[total];
        boolean[] parCoupon = new boolean[total], done = new boolean[total];
        Arrays.fill(dist, INF);
        int start = id(0, 1, 0, n, cm);
        dist[start] = 0;
        parState[start] = -1;
        PriorityQueue<long[]> pq = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
        pq.add(new long[]{0, start});
        long work = 0;
        int target = -1;
        while (!pq.isEmpty()) {
            long[] top = pq.poll();
            int s = (int) top[1];
            if (done[s]) continue;
            done[s] = true;
            work++;
            int f = s / layer, v = (s % layer) / (cm + 1), used = s % (cm + 1);
            if (v == n) { target = s; break; } // first settled destination state is optimal
            if (f == F) continue;               // stop limit reached
            for (FlightGraph.Flight e : out.get(v)) {
                relax(dist, parState, parFlight, parCoupon, pq, s, id(f + 1, e.to, used, n, cm), dist[s] + e.price, e.index, false);
                if (used < cm) {
                    relax(dist, parState, parFlight, parCoupon, pq, s, id(f + 1, e.to, used + 1, n, cm),
                            dist[s] + e.discounted(), e.index, true);
                }
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        if (target < 0) return new Result(-1, Collections.emptyList(), "Dijkstra on states", work, ms);
        List<FlightGraph.Flight> route = new ArrayList<>();
        List<Boolean> coupons = new ArrayList<>();
        for (int s = target; parState[s] != -1; s = parState[s]) {
            route.add(g.flights().get(parFlight[s]));
            coupons.add(parCoupon[s]);
        }
        Collections.reverse(route);
        Collections.reverse(coupons);
        return new Result(dist[target], buildLegs(route, coupons), "Dijkstra on states", work, ms);
    }

    private static int id(int f, int v, int used, int n, int cm) {
        return (f * (n + 1) + v) * (cm + 1) + used;
    }

    private static void relax(long[] dist, int[] parState, int[] parFlight, boolean[] parCoupon,
                              PriorityQueue<long[]> pq, int from, int to, long cand, int flight, boolean coupon) {
        if (cand < dist[to]) {
            dist[to] = cand;
            parState[to] = from;
            parFlight[to] = flight;
            parCoupon[to] = coupon;
            pq.add(new long[]{cand, to});
        }
    }

    // ------------------------------------------------------------------ brute force (verification)

    /**
     * Enumerates every walk 1 -> N with at most K + 1 flights; for each walk the coupons go to
     * the flights with the largest saving ceil(p/2). Exponential - only for small test graphs.
     */
    public static Result solveBruteForce(FlightGraph g, int k, int c) {
        validate(g, k, c);
        long t0 = System.nanoTime();
        int n = g.cities();
        if (n == 1) return new Result(0, Collections.emptyList(), "Brute force", 0, 0);
        List<List<FlightGraph.Flight>> out = new ArrayList<>();
        for (int i = 0; i <= n; i++) out.add(new ArrayList<>());
        for (FlightGraph.Flight e : g.flights()) out.get(e.from).add(e);
        BruteState st = new BruteState();
        dfs(1, n, k + 1, c, out, new ArrayList<>(), st);
        double ms = (System.nanoTime() - t0) / 1e6;
        if (st.bestRoute == null) return new Result(-1, Collections.emptyList(), "Brute force", st.work, ms);
        return new Result(st.best, buildLegs(st.bestRoute, st.bestCoupons), "Brute force", st.work, ms);
    }

    private static final class BruteState {
        long best = INF, work = 0;
        List<FlightGraph.Flight> bestRoute;
        List<Boolean> bestCoupons;
    }

    private static void dfs(int v, int n, int flightsLeft, int c, List<List<FlightGraph.Flight>> out,
                            List<FlightGraph.Flight> walk, BruteState st) {
        st.work++;
        if (v == n && !walk.isEmpty()) {
            Integer[] order = new Integer[walk.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            Arrays.sort(order, (a, b) -> Long.compare(saving(walk.get(b)), saving(walk.get(a))));
            boolean[] use = new boolean[walk.size()];
            for (int i = 0; i < Math.min(c, order.length); i++) use[order[i]] = true;
            long cost = 0;
            for (int i = 0; i < walk.size(); i++) cost += use[i] ? walk.get(i).discounted() : walk.get(i).price;
            if (cost < st.best) {
                st.best = cost;
                st.bestRoute = new ArrayList<>(walk);
                st.bestCoupons = new ArrayList<>();
                for (boolean b : use) st.bestCoupons.add(b);
            }
        }
        if (flightsLeft == 0) return;
        for (FlightGraph.Flight e : out.get(v)) {
            walk.add(e);
            dfs(e.to, n, flightsLeft - 1, c, out, walk, st);
            walk.remove(walk.size() - 1);
        }
    }

    private static long saving(FlightGraph.Flight f) { return f.price - f.discounted(); }

    // ------------------------------------------------------------------ helpers

    private static List<Leg> buildLegs(List<FlightGraph.Flight> route, List<Boolean> coupons) {
        List<Leg> legs = new ArrayList<>();
        long running = 0;
        for (int i = 0; i < route.size(); i++) {
            FlightGraph.Flight f = route.get(i);
            running += coupons.get(i) ? f.discounted() : f.price;
            legs.add(new Leg(f, coupons.get(i), running));
        }
        return legs;
    }

    private static void checkSize(int F, int n, int cm) {
        long states = (long) (F + 1) * (n + 1) * (cm + 1);
        if (states > MAX_STATES) {
            throw new IllegalArgumentException("Instance too large: " + states + " states (limit " + MAX_STATES
                    + "). Reduce N, K or C.");
        }
    }

    /**
     * Independent check of a route: it starts at 1, ends at N, uses only existing flights,
     * respects the stop and coupon limits, and its recomputed cost equals the reported cost.
     */
    public static String verify(FlightGraph g, int k, int c, Result r) {
        if (!r.feasible()) return r.legs.isEmpty() ? null : "infeasible result has legs";
        if (g.cities() == 1) return r.cost == 0 && r.legs.isEmpty() ? null : "N = 1 must cost 0";
        if (r.legs.isEmpty()) return "feasible result without legs";
        if (r.flightsUsed() > k + 1) return "uses " + r.flightsUsed() + " flights > K + 1 = " + (k + 1);
        if (r.couponsUsed() > c) return "uses " + r.couponsUsed() + " coupons > C = " + c;
        if (r.legs.get(0).flight.from != 1) return "route does not start in city 1";
        if (r.legs.get(r.legs.size() - 1).flight.to != g.cities()) return "route does not end in city N";
        long total = 0;
        for (int i = 0; i < r.legs.size(); i++) {
            Leg l = r.legs.get(i);
            if (g.flights().get(l.flight.index) != l.flight) return "leg " + (i + 1) + " is not a flight of the graph";
            if (i > 0 && r.legs.get(i - 1).flight.to != l.flight.from) return "legs " + i + " and " + (i + 1) + " are not connected";
            total += l.coupon ? l.flight.price / 2 : l.flight.price;
            if (l.paid != (l.coupon ? l.flight.price / 2 : l.flight.price)) return "wrong paid price on leg " + (i + 1);
            if (l.runningCost != total) return "wrong running cost on leg " + (i + 1);
        }
        return total == r.cost ? null : "recomputed cost " + total + " != reported " + r.cost;
    }
}
