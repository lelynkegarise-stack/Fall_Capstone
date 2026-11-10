package scheduler;

import java.util.*;

import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.Literal;

/**
 * Leaner version of your model.
 *
 * Changes vs. the original:
 *  - x[e][t][r] is only created when the room is big enough (no "== 0" constraints).
 *  - y[e][t] = "exam e is in timeslot t" (sum over rooms). Conflict groups are
 *    posted on y with AddAtMostOne, which is much smaller than summing every room.
 *  - Takes the shared conflict groups (instructor + optional student) so it is
 *    comparable to the greedy search.
 *  - Objective: minimise the number of timeslots used (a pure feasibility model
 *    gives you nothing to compare besides runtime). Time limit + workers are set.
 */
public class ScehdulingSolver {

    public static ScheduleResult solve(CSVDataLoader.ScheduleData d, List<int[]> groups,
                                       String datasetName, double timeLimitSec, int workers) {
        long t0 = System.nanoTime();
        int E = d.exams.length, T = d.timeslots.length, R = d.rooms.length;

        ScheduleResult res = new ScheduleResult();
        res.dataset = datasetName;
        res.method = "CP-SAT";
        res.exams = E; res.timeslots = T; res.rooms = R;
        res.slotOf = new int[E];
        res.roomOf = new int[E];
        Arrays.fill(res.slotOf, -1);
        Arrays.fill(res.roomOf, -1);

        CpModel model = new CpModel();
        BoolVar[][][] x = new BoolVar[E][T][R];
        BoolVar[][] y = new BoolVar[E][T];
        BoolVar[] used = new BoolVar[T];
        for (int t = 0; t < T; t++) used[t] = model.newBoolVar("used_" + t);

        List<List<BoolVar>> roomSlot = new ArrayList<>(); // index t*R + r
        for (int i = 0; i < T * R; i++) roomSlot.add(new ArrayList<>());

        for (int e = 0; e < E; e++) {
            List<Integer> fitRooms = new ArrayList<>();
            for (int r = 0; r < R; r++) if (d.roomCapacities[r] >= d.examStudents[e]) fitRooms.add(r);

            if (fitRooms.isEmpty()) { // no room can ever hold this exam
                res.status = "INFEASIBLE";
                res.computeMetrics(d);
                res.runtimeMs = (System.nanoTime() - t0) / 1e6;
                return res;
            }

            for (int t = 0; t < T; t++) {
                y[e][t] = model.newBoolVar("y_" + e + "_" + t);
                List<BoolVar> perRoom = new ArrayList<>();
                for (int r : fitRooms) {
                    x[e][t][r] = model.newBoolVar("x_" + e + "_" + t + "_" + r);
                    perRoom.add(x[e][t][r]);
                    roomSlot.get(t * R + r).add(x[e][t][r]);
                }
                model.addEquality(LinearExpr.sum(perRoom.toArray(new BoolVar[0])), y[e][t]);
                model.addImplication(y[e][t], used[t]);
            }
            model.addExactlyOne(y[e]); // every exam exactly once
        }

        // No room double booking
        for (List<BoolVar> l : roomSlot)
            if (l.size() > 1) model.addAtMostOne(l.toArray(new Literal[0]));

        // Instructor / student conflicts: one group, one timeslot, at most one exam
        for (int[] g : groups) {
            for (int t = 0; t < T; t++) {
                Literal[] lits = new Literal[g.length];
                for (int i = 0; i < g.length; i++) lits[i] = y[g[i]][t];
                model.addAtMostOne(lits);
            }
        }

        // Objective: fewest timeslots used
        model.minimize(LinearExpr.sum(used));

        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(timeLimitSec);
        solver.getParameters().setNumWorkers(workers);
        CpSolverStatus status = solver.solve(model);
        res.status = status.toString();

        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            for (int e = 0; e < E; e++)
                for (int t = 0; t < T; t++)
                    for (int r = 0; r < R; r++)
                        if (x[e][t][r] != null && solver.value(x[e][t][r]) == 1) {
                            res.slotOf[e] = t;
                            res.roomOf[e] = r;
                        }
        }
        res.computeMetrics(d);
        res.runtimeMs = (System.nanoTime() - t0) / 1e6;
        return res;
    }
}
