import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Directed, weighted flight graph. Cities are numbered 1..N; travel is from city 1 to city N. */
public final class FlightGraph {
    /** One-way flight u -> v with ticket price p. */
    public static final class Flight {
        public final int index, from, to;
        public final long price;

        Flight(int index, int from, int to, long price) {
            this.index = index; this.from = from; this.to = to; this.price = price;
        }

        public long discounted() { return price / 2; } // coupon halves the price, rounded down
    }

    private final int n;
    private final List<Flight> flights = new ArrayList<>();

    public FlightGraph(int n) {
        if (n < 1) throw new IllegalArgumentException("Number of cities N must be at least 1 (got " + n + ")");
        this.n = n;
    }

    public int cities() { return n; }
    public List<Flight> flights() { return Collections.unmodifiableList(flights); }

    public Flight addFlight(int u, int v, long p) {
        if (u < 1 || u > n || v < 1 || v > n) {
            throw new IllegalArgumentException("Flight " + u + " -> " + v + " uses a city outside 1.." + n);
        }
        if (p < 0) throw new IllegalArgumentException("Flight " + u + " -> " + v + " has negative price " + p);
        Flight f = new Flight(flights.size(), u, v, p);
        flights.add(f);
        return f;
    }

    /**
     * Random graph with N cities and M flights that always contains a route 1 -> N using at
     * most K + 1 flights (a "backbone"), so the instance is valid for the given K.
     */
    public static FlightGraph random(int n, int m, int k, long maxPrice, long seed) {
        if (n < 2) throw new IllegalArgumentException("Random graph needs N >= 2");
        if (k < 0) throw new IllegalArgumentException("K must be >= 0");
        if (maxPrice < 1) throw new IllegalArgumentException("Maximum price must be >= 1");
        long maxEdges = (long) n * (n - 1);
        int legs = Math.min(k + 1, n - 1);
        if (m < 1 || m > maxEdges) throw new IllegalArgumentException("M must be between 1 and N*(N-1) = " + maxEdges);
        Random rnd = new Random(seed);
        legs = 1 + rnd.nextInt(legs);
        if (m < legs) legs = m;
        FlightGraph g = new FlightGraph(n);
        Set<Long> used = new HashSet<>();
        // Backbone 1 -> x1 -> ... -> N with distinct intermediate cities.
        List<Integer> middle = new ArrayList<>();
        for (int c = 2; c < n; c++) middle.add(c);
        Collections.shuffle(middle, rnd);
        int prev = 1;
        for (int i = 0; i < legs; i++) {
            int next = (i == legs - 1) ? n : middle.get(i);
            g.addFlight(prev, next, 1 + (long) (rnd.nextDouble() * maxPrice));
            used.add((long) prev * (n + 1) + next);
            prev = next;
        }
        while (g.flights.size() < m) {
            int u = 1 + rnd.nextInt(n), v = 1 + rnd.nextInt(n);
            if (u == v || !used.add((long) u * (n + 1) + v)) continue;
            g.addFlight(u, v, 1 + (long) (rnd.nextDouble() * maxPrice));
        }
        return g;
    }
}
