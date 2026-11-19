package scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.Literal;

public class ScehdulingSolver {

    public static ScheduleResult solve(CSVDataLoader.ScheduleData d, List<int[]> groups,
                                       String datasetName, double timeLimitSec, int workers) {
        return solve(d, groups, datasetName, timeLimitSec, workers, null);
    }

    public static ScheduleResult solve(CSVDataLoader.ScheduleData d, List<int[]> groups,
                                       String datasetName, double timeLimitSec, int workers,
                                       ScheduleResult greedyHint) {
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

        // ---- Quick infeasibility: some exam fits in no room ----
        int maxCap = 0;
        for (int r = 0; r < R; r++) maxCap = Math.max(maxCap, d.roomCapacities[r]);
        for (int e = 0; e < E; e++) {
            if (d.examStudents[e] > maxCap) return infeasible(d, res, t0);
        }

        // ---- Lower bound on timeslots from largest conflict clique ----
        int[] clique = new int[0];
        for (int[] g : groups) if (g.length > clique.length) clique = g;
        int lb = Math.max(1, clique.length);

        // Room-capacity lower bound: each slot can host at most R exams
        int roomLb = (R > 0) ? (int) Math.ceil((double) E / R) : T;
        lb = Math.max(lb, roomLb);

        System.out.println("[CP-SAT Debug] Lower Bound (clique/rooms): " + lb + " | Available Timeslots (T): " + T);
        System.out.flush();

        if (lb > T) {
            System.err.println("[CP-SAT ERROR] Infeasible: Lower bound (" + lb + ") exceeds available timeslots (" + T + ")!");
            System.out.flush();
            return infeasible(d, res, t0);
        }

        System.out.println("[CP-SAT Debug] Initializing CP-SAT model and variables...");
        System.out.flush();

        CpModel model = new CpModel();

        // x[e][r][t] = 1 if exam e is placed in room r at timeslot t
        BoolVar[][][] x = new BoolVar[E][R][T];
        // y[e][t] = 1 if exam e is scheduled in timeslot t
        BoolVar[][] y = new BoolVar[E][T];
        // used[t] = 1 if timeslot t is used
        BoolVar[] used = new BoolVar[T];

        for (int t = 0; t < T; t++) {
            used[t] = model.newBoolVar("used_" + t);
        }

        for (int e = 0; e < E; e++) {
            for (int t = 0; t < T; t++) {
                y[e][t] = model.newBoolVar("y_" + e + "_" + t);
                model.addImplication(y[e][t], used[t]);
            }
        }

        // 1. Each exam must be scheduled in exactly one (room, timeslot) where room capacity is sufficient
        for (int e = 0; e < E; e++) {
            List<Literal> examPlacements = new ArrayList<>();
            for (int t = 0; t < T; t++) {
                List<Literal> roomVarsForSlot = new ArrayList<>();
                for (int r = 0; r < R; r++) {
                    if (d.roomCapacities[r] >= d.examStudents[e]) {
                        x[e][r][t] = model.newBoolVar("x_" + e + "_" + r + "_" + t);
                        roomVarsForSlot.add(x[e][r][t]);
                        examPlacements.add(x[e][r][t]);
                    } else {
                        x[e][r][t] = null;
                    }
                }
                if (!roomVarsForSlot.isEmpty()) {
                    Literal[] roomArray = roomVarsForSlot.toArray(new Literal[0]);
                    model.addEquality(LinearExpr.sum(roomArray), y[e][t]);
                } else {
                    model.addEquality(y[e][t], 0);
                }
            }
            if (examPlacements.isEmpty()) {
                return infeasible(d, res, t0);
            }
            model.addExactlyOne(examPlacements.toArray(new Literal[0]));
        }

        // 2. Room exclusivity per timeslot
        for (int t = 0; t < T; t++) {
            for (int r = 0; r < R; r++) {
                List<Literal> examsInRoom = new ArrayList<>();
                for (int e = 0; e < E; e++) {
                    if (x[e][r][t] != null) {
                        examsInRoom.add(x[e][r][t]);
                    }
                }
                if (!examsInRoom.isEmpty()) {
                    model.addAtMostOne(examsInRoom.toArray(new Literal[0]));
                }
            }
        }

        // 3. Conflict groups cannot overlap in timeslots
        for (int[] g : groups) {
            if (g.length < 2) continue;
            for (int t = 0; t < T; t++) {
                Literal[] lits = new Literal[g.length];
                for (int i = 0; i < g.length; i++) {
                    lits[i] = y[g[i]][t];
                }
                model.addAtMostOne(lits);
            }
        }

        // 4. Symmetry breaking: used slots form a prefix; first lb slots always used
        for (int t = 0; t + 1 < T; t++) {
            model.addImplication(used[t + 1], used[t]);
        }
        for (int t = 0; t < Math.min(lb, T); t++) {
            model.addEquality(used[t], 1);
        }

        // 5. Optional warm-start hint from Greedy if it scheduled every exam
        if (greedyHint != null && greedyHint.scheduled == E && greedyHint.slotOf != null && greedyHint.roomOf != null) {
            boolean validHint = true;
            int maxHintSlot = -1;
            for (int e = 0; e < E; e++) {
                int hs = greedyHint.slotOf[e];
                int hr = greedyHint.roomOf[e];
                if (hs < 0 || hs >= T || hr < 0 || hr >= R || x[e][hr][hs] == null) {
                    validHint = false;
                    break;
                }
                if (hs > maxHintSlot) maxHintSlot = hs;
            }
            if (validHint) {
                for (int e = 0; e < E; e++) {
                    int hs = greedyHint.slotOf[e];
                    int hr = greedyHint.roomOf[e];
                    for (int t = 0; t < T; t++) {
                        model.addHint(y[e][t], t == hs ? 1 : 0);
                        for (int r = 0; r < R; r++) {
                            if (x[e][r][t] != null) {
                                model.addHint(x[e][r][t], (t == hs && r == hr) ? 1 : 0);
                            }
                        }
                    }
                }
                for (int t = 0; t < T; t++) {
                    model.addHint(used[t], t <= maxHintSlot ? 1 : 0);
                }
            }
        }

        // 6. Objective: Minimize total timeslots used
        model.minimize(LinearExpr.sum(used));

        System.out.println("[CP-SAT Debug] Model built successfully. Starting solver search...");
        System.out.flush();

        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(timeLimitSec);
        solver.getParameters().setNumWorkers(workers);
        solver.getParameters().setLinearizationLevel(1);
        solver.getParameters().setSymmetryLevel(2);
        solver.getParameters().setCpModelProbingLevel(1);
        // solver.getParameters().setLogSearchProgress(true); // Uncomment to see live solver progress

        CpSolverStatus status = solver.solve(model);
        res.status = status.toString();

        System.out.println("[CP-SAT Debug] Solver finished with status: " + res.status);
        System.out.flush();

        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            for (int e = 0; e < E; e++) {
                for (int t = 0; t < T; t++) {
                    for (int r = 0; r < R; r++) {
                        if (x[e][r][t] != null && solver.booleanValue(x[e][r][t])) {
                            res.slotOf[e] = t;
                            res.roomOf[e] = r;
                        }
                    }
                }
            }
        }

        res.computeMetrics(d);
        res.runtimeMs = (System.nanoTime() - t0) / 1e6;
        return res;
    }

    private static ScheduleResult infeasible(CSVDataLoader.ScheduleData d, ScheduleResult res, long t0) {
        res.status = "INFEASIBLE";
        res.computeMetrics(d);
        res.runtimeMs = (System.nanoTime() - t0) / 1e6;
        return res;
    }
}