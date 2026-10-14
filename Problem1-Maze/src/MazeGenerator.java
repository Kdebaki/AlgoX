import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Random maze generator that always produces a solvable maze with exactly T keys and T doors.
 *
 * 1. Carve a perfect maze (randomised DFS / recursive backtracker) and knock out a few
 *    extra walls to create loops.
 * 2. Pick S, put E at the open cell farthest from S, and take the BFS path S -> E.
 * 3. Put the T doors on that path, spread out along it, with a random letter order.
 * 4. Door j (j-th along the path) gets its key in the region reachable from S while doors
 *    j..T-1 are still closed. These regions are nested, so the keys can be collected in
 *    order and every door opened: the maze is solvable by construction.
 * 5. The solver re-checks the result anyway; on failure the attempt is retried.
 */
public final class MazeGenerator {
    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = {0, 0, -1, 1};

    private MazeGenerator() { }

    public static Maze generate(int n, int types, long seed) {
        if (types < 1 || types > Maze.MAX_TYPES) throw new IllegalArgumentException("T must be between 1 and 6 (got " + types + ")");
        if (n < 5) throw new IllegalArgumentException("N must be at least 5 to generate a maze (got " + n + ")");
        if (n > 300) throw new IllegalArgumentException("N must be at most 300 for random generation (got " + n + ")");
        Random rnd = new Random(seed);
        for (int attempt = 0; attempt < 500; attempt++) {
            Maze m = tryGenerate(n, types, rnd);
            if (m != null && MazeSolver.solve(m).reachable()) return m;
        }
        throw new IllegalStateException("Could not generate a solvable " + n + "x" + n + " maze with T = " + types
                + " after 500 attempts; try a larger N");
    }

    private static Maze tryGenerate(int n, int types, Random rnd) {
        int[][] g = new int[n][n];
        for (int[] row : g) java.util.Arrays.fill(row, Maze.WALL);

        // 1. Recursive backtracker on the odd lattice (iterative to avoid stack overflow).
        int sr = 1 + 2 * rnd.nextInt((n - 1) / 2), sc = 1 + 2 * rnd.nextInt((n - 1) / 2);
        if (sr > n - 2) sr = 1;
        if (sc > n - 2) sc = 1;
        g[sr][sc] = Maze.OPEN;
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{sr, sc});
        while (!stack.isEmpty()) {
            int[] cur = stack.peek();
            List<Integer> dirs = new ArrayList<>(List.of(0, 1, 2, 3));
            Collections.shuffle(dirs, rnd);
            boolean moved = false;
            for (int d : dirs) {
                int nr = cur[0] + 2 * DR[d], nc = cur[1] + 2 * DC[d];
                if (nr < 1 || nc < 1 || nr > n - 2 || nc > n - 2 || g[nr][nc] != Maze.WALL) continue;
                g[cur[0] + DR[d]][cur[1] + DC[d]] = Maze.OPEN;
                g[nr][nc] = Maze.OPEN;
                stack.push(new int[]{nr, nc});
                moved = true;
                break;
            }
            if (!moved) stack.pop();
        }
        // Extra openings -> loops, so that the maze is not a tree.
        for (int r = 1; r < n - 1; r++) {
            for (int c = 1; c < n - 1; c++) {
                if (g[r][c] != Maze.WALL || rnd.nextDouble() > 0.08) continue;
                boolean vertical = g[r - 1][c] == Maze.OPEN && g[r + 1][c] == Maze.OPEN;
                boolean horizontal = g[r][c - 1] == Maze.OPEN && g[r][c + 1] == Maze.OPEN;
                if (vertical ^ horizontal) g[r][c] = Maze.OPEN;
            }
        }

        List<int[]> open = new ArrayList<>();
        for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) if (g[r][c] == Maze.OPEN) open.add(new int[]{r, c});
        if (open.size() < 2 * types + 2) return null;

        // 2. Start and exit.
        int[] s = open.get(rnd.nextInt(open.size()));
        int[][] dist = bfs(g, n, s[0], s[1]);
        int far = -1;
        int[] e = null;
        for (int[] p : open) if (dist[p[0]][p[1]] > far) { far = dist[p[0]][p[1]]; e = p; }
        List<int[]> path = pathTo(dist, n, e);
        if (path.size() - 2 < types) return null; // need T interior cells for the doors

        // 3. Doors spread along the path, random letter order.
        List<Integer> order = new ArrayList<>();
        for (int k = 0; k < types; k++) order.add(k);
        Collections.shuffle(order, rnd);
        int interior = path.size() - 2;
        int[][] doorPos = new int[types][];
        for (int j = 0; j < types; j++) {
            int idx = 1 + (int) ((long) (j + 1) * interior / (types + 1));
            idx = Math.max(1 + j, Math.min(idx, path.size() - 2 - (types - 1 - j)));
            doorPos[j] = path.get(idx);
        }
        for (int j = 0; j < types; j++) g[doorPos[j][0]][doorPos[j][1]] = Maze.DOOR_BASE + order.get(j);
        g[s[0]][s[1]] = Maze.START;
        g[e[0]][e[1]] = Maze.EXIT;

        boolean[][] onPath = new boolean[n][n];
        for (int[] p : path) onPath[p[0]][p[1]] = true;

        // 4. Keys: key for door j inside the region reachable while doors j.. are closed.
        for (int j = 0; j < types; j++) {
            int[][] reach = new int[n][n];
            for (int[] row : reach) java.util.Arrays.fill(row, -1);
            ArrayDeque<int[]> q = new ArrayDeque<>();
            reach[s[0]][s[1]] = 0;
            q.add(s);
            List<int[]> offPath = new ArrayList<>(), anyCell = new ArrayList<>();
            while (!q.isEmpty()) {
                int[] cur = q.poll();
                if (g[cur[0]][cur[1]] == Maze.OPEN) (onPath[cur[0]][cur[1]] ? anyCell : offPath).add(cur);
                for (int d = 0; d < 4; d++) {
                    int nr = cur[0] + DR[d], nc = cur[1] + DC[d];
                    if (nr < 0 || nc < 0 || nr >= n || nc >= n || reach[nr][nc] != -1) continue;
                    int code = g[nr][nc];
                    if (code == Maze.WALL || code == Maze.EXIT) continue;
                    if (Maze.isDoor(code) && isClosedDoor(code, j, order)) continue;
                    reach[nr][nc] = 0;
                    q.add(new int[]{nr, nc});
                }
            }
            List<int[]> candidates = offPath.isEmpty() ? anyCell : offPath;
            if (candidates.isEmpty()) return null;
            int[] k = candidates.get(rnd.nextInt(candidates.size()));
            g[k[0]][k[1]] = Maze.KEY_BASE + order.get(j);
        }
        return new Maze(g, types);
    }

    /** Door of type t is still closed while placing key j if it is door j or later along the path. */
    private static boolean isClosedDoor(int code, int j, List<Integer> order) {
        return order.indexOf(Maze.typeOf(code)) >= j;
    }

    private static int[][] bfs(int[][] g, int n, int sr, int sc) {
        int[][] dist = new int[n][n];
        for (int[] row : dist) java.util.Arrays.fill(row, -1);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        dist[sr][sc] = 0;
        q.add(new int[]{sr, sc});
        while (!q.isEmpty()) {
            int[] cur = q.poll();
            for (int d = 0; d < 4; d++) {
                int nr = cur[0] + DR[d], nc = cur[1] + DC[d];
                if (nr < 0 || nc < 0 || nr >= n || nc >= n || g[nr][nc] == Maze.WALL || dist[nr][nc] != -1) continue;
                dist[nr][nc] = dist[cur[0]][cur[1]] + 1;
                q.add(new int[]{nr, nc});
            }
        }
        return dist;
    }

    /** Walks back from target along strictly decreasing distances. */
    private static List<int[]> pathTo(int[][] dist, int n, int[] target) {
        List<int[]> path = new ArrayList<>();
        int[] cur = target;
        path.add(cur);
        while (dist[cur[0]][cur[1]] > 0) {
            for (int d = 0; d < 4; d++) {
                int nr = cur[0] + DR[d], nc = cur[1] + DC[d];
                if (nr >= 0 && nc >= 0 && nr < n && nc < n && dist[nr][nc] == dist[cur[0]][cur[1]] - 1) {
                    cur = new int[]{nr, nc};
                    break;
                }
            }
            path.add(cur);
        }
        Collections.reverse(path);
        return path;
    }
}
