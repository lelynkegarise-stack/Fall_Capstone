package scheduler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        // Pull your arrays out of the dataset object 
        String[] exams = dataset.exams;        
        String[] timeslots = dataset.timeslots;  
        String[] timeslotDays = dataset.timeslotDays;  
        String[] timeslotTimes = dataset.timeslotTimes;
        String[] rooms = dataset.rooms;   
        String[] examInstructorIds = dataset.examInstructorIds;      
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

        // Constraint 4: Dynamic Instructor Conflict Constraint (Zero-Tolerance Grouping)
        Set<String> uniqueInstructors = new HashSet<>();
        for (String id : examInstructorIds) {
            if (id != null && !id.trim().isEmpty()) {
                uniqueInstructors.add(id.trim().toLowerCase());
            }
        }

        for (int t = 0; t < timeslots.length; t++) {
            for (String instId : uniqueInstructors) {
                List<BoolVar> instructorExamSlots = new ArrayList<>();
                for (int e = 0; e < exams.length; e++) {
                    String eInst = examInstructorIds[e] != null ? examInstructorIds[e].trim().toLowerCase() : "";
                    if (eInst.equals(instId)) {
                        for (int r = 0; r < rooms.length; r++) {
                            instructorExamSlots.add(x[e][t][r]);
                        }
                    }
                }
                if (instructorExamSlots.size() > 1) {
                    model.addLessOrEqual(
                        LinearExpr.sum(instructorExamSlots.toArray(new BoolVar[0])), 
                        1
                    );
                }
            }
        }

        // --- 3. Solve and Output Results ---
        CpSolver solver = new CpSolver();
        CpSolverStatus status = solver.solve(model);
        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            ScheduleValidator.validateSchedule(dataset, solver, x);
            System.out.println("\n================================================");
            System.out.println("          EXAM SCHEDULE TIMETABLE REPORT        ");
            System.out.println("================================================");

            for (int t = 0; t < timeslots.length; t++) {
                boolean timeslotHasExams = false;
                StringBuilder slotOutput = new StringBuilder();
                
                slotOutput.append("\n📅 TIMESLOT: ")
                          .append(timeslotDays[t])
                          .append(" @ ")
                          .append(timeslotTimes[t])
                          .append("\n");
                slotOutput.append("------------------------------------------------\n");                
                
                for (int r = 0; r < rooms.length; r++) {
                    for (int e = 0; e < exams.length; e++) {
                        if (solver.value(x[e][t][r]) == 1) {
                            slotOutput.append("  🏛️ Room: ").append(rooms[r])
                                      .append(" | 📚 Exam: ").append(exams[e])
                                      .append("\n     ↳ Instructor ID: ").append(examInstructorIds[e])
                                      .append(" | Total Students: ").append(examStudents[e])
                                      .append("\n     ↳ Enrolled Student IDs:\n");

                            for (String studentId : dataset.examStudentLists[e]) {
                                slotOutput.append("         • ").append(studentId).append("\n");
                            }
                            slotOutput.append("\n");

                            timeslotHasExams = true;
                        }
                    }
                }
                
                if (timeslotHasExams) {
                    System.out.print(slotOutput.toString());
                }
            }
            System.out.println("================================================");
        } else {
            System.out.println("No valid timetable could be found with the current constraints.");
        }
    }
}