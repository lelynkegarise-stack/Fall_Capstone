package scheduler;

import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.Literal;
import com.google.ortools.sat.LinearExpr;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SchedulingSolver {
    public static void main(String[] args) {
        // 1. Initialize OR-Tools core native backend
        Loader.loadNativeLibraries();

        // 2. Instantiate and run your separate CSV loader file
        CSVDataLoader loader = new CSVDataLoader();
        CSVDataLoader.ScheduleData dataset = loader.loadAllDatasets();

        // 3. Initialize the Google CP-SAT Optimization Model
        CpModel model = new CpModel();
        Map<String, Literal> X = new HashMap<>();
 // --- 1. Define Decision Variables (creates a Boolean exspession saying if the exam works with all of our constraints then it sets it to true.)---
        BoolVar[][][] x = new BoolVar[exam.length][timeslot.length][room.length];
        for (int e = 0; e < exam.length; e++) {
            for (int t = 0; t < timeslot.length; t++) {
                for (int r = 0; r < room.length; r++) {
                    x[e][t][r] = model.newBoolVar(exam[e] + "_" + timeslot[t] + "_" + room[r]);
                }
            }
        }

        // --- 2. Apply Constraints ---
        // Constraint 1 (Exam uniqueness)
        for (int e = 0; e < exam.length; e++) {
            BoolVar[] allPossibleSlots = new BoolVar[timeslot.length * room.length];
            int idx = 0;
            for (int t = 0; t < timeslot.length; t++) {
                for (int r = 0; r < room.length; r++) {
                    allPossibleSlots[idx] = x[e][t][r];
                    idx++;
                }
            }
            model.addExactlyOne(allPossibleSlots);
        }
                // --- 3. Solve and Output Results ---
        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(model);

        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            System.out.println("--- Successfully Scheduled Exams ---");
            for (int t = 0; t < timeslot.length; t++) {
                System.out.println("\n" + timeslot[t] + ":");
                for (int r = 0; r < room.length; r++) {
                    boolean roomEmpty = true;
                    for (int e = 0; e < exam.length; e++) {
                        if (solver.value(x[e][t][r]) == 1) {
                            System.out.println("  * " + room[r] + " -> " + exam[e] + " (" + examStudents[e] + " students)");
                            roomEmpty = false;
                        }
                    }
                    if (roomEmpty) {
                        System.out.println("  * " + room[r] + " -> [EMPTY]");
                    }
                }
            }
        } else {
            System.out.println("No valid timetable could be found with the current constraints.");
        }
    }
    }
}
