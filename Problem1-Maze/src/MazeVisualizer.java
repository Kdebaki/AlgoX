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
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Swing visualisation of the maze and the BFS shortest path.
 *
 * Colours (as required): white = open, black = wall, green = start, red = exit,
 * every key/door pair has its own colour. The path taken is highlighted; while the
 * animation runs, a key shrinks and disappears when it is picked up (the inventory
 * panel updates) and a door fades from its colour to white and shows an unlock mark.
 */
public final class MazeVisualizer {
    static final Color[] PAIR_COLORS = {
            new Color(255, 140, 0),   // a / A  orange
            new Color(30, 110, 255),  // b / B  blue
            new Color(160, 60, 220),  // c / C  purple
            new Color(0, 165, 165),   // d / D  teal
            new Color(230, 40, 160),  // e / E  magenta
            new Color(150, 100, 40),  // f / F  brown
    };
    static final Color START_GREEN = new Color(0, 170, 60);
    static final Color EXIT_RED = new Color(220, 30, 30);
    static final Color PATH_YELLOW = new Color(255, 230, 120);
    static final Color AGENT = new Color(20, 40, 160);
    static final int SIDEBAR = 300;

    private MazeVisualizer() { }

    /** Everything needed to paint one frame. pos = step index (fractional during movement). */
    static final class Scene {
        final Maze maze;
        final MazeSolver.Result result;
        final String title;
        final Map<Integer, Integer> firstVisit = new HashMap<>(); // cell -> first step index on the path

        Scene(Maze maze, MazeSolver.Result result, String title) {
            this.maze = maze; this.result = result; this.title = title;
            int n = maze.size();
            for (int i = 0; i < result.steps.size(); i++) {
                MazeSolver.Step s = result.steps.get(i);
                firstVisit.putIfAbsent(s.row * n + s.col, i);
            }
        }

        int lastStep() { return Math.max(0, result.steps.size() - 1); }

        void paint(Graphics2D g, int width, int height, double pos) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(245, 246, 250));
            g.fillRect(0, 0, width, height);
            int n = maze.size();
            int margin = 16;
            int avail = Math.min(width - SIDEBAR - 2 * margin, height - 2 * margin);
            int cs = Math.max(4, avail / n);
            int ox = margin, oy = margin;
            int step = (int) Math.floor(pos);
            double frac = pos - step;
            boolean animating = frac > 1e-9 && step + 1 <= lastStep();

            // Cells
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    int code = maze.cell(r, c);
                    int x = ox + c * cs, y = oy + r * cs;
                    Integer visit = firstVisit.get(r * n + c);
                    paintCell(g, code, x, y, cs, visit, step, frac, animating);
                }
            }
            // Path highlight: whole path faintly, travelled part strongly
            if (result.reachable()) {
                g.setStroke(new BasicStroke(Math.max(1f, cs / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        1f, new float[]{cs / 4f, cs / 4f}, 0f));
                g.setColor(new Color(120, 120, 120, 160));
                g.draw(polyline(0, lastStep(), ox, oy, cs, 0));
                g.setStroke(new BasicStroke(Math.max(2f, cs / 6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(30, 90, 220));
                g.draw(polyline(0, Math.min(step, lastStep()), ox, oy, cs, animating ? frac : 0));
                // Agent
                double[] a = agentCenter(step, frac, ox, oy, cs);
                double rad = cs * 0.32;
                g.setColor(AGENT);
                g.fill(new Ellipse2D.Double(a[0] - rad, a[1] - rad, 2 * rad, 2 * rad));
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(Math.max(1f, cs / 16f)));
                g.draw(new Ellipse2D.Double(a[0] - rad, a[1] - rad, 2 * rad, 2 * rad));
            }
            // Grid lines
            g.setStroke(new BasicStroke(1f));
            g.setColor(new Color(200, 200, 200));
            for (int i = 0; i <= n; i++) {
                g.drawLine(ox, oy + i * cs, ox + n * cs, oy + i * cs);
                g.drawLine(ox + i * cs, oy, ox + i * cs, oy + n * cs);
            }
            paintSidebar(g, ox + n * cs + 24, oy, height, step);
        }

        private void paintCell(Graphics2D g, int code, int x, int y, int cs, Integer visit, int step,
                               double frac, boolean animating) {
            Font letterFont = new Font("SansSerif", Font.BOLD, Math.max(8, (int) (cs * 0.5)));
            if (code == Maze.WALL) { g.setColor(Color.BLACK); g.fillRect(x, y, cs, cs); return; }
            if (code == Maze.START) { g.setColor(START_GREEN); g.fillRect(x, y, cs, cs); centered(g, "S", x, y, cs, Color.WHITE, letterFont); return; }
            if (code == Maze.EXIT) { g.setColor(EXIT_RED); g.fillRect(x, y, cs, cs); centered(g, "E", x, y, cs, Color.WHITE, letterFont); return; }
            boolean done = visit != null && visit <= step; // cell already on the travelled path
            g.setColor(done ? PATH_YELLOW : Color.WHITE);
            g.fillRect(x, y, cs, cs);
            if (code == Maze.OPEN) return;
            Color pc = PAIR_COLORS[Maze.typeOf(code)];
            String letter = String.valueOf(Maze.letter(code));
            boolean inTransition = visit != null && animating && visit == step + 1;
            if (Maze.isKey(code)) {
                if (done) return; // key picked up -> disappears
                double scale = inTransition ? 1 - frac : 1;
                double d = cs * 0.7 * scale;
                g.setColor(pc);
                g.fill(new Ellipse2D.Double(x + (cs - d) / 2, y + (cs - d) / 2, d, d));
                if (scale > 0.4) centered(g, letter, x, y, cs, Color.WHITE, letterFont);
            } else { // door
                if (done) { // opened: white with unlock mark
                    drawPadlock(g, x, y, cs, pc, true);
                    return;
                }
                double t = inTransition ? frac : 0;
                g.setColor(blend(pc, Color.WHITE, t));
                g.fillRect(x + 1, y + 1, cs - 1, cs - 1);
                if (t > 0.5) drawPadlock(g, x, y, cs, pc, true);
                else {
                    centered(g, letter, x, y, cs, Color.WHITE, letterFont);
                    g.setColor(Color.WHITE);
                    g.setStroke(new BasicStroke(Math.max(1f, cs / 14f)));
                    g.drawRect(x + 2, y + 2, cs - 4, cs - 4);
                }
            }
        }

        private Path2D polyline(int from, int to, int ox, int oy, int cs, double extra) {
            Path2D p = new Path2D.Double();
            for (int i = from; i <= to; i++) {
                MazeSolver.Step s = result.steps.get(i);
                double px = ox + s.col * cs + cs / 2.0, py = oy + s.row * cs + cs / 2.0;
                if (i == from) p.moveTo(px, py); else p.lineTo(px, py);
            }
            if (extra > 0 && to + 1 < result.steps.size()) {
                double[] a = agentCenter(to, extra, ox, oy, cs);
                p.lineTo(a[0], a[1]);
            }
            return p;
        }

        private double[] agentCenter(int step, double frac, int ox, int oy, int cs) {
            int i = Math.min(step, lastStep());
            MazeSolver.Step a = result.steps.get(i);
            MazeSolver.Step b = result.steps.get(Math.min(i + 1, lastStep()));
            double col = a.col + (b.col - a.col) * frac, row = a.row + (b.row - a.row) * frac;
            return new double[]{ox + col * cs + cs / 2.0, oy + row * cs + cs / 2.0};
        }

        private void paintSidebar(Graphics2D g, int x, int y, int height, int step) {
            Font h = new Font("SansSerif", Font.BOLD, 16), b = new Font("SansSerif", Font.PLAIN, 13);
            g.setColor(Color.BLACK);
            g.setFont(h);
            g.drawString(title, x, y + 14);
            g.setFont(b);
            int lineY = y + 38;
            if (!result.reachable()) {
                g.setColor(EXIT_RED);
                g.setFont(h);
                g.drawString("No path: output -1", x, lineY);
                g.setFont(b);
                lineY += 26;
            } else {
                int shown = Math.min(step, lastStep());
                g.drawString("Shortest path length: " + result.length, x, lineY); lineY += 18;
                g.drawString("Step " + shown + " / " + result.length, x, lineY); lineY += 18;
                MazeSolver.Step s = result.steps.get(shown);
                g.drawString("Position: row " + (s.row + 1) + ", col " + (s.col + 1), x, lineY); lineY += 26;
            }
            // Key inventory
            g.setFont(h);
            g.setColor(Color.BLACK);
            g.drawString("Key inventory", x, lineY); lineY += 10;
            int mask = result.reachable() ? result.steps.get(Math.min(step, lastStep())).mask : 0;
            for (int k = 0; k < maze.types(); k++) {
                int bx = x + k * 44;
                RoundRectangle2D box = new RoundRectangle2D.Double(bx, lineY, 38, 38, 10, 10);
                boolean held = (mask & (1 << k)) != 0;
                g.setColor(held ? PAIR_COLORS[k] : new Color(225, 225, 225));
                g.fill(box);
                g.setColor(held ? PAIR_COLORS[k].darker() : new Color(170, 170, 170));
                g.setStroke(new BasicStroke(2f));
                g.draw(box);
                centered(g, String.valueOf((char) ('a' + k)), bx, lineY, 38, held ? Color.WHITE : new Color(150, 150, 150), h);
            }
            lineY += 58;
            // Events so far
            g.setFont(h);
            g.setColor(Color.BLACK);
            g.drawString("Events", x, lineY); lineY += 18;
            g.setFont(b);
            List<String> events = new ArrayList<>();
            for (int i = 0; i <= Math.min(step, lastStep()) && result.reachable(); i++) {
                MazeSolver.Step s = result.steps.get(i);
                if (!s.event.isEmpty() && !s.event.startsWith("key ") && !s.event.startsWith("passed")) {
                    events.add("step " + i + ": " + s.event);
                }
            }
            for (int i = Math.max(0, events.size() - 8); i < events.size(); i++) {
                g.drawString(events.get(i), x, lineY);
                lineY += 17;
            }
            lineY += 12;
            // Legend
            g.setFont(h);
            g.drawString("Legend", x, lineY); lineY += 8;
            g.setFont(b);
            lineY = legend(g, x, lineY, Color.WHITE, "Open cell");
            lineY = legend(g, x, lineY, Color.BLACK, "Wall");
            lineY = legend(g, x, lineY, START_GREEN, "Start (S)");
            lineY = legend(g, x, lineY, EXIT_RED, "Exit (E)");
            for (int k = 0; k < maze.types(); k++) {
                lineY = legend(g, x, lineY, PAIR_COLORS[k], "Key " + (char) ('a' + k) + " / Door " + (char) ('A' + k));
            }
            lineY = legend(g, x, lineY, PATH_YELLOW, "Path taken (blue line = route)");
        }

        private int legend(Graphics2D g, int x, int y, Color c, String label) {
            g.setColor(c);
            g.fillRect(x, y + 4, 16, 16);
            g.setColor(Color.GRAY);
            g.setStroke(new BasicStroke(1f));
            g.drawRect(x, y + 4, 16, 16);
            g.setColor(Color.BLACK);
            g.drawString(label, x + 24, y + 17);
            return y + 22;
        }
    }

    static Color blend(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static void centered(Graphics2D g, String s, int x, int y, int size, Color color, Font font) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(color);
        g.drawString(s, x + (size - fm.stringWidth(s)) / 2, y + (size - fm.getHeight()) / 2 + fm.getAscent());
    }

    /** Open padlock = "unlock mark" drawn on a white (opened) door cell. */
    static void drawPadlock(Graphics2D g, int x, int y, int cs, Color color, boolean open) {
        double bw = cs * 0.5, bh = cs * 0.34;
        double bx = x + (cs - bw) / 2, by = y + cs * 0.5;
        g.setColor(color);
        g.fill(new RoundRectangle2D.Double(bx, by, bw, bh, cs * 0.08, cs * 0.08));
        g.setStroke(new BasicStroke(Math.max(1.5f, cs / 12f)));
        double sw = bw * 0.7, sh = cs * 0.38;
        double sx = bx + (bw - sw) / 2 + (open ? bw * 0.3 : 0);
        g.draw(new Arc2D.Double(sx, by - sh / 2 - (open ? cs * 0.08 : 0), sw, sh, 0, 180, Arc2D.OPEN));
        if (!open) g.drawLine((int) sx, (int) by, (int) sx, (int) (by - cs * 0.02));
    }

    /** Renders one frame to a PNG (used for report screenshots; works headless). */
    public static void saveSnapshot(Maze maze, MazeSolver.Result res, String title, double pos, File file,
                                    int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        new Scene(maze, res, title).paint(g, width, height, pos);
        g.dispose();
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null) parent.mkdirs();
        ImageIO.write(img, "png", file);
    }

    public static int[] preferredSize(Maze maze) {
        int cs = Math.max(14, Math.min(44, 760 / maze.size()));
        return new int[]{maze.size() * cs + 32 + SIDEBAR + 24, Math.max(560, maze.size() * cs + 32)};
    }

    /** Opens the interactive window (Play / Pause / Step / Reset / End, speed slider). */
    public static void show(Maze maze, MazeSolver.Result res, String title) {
        SwingUtilities.invokeLater(() -> {
            Scene scene = new Scene(maze, res, title);
            double[] pos = {0};
            JPanel canvas = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    scene.paint((Graphics2D) g, getWidth(), getHeight(), pos[0]);
                }
            };
            int[] size = preferredSize(maze);
            canvas.setPreferredSize(new Dimension(size[0], size[1]));

            JButton play = new JButton("Play"), stepBtn = new JButton("Step"), reset = new JButton("Reset"),
                    end = new JButton("Show full path");
            JSlider speed = new JSlider(1, 20, 4);
            JLabel speedLabel = new JLabel("Speed (steps/s): 4");
            final double[] stepsPerTick = {4 * 0.03};
            Timer timer = new Timer(30, e -> {
                pos[0] = Math.min(scene.lastStep(), pos[0] + stepsPerTick[0]);
                if (pos[0] >= scene.lastStep()) { ((Timer) e.getSource()).stop(); play.setText("Play"); }
                canvas.repaint();
            });
            play.addActionListener(e -> {
                if (timer.isRunning()) { timer.stop(); play.setText("Play"); }
                else {
                    if (pos[0] >= scene.lastStep()) pos[0] = 0;
                    timer.start(); play.setText("Pause");
                }
            });
            stepBtn.addActionListener(e -> {
                timer.stop(); play.setText("Play");
                pos[0] = Math.min(scene.lastStep(), Math.floor(pos[0]) + 1);
                canvas.repaint();
            });
            reset.addActionListener(e -> { timer.stop(); play.setText("Play"); pos[0] = 0; canvas.repaint(); });
            end.addActionListener(e -> { timer.stop(); play.setText("Play"); pos[0] = scene.lastStep(); canvas.repaint(); });
            speed.addChangeListener(e -> {
                stepsPerTick[0] = speed.getValue() * 0.03;
                speedLabel.setText("Speed (steps/s): " + speed.getValue());
            });
            if (!res.reachable()) { play.setEnabled(false); stepBtn.setEnabled(false); end.setEnabled(false); }

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
            controls.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            controls.add(play); controls.add(stepBtn); controls.add(reset); controls.add(end);
            controls.add(speedLabel); controls.add(speed);

            JFrame frame = new JFrame("Problem 1 - Maze with Keys and Doors (BFS + key inventory)");
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
