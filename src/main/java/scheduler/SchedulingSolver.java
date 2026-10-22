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
        String[] timeslotDays = dataset.timeslotDays;  
        String[] timeslotTimes = dataset.timeslotTimes;
        String[] rooms = dataset.rooms;   
        int[] examInstructors = dataset.examInstructors;        
        int[] examStudents = dataset.examStudents; 
        int[] roomCapacities = dataset.roomCapacities;

        // Initialize the conflicts matrix
        boolean[][] conflicts = new boolean[exams.length][exams.length];

        for (int i = 0; i < exams.length; i++) {
            for (int j = i + 1; j < exams.length; j++) {
                // If both exams have the same instructor, mark them as conflicting
                if (examInstructors[i] == examInstructors[j]) {
                    conflicts[i][j] = true;
                    conflicts[j][i] = true; // Make it symmetric
                }
            }
        }

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
        
        // Constraint 1: Every exam must be scheduled exactly once
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
                System.out.println("Warning: Exam " + exams[i] + " has " + examStudents[i] + " students, but the largest room only holds " + maxRoomCap + "!");
            }
        }

        // Constraint 2: Room Capacity
        for (int e = 0; e < exams.length; e++) {
            for (int t = 0; t < timeslots.length; t++) {
                for (int r = 0; r < rooms.length; r++) {
                    if (examStudents[e] > roomCapacities[r]) {
                        model.addEquality(x[e][t][r], 0);
                    }
                }
            }
        }

        // Constraint 3: No room double booking 
        for (int t = 0; t < timeslots.length; t++) {
            for (int r = 0; r < rooms.length; r++) {
                BoolVar[] examsInRoomSlot = new BoolVar[exams.length];
                for (int e = 0; e < exams.length; e++) {
                    examsInRoomSlot[e] = x[e][t][r];
                }
                model.addLessOrEqual(LinearExpr.sum(examsInRoomSlot), 1);
            }
        }

        // Constraint 4: Instructor Conflict Constraint (Cleaned up with flat array)
        for (int t = 0; t < timeslots.length; t++) {
            for (int i = 0; i < exams.length; i++) {
                for (int j = i + 1; j < exams.length; j++) {
                    if (conflicts[i][j]) {
                        BoolVar[] conflictVars = new BoolVar[rooms.length * 2];
                        int idx = 0;
                        for (int r = 0; r < rooms.length; r++) {
                            conflictVars[idx++] = x[i][t][r];
                            conflictVars[idx++] = x[j][t][r];
                        }
                        model.addLessOrEqual(LinearExpr.sum(conflictVars), 1);
                    }
                }
            }
        }

        // --- 3. Solve and Output Results ---
        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(model);

        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            System.out.println("\n--- Successfully Scheduled Exams ---");
            for (int t = 0; t < timeslots.length; t++) {
                boolean timeslotHasExams = false;
                StringBuilder slotOutput = new StringBuilder();
                
                slotOutput.append("\nTimeslot: ")
                          .append(timeslotDays[t])
                          .append(" at ")
                          .append(timeslotTimes[t])
                          .append("\n");                
                
                for (int r = 0; r < rooms.length; r++) {
                    for (int e = 0; e < exams.length; e++) {
                        if (solver.value(x[e][t][r]) == 1) {
                            slotOutput.append("  * Room ").append(rooms[r])
                                      .append(" -> Exam ").append(exams[e])
                                      .append(" (Students: ").append(examStudents[e])
                                      .append(", Instructor ID: ").append(examInstructors[e])
                                      .append(")\n");
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
