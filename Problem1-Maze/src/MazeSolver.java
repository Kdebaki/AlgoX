import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Breadth-first search over the state space (row, col, keyMask).
 *
 * keyMask is a T-bit set of the keys collected so far. Because every move costs exactly 1,
 * BFS visits states in non-decreasing distance order, so the first time the exit cell is
 * dequeued (with any mask) its distance is the shortest path length.
 *
 * Time  O(N^2 * 2^T * 4)   Space O(N^2 * 2^T)
 */
public final class MazeSolver {
    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = {0, 0, -1, 1};

    private MazeSolver() { }

    /** One cell of the shortest path, together with the key inventory after entering it. */
    public static final class Step {
        public final int row, col, code, mask;
        public final String event; // "" when nothing special happens

        Step(int row, int col, int code, int mask, String event) {
            this.row = row; this.col = col; this.code = code; this.mask = mask; this.event = event;
        }
    }

    public static final class Result {
        public final int length;           // shortest path length or -1
        public final List<Step> steps;     // path from S (index 0) to E (index length); empty if -1
        public final long statesExplored;  // states dequeued by BFS
        public final double millis;

        Result(int length, List<Step> steps, long statesExplored, double millis) {
            this.length = length; this.steps = steps; this.statesExplored = statesExplored; this.millis = millis;
        }

        public boolean reachable() { return length >= 0; }
    }

    public static Result solve(Maze m) {
        long t0 = System.nanoTime();
        int n = m.size();
        int masks = 1 << m.types();
        int total = n * n * masks;
        int[] dist = new int[total];
        int[] parent = new int[total];
        Arrays.fill(dist, -1);
        int[] queue = new int[total];
        int head = 0, tail = 0;

        int startId = (m.startRow() * n + m.startCol()) * masks; // no keys yet
        int exitCell = m.exitRow() * n + m.exitCol();
        dist[startId] = 0;
        parent[startId] = -1;
        queue[tail++] = startId;
        int found = -1;

        while (head < tail) {
            int id = queue[head++];
            int cell = id / masks, mask = id % masks;
            if (cell == exitCell) { found = id; break; }
            int r = cell / n, c = cell % n;
            for (int d = 0; d < 4; d++) {
                int nr = r + DR[d], nc = c + DC[d];
                if (nr < 0 || nc < 0 || nr >= n || nc >= n) continue;
                int code = m.cell(nr, nc);
                if (code == Maze.WALL) continue;
                if (Maze.isDoor(code) && (mask & (1 << Maze.typeOf(code))) == 0) continue; // locked
                int nmask = Maze.isKey(code) ? mask | (1 << Maze.typeOf(code)) : mask; // auto pick-up
                int nid = (nr * n + nc) * masks + nmask;
                if (dist[nid] == -1) {
                    dist[nid] = dist[id] + 1;
                    parent[nid] = id;
                    queue[tail++] = nid;
                }
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        if (found == -1) return new Result(-1, Collections.emptyList(), head, ms);

        List<Integer> ids = new ArrayList<>();
        for (int id = found; id != -1; id = parent[id]) ids.add(id);
        Collections.reverse(ids);

        List<Step> steps = new ArrayList<>();
        Set<Integer> openedDoors = new HashSet<>();
        int prevMask = 0;
        for (int id : ids) {
            int cell = id / masks, mask = id % masks;
            int r = cell / n, c = cell % n, code = m.cell(r, c);
            String event = "";
            if (code == Maze.START) event = "start";
            else if (code == Maze.EXIT) event = "reached exit";
            else if (Maze.isKey(code)) {
                event = (prevMask & (1 << Maze.typeOf(code))) == 0
                        ? "picked up key " + Maze.letter(code)
                        : "key " + Maze.letter(code) + " already held";
            } else if (Maze.isDoor(code)) {
                event = openedDoors.add(cell)
                        ? "unlocked door " + Maze.letter(code)
                        : "passed open door " + Maze.letter(code);
            }
            steps.add(new Step(r, c, code, mask, event));
            prevMask = mask;
        }
        return new Result(dist[found], steps, head, ms);
    }

    /** Human readable inventory, e.g. "{a, c}". */
    public static String inventory(int mask) {
        StringBuilder sb = new StringBuilder("{");
        for (int k = 0; k < Maze.MAX_TYPES; k++) {
            if ((mask & (1 << k)) != 0) {
                if (sb.length() > 1) sb.append(", ");
                sb.append((char) ('a' + k));
            }
        }
        return sb.append('}').toString();
    }
}
