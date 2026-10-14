import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
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
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Swing visualisation of the flight graph and the cheapest route.
 * Start = green, destination = red, intermediate stops = orange. The chosen route is
 * highlighted; the animation moves a plane along it, shows the running cost and the stops
 * used, and turns a flight gold when a coupon is applied ("original -> discounted" price).
 */
public final class BudgetVisualizer {
    static final Color START = new Color(0, 160, 70), DEST = new Color(215, 30, 30), STOP = new Color(255, 140, 0);
    static final Color OTHER = new Color(200, 210, 225), ROUTE = new Color(30, 100, 230), GOLD = new Color(218, 165, 32);
    static final int SIDEBAR = 330, R = 20;

    private BudgetVisualizer() { }

    static final class Scene {
        final FlightGraph g;
        final BudgetSolver.Result res;
        final int k, c;
        final String title;
        final Map<Integer, Integer> legOf = new HashMap<>(); // flight index -> first leg index on the route
        int[] column = new int[0]; // layout column of each city (set by layout)

        Scene(FlightGraph g, BudgetSolver.Result res, int k, int c, String title) {
            this.g = g; this.res = res; this.k = k; this.c = c; this.title = title;
            for (int i = 0; i < res.legs.size(); i++) legOf.putIfAbsent(res.legs.get(i).flight.index, i);
        }

        int legs() { return res.legs.size(); }

        /** Layered layout: column = BFS level (number of flights) from city 1. */
        Point2D.Double[] layout(int x0, int y0, int w, int h) {
            int n = g.cities();
            int[] level = new int[n + 1];
            java.util.Arrays.fill(level, -1);
            List<List<Integer>> out = new ArrayList<>();
            for (int i = 0; i <= n; i++) out.add(new ArrayList<>());
            for (FlightGraph.Flight f : g.flights()) out.get(f.from).add(f.to);
            ArrayDeque<Integer> q = new ArrayDeque<>();
            level[1] = 0;
            q.add(1);
            while (!q.isEmpty()) {
                int u = q.poll();
                for (int v : out.get(u)) if (level[v] < 0) { level[v] = level[u] + 1; q.add(v); }
            }
            // Start in column 0, destination alone in the right-most column, unreachable cities in the middle.
            int maxOther = 0;
            for (int v = 2; v < n; v++) maxOther = Math.max(maxOther, level[v]);
            int maxLevel = n == 1 ? 0 : (n == 2 ? 1 : Math.max(2, maxOther + 1));
            for (int v = 2; v < n; v++) if (level[v] < 0) level[v] = Math.max(1, maxLevel / 2);
            if (n > 1) level[n] = maxLevel;
            List<List<Integer>> cols = new ArrayList<>();
            for (int i = 0; i <= maxLevel; i++) cols.add(new ArrayList<>());
            for (int v = 1; v <= n; v++) cols.get(Math.max(0, level[v])).add(v);
            column = level;
            Point2D.Double[] pos = new Point2D.Double[n + 1];
            for (int col = 0; col <= maxLevel; col++) {
                List<Integer> list = cols.get(col);
                double x = maxLevel == 0 ? x0 + w / 2.0 : x0 + col * (double) w / maxLevel;
                for (int i = 0; i < list.size(); i++) {
                    double y = y0 + (i + 1) * (double) h / (list.size() + 1);
                    if (list.size() > 1) y += ((col % 2 == 0) ? -1 : 1) * h / (list.size() + 1) * 0.2;
                    double cx = x;
                    if (list.size() > 2 && maxLevel > 0) cx += (i % 2 == 0 ? -1 : 1) * Math.min(45, 0.22 * w / maxLevel); // zig-zag avoids straight overlaps
                    pos[list.get(i)] = new Point2D.Double(cx, y);
                }
            }
            return pos;
        }

        void paint(Graphics2D g2, int width, int height, double pos) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(new Color(247, 248, 252));
            g2.fillRect(0, 0, width, height);
            Point2D.Double[] p = layout(60, 50, width - SIDEBAR - 120, height - 100);
            int n = g.cities();
            int leg = (int) Math.floor(pos);
            double frac = pos - leg;

            // Group flights between the same pair of cities to curve them apart.
            Map<Long, List<FlightGraph.Flight>> pairs = new HashMap<>();
            for (FlightGraph.Flight f : g.flights()) {
                long key = (long) Math.min(f.from, f.to) * (n + 1) + Math.max(f.from, f.to);
                pairs.computeIfAbsent(key, x -> new ArrayList<>()).add(f);
            }
            Font label = new Font("SansSerif", Font.BOLD, 12);
            List<Runnable> routeDraws = new ArrayList<>();
            for (List<FlightGraph.Flight> group : pairs.values()) {
                for (FlightGraph.Flight f : group) {
                    Integer li = legOf.get(f.index);
                    final double b = bendOf(f, pairs, n);
                    if (li == null) {
                        drawFlight(g2, p[f.from], p[f.to], b, new Color(150, 160, 175), 1.4f, false,
                                String.valueOf(f.price), new Color(90, 90, 90), label);
                    } else {
                        routeDraws.add(() -> drawRouteFlight(g2, p, f, b, li, leg, frac, label));
                    }
                }
            }
            for (Runnable r : routeDraws) r.run(); // route on top

            // Nodes
            Font nodeFont = new Font("SansSerif", Font.BOLD, 14);
            java.util.Set<Integer> stops = new java.util.HashSet<>();
            for (int i = 0; i + 1 < res.legs.size(); i++) stops.add(res.legs.get(i).flight.to);
            for (int v = 1; v <= n; v++) {
                Color col = v == 1 ? START : v == n ? DEST : stops.contains(v) ? STOP : OTHER;
                Ellipse2D.Double e = new Ellipse2D.Double(p[v].x - R, p[v].y - R, 2 * R, 2 * R);
                g2.setColor(col);
                g2.fill(e);
                g2.setColor(col.darker());
                g2.setStroke(new BasicStroke(2f));
                g2.draw(e);
                text(g2, String.valueOf(v), p[v].x, p[v].y, v == 1 || v == n || stops.contains(v) ? Color.WHITE : Color.BLACK, nodeFont);
            }
            // Plane
            if (res.feasible() && legs() > 0) {
                int li = Math.min(leg, legs() - 1);
                double t = leg >= legs() ? 1 : frac;
                Point2D.Double at = pointOn(p, res.legs.get(li).flight, bendOf(res.legs.get(li).flight, pairs, n), t);
                g2.setColor(new Color(20, 30, 90));
                g2.fill(new Ellipse2D.Double(at.x - 9, at.y - 9, 18, 18));
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new Ellipse2D.Double(at.x - 9, at.y - 9, 18, 18));
            }
            paintSidebar(g2, width - SIDEBAR + 10, 20, pos);
        }

        private double bendOf(FlightGraph.Flight f, Map<Long, List<FlightGraph.Flight>> pairs, int n) {
            List<FlightGraph.Flight> group = pairs.get((long) Math.min(f.from, f.to) * (n + 1) + Math.max(f.from, f.to));
            if (f.from == f.to) return 0;
            int span = column.length > Math.max(f.from, f.to) ? Math.abs(column[f.from] - column[f.to]) : 1;
            if (group.size() == 1) return span > 1 ? 16.0 * (span - 1) : 0; // arc long flights around nodes
            // spread parallel/opposite flights between the same two cities; one side per direction
            double bend = (group.indexOf(f) - (group.size() - 1) / 2.0) * 34;
            return f.from > f.to ? -bend : bend; // the perpendicular flips with direction
        }

        private void drawRouteFlight(Graphics2D g2, Point2D.Double[] p, FlightGraph.Flight f, double bend, int li,
                                     int leg, double frac, Font label) {
            BudgetSolver.Leg l = res.legs.get(li);
            boolean travelled = leg > li || (leg == li && frac > 0);
            boolean couponShown = l.coupon && travelled;
            Color col = couponShown ? GOLD : ROUTE;
            String text = couponShown ? l.flight.price + " → " + l.paid : String.valueOf(l.flight.price);
            drawFlight(g2, p[f.from], p[f.to], bend, col, travelled ? 4.5f : 3f, !travelled, text,
                    couponShown ? new Color(140, 95, 0) : ROUTE.darker(), label);
        }

        private void paintSidebar(Graphics2D g2, int x, int y, double pos) {
            Font h = new Font("SansSerif", Font.BOLD, 16), b = new Font("SansSerif", Font.PLAIN, 13), bb = new Font("SansSerif", Font.BOLD, 13);
            g2.setColor(new Color(255, 255, 255));
            g2.fill(new RoundRectangle2D.Double(x - 10, y - 10, SIDEBAR - 10, 10000, 14, 14));
            g2.setColor(Color.BLACK);
            g2.setFont(h);
            g2.drawString(title, x, y + 14);
            g2.setFont(b);
            int ly = y + 38;
            g2.drawString("Cities N = " + g.cities() + ", flights M = " + g.flights().size(), x, ly); ly += 18;
            g2.drawString("Max stops K = " + k + " (<= " + (k + 1) + " flights), coupons C = " + c, x, ly); ly += 26;
            if (!res.feasible()) {
                g2.setColor(DEST);
                g2.setFont(h);
                g2.drawString("No valid route: output -1", x, ly);
                ly += 30;
            } else {
                int done = Math.min(legs(), (int) Math.floor(pos));
                long running = done == 0 ? 0 : res.legs.get(done - 1).runningCost;
                int couponsUsed = 0;
                for (int i = 0; i < done; i++) if (res.legs.get(i).coupon) couponsUsed++;
                int stopsUsed = done == legs() ? Math.max(0, legs() - 1) : done; // cities landed in before N
                g2.setFont(h);
                g2.drawString("Running cost: " + running, x, ly); ly += 22;
                g2.setFont(b);
                g2.drawString("Flights taken: " + done + " / " + legs(), x, ly); ly += 18;
                g2.drawString("Stops used: " + stopsUsed + " / K = " + k, x, ly); ly += 18;
                g2.drawString("Coupons used: " + couponsUsed + " / C = " + c, x, ly); ly += 18;
                g2.setFont(bb);
                g2.drawString("Minimum cost (Output 1): " + res.cost, x, ly); ly += 26;
                g2.setFont(h);
                g2.drawString("Route", x, ly); ly += 20;
                g2.setFont(b);
                for (int i = 0; i < legs(); i++) {
                    BudgetSolver.Leg l = res.legs.get(i);
                    boolean shown = i < done;
                    g2.setColor(shown ? (l.coupon ? new Color(150, 100, 0) : Color.BLACK) : Color.GRAY);
                    String s = (i + 1) + ". " + l.flight.from + " → " + l.flight.to + ":  "
                            + (l.coupon ? l.flight.price + " → " + l.paid + " (coupon)" : String.valueOf(l.paid))
                            + "   total " + l.runningCost;
                    g2.drawString(s, x, ly);
                    ly += 18;
                    if (ly > 520) { g2.drawString("...", x, ly); ly += 18; break; }
                }
                ly += 10;
            }
            g2.setFont(h);
            g2.setColor(Color.BLACK);
            g2.drawString("Legend", x, ly); ly += 8;
            g2.setFont(b);
            ly = legend(g2, x, ly, START, "Start city 1", true);
            ly = legend(g2, x, ly, DEST, "Destination city N", true);
            ly = legend(g2, x, ly, STOP, "Intermediate stop", true);
            ly = legend(g2, x, ly, OTHER, "Other city", true);
            ly = legend(g2, x, ly, ROUTE, "Chosen route (dashed = not yet flown)", false);
            ly = legend(g2, x, ly, GOLD, "Coupon flight: original → discounted", false);
            legend(g2, x, ly, new Color(150, 160, 175), "Other flight (label = price)", false);
        }

        private int legend(Graphics2D g2, int x, int y, Color c, String s, boolean circle) {
            g2.setColor(c);
            if (circle) g2.fill(new Ellipse2D.Double(x, y + 4, 16, 16));
            else g2.fillRect(x, y + 10, 18, 5);
            g2.setColor(Color.BLACK);
            g2.drawString(s, x + 26, y + 17);
            return y + 22;
        }
    }

    static Point2D.Double control(Point2D.Double a, Point2D.Double b, double bend) {
        double mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2, dx = b.x - a.x, dy = b.y - a.y;
        double len = Math.max(1, Math.hypot(dx, dy));
        return new Point2D.Double(mx - dy / len * bend * 2, my + dx / len * bend * 2);
    }

    static Point2D.Double quad(Point2D.Double a, Point2D.Double c, Point2D.Double b, double t) {
        double u = 1 - t;
        return new Point2D.Double(u * u * a.x + 2 * u * t * c.x + t * t * b.x, u * u * a.y + 2 * u * t * c.y + t * t * b.y);
    }

    static Point2D.Double pointOn(Point2D.Double[] p, FlightGraph.Flight f, double bend, double t) {
        if (f.from == f.to) {
            double ang = t * 2 * Math.PI;
            return new Point2D.Double(p[f.from].x + Math.sin(ang) * 22, p[f.from].y - R - 22 + Math.cos(ang) * 22);
        }
        return quad(p[f.from], control(p[f.from], p[f.to], bend), p[f.to], t);
    }

    static void drawFlight(Graphics2D g2, Point2D.Double a, Point2D.Double b, double bend, Color color, float width,
                           boolean dashed, String label, Color labelColor, Font font) {
        g2.setColor(color);
        g2.setStroke(dashed
                ? new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{9f, 7f}, 0f)
                : new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Point2D.Double mid;
        if (a == b) { // self loop
            Ellipse2D.Double loop = new Ellipse2D.Double(a.x - 16, a.y - R - 30, 32, 32);
            g2.draw(loop);
            mid = new Point2D.Double(a.x, a.y - R - 36);
        } else {
            Point2D.Double c = control(a, b, bend);
            // trim the curve so it starts/ends on the node border
            Point2D.Double s = quad(a, c, b, trimT(a, c, b, true)), e = quad(a, c, b, trimT(a, c, b, false));
            Point2D.Double c2 = control(s, e, bend * Math.hypot(e.x - s.x, e.y - s.y) / Math.max(1, Math.hypot(b.x - a.x, b.y - a.y)));
            g2.draw(new QuadCurve2D.Double(s.x, s.y, c2.x, c2.y, e.x, e.y));
            Point2D.Double before = quad(s, c2, e, 0.9);
            arrowHead(g2, before, e, color);
            mid = quad(a, c, b, 0.5);
        }
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(label) + 8;
        g2.setColor(new Color(255, 255, 255, 230));
        g2.fill(new RoundRectangle2D.Double(mid.x - w / 2.0, mid.y - 9, w, 18, 8, 8));
        g2.setColor(labelColor);
        g2.drawString(label, (float) (mid.x - fm.stringWidth(label) / 2.0), (float) (mid.y + fm.getAscent() / 2.0 - 1));
    }

    private static double trimT(Point2D.Double a, Point2D.Double c, Point2D.Double b, boolean fromStart) {
        double lo = 0, hi = 0.5;
        Point2D.Double center = fromStart ? a : b;
        for (int i = 0; i < 30; i++) {
            double mid = (lo + hi) / 2;
            Point2D.Double q = quad(a, c, b, fromStart ? mid : 1 - mid);
            if (q.distance(center) < R + 2) lo = mid; else hi = mid;
        }
        return fromStart ? hi : 1 - hi;
    }

    private static void arrowHead(Graphics2D g2, Point2D.Double from, Point2D.Double tip, Color color) {
        double ang = Math.atan2(tip.y - from.y, tip.x - from.x), len = 12, spread = 0.42;
        Path2D.Double head = new Path2D.Double();
        head.moveTo(tip.x, tip.y);
        head.lineTo(tip.x - len * Math.cos(ang - spread), tip.y - len * Math.sin(ang - spread));
        head.lineTo(tip.x - len * Math.cos(ang + spread), tip.y - len * Math.sin(ang + spread));
        head.closePath();
        g2.setColor(color);
        g2.fill(head);
    }

    static void text(Graphics2D g2, String s, double cx, double cy, Color c, Font f) {
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(c);
        g2.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) (cy - fm.getHeight() / 2.0 + fm.getAscent()));
    }

    public static void saveSnapshot(FlightGraph g, BudgetSolver.Result r, int k, int c, String title, double pos,
                                    File file, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        new Scene(g, r, k, c, title).paint(g2, width, height, pos);
        g2.dispose();
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) parent.mkdirs();
        ImageIO.write(img, "png", file);
    }

    public static void show(FlightGraph g, BudgetSolver.Result r, int k, int c, String title) {
        SwingUtilities.invokeLater(() -> {
            Scene scene = new Scene(g, r, k, c, title);
            double[] pos = {0};
            JPanel canvas = new JPanel() {
                @Override
                protected void paintComponent(Graphics gr) {
                    super.paintComponent(gr);
                    scene.paint((Graphics2D) gr, getWidth(), getHeight(), pos[0]);
                }
            };
            canvas.setPreferredSize(new Dimension(1180, 640));
            JButton play = new JButton("Play journey"), step = new JButton("Next flight"), reset = new JButton("Reset"),
                    end = new JButton("Show full route");
            JSlider speed = new JSlider(1, 10, 3);
            JLabel sl = new JLabel("Speed");
            double[] perTick = {0.01 * 3};
            Timer timer = new Timer(30, e -> {
                pos[0] = Math.min(scene.legs(), pos[0] + perTick[0]);
                if (pos[0] >= scene.legs()) { ((Timer) e.getSource()).stop(); play.setText("Play journey"); }
                canvas.repaint();
            });
            play.addActionListener(e -> {
                if (timer.isRunning()) { timer.stop(); play.setText("Play journey"); return; }
                if (pos[0] >= scene.legs()) pos[0] = 0;
                timer.start();
                play.setText("Pause");
            });
            step.addActionListener(e -> { timer.stop(); play.setText("Play journey"); pos[0] = Math.min(scene.legs(), Math.floor(pos[0]) + 1); canvas.repaint(); });
            reset.addActionListener(e -> { timer.stop(); play.setText("Play journey"); pos[0] = 0; canvas.repaint(); });
            end.addActionListener(e -> { timer.stop(); play.setText("Play journey"); pos[0] = scene.legs(); canvas.repaint(); });
            speed.addChangeListener(e -> perTick[0] = 0.01 * speed.getValue());
            if (!r.feasible() || r.legs.isEmpty()) { play.setEnabled(false); step.setEnabled(false); end.setEnabled(false); }
            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
            controls.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            controls.add(play); controls.add(step); controls.add(reset); controls.add(end); controls.add(sl); controls.add(speed);
            JFrame frame = new JFrame("Problem 3 - Budget Traveller with Coupons");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setLayout(new BorderLayout());
            frame.add(canvas, BorderLayout.CENTER);
            frame.add(controls, BorderLayout.SOUTH);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
