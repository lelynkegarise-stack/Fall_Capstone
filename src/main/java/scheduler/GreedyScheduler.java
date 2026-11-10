package scheduler;

import java.util.*;

/**
 * Greedy baseline (DSatur-style graph colouring + best-fit room).
 *
 * 1. Pick the unscheduled exam with the highest "saturation" (number of distinct
 *    timeslots already taken by its conflicting exams); ties -> most conflicts,
 *    then most students.
 * 2. Put it in the earliest timeslot with no conflict and a free room.
 * 3. In that timeslot use the smallest free room that fits (least wasted seats).
 *
 * Hard constraints are identical to the CP-SAT model. If no slot works the exam
 * is left unscheduled (greedy can fail where CP-SAT finds a solution).
 */
public class GreedyScheduler {

    public static ScheduleResult solve(CSVDataLoader.ScheduleData d, List<int[]> groups, String datasetName) {
        long t0 = System.nanoTime();
        int E = d.exams.length, T = d.timeslots.length, R = d.rooms.length;

        int[][] adj = ConflictGroups.adjacency(groups, E);
        int[] slotOf = new int[E], roomOf = new int[E];
        Arrays.fill(slotOf, -1);
        Arrays.fill(roomOf, -1);

        int[][] nbCount = new int[E][T]; // scheduled neighbours per timeslot
        int[] sat = new int[E];
        boolean[][] roomBusy = new boolean[T][R];
        boolean[] done = new boolean[E];

        Integer[] roomOrder = new Integer[R];
        for (int r = 0; r < R; r++) roomOrder[r] = r;
        Arrays.sort(roomOrder, Comparator.comparingInt(r -> d.roomCapacities[r]));

        for (int step = 0; step < E; step++) {
            int best = -1;
            for (int e = 0; e < E; e++) {
                if (done[e]) continue;
                if (best == -1
                        || sat[e] > sat[best]
                        || (sat[e] == sat[best] && adj[e].length > adj[best].length)
                        || (sat[e] == sat[best] && adj[e].length == adj[best].length
                            && d.examStudents[e] > d.examStudents[best])) {
                    best = e;
                }
            }
            done[best] = true;

            boolean placed = false;
            for (int t = 0; t < T && !placed; t++) {
                if (nbCount[best][t] > 0) continue;
                for (int r : roomOrder) {
                    if (!roomBusy[t][r] && d.roomCapacities[r] >= d.examStudents[best]) {
                        slotOf[best] = t;
                        roomOf[best] = r;
                        roomBusy[t][r] = true;
                        placed = true;
                        break;
                    }
                }
            }
            if (placed) {
                int t = slotOf[best];
                for (int nb : adj[best]) if (nbCount[nb][t]++ == 0) sat[nb]++;
            }
        }

        ScheduleResult res = new ScheduleResult();
        res.dataset = datasetName;
        res.method = "GREEDY";
        res.exams = E; res.timeslots = T; res.rooms = R;
        res.slotOf = slotOf; res.roomOf = roomOf;
        res.computeMetrics(d);
        res.status = res.scheduled == E ? "GREEDY_COMPLETE" : "GREEDY_PARTIAL";
        res.runtimeMs = (System.nanoTime() - t0) / 1e6;
        return res;
    }
}
