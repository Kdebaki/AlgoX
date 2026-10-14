import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Input and CSV output for Problem 4.
 *
 * Option 1 text:  "N M R" then M lines "u v" (courses u and v conflict, 1-based).
 * Option 2:       "N R" plus an edge density -> random conflict graph (see ConflictGraph.random).
 * Output CSV:     rows = slots, columns = exam positions / halls (Hall 1 .. Hall R).
 */
public final class TimetableIO {
    private TimetableIO() { }

    public static final class Instance {
        public final ConflictGraph graph;
        public final int r;

        public Instance(ConflictGraph graph, int r) { this.graph = graph; this.r = r; }
    }

    public static Instance parseText(String text) {
        List<Integer> nums = new ArrayList<>();
        for (String tok : text.replace("﻿", "").trim().split("\\s+")) { // strip BOM added by PowerShell pipes
            if (tok.isEmpty()) continue;
            try { nums.add(Integer.parseInt(tok)); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Not an integer: '" + tok + "'"); }
        }
        if (nums.size() < 3) throw new IllegalArgumentException("Expected first line 'N M R'");
        int n = nums.get(0), m = nums.get(1), r = nums.get(2);
        if (m < 0) throw new IllegalArgumentException("M must be >= 0");
        if (nums.size() != 3 + 2L * m) {
            throw new IllegalArgumentException("Expected " + m + " conflict lines (" + (3 + 2L * m) + " numbers) but found " + nums.size());
        }
        ConflictGraph g = new ConflictGraph(n);
        for (int i = 0; i < m; i++) g.addConflict(nums.get(3 + 2 * i), nums.get(4 + 2 * i));
        return new Instance(g, r);
    }

    public static Instance readText(Path file) throws IOException {
        return parseText(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    public static void writeText(ConflictGraph g, int r, Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(g.size() + " " + g.edgeCount() + " " + r);
        for (int[] e : g.edges()) lines.add((e[0] + 1) + " " + (e[1] + 1));
        write(file, lines);
    }

    /** Timetable grid: "Slot,Hall 1,...,Hall R"; empty cell = unused hall. */
    public static void writeTimetable(Schedule s, int r, Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder header = new StringBuilder("Slot");
        int halls = Math.max(1, r);
        for (int h = 1; h <= halls; h++) header.append(",Hall ").append(h);
        lines.add(header.toString());
        if (s.feasible()) {
            List<List<Integer>> slots = s.slotLists();
            for (int i = 0; i < slots.size(); i++) {
                StringBuilder row = new StringBuilder("Slot " + (i + 1));
                for (int h = 0; h < halls; h++) row.append(',').append(h < slots.get(i).size() ? String.valueOf(slots.get(i).get(h)) : "");
                lines.add(row.toString());
            }
        } else {
            lines.add("INFEASIBLE," + s.infeasibleReason.replace(',', ';'));
        }
        write(file, lines);
    }

    /** One row per course: course,slot (both 1-based). */
    public static void writeAssignment(Schedule s, Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("course,slot");
        for (int v = 0; v < s.slotOf.length; v++) lines.add((v + 1) + "," + (s.slotOf[v] + 1));
        write(file, lines);
    }

    static void write(Path file, List<String> lines) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.write(file, lines, StandardCharsets.UTF_8);
    }
}
