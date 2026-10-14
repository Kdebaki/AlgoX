import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Swing visualisation for Problem 4:
 *  - conflict graph with every course coloured by its slot,
 *  - the timetable grid (rows = slots, columns = halls) in the same colours,
 *  - animation of the order in which courses are assigned (optional feature),
 *  - comparison chart of slots used and running time as N increases.
 */
public final class ExamVisualizer {
    private static final Color[] PALETTE = {
            new Color(31, 119, 180), new Color(255, 127, 14), new Color(44, 160, 44), new Color(214, 39, 40),
            new Color(148, 103, 189), new Color(140, 86, 75), new Color(227, 119, 194), new Color(188, 189, 34),
            new Color(23, 190, 207), new Color(127, 127, 127), new Color(0, 90, 70), new Color(240, 200, 0)};
    static final Color[] ALG_COLORS = {new Color(31, 119, 180), new Color(255, 127, 14), new Color(44, 160, 44)};

    private ExamVisualizer() { }

    static Color slotColor(int s) {
        if (s < PALETTE.length) return PALETTE[s];
        return Color.getHSBColor((float) ((s * 0.618033988749895) % 1.0), 0.65f, 0.85f);
    }

    static boolean darkText(Color c) { return (c.getRed() * 299 + c.getGreen() * 587 + c.getBlue() * 114) / 1000 > 150; }

    // ------------------------------------------------------------------ graph + timetable scene

    static final class Scene {
        final ConflictGraph g;
        final Schedule s;
        final int r, lowerBound;
        final String title;
        final int[] rank; // position of each course in the assignment order

        Scene(ConflictGraph g, Schedule s, int r, int lowerBound, String title) {
            this.g = g; this.s = s; this.r = r; this.lowerBound = lowerBound; this.title = title;
            rank = new int[g.size()];
            for (int i = 0; i < s.order.length; i++) rank[s.order[i]] = i;
        }

        /** @param shown number of courses (in assignment order) already placed */
        void paint(Graphics2D g2, int width, int height, int shown) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(new Color(248, 249, 252));
            g2.fillRect(0, 0, width, height);
            Font h = new Font("SansSerif", Font.BOLD, 16), b = new Font("SansSerif", Font.PLAIN, 13);
            g2.setColor(Color.BLACK);
            g2.setFont(h);
            g2.drawString(title + "  -  " + s.algorithm, 16, 24);
            g2.setFont(b);
            String info = s.feasible()
                    ? "N = " + g.size() + " courses, " + g.edgeCount() + " conflicts, R = " + r + " exams per slot  |  slots used: "
                      + s.slots + "  |  lower bound: " + lowerBound + "  |  " + String.format(Locale.US, "%.3f ms", s.millis)
                    : "INFEASIBLE: " + s.infeasibleReason;
            g2.drawString(info, 16, 44);
            g2.setColor(s.provenOptimal ? new Color(0, 130, 60) : new Color(190, 90, 0));
            g2.drawString((s.provenOptimal ? "Optimality: " : "Optimality: ") + s.proof, 16, 62);
            if (!s.feasible()) return;

            int n = g.size();
            int graphW = (int) (width * 0.55);
            double cx = graphW / 2.0 + 8, cy = 80 + (height - 100) / 2.0;
            double rad = Math.max(40, Math.min(graphW, height - 110) / 2.0 - 34);
            double nodeR = Math.max(9, Math.min(20, 300.0 / Math.max(1, n)));
            Point2D.Double[] p = new Point2D.Double[n];
            for (int v = 0; v < n; v++) {
                double ang = -Math.PI / 2 + 2 * Math.PI * v / Math.max(1, n);
                p[v] = new Point2D.Double(cx + rad * Math.cos(ang), cy + rad * Math.sin(ang));
            }
            g2.setStroke(new BasicStroke(1.1f));
            for (int[] e : g.edges()) {
                boolean both = rank[e[0]] < shown && rank[e[1]] < shown;
                g2.setColor(both ? new Color(110, 110, 120, 170) : new Color(190, 190, 200, 120));
                g2.draw(new Line2D.Double(p[e[0]], p[e[1]]));
            }
            Font nf = new Font("SansSerif", Font.BOLD, (int) Math.max(9, nodeR * 0.85));
            for (int v = 0; v < n; v++) {
                boolean placed = rank[v] < shown;
                Color c = placed ? slotColor(s.slotOf[v]) : new Color(225, 225, 230);
                Ellipse2D.Double e = new Ellipse2D.Double(p[v].x - nodeR, p[v].y - nodeR, 2 * nodeR, 2 * nodeR);
                g2.setColor(c);
                g2.fill(e);
                boolean latest = placed && rank[v] == shown - 1 && shown < n;
                g2.setColor(latest ? Color.BLACK : c.darker());
                g2.setStroke(new BasicStroke(latest ? 3.5f : 1.5f));
                g2.draw(e);
                text(g2, String.valueOf(v + 1), p[v].x, p[v].y, placed && !darkText(c) ? Color.WHITE : Color.BLACK, nf);
            }

            // Timetable grid in the same colours
            int gx = graphW + 30, gy = 90;
            int cols = Math.max(1, r);
            int cellW = Math.max(34, Math.min(70, (width - gx - 20 - 70) / cols));
            int cellH = Math.max(18, Math.min(34, (height - gy - 30) / Math.max(1, s.slots + 1)));
            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            g2.setColor(Color.BLACK);
            g2.drawString("Timetable (rows = slots, columns = halls)", gx, gy - 12);
            Font cf = new Font("SansSerif", Font.BOLD, Math.max(10, Math.min(14, cellH - 6)));
            for (int c = 0; c < cols; c++) text(g2, "Hall " + (c + 1), gx + 70 + c * cellW + cellW / 2.0, gy + cellH / 2.0, Color.DARK_GRAY, cf);
            List<List<Integer>> lists = s.slotLists();
            for (int sl = 0; sl < s.slots; sl++) {
                int y = gy + (sl + 1) * cellH;
                text(g2, "Slot " + (sl + 1), gx + 35, y + cellH / 2.0, Color.BLACK, cf);
                int col = 0;
                for (int course : lists.get(sl)) {
                    if (rank[course - 1] >= shown) continue;
                    int x = gx + 70 + col * cellW;
                    Color c = slotColor(sl);
                    g2.setColor(c);
                    g2.fillRect(x + 1, y + 1, cellW - 2, cellH - 2);
                    text(g2, String.valueOf(course), x + cellW / 2.0, y + cellH / 2.0, darkText(c) ? Color.BLACK : Color.WHITE, cf);
                    col++;
                }
                g2.setColor(new Color(200, 200, 205));
                g2.setStroke(new BasicStroke(1f));
                for (int c = 0; c < cols; c++) g2.drawRect(gx + 70 + c * cellW, y, cellW, cellH);
            }
            if (shown < n) {
                g2.setFont(b);
                g2.setColor(Color.BLACK);
                g2.drawString("Assigned " + shown + " / " + n + " courses (order of the algorithm)", gx, gy + (s.slots + 1) * cellH + 24);
            }
        }
    }

    // ------------------------------------------------------------------ comparison chart

    static void paintComparison(Graphics2D g2, int width, int height, List<ExamComparison.Row> rows, String subtitle) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2.drawString("DSatur vs Welsh-Powell vs Exact branch and bound (" + subtitle + ")", 20, 26);
        if (rows == null || rows.isEmpty()) {
            g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g2.drawString("Press \"Run comparison\" to measure.", 20, 60);
            return;
        }
        int pw = (width - 60) / 2, ph = height - 110;
        double[][] slots = new double[4][rows.size()], times = new double[3][rows.size()];
        double[] xs = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            xs[i] = rows.get(i).n;
            for (int a = 0; a < 3; a++) { slots[a][i] = rows.get(i).slots[a]; times[a][i] = Math.max(1e-3, rows.get(i).millis[a]); }
            slots[3][i] = rows.get(i).lowerBound;
        }
        String[] names = {"DSatur", "Welsh-Powell", "Exact B&B", "Lower bound"};
        Color[] colors = {ALG_COLORS[0], ALG_COLORS[1], ALG_COLORS[2], Color.GRAY};
        lineChart(g2, 20, 50, pw, ph, xs, slots, names, colors, "Slots used (average)", false);
        lineChart(g2, 40 + pw, 50, pw, ph, xs, times, names, colors, "Running time (ms, log scale)", true);
    }

    private static void lineChart(Graphics2D g2, int x0, int y0, int w, int h, double[] xs, double[][] ys, String[] names,
                                  Color[] colors, String title, boolean log) {
        int left = 60, bottom = 40, top = 30, right = 10;
        int px = x0 + left, py = y0 + top, pw = w - left - right, ph = h - top - bottom;
        double xmin = xs[0], xmax = xs[xs.length - 1];
        double ymin = Double.MAX_VALUE, ymax = -Double.MAX_VALUE;
        for (double[] s : ys) for (double v : s) { double t = log ? Math.log10(v) : v; ymin = Math.min(ymin, t); ymax = Math.max(ymax, t); }
        double step = 1;
        if (log) { ymin = Math.floor(ymin); ymax = Math.ceil(ymax); }
        else {
            ymin = 0;
            step = Math.max(1, Math.ceil((ymax + 1) / 8.0));      // integer tick spacing
            ymax = step * Math.ceil((ymax + 1) / step);
        }
        if (ymax <= ymin) ymax = ymin + 1;
        if (xmax <= xmin) xmax = xmin + 1;
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2.drawString(title, px, y0 + 16);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.setStroke(new BasicStroke(1f));
        int yTicks = log ? (int) (ymax - ymin) : (int) Math.round((ymax - ymin) / step);
        for (int t = 0; t <= yTicks; t++) {
            double val = ymin + (ymax - ymin) * t / yTicks;
            int yy = (int) (py + ph - ph * (val - ymin) / (ymax - ymin));
            g2.setColor(new Color(230, 230, 235));
            g2.drawLine(px, yy, px + pw, yy);
            g2.setColor(Color.DARK_GRAY);
            String lab = log ? (val >= 0 ? String.format(Locale.US, "%.0f", Math.pow(10, val)) : String.format(Locale.US, "%." + (int) -val + "f", Math.pow(10, val)))
                    : String.format(Locale.US, "%.0f", val);
            g2.drawString(lab, px - 8 - g2.getFontMetrics().stringWidth(lab), yy + 4);
        }
        for (double xv : xs) {
            int xx = (int) (px + pw * (xv - xmin) / (xmax - xmin));
            g2.setColor(Color.DARK_GRAY);
            String lab = String.valueOf((int) xv);
            g2.drawString(lab, xx - g2.getFontMetrics().stringWidth(lab) / 2, py + ph + 16);
        }
        g2.drawString("N (number of courses)", px + pw / 2 - 50, py + ph + 32);
        g2.setColor(Color.BLACK);
        g2.drawRect(px, py, pw, ph);
        for (int sIdx = 0; sIdx < ys.length; sIdx++) {
            if (ys[sIdx] == null) continue;
            Path2D.Double path = new Path2D.Double();
            g2.setColor(colors[sIdx]);
            g2.setStroke(sIdx == 3 ? new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{6f, 5f}, 0f)
                    : new BasicStroke(2.2f));
            for (int i = 0; i < xs.length; i++) {
                double v = log ? Math.log10(ys[sIdx][i]) : ys[sIdx][i];
                double xx = px + pw * (xs[i] - xmin) / (xmax - xmin), yy = py + ph - ph * (v - ymin) / (ymax - ymin);
                if (i == 0) path.moveTo(xx, yy); else path.lineTo(xx, yy);
                g2.fill(new Ellipse2D.Double(xx - 3, yy - 3, 6, 6));
            }
            g2.draw(path);
        }
        int ly = py + 10;
        for (int sIdx = 0; sIdx < ys.length; sIdx++) {
            g2.setColor(colors[sIdx]);
            g2.fillRect(px + 10, ly, 14, 4);
            g2.setColor(Color.BLACK);
            g2.drawString(names[sIdx], px + 30, ly + 6);
            ly += 16;
        }
    }

    static void text(Graphics2D g2, String s, double cx, double cy, Color c, Font f) {
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(c);
        g2.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) (cy - fm.getHeight() / 2.0 + fm.getAscent()));
    }

    // ------------------------------------------------------------------ snapshots

    public static void saveScene(ConflictGraph g, Schedule s, int r, int lb, String title, int shown, File file) throws IOException {
        BufferedImage img = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        new Scene(g, s, r, lb, title).paint(g2, 1280, 720, shown);
        g2.dispose();
        writePng(img, file);
    }

    public static void saveComparison(List<ExamComparison.Row> rows, String subtitle, File file) throws IOException {
        BufferedImage img = new BufferedImage(1280, 560, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        paintComparison(g2, 1280, 560, rows, subtitle);
        g2.dispose();
        writePng(img, file);
    }

    private static void writePng(BufferedImage img, File file) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) parent.mkdirs();
        ImageIO.write(img, "png", file);
    }

    // ------------------------------------------------------------------ interactive window

    public static void show(ConflictGraph g, List<Schedule> schedules, int r, int lb, String title,
                            double density, List<ExamComparison.Row> initialRows) {
        SwingUtilities.invokeLater(() -> {
            int[] current = {0};
            int[] shown = {g.size()};
            Scene[] scene = {new Scene(g, schedules.get(0), r, lb, title)};
            JPanel canvas = new JPanel() {
                @Override
                protected void paintComponent(Graphics gr) {
                    super.paintComponent(gr);
                    scene[0].paint((Graphics2D) gr, getWidth(), getHeight(), shown[0]);
                }
            };
            canvas.setPreferredSize(new Dimension(1280, 680));
            String[] names = new String[schedules.size()];
            for (int i = 0; i < names.length; i++) names[i] = schedules.get(i).algorithm + " (" + schedules.get(i).slots + " slots)";
            JComboBox<String> pick = new JComboBox<>(names);
            JButton animate = new JButton("Animate assignment order"), full = new JButton("Show final timetable");
            Timer timer = new Timer(Math.max(60, 3000 / Math.max(1, g.size())), e -> {
                shown[0]++;
                if (shown[0] >= g.size()) ((Timer) e.getSource()).stop();
                canvas.repaint();
            });
            pick.addActionListener(e -> {
                current[0] = pick.getSelectedIndex();
                scene[0] = new Scene(g, schedules.get(current[0]), r, lb, title);
                timer.stop();
                shown[0] = g.size();
                canvas.repaint();
            });
            animate.addActionListener(e -> { shown[0] = 0; timer.restart(); canvas.repaint(); });
            full.addActionListener(e -> { timer.stop(); shown[0] = g.size(); canvas.repaint(); });
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            top.add(new JLabel("Algorithm:"));
            top.add(pick); top.add(animate); top.add(full);
            JPanel tab1 = new JPanel(new BorderLayout());
            tab1.add(top, BorderLayout.NORTH);
            tab1.add(canvas, BorderLayout.CENTER);

            java.util.concurrent.atomic.AtomicReference<List<ExamComparison.Row>> rows =
                    new java.util.concurrent.atomic.AtomicReference<>(initialRows);
            String sub = String.format(Locale.US, "density p = %.2f, R = %d", density, r);
            JPanel chart = new JPanel() {
                @Override
                protected void paintComponent(Graphics gr) {
                    super.paintComponent(gr);
                    paintComparison((Graphics2D) gr, getWidth(), getHeight(), rows.get(), sub);
                }
            };
            JButton run = new JButton("Run comparison (N = 5..40, 3 seeds, 5 s limit)");
            JLabel status = new JLabel(" ");
            run.addActionListener(e -> {
                run.setEnabled(false);
                status.setText("Running...");
                new SwingWorker<List<ExamComparison.Row>, Void>() {
                    @Override
                    protected List<ExamComparison.Row> doInBackground() {
                        return ExamComparison.run(new int[]{5, 10, 15, 20, 25, 30, 35, 40}, density, r, 3, 5000);
                    }

                    @Override
                    protected void done() {
                        try { rows.set(get()); status.setText("Done."); }
                        catch (Exception ex) { status.setText("Failed: " + ex.getMessage()); }
                        run.setEnabled(true);
                        chart.repaint();
                    }
                }.execute();
            });
            JPanel tab2 = new JPanel(new BorderLayout());
            JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
            bar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            bar.add(run); bar.add(status);
            tab2.add(bar, BorderLayout.NORTH);
            tab2.add(chart, BorderLayout.CENTER);

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Conflict graph + timetable", tab1);
            tabs.addTab("Comparison as N increases", tab2);
            JFrame frame = new JFrame("Problem 4 - Exam Timetable Scheduling (graph colouring with capacity R)");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.add(tabs);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
