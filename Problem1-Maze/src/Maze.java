import java.util.Arrays;

/**
 * Immutable N x N maze for Problem 1 (Maze with Keys and Doors).
 *
 * Cells are stored as integer codes rather than characters. The problem statement
 * uses the letter 'E' both for the exit and for the door of key type 'e'. To keep the
 * two apart, a single "E" token always means the EXIT, and the door of type 'e' is
 * written as the two-character token "DE" (any door may optionally be written "D" + letter,
 * e.g. "DA" == "A").
 */
public final class Maze {
    public static final int MAX_TYPES = 6;

    public static final int OPEN = 0;
    public static final int WALL = 1;
    public static final int START = 2;
    public static final int EXIT = 3;
    public static final int KEY_BASE = 10;  // KEY_BASE + k  -> key  'a' + k
    public static final int DOOR_BASE = 20; // DOOR_BASE + k -> door 'A' + k

    private final int n;
    private final int[][] cells;
    private final int types;
    private final int startRow, startCol, exitRow, exitCol;

    /**
     * @param cells N x N grid of cell codes
     * @param types T, number of key/door types (1..6). Pass 0 to infer it from the grid.
     */
    public Maze(int[][] cells, int types) {
        if (cells == null || cells.length == 0) {
            throw new IllegalArgumentException("Grid is empty");
        }
        n = cells.length;
        this.cells = new int[n][];
        int sr = -1, sc = -1, er = -1, ec = -1, sCount = 0, eCount = 0, maxType = -1;
        for (int r = 0; r < n; r++) {
            if (cells[r] == null || cells[r].length != n) {
                int len = cells[r] == null ? 0 : cells[r].length;
                throw new IllegalArgumentException("Grid must be N x N (N = " + n + "), but row " + (r + 1)
                        + " has " + len + " cells");
            }
            this.cells[r] = cells[r].clone();
            for (int c = 0; c < n; c++) {
                int code = cells[r][c];
                if (code == START) { sCount++; sr = r; sc = c; }
                else if (code == EXIT) { eCount++; er = r; ec = c; }
                else if (isKey(code) || isDoor(code)) { maxType = Math.max(maxType, typeOf(code)); }
                else if (code != OPEN && code != WALL) {
                    throw new IllegalArgumentException("Invalid cell code " + code + " at row " + (r + 1) + ", col " + (c + 1));
                }
            }
        }
        if (sCount != 1) throw new IllegalArgumentException("Grid must contain exactly one start 'S' (found " + sCount + ")");
        if (eCount != 1) throw new IllegalArgumentException("Grid must contain exactly one exit 'E' (found " + eCount + ")");
        if (types <= 0) types = Math.max(1, maxType + 1);
        if (types > MAX_TYPES) throw new IllegalArgumentException("T must be between 1 and 6 (got " + types + ")");
        if (maxType >= types) {
            throw new IllegalArgumentException("Grid uses key/door type '" + (char) ('a' + maxType)
                    + "' but T = " + types + " only allows 'a'..'" + (char) ('a' + types - 1) + "'");
        }
        this.types = types;
        startRow = sr; startCol = sc; exitRow = er; exitCol = ec;
    }

    public int size() { return n; }
    public int types() { return types; }
    public int cell(int r, int c) { return cells[r][c]; }
    public int startRow() { return startRow; }
    public int startCol() { return startCol; }
    public int exitRow() { return exitRow; }
    public int exitCol() { return exitCol; }

    public int[][] copyCells() {
        int[][] copy = new int[n][];
        for (int r = 0; r < n; r++) copy[r] = cells[r].clone();
        return copy;
    }

    public int countKeys() { return count(true); }
    public int countDoors() { return count(false); }

    private int count(boolean keys) {
        int total = 0;
        for (int[] row : cells) for (int code : row) if (keys ? isKey(code) : isDoor(code)) total++;
        return total;
    }

    public static boolean isKey(int code) { return code >= KEY_BASE && code < KEY_BASE + MAX_TYPES; }
    public static boolean isDoor(int code) { return code >= DOOR_BASE && code < DOOR_BASE + MAX_TYPES; }
    public static int typeOf(int code) { return isKey(code) ? code - KEY_BASE : code - DOOR_BASE; }

    /** Parses a CSV token. Row/col are 1-based and only used for error messages. */
    public static int parseToken(String raw, int row, int col) {
        String t = raw.trim();
        switch (t) {
            case ".": return OPEN;
            case "#": return WALL;
            case "S": return START;
            case "E": return EXIT;
            default: break;
        }
        if (t.length() == 1) {
            char ch = t.charAt(0);
            if (ch >= 'a' && ch <= 'f') return KEY_BASE + (ch - 'a');
            if (ch >= 'A' && ch <= 'F') return DOOR_BASE + (ch - 'A'); // 'E' was handled above (exit)
        }
        if (t.length() == 2 && t.charAt(0) == 'D') {
            char ch = t.charAt(1);
            if (ch >= 'A' && ch <= 'F') return DOOR_BASE + (ch - 'A');
        }
        throw new IllegalArgumentException("Invalid cell '" + t + "' at row " + row + ", col " + col
                + " (allowed: . # S E a-f A-F, door of type e is written DE)");
    }

    /** CSV token for a cell code (inverse of {@link #parseToken}). */
    public static String token(int code) {
        switch (code) {
            case OPEN: return ".";
            case WALL: return "#";
            case START: return "S";
            case EXIT: return "E";
            default: break;
        }
        if (isKey(code)) return String.valueOf((char) ('a' + typeOf(code)));
        if (isDoor(code)) return typeOf(code) == 4 ? "DE" : String.valueOf((char) ('A' + typeOf(code)));
        throw new IllegalArgumentException("Unknown cell code " + code);
    }

    /** Display letter of a key/door (door 'e' is shown as 'E', drawn differently from the exit). */
    public static char letter(int code) {
        return isKey(code) ? (char) ('a' + typeOf(code)) : (char) ('A' + typeOf(code));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int[] row : cells) {
            for (int c = 0; c < n; c++) {
                String tok = token(row[c]);
                sb.append(tok);
                if (c + 1 < n) sb.append(tok.length() == 1 ? "  " : " ");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Maze && ((Maze) o).types == types && Arrays.deepEquals(((Maze) o).cells, cells);
    }

    @Override
    public int hashCode() { return Arrays.deepHashCode(cells) * 31 + types; }
}
