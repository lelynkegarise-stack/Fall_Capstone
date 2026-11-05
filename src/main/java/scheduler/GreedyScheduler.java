package scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class GreedyScheduler {

    public static class ScheduleAssignment {
        public int examIndex;
        public String examName;
        public int timeslotIndex;
        public String timeslotId;
        public int roomIndex;
        public String roomId;

        public ScheduleAssignment(int examIndex, String examName, int timeslotIndex, String timeslotId, int roomIndex, String roomId) {
            this.examIndex = examIndex;
            this.examName = examName;
            this.timeslotIndex = timeslotIndex;
            this.timeslotId = timeslotId;
            this.roomIndex = roomIndex;
            this.roomId = roomId;
        }
    }

    public static void main(String[] args) {
        // 1. Load data using your provided CSVDataLoader
        CSVDataLoader loader = new CSVDataLoader();
        CSVDataLoader.ScheduleData data = loader.loadAllDatasets();

        if (data.exams == null || data.exams.length == 0) {
            System.err.println("Error: No exams loaded from dataset. Please check your CSV file paths.");
            return;
        }

        System.out.println("\n--- Starting Greedy Search Exam Scheduling ---");
        System.out.println("Total Exams to Schedule: " + data.exams.length);
        System.out.println("Total Timeslots Available: " + data.timeslots.length);
        System.out.println("Total Rooms Available: " + data.rooms.length);

        // 2. Perform Greedy Scheduling
        List<ScheduleAssignment> schedule = runGreedyScheduler(data);

        // 3. Print Results & Statistics
        printScheduleResults(schedule, data);
    }

    public static List<ScheduleAssignment> runGreedyScheduler(CSVDataLoader.ScheduleData data) {
        List<ScheduleAssignment> assignments = new ArrayList<>();

        // Track what timeslots students and instructors are busy with
        // Key: "studentId_timeslotIndex" or "instructorId_timeslotIndex"
        Set<String> studentTimeslotBusy = new HashSet<>();
        Set<String> instructorTimeslotBusy = new HashSet<>();

        // Track room availability: roomTimeslotOccupied[timeslotIndex][roomIndex] = true if taken
        boolean[][] roomTimeslotOccupied = new boolean[data.timeslots.length][data.rooms.length];

        // Greedy Heuristic: Sort exams by number of students descending (hardest-to-schedule first)
        Integer[] examIndices = new Integer[data.exams.length];
        for (int i = 0; i < data.exams.length; i++) examIndices[i] = i;
        
        Arrays.sort(examIndices, (a, b) -> Integer.compare(data.examStudents[b], data.examStudents[a]));

        int unassignedCount = 0;

        for (int idx : examIndices) {
            String exam = data.exams[idx];
            int numStudents = data.examStudents[idx];
            String[] students = data.examStudentLists[idx];
            String instructorId = data.examInstructorIds[idx];

            boolean assigned = false;

            // Try to find the first valid (timeslot, room) pair
            timeslotLoop:
            for (int t = 0; t < data.timeslots.length; t++) {
                
                // Check if instructor is already busy in this timeslot
                String instKey = instructorId + "_" + t;
                if (instructorTimeslotBusy.contains(instKey)) {
                    continue;
                }

                // Check if any student has a conflict in this timeslot
                boolean studentConflict = false;
                for (String sId : students) {
                    if (studentTimeslotBusy.contains(sId + "_" + t)) {
                        studentConflict = true;
                        break;
                    }
                }
                if (studentConflict) {
                    continue;
                }

                // Now look for an available room with enough capacity
                for (int r = 0; r < data.rooms.length; r++) {
                    if (!roomTimeslotOccupied[t][r] && data.roomCapacities[r] >= numStudents) {
                        
                        // Valid slot found! Assign it.
                        roomTimeslotOccupied[t][r] = true;
                        
                        // Mark instructor busy
                        instructorTimeslotBusy.add(instKey);

                        // Mark all students busy
                        for (String sId : students) {
                            studentTimeslotBusy.add(sId + "_" + t);
                        }

                        assignments.add(new ScheduleAssignment(
                            idx, exam, t, data.timeslots[t], r, data.rooms[r]
                        ));

                        assigned = true;
                        break timeslotLoop;
                    }
                }
            }

            if (!assigned) {
                unassignedCount++;
                System.err.println("Warning: Could not schedule exam '" + exam + "' (Students: " + numStudents + ") - No valid slot/room found.");
            }
        }

        System.out.println("Greedy Search complete. Successfully scheduled: " + assignments.size() + "/" + data.exams.length + " exams.");
        if (unassignedCount > 0) {
            System.out.println("Unassigned exams: " + unassignedCount + " (Consider adding more rooms or timeslots).");
        }

        return assignments;
    }

   public static void printScheduleResults(List<ScheduleAssignment> schedule, CSVDataLoader.ScheduleData data) {
        System.out.println("\n================================================");
        System.out.println("          EXAM SCHEDULE TIMETABLE REPORT        ");
        System.out.println("================================================");

        if (schedule == null || schedule.isEmpty()) {
            System.out.println("No valid timetable could be found with the current constraints.");
            System.out.println("================================================");
            return;
        }

        // Group assignments by timeslot index for hierarchical reporting
        Map<Integer, List<ScheduleAssignment>> byTimeslot = new TreeMap<>();
        for (ScheduleAssignment sa : schedule) {
            byTimeslot.computeIfAbsent(sa.timeslotIndex, k -> new ArrayList<>()).add(sa);
        }

        for (int t = 0; t < data.timeslots.length; t++) {
            if (!byTimeslot.containsKey(t)) {
                continue; // Skip timeslots with no scheduled exams
            }

            String day = (t < data.timeslotDays.length) ? data.timeslotDays[t] : "Day";
            String time = (t < data.timeslotTimes.length) ? data.timeslotTimes[t] : "Time";

            System.out.println("\n📅 TIMESLOT: " + day + " @ " + time);
            System.out.println("------------------------------------------------");

            for (ScheduleAssignment sa : byTimeslot.get(t)) {
                int e = sa.examIndex;
                int r = sa.roomIndex;

                System.out.println("  🏛️ Room: " + data.rooms[r] + " | 📚 Exam: " + data.exams[e]);
                System.out.println("     ↳ Instructor ID: " + data.examInstructorIds[e] 
                                  + " | Total Students: " + data.examStudents[e] 
                                  + " | Capacity: " + data.roomCapacities[r]);
                System.out.println("     ↳ Enrolled Student IDs:");

                if (data.examStudentLists[e] != null) {
                    for (String studentId : data.examStudentLists[e]) {
                        System.out.println("         • " + studentId);
                    }
                }
                System.out.println();
            }
        }
        System.out.println("================================================");
    }
}