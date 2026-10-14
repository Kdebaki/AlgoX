import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Comparison of DSatur, Welsh-Powell and the exact branch and bound as N increases
 * (random G(N, p) conflict graphs, several seeds per N, averaged).
 */
public final class ExamComparison {
    public static final String[] ALGORITHMS = {"DSatur", "Welsh-Powell", "Exact B&B"};

    /** Averages for one N. */
    public static final class Row {
        public final int n;
        public final double[] slots = new double[3], millis = new double[3];
        public double lowerBound;
        public int proven, seeds;

        Row(int n) { this.n = n; }
    }

    private ExamComparison() { }

    public static List<Row> run(int[] sizes, double density, int r, int seeds, long timeLimitMs) {
        List<Row> rows = new ArrayList<>();
        for (int n : sizes) {
            Row row = new Row(n);
            row.seeds = seeds;
            for (int s = 0; s < seeds; s++) {
                ConflictGraph g = ConflictGraph.random(n, density, 1000L * n + s);
                Schedulers.dsatur(g, r); // warm-up for fair timing
                Schedule[] res = {Schedulers.dsatur(g, r), Schedulers.welshPowell(g, r), Schedulers.exact(g, r, timeLimitMs)};
                for (int a = 0; a < 3; a++) {
                    String err = res[a].validate(g, r);
                    if (err != null) throw new IllegalStateException(ALGORITHMS[a] + " produced an invalid timetable: " + err);
                    row.slots[a] += res[a].slots;
                    row.millis[a] += res[a].millis;
                }
                if (res[2].provenOptimal) row.proven++;
                row.lowerBound += Schedulers.lowerBound(g, r);
            }
            for (int a = 0; a < 3; a++) { row.slots[a] /= seeds; row.millis[a] /= seeds; }
            row.lowerBound /= seeds;
            rows.add(row);
        }
        return rows;
    }

    public static void print(List<Row> rows) {
        System.out.printf("%5s %10s %14s %11s %12s %14s %12s %12s %8s%n", "N", "DSatur", "Welsh-Powell", "Exact",
                "DSatur_ms", "WelshP_ms", "Exact_ms", "LowerBound", "proven");
        for (Row r : rows) {
            System.out.printf(Locale.US, "%5d %10.2f %14.2f %11.2f %12.3f %14.3f %12.3f %12.2f %5d/%d%n", r.n, r.slots[0], r.slots[1],
                    r.slots[2], r.millis[0], r.millis[1], r.millis[2], r.lowerBound, r.proven, r.seeds);
        }
    }

    public static void writeCsv(List<Row> rows, double density, int r, Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("N,density,R,seeds,dsatur_slots,welsh_powell_slots,exact_slots,lower_bound,dsatur_ms,welsh_powell_ms,exact_ms,exact_proven");
        for (Row row : rows) {
            lines.add(String.format(Locale.US, "%d,%.2f,%d,%d,%.2f,%.2f,%.2f,%.2f,%.3f,%.3f,%.3f,%d/%d", row.n, density, r, row.seeds,
                    row.slots[0], row.slots[1], row.slots[2], row.lowerBound, row.millis[0], row.millis[1], row.millis[2],
                    row.proven, row.seeds));
        }
        TimetableIO.write(file, lines);
    }
}
