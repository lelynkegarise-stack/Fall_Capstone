package scheduler;

/** Common result object so CP-SAT and greedy are measured identically. */
public class ScheduleResult {
    public String dataset;
    public String method;
    public String status;        // OPTIMAL / FEASIBLE / INFEASIBLE / GREEDY_COMPLETE / GREEDY_PARTIAL ...
    public int exams, timeslots, rooms;
    public int scheduled;        // exams that received a (timeslot, room)
    public int timeslotsUsed;    // distinct timeslots with at least one exam
    public long wastedSeats;     // sum(roomCapacity - examSize) over scheduled exams
    public double runtimeMs;
    public int[] slotOf;         // -1 if unscheduled
    public int[] roomOf;         // -1 if unscheduled

    public static String csvHeader() {
        return "dataset,method,status,exams,timeslots,rooms,scheduled,timeslots_used,wasted_seats,runtime_ms";
    }

    public String toCsvRow() {
        return String.format("%s,%s,%s,%d,%d,%d,%d,%d,%d,%.2f",
                dataset, method, status, exams, timeslots, rooms,
                scheduled, timeslotsUsed, wastedSeats, runtimeMs);
    }

    /** Fills scheduled / timeslotsUsed / wastedSeats from slotOf + roomOf. */
    public void computeMetrics(CSVDataLoader.ScheduleData d) {
        boolean[] used = new boolean[d.timeslots.length];
        scheduled = 0;
        wastedSeats = 0;
        for (int e = 0; e < slotOf.length; e++) {
            if (slotOf[e] < 0) continue;
            scheduled++;
            used[slotOf[e]] = true;
            wastedSeats += d.roomCapacities[roomOf[e]] - d.examStudents[e];
        }
        timeslotsUsed = 0;
        for (boolean u : used) if (u) timeslotsUsed++;
    }

    @Override
    public String toString() {
        return String.format("%-12s %-8s %-16s scheduled=%d/%d slotsUsed=%d wasted=%d time=%.1fms",
                dataset, method, status, scheduled, exams, timeslotsUsed, wastedSeats, runtimeMs);
    }
}
