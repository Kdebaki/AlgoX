import java.util.ArrayList;
import java.util.List;

/** A timetable produced by one algorithm, plus what is known about its optimality. */
public final class Schedule {
    public final String algorithm;
    public final int[] slotOf;      // 0-based slot of each 0-based course; -1 if not scheduled
    public final int slots;         // number of slots used; -1 if infeasible
    public final int[] order;       // order in which courses were assigned (0-based)
    public final double millis;
    public final long nodes;        // search nodes (exact) or assignment steps (greedy)
    public boolean provenOptimal;   // only true when backed by a proof (lower bound or exhaustive search)
    public String proof;            // how optimality was / was not established
    public final String infeasibleReason; // null when feasible

    public Schedule(String algorithm, int[] slotOf, int slots, int[] order, double millis, long nodes,
                    boolean provenOptimal, String proof) {
        this.algorithm = algorithm; this.slotOf = slotOf; this.slots = slots; this.order = order;
        this.millis = millis; this.nodes = nodes; this.provenOptimal = provenOptimal; this.proof = proof;
        this.infeasibleReason = null;
    }

    private Schedule(String algorithm, String reason, double millis) {
        this.algorithm = algorithm; this.slotOf = new int[0]; this.slots = -1; this.order = new int[0];
        this.millis = millis; this.nodes = 0; this.provenOptimal = true; this.proof = reason; this.infeasibleReason = reason;
    }

    public static Schedule infeasible(String algorithm, String reason) { return new Schedule(algorithm, reason, 0); }

    public boolean feasible() { return infeasibleReason == null; }

    /** Courses (1-based, increasing) in each slot. */
    public List<List<Integer>> slotLists() {
        List<List<Integer>> lists = new ArrayList<>();
        for (int s = 0; s < Math.max(0, slots); s++) lists.add(new ArrayList<>());
        for (int v = 0; v < slotOf.length; v++) if (slotOf[v] >= 0) lists.get(slotOf[v]).add(v + 1);
        return lists;
    }

    /**
     * Independent validity check: every course scheduled, slots numbered 0..slots-1 and all used,
     * no conflicting pair shares a slot, and no slot holds more than R exams.
     * @return null if valid, otherwise a description of the first violation
     */
    public String validate(ConflictGraph g, int r) {
        if (!feasible()) return null;
        int n = g.size();
        if (slotOf.length != n) return "timetable covers " + slotOf.length + " courses, expected " + n;
        int[] count = new int[Math.max(1, slots)];
        for (int v = 0; v < n; v++) {
            if (slotOf[v] < 0 || slotOf[v] >= slots) return "course " + (v + 1) + " has invalid slot " + slotOf[v];
            count[slotOf[v]]++;
        }
        for (int s = 0; s < slots; s++) {
            if (count[s] == 0) return "slot " + (s + 1) + " is empty";
            if (count[s] > r) return "slot " + (s + 1) + " holds " + count[s] + " exams > R = " + r;
        }
        for (int[] e : g.edges()) {
            if (slotOf[e[0]] == slotOf[e[1]]) return "conflicting courses " + (e[0] + 1) + " and " + (e[1] + 1) + " share slot " + (slotOf[e[0]] + 1);
        }
        return null;
    }
}
