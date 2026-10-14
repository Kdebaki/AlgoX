import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Input/output formats for Problem 3.
 *
 * Option 1 (text, as in the problem statement):
 *     N M
 *     K C
 *     u v p      (M lines)
 * Option 2 (CSV graph, K and C given separately):
 *     from,to,price       (header optional; N = largest city id unless given explicitly)
 * Output CSV: every flight of the graph, path flights marked with 'x' and coupon flights marked.
 */
public final class GraphIO {
    private GraphIO() { }

    /** Parsed Option 1 instance. */
    public static final class Instance {
        public final FlightGraph graph;
        public final int k, c;

        public Instance(FlightGraph graph, int k, int c) { this.graph = graph; this.k = k; this.c = c; }
    }

    public static Instance parseText(String text) {
        List<Long> nums = new ArrayList<>();
        for (String tok : text.replace("﻿", "").trim().split("\\s+")) { // strip BOM added by PowerShell pipes
            if (tok.isEmpty()) continue;
            try { nums.add(Long.parseLong(tok)); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Not an integer: '" + tok + "'"); }
        }
        if (nums.size() < 4) throw new IllegalArgumentException("Expected 'N M' then 'K C' then M lines 'u v p'");
        int n = toInt(nums.get(0), "N"), m = toInt(nums.get(1), "M"), k = toInt(nums.get(2), "K"), c = toInt(nums.get(3), "C");
        if (m < 0) throw new IllegalArgumentException("M must be >= 0");
        if (nums.size() != 4 + 3L * m) {
            throw new IllegalArgumentException("Expected " + m + " flights (" + (4 + 3L * m) + " numbers) but found "
                    + nums.size() + " numbers");
        }
        FlightGraph g = new FlightGraph(n);
        for (int i = 0; i < m; i++) {
            g.addFlight(toInt(nums.get(4 + 3 * i), "u"), toInt(nums.get(5 + 3 * i), "v"), nums.get(6 + 3 * i));
        }
        return new Instance(g, k, c);
    }

    public static Instance readText(Path file) throws IOException {
        return parseText(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    /** Reads a CSV graph: rows "from,to,price". @param n number of cities, or 0 to use the largest id. */
    public static FlightGraph readCsv(Path file, int n) throws IOException {
        List<long[]> rows = new ArrayList<>();
        int lineNo = 0, maxId = 1;
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            lineNo++;
            String line = raw.replace("﻿", "").trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split(",");
            if (p.length < 3) throw new IllegalArgumentException("Line " + lineNo + ": expected from,to,price");
            long u, v, w;
            try {
                u = Long.parseLong(p[0].trim()); v = Long.parseLong(p[1].trim()); w = Long.parseLong(p[2].trim());
            } catch (NumberFormatException e) {
                if (rows.isEmpty() && lineNo <= 1) continue; // header row
                throw new IllegalArgumentException("Line " + lineNo + ": non-numeric value in '" + line + "'");
            }
            rows.add(new long[]{u, v, w});
            maxId = (int) Math.max(maxId, Math.max(u, v));
        }
        FlightGraph g = new FlightGraph(n > 0 ? n : maxId);
        for (long[] r : rows) g.addFlight(toInt(r[0], "from"), toInt(r[1], "to"), r[2]);
        return g;
    }

    public static void writeGraphCsv(FlightGraph g, Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("from,to,price");
        for (FlightGraph.Flight f : g.flights()) lines.add(f.from + "," + f.to + "," + f.price);
        write(file, lines);
    }

    public static void writeText(FlightGraph g, int k, int c, Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(g.cities() + " " + g.flights().size());
        lines.add(k + " " + c);
        for (FlightGraph.Flight f : g.flights()) lines.add(f.from + " " + f.to + " " + f.price);
        write(file, lines);
    }

    /**
     * Output 2: the whole graph, one row per flight.
     * on_path = x if the flight is on the chosen route; coupon = x if a coupon was used on it.
     */
    public static void writeResultCsv(FlightGraph g, BudgetSolver.Result r, Path file) throws IOException {
        int m = g.flights().size();
        String[] leg = new String[m], coupon = new String[m], paid = new String[m];
        for (int i = 0; i < m; i++) { leg[i] = ""; coupon[i] = ""; paid[i] = ""; }
        for (int i = 0; i < r.legs.size(); i++) {
            BudgetSolver.Leg l = r.legs.get(i);
            int idx = l.flight.index;
            leg[idx] = leg[idx].isEmpty() ? String.valueOf(i + 1) : leg[idx] + "+" + (i + 1);
            if (l.coupon) coupon[idx] = "x";
            paid[idx] = String.valueOf(l.paid);
        }
        List<String> lines = new ArrayList<>();
        lines.add("from,to,price,on_path,leg_order,coupon_used,paid_price");
        for (FlightGraph.Flight f : g.flights()) {
            int i = f.index;
            lines.add(f.from + "," + f.to + "," + f.price + "," + (leg[i].isEmpty() ? "" : "x") + "," + leg[i] + ","
                    + coupon[i] + "," + paid[i]);
        }
        lines.add("");
        lines.add("# minimum_cost," + r.cost);
        lines.add("# route," + r.routeString().replace(" -> ", "->"));
        lines.add("# flights_used," + r.flightsUsed() + ",stops," + r.stops() + ",coupons_used," + r.couponsUsed());
        write(file, lines);
    }

    /** Route only: leg,from,to,original_price,coupon_used,paid_price,running_cost. */
    public static void writeRouteCsv(BudgetSolver.Result r, Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("leg,from,to,original_price,coupon_used,paid_price,running_cost");
        for (int i = 0; i < r.legs.size(); i++) {
            BudgetSolver.Leg l = r.legs.get(i);
            lines.add((i + 1) + "," + l.flight.from + "," + l.flight.to + "," + l.flight.price + ","
                    + (l.coupon ? "x" : "") + "," + l.paid + "," + l.runningCost);
        }
        write(file, lines);
    }

    static void write(Path file, List<String> lines) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static int toInt(long v, String name) {
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) throw new IllegalArgumentException(name + " out of range: " + v);
        return (int) v;
    }
}
