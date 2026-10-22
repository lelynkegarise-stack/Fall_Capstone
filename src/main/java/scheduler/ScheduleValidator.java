package scheduler;

import java.util.HashMap;
import java.util.Map;

import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpSolver;

public class ScheduleValidator {

    public static boolean validateSchedule(CSVDataLoader.ScheduleData data, CpSolver solver, BoolVar[][][] x) {
        boolean allPassed = true;
        System.out.println("\n------------------------------------------------");
        System.out.println("          RUNNING CONSTRAINT VALIDATOR          ");
        System.out.println("------------------------------------------------");

        // 1. Check Exam Uniqueness (Every exam scheduled exactly once)
        for (int e = 0; e < data.exams.length; e++) {
            int scheduledCount = 0;
            for (int t = 0; t < data.timeslots.length; t++) {
                for (int r = 0; r < data.rooms.length; r++) {
                    if (solver.value(x[e][t][r]) == 1) {
                        scheduledCount++;
                    }
                }
            }
            if (scheduledCount != 1) {
                System.out.println("❌ VIOLATION: Exam " + data.exams[e] + " is scheduled " + scheduledCount + " times (Must be exactly 1).");
                allPassed = false;
            }
        }

        // 2. Check Room Capacities & Room Double-Booking
        for (int t = 0; t < data.timeslots.length; t++) {
            for (int r = 0; r < data.rooms.length; r++) {
                int examsInRoomSlot = 0;
                for (int e = 0; e < data.exams.length; e++) {
                    if (solver.value(x[e][t][r]) == 1) {
                        examsInRoomSlot++;
                        if (data.examStudents[e] > data.roomCapacities[r]) {
                            System.out.println("❌ VIOLATION: Exam " + data.exams[e] + " (" + data.examStudents[e] + " students) exceeds Room " + data.rooms[r] + " capacity (" + data.roomCapacities[r] + ").");
                            allPassed = false;
                        }
                    }
                }
                if (examsInRoomSlot > 1) {
                    System.out.println("❌ VIOLATION: Room " + data.rooms[r] + " has " + examsInRoomSlot + " exams booked at timeslot " + data.timeslotDays[t] + " @ " + data.timeslotTimes[t] + ".");
                    allPassed = false;
                }
            }
        }

        // 3. Strict Instructor Conflict Check
        for (int t = 0; t < data.timeslots.length; t++) {
            Map<String, String> instructorToExam = new HashMap<>();
            for (int e = 0; e < data.exams.length; e++) {
                for (int r = 0; r < data.rooms.length; r++) {
                    if (solver.value(x[e][t][r]) == 1) {
                        String instId = data.examInstructorIds[e] != null ? data.examInstructorIds[e].trim() : "";
                        if (!instId.isEmpty()) {
                            if (instructorToExam.containsKey(instId)) {
                                System.out.println("❌ VIOLATION: Instructor " + instId + " assigned to both Exam " 
                                    + instructorToExam.get(instId) + " and Exam " + data.exams[e] 
                                    + " at " + data.timeslotDays[t] + " @ " + data.timeslotTimes[t]);
                                allPassed = false;
                            } else {
                                instructorToExam.put(instId, data.exams[e]);
                            }
                        }
                    }
                }
            }
        }

        if (allPassed) {
            System.out.println("✅ SUCCESS: All constraints independently verified and passed!");
        } else {
            System.out.println("⚠️ WARNING: Constraint violations were found!");
        }
        System.out.println("------------------------------------------------\n");
        return allPassed;
    }
}