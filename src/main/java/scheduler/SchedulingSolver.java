package scheduler;

import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus; 
import com.google.ortools.sat.LinearExpr;


public class SchedulingSolver {
    public static void main(String[] args) {
        // 1. Initialize OR-Tools core native backend
        Loader.loadNativeLibraries();

        // 2. Instantiate and run your separate CSV loader file
        CSVDataLoader loader = new CSVDataLoader();
        CSVDataLoader.ScheduleData dataset = loader.loadAllDatasets();

        // Pull your arrays/lists out of the dataset object 
        String[] exams = dataset.exams;         
        String[] timeslots = dataset.timeslots;  
        String[] rooms = dataset.rooms;          
        int[] examStudents = dataset.examStudents; 
        int[] roomCapacities = dataset.roomCapacities;
        
        // 3. Initialize the Google CP-SAT Optimization Model
        CpModel model = new CpModel();
        
        // --- 1. Define Decision Variables ---
        BoolVar[][][] x = new BoolVar[exams.length][timeslots.length][rooms.length];
        for (int e = 0; e < exams.length; e++) {
            for (int t = 0; t < timeslots.length; t++) {
                for (int r = 0; r < rooms.length; r++) {
                    x[e][t][r] = model.newBoolVar(exams[e] + "_" + timeslots[t] + "_" + rooms[r]);
                }
            }
        }

        // --- 2. Apply Constraints ---
        // Constraint 1 (Exam uniqueness - every exam must be scheduled exactly once)
        for (int e = 0; e < exams.length; e++) {
            BoolVar[] allPossibleSlots = new BoolVar[timeslots.length * rooms.length];
            int idx = 0;
            for (int t = 0; t < timeslots.length; t++) {
                for (int r = 0; r < rooms.length; r++) {
                    allPossibleSlots[idx] = x[e][t][r];
                    idx++;
                }
            }
            model.addExactlyOne(allPossibleSlots);
        }

        // Diagnostic check: print max room capacity vs large exams
        int maxRoomCap = 0;
        for (int cap : roomCapacities) {
            if (cap > maxRoomCap) maxRoomCap = cap;
        }
        System.out.println("Maximum room capacity available: " + maxRoomCap);

        for (int i = 0; i < exams.length; i++) {
            if (examStudents[i] > maxRoomCap) {
                System.out.println("⚠️ Warning: Exam " + exams[i] + " has " + examStudents[i] + " students, but the largest room only holds " + maxRoomCap + "!");
            }
        }

        //Room Capacity
        for (int e = 0; e < exams.length; e++) {
            for (int t = 0; t < timeslots.length; t++) {
                for (int r = 0; r < rooms.length; r++) {
                    // Simple, fast array lookup since roomCapacities[r] directly maps to rooms[r]
                    if (examStudents[e] > roomCapacities[r]) {
                        model.addEquality(x[e][t][r], 0);
                    }
                }
            }
        }
        // no room double booking 
        for (int t=0; t<timeslots.length; t++){
            for(int r=0;r<rooms.length;r++){
                BoolVar[] examsInRoomSlot = new BoolVar[exams.length];
                for (int e=0; e<exams.length;e++){
                    examsInRoomSlot[e]=x[e][t][r];
                }
                model.addLessOrEqual(LinearExpr.sum(examsInRoomSlot),1);
            }
        }
        // --- 3. Solve and Output Results ---
        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(model);

        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            System.out.println("--- Successfully Scheduled Exams ---");
            for (int t = 0; t < timeslots.length; t++) {
                boolean timeslotHasExams = false;
                StringBuilder slotOutput = new StringBuilder();
                
                slotOutput.append("\nTimeslot: ").append(timeslots[t]).append("\n");
                
                for (int r = 0; r < rooms.length; r++) {
                    for (int e = 0; e < exams.length; e++) {
                        if (solver.value(x[e][t][r]) == 1) {
                            slotOutput.append("  * Room ").append(rooms[r])
                                      .append(" -> Exam ").append(exams[e])
                                      .append(" (").append(examStudents[e]).append(" students)\n");
                            timeslotHasExams = true;
                        }
                    }
                }
                
                // Only print timeslots that actually contain scheduled exams
                if (timeslotHasExams) {
                    System.out.print(slotOutput.toString());
                }
            }
        } else {
            System.out.println("No valid timetable could be found with the current constraints.");
        }
    }
}