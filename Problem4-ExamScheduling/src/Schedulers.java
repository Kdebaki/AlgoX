import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * Capacity-constrained graph colouring: slot = colour, at most R courses per slot.
 *
 *  - DSatur (greedy heuristic): repeatedly schedule the course whose conflicting courses already
 *    occupy the most distinct slots; put it in the lowest slot that is conflict-free and not full.
 *  - Welsh-Powell (greedy heuristic, for comparison): courses by decreasing degree, fill one
 *    slot at a time.
 *  - Exact branch and bound: DSatur-ordered backtracking that starts from the DSatur solution as
 *    the incumbent and prunes with lower bounds. If the search finishes, the answer is proven optimal.
 */
public final class Schedulers {
    private Schedulers() { }

    /** Lower bound on the number of slots: max(largest clique found, ceil(N / R)). */
    public static int lowerBound(ConflictGraph g, int r) {
        if (g.size() == 0) return 0;
        return Math.max(g.greedyCliqueSize(), (g.size() + r - 1) / r);
    }

    private static Schedule checkCapacity(ConflictGraph g, int r, String name) {
        if (g.size() > 0 && r < 1) return Schedule.infeasible(name, "R = " + r + ": no exam can be held in any slot");
        return null;
    }

    // ------------------------------------------------------------------ DSatur

    public static Schedule dsatur(ConflictGraph g, int r) {
        Schedule bad = checkCapacity(g, r, "DSatur");
        if (bad != null) return bad;
        long t0 = System.nanoTime();
        int n = g.size();
        int[] slot = new int[n], satDeg = new int[n], uncolDeg = new int[n], order = new int[n];
        Arrays.fill(slot, -1);
        BitSet[] sat = new BitSet[n];
        for (int v = 0; v < n; v++) { sat[v] = new BitSet(); uncolDeg[v] = g.degree(v); }
        List<Integer> count = new ArrayList<>();
        for (int step = 0; step < n; step++) {
            int v = -1;
            for (int u = 0; u < n; u++) {
                if (slot[u] != -1) continue;
                if (v == -1 || satDeg[u] > satDeg[v] || (satDeg[u] == satDeg[v] && uncolDeg[u] > uncolDeg[v])) v = u;
            }
            int s = 0;
            while (s < count.size() && (sat[v].get(s) || count.get(s) >= r)) s++;
            if (s == count.size()) count.add(0);
            slot[v] = s;
            count.set(s, count.get(s) + 1);
            order[step] = v;
            for (int u : g.neighbours(v)) {
                if (slot[u] != -1) continue;
                uncolDeg[u]--;
                if (!sat[u].get(s)) { sat[u].set(s); satDeg[u]++; }
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        return new Schedule("DSatur", slot, count.size(), order, ms, n, false, "heuristic (not proven)");
    }

    // ------------------------------------------------------------------ Welsh-Powell

    public static Schedule welshPowell(ConflictGraph g, int r) {
        Schedule bad = checkCapacity(g, r, "Welsh-Powell");
        if (bad != null) return bad;
        long t0 = System.nanoTime();
        int n = g.size();
        Integer[] byDegree = new Integer[n];
        for (int i = 0; i < n; i++) byDegree[i] = i;
        Arrays.sort(byDegree, (a, b) -> g.degree(b) != g.degree(a) ? g.degree(b) - g.degree(a) : a - b);
        int[] slot = new int[n], order = new int[n];
        Arrays.fill(slot, -1);
        int done = 0, s = 0;
        while (done < n) {
            List<Integer> members = new ArrayList<>();
            for (int v : byDegree) {
                if (slot[v] != -1 || members.size() >= r) continue;
                boolean ok = true;
                for (int m : members) if (g.conflict(v, m)) { ok = false; break; }
                if (!ok) continue;
                members.add(v);
                slot[v] = s;
                order[done++] = v;
            }
            s++;
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        return new Schedule("Welsh-Powell", slot, s, order, ms, n, false, "heuristic (not proven)");
    }

    // ------------------------------------------------------------------ exact branch and bound

    public static Schedule exact(ConflictGraph g, int r, long timeLimitMs) {
        Schedule bad = checkCapacity(g, r, "Exact B&B");
        if (bad != null) return bad;
        long t0 = System.nanoTime();
        int n = g.size();
        if (n == 0) return new Schedule("Exact B&B", new int[0], 0, new int[0], 0, 0, true, "no courses: 0 slots");
        if (n > 1500) {
            Schedule d = dsatur(g, r);
            return new Schedule("Exact B&B", d.slotOf, d.slots, d.order, d.millis, 0, false,
                    "not attempted (N > 1500); DSatur solution returned, NOT proven optimal");
        }
        Search s = new Search(g, r, t0 + timeLimitMs * 1_000_000L);
        Schedule start = dsatur(g, r);
        s.best = start.slots;
        s.bestSlot = start.slotOf.clone();
        s.bestOrder = start.order.clone();
        s.lowerBound = lowerBound(g, r);
        if (s.best > s.lowerBound) s.dfs(0, 0);
        double ms = (System.nanoTime() - t0) / 1e6;
        boolean proven = !s.timedOut || s.best == s.lowerBound;
        String proof;
        if (s.best == s.lowerBound) {
            proof = "proven optimal: matches lower bound " + s.lowerBound + " (max of clique size and ceil(N/R))";
        } else if (!s.timedOut) {
            proof = "proven optimal: branch and bound explored the whole search tree (" + s.nodes + " nodes)";
        } else {
            proof = "NOT proven: time limit " + timeLimitMs + " ms reached; best found " + s.best
                    + ", optimum is between " + s.lowerBound + " and " + s.best;
        }
        return new Schedule("Exact B&B", s.bestSlot, s.best, s.bestOrder, ms, s.nodes, proven, proof);
    }

    /** Backtracking state. Slots are opened in order 0,1,2,... (symmetry breaking). */
    private static final class Search {
        final ConflictGraph g;
        final int n, r;
        final long deadline;
        final int[] slot, count, satDeg, uncolDeg, orderStack;
        final int[][] nbInSlot; // nbInSlot[v][s] = number of v's neighbours placed in slot s
        int best, lowerBound;
        int[] bestSlot, bestOrder;
        long nodes = 0;
        boolean timedOut = false, stop = false;

        Search(ConflictGraph g, int r, long deadline) {
            this.g = g; this.n = g.size(); this.r = r; this.deadline = deadline;
            slot = new int[n];
            Arrays.fill(slot, -1);
            count = new int[n + 1];
            satDeg = new int[n];
            uncolDeg = new int[n];
            orderStack = new int[n];
            nbInSlot = new int[n][n + 1];
            for (int v = 0; v < n; v++) uncolDeg[v] = g.degree(v);
        }

        void dfs(int colored, int used) {
            if (stop) return;
            nodes++;
            if ((nodes & 1023) == 0 && System.nanoTime() > deadline) { timedOut = true; stop = true; return; }
            if (colored == n) {
                best = used; // pruning guarantees used < best
                bestSlot = slot.clone();
                bestOrder = orderStack.clone();
                if (best <= lowerBound) stop = true;
                return;
            }
            // Bound: remaining courses need at least ceil((remaining - free seats in open slots) / R) new slots.
            int remaining = n - colored;
            long free = (long) used * r - colored;
            int need = used + (int) Math.max(0, (remaining - free + r - 1) / r);
            if (need >= best) return;

            int v = -1;
            for (int u = 0; u < n; u++) {
                if (slot[u] != -1) continue;
                if (v == -1 || satDeg[u] > satDeg[v] || (satDeg[u] == satDeg[v] && uncolDeg[u] > uncolDeg[v])) v = u;
            }
            orderStack[colored] = v;
            for (int s = 0; s < used && !stop; s++) {
                if (count[s] >= r || nbInSlot[v][s] > 0) continue;
                assign(v, s);
                dfs(colored + 1, used);
                unassign(v, s);
            }
            if (!stop && used + 1 < best) {
                assign(v, used);
                dfs(colored + 1, used + 1);
                unassign(v, used);
            }
        }

        void assign(int v, int s) {
            slot[v] = s;
            count[s]++;
            for (int u : g.neighbours(v)) {
                if (nbInSlot[u][s]++ == 0) satDeg[u]++;
                uncolDeg[u]--;
            }
        }

        void unassign(int v, int s) {
            slot[v] = -1;
            count[s]--;
            for (int u : g.neighbours(v)) {
                if (--nbInSlot[u][s] == 0) satDeg[u]--;
                uncolDeg[u]++;
            }
        }
    }

    /** If the exact search proved the optimum, a heuristic that reached it is optimal too. */
    public static void markHeuristicOptimality(Schedule heuristic, Schedule exact, int lowerBound) {
        if (!heuristic.feasible()) return;
        if (heuristic.slots == lowerBound) {
            heuristic.provenOptimal = true;
            heuristic.proof = "proven optimal: equals lower bound " + lowerBound;
        } else if (exact.feasible() && exact.provenOptimal && heuristic.slots == exact.slots) {
            heuristic.provenOptimal = true;
            heuristic.proof = "proven optimal: equals the optimum certified by the exact search";
        } else if (exact.feasible() && exact.provenOptimal) {
            heuristic.provenOptimal = false;
            heuristic.proof = "NOT optimal: uses " + (heuristic.slots - exact.slots) + " more slot(s) than the proven optimum " + exact.slots;
        } else {
            heuristic.provenOptimal = false;
            heuristic.proof = "heuristic, NOT proven optimal (lower bound " + lowerBound + ")";
        }
    }
}
