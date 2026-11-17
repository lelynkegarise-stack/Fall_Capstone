package scheduler;

import java.util.*;

/** Common result object so CP-SAT and greedy are measured identically. */
public class ScheduleResult {
    public String dataset;
    public String method;
    public String status;        // OPTIMAL / FEASIBLE / INFEASIBLE / GREEDY_COMPLETE / GREEDY_PARTIAL ...
    public int exams, timeslots, rooms;
    public int scheduled;        // exams that received a (timeslot, room)
    public int unscheduled;      // exams - scheduled
    public int timeslotsUsed;    // distinct timeslots with at least one exam
    public long wastedSeats;     // sum(roomCapacity - examSize) over scheduled exams
    public double roomUtilizationPct; // students seated / seats used
    public double runtimeMs;
    public int[] slotOf;         // -1 if unscheduled
    public int[] roomOf;         // -1 if unscheduled

    // Proof-of-quality columns
    public int lowerBound;       // fewest timeslots any valid schedule could use
    public double gapPct;        // how far timeslotsUsed is above the lower bound
    public int hardViolations;   // rule breaks found by the independent checker

    // Optional student-based metrics: -1 means "not measured" (prints blank)
    public int backToBack = -1;
    public int sameDayExams = -1;

    public static String csvHeader() {
        return "dataset,method,status,exams,timeslots,rooms,scheduled,unscheduled,timeslots_used,"
             + "lower_bound,gap_pct,hard_violations,wasted_seats,room_utilization_pct,"
             + "back_to_back,same_day_exams,runtime_ms";
    }

    public String toCsvRow() {
        return String.format("%s,%s,%s,%d,%d,%d,%d,%d,%d,%d,%.2f,%d,%d,%.2f,%s,%s,%.2f",
                dataset, method, status, exams, timeslots, rooms,
                scheduled, unscheduled, timeslotsUsed,
                lowerBound, gapPct, hardViolations, wastedSeats, roomUtilizationPct,
                backToBack < 0 ? "" : String.valueOf(backToBack),
                sameDayExams < 0 ? "" : String.valueOf(sameDayExams),
                runtimeMs);
    }

    /** Fills scheduled / timeslotsUsed / wastedSeats / utilization from slotOf + roomOf. */
    public void computeMetrics(CSVDataLoader.ScheduleData d) {
        boolean[] used = new boolean[d.timeslots.length];
        scheduled = 0;
        wastedSeats = 0;
        long students = 0;
        for (int e = 0; e < slotOf.length; e++) {
            if (slotOf[e] < 0 || roomOf[e] < 0) continue;
            scheduled++;
            used[slotOf[e]] = true;
            wastedSeats += d.roomCapacities[roomOf[e]] - d.examStudents[e];
            students += d.examStudents[e];
        }
        unscheduled = exams - scheduled;
        timeslotsUsed = 0;
        for (boolean u : used) if (u) timeslotsUsed++;
        long seats = students + wastedSeats;
        roomUtilizationPct = seats == 0 ? 0 : 100.0 * students / seats;
    }

    /**
     * Fills lowerBound, gapPct and hardViolations. Call this once per result
     * (CP-SAT and greedy) after computeMetrics, with the same groups for both.
     */
    public void computeExtras(CSVDataLoader.ScheduleData d, List<int[]> groups) {
        lowerBound = lowerBound(d, groups);
        gapPct = (scheduled == 0 || lowerBound == 0) ? 0
                : 100.0 * (timeslotsUsed - lowerBound) / lowerBound;
        hardViolations = countViolations(d, groups);
    }

    /** Same bound for both methods: largest conflict group, or room-capacity crowding. */
    public static int lowerBound(CSVDataLoader.ScheduleData d, List<int[]> groups) {
        int lb = 1;
        for (int[] g : groups) lb = Math.max(lb, g.length);
        TreeSet<Integer> sizes = new TreeSet<>();
        for (int s : d.examStudents) sizes.add(s);
        for (int s : sizes) {
            int roomsFit = 0, examsBig = 0;
            for (int cap : d.roomCapacities) if (cap >= s) roomsFit++;
            for (int n : d.examStudents) if (n >= s) examsBig++;
            if (roomsFit > 0) lb = Math.max(lb, (examsBig + roomsFit - 1) / roomsFit);
        }
        return lb;
    }

    /** Independent checker: conflicts in the same slot, room too small, room double booked. */
    public int countViolations(CSVDataLoader.ScheduleData d, List<int[]> groups) {
        int v = 0;
        int R = d.roomCapacities.length;

        // 1) Conflict groups: no two exams of a group in the same slot
        for (int[] g : groups) {
            Map<Integer, Integer> perSlot = new HashMap<>();
            for (int e : g) if (slotOf[e] >= 0) perSlot.merge(slotOf[e], 1, Integer::sum);
            for (int c : perSlot.values()) if (c > 1) v += c - 1;
        }

        // 2) Room too small, missing room, or room used twice in one slot
        Set<Long> taken = new HashSet<>();
        for (int e = 0; e < slotOf.length; e++) {
            if (slotOf[e] < 0) continue;
            if (roomOf[e] < 0) { v++; continue; }
            if (d.roomCapacities[roomOf[e]] < d.examStudents[e]) v++;
            if (!taken.add((long) slotOf[e] * R + roomOf[e])) v++;
        }
        return v;
    }

    @Override
    public String toString() {
        return String.format("%-12s %-8s %-16s scheduled=%d/%d slotsUsed=%d (bound=%d, gap=%.1f%%) "
                           + "violations=%d wasted=%d util=%.1f%% time=%.1fms",
                dataset, method, status, scheduled, exams, timeslotsUsed, lowerBound, gapPct,
                hardViolations, wastedSeats, roomUtilizationPct, runtimeMs);
    }
}