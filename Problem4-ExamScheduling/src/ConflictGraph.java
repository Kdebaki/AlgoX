import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Undirected conflict graph: vertices = courses (1..N in input/output, 0..N-1 internally),
 * edge u-v = at least one student takes both courses, so they need different slots.
 */
public final class ConflictGraph {
    private final int n;
    private final boolean[][] adj;
    private final List<List<Integer>> nbrs = new ArrayList<>();
    private final List<int[]> edges = new ArrayList<>();

    public ConflictGraph(int n) {
        if (n < 0) throw new IllegalArgumentException("Number of courses N must be >= 0 (got " + n + ")");
        this.n = n;
        adj = new boolean[n][n];
        for (int i = 0; i < n; i++) nbrs.add(new ArrayList<>());
    }

    /** Adds the conflict between courses u and v (1-based). Duplicate edges are ignored. */
    public void addConflict(int u, int v) {
        if (u < 1 || u > n || v < 1 || v > n) {
            throw new IllegalArgumentException("Conflict " + u + " - " + v + " uses a course outside 1.." + n);
        }
        if (u == v) {
            throw new IllegalArgumentException("Course " + u + " cannot conflict with itself (self-loop makes every timetable infeasible)");
        }
        int a = u - 1, b = v - 1;
        if (adj[a][b]) return;
        adj[a][b] = adj[b][a] = true;
        nbrs.get(a).add(b);
        nbrs.get(b).add(a);
        edges.add(new int[]{Math.min(a, b), Math.max(a, b)});
    }

    public int size() { return n; }
    public boolean conflict(int a, int b) { return adj[a][b]; }        // 0-based
    public List<Integer> neighbours(int a) { return Collections.unmodifiableList(nbrs.get(a)); }
    public int degree(int a) { return nbrs.get(a).size(); }
    public List<int[]> edges() { return Collections.unmodifiableList(edges); } // 0-based pairs
    public int edgeCount() { return edges.size(); }

    /** Erdos-Renyi G(N, p): every pair of courses conflicts independently with probability p. */
    public static ConflictGraph random(int n, double density, long seed) {
        if (density < 0 || density > 1) throw new IllegalArgumentException("Edge density must be between 0 and 1 (got " + density + ")");
        Random rnd = new Random(seed);
        ConflictGraph g = new ConflictGraph(n);
        for (int u = 1; u <= n; u++) for (int v = u + 1; v <= n; v++) if (rnd.nextDouble() < density) g.addConflict(u, v);
        return g;
    }

    public static ConflictGraph complete(int n) {
        ConflictGraph g = new ConflictGraph(n);
        for (int u = 1; u <= n; u++) for (int v = u + 1; v <= n; v++) g.addConflict(u, v);
        return g;
    }

    /**
     * Size of a large clique found greedily (every clique is a lower bound on the number of
     * slots, because all its courses must be in different slots).
     */
    public int greedyCliqueSize() {
        if (n == 0) return 0;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> degree(b) - degree(a));
        int seeds = Math.min(n, 300), best = 1;
        List<Integer> clique = new ArrayList<>();
        for (int s = 0; s < seeds; s++) {
            clique.clear();
            clique.add(order[s]);
            for (int v : order) {
                if (v == order[s]) continue;
                boolean all = true;
                for (int c : clique) if (!adj[v][c]) { all = false; break; }
                if (all) clique.add(v);
            }
            best = Math.max(best, clique.size());
        }
        return best;
    }
}
