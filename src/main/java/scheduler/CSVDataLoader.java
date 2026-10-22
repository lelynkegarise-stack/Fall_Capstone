package scheduler;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CSVDataLoader {

    public static class ScheduleData {
        public String[] exams;
        public String[] timeslots;
        public String[] timeslotDays;
        public String[] timeslotTimes;
        public String[] rooms;
        public int[] roomCapacities;
        public int[] examStudents;
        public String[][] examStudentLists; // Real student IDs
        public int[] examInstructors;       // Internal index for conflict solver
        public String[] examInstructorIds;  // Real instructor IDs for printing
        public String[] courseDepartments;
    }

    public ScheduleData loadAllDatasets() {
        ScheduleData data = new ScheduleData();

        // 1. Load Timeslots
        List<String> tIds = new ArrayList<>(), tDays = new ArrayList<>(), tTimes = new ArrayList<>();
        loadTimeslotData("dataset/timeslot.csv", tIds, tDays, tTimes);
        data.timeslots = tIds.toArray(new String[0]);
        data.timeslotDays = tDays.toArray(new String[0]);
        data.timeslotTimes = tTimes.toArray(new String[0]);

        // 2. Load Classrooms & Capacities
        List<String> roomsList = new ArrayList<>();
        List<Integer> capsList = new ArrayList<>();
        loadClassroomData("dataset/classrooms.csv", roomsList, capsList);
        data.rooms = roomsList.toArray(new String[0]);
        data.roomCapacities = capsList.stream().mapToInt(Integer::intValue).toArray();

        // 3. Load Courses & Departments
        List<String> coursesList = new ArrayList<>();
        List<String> deptsList = new ArrayList<>();
        loadCourseData("dataset/courses.csv", coursesList, deptsList);
        data.exams = coursesList.toArray(new String[0]);
        data.courseDepartments = deptsList.toArray(new String[0]);

        // 4. Load Students Data (Using student_id at index 0, program_name at index 6)
        List<String[]> studentRows = readCSV("dataset/students.csv");
        List<String> studentIds = new ArrayList<>();
        List<String> studentPrograms = new ArrayList<>();

        if (!studentRows.isEmpty()) {
            String[] headers = studentRows.get(0);
            int idCol = findColumnIndex(headers, new String[]{"student_id"});
            int progCol = findColumnIndex(headers, new String[]{"program_name"});

            for (int r = 1; r < studentRows.size(); r++) {
                String[] row = studentRows.get(r);
                if (row.length > Math.max(idCol, progCol)) {
                    String sId = row[idCol].trim().replaceAll("^\"|\"$", "");
                    String sProg = row[progCol].trim().replaceAll("^\"|\"$", "");
                    if (!sId.isEmpty()) {
                        studentIds.add(sId);
                        studentPrograms.add(sProg);
                    }
                }
            }
        }

        // 5. Load Instructors Data (Extracting real instructor_id at index 0, department at index 5)
        List<String[]> instructorRows = readCSV("dataset/instructors.csv");
        List<String> realInstructorIds = new ArrayList<>();
        List<String> instructorDepts = new ArrayList<>();

        if (!instructorRows.isEmpty()) {
            String[] headers = instructorRows.get(0);
            int instIdCol = findColumnIndex(headers, new String[]{"instructor_id"});
            int deptCol = findColumnIndex(headers, new String[]{"department", "dept"});

            for (int r = 1; r < instructorRows.size(); r++) {
                String[] row = instructorRows.get(r);
                if (row.length > Math.max(instIdCol, deptCol)) {
                    String instId = row[instIdCol].trim().replaceAll("^\"|\"$", "");
                    String dept = row[deptCol].trim().replaceAll("^\"|\"$", "");
                    if (!instId.isEmpty()) {
                        realInstructorIds.add(instId);
                        instructorDepts.add(dept);
                    }
                }
            }
        }

        // 6. Map Real Student IDs and Instructor IDs to Exams
        data.examStudents = new int[data.exams.length];
        data.examStudentLists = new String[data.exams.length][];
        data.examInstructors = new int[data.exams.length];
        data.examInstructorIds = new String[data.exams.length];

        for (int i = 0; i < data.exams.length; i++) {
            String dept = data.courseDepartments[i].toLowerCase().trim();
            
            // --- Map Students ---
            List<String> matchedStudents = new ArrayList<>();
            for (int s = 0; s < studentPrograms.size(); s++) {
                String pLower = studentPrograms.get(s).toLowerCase().trim();
                if (pLower.contains(dept) || dept.contains(pLower)) {
                    matchedStudents.add(studentIds.get(s));
                }
            }

            int maxClassSize = 35;
            if (matchedStudents.size() > maxClassSize) {
                int startIndex = (i * 15) % (matchedStudents.size() - maxClassSize);
                List<String> subList = matchedStudents.subList(startIndex, startIndex + maxClassSize);
                matchedStudents = new ArrayList<>(subList);
            }

            if (matchedStudents.isEmpty() && !studentIds.isEmpty()) {
                int fallbackSize = Math.min(25, studentIds.size());
                int startIndex = (i * 10) % (studentIds.size() - fallbackSize + 1);
                for (int sc = 0; sc < fallbackSize; sc++) {
                    matchedStudents.add(studentIds.get((startIndex + sc) % studentIds.size()));
                }
            }

            data.examStudents[i] = matchedStudents.size();
            data.examStudentLists[i] = matchedStudents.toArray(new String[0]);

            // --- Map Real Instructors ---
            int assignedIndex = Math.abs(dept.hashCode());
            String realInstId = "INST_" + (i + 1);

            if (!instructorDepts.isEmpty()) {
                boolean foundInstructor = false;
                for (int inst = 0; inst < instructorDepts.size(); inst++) {
                    if (instructorDepts.get(inst).toLowerCase().trim().contains(dept) || dept.contains(instructorDepts.get(inst).toLowerCase().trim())) {
                        assignedIndex = inst;
                        realInstId = realInstructorIds.get(inst);
                        foundInstructor = true;
                        break;
                    }
                }
                if (!foundInstructor) {
                    assignedIndex = Math.abs(dept.hashCode() + i) % instructorDepts.size();
                    realInstId = realInstructorIds.get(assignedIndex);
                }
            }

            data.examInstructors[i] = assignedIndex;
            data.examInstructorIds[i] = realInstId;
        }

        System.out.println("Dataset successfully loaded, parsed, and mapped!");
        return data;
    }

    // --- Helper Loaders & Finders ---

    private int findColumnIndex(String[] headers, String[] possibleNames) {
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim().toLowerCase();
            for (String name : possibleNames) {
                if (h.equalsIgnoreCase(name) || h.contains(name)) return i;
            }
        }
        return 0;
    }

    private void loadTimeslotData(String path, List<String> ids, List<String> days, List<String> times) {
        List<String[]> rows = readCSV(path);
        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length > 0) ids.add(row[0].trim());
            days.add(row.length > 1 ? row[1].trim() : "Day");
            times.add(row.length > 2 ? row[2].trim() : "Time");
        }
    }

    private void loadClassroomData(String path, List<String> rooms, List<Integer> caps) {
        List<String[]> rows = readCSV(path);
        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length > 0 && !row[0].isEmpty()) {
                rooms.add(row[0].trim());
                String capStr = row.length > 3 ? row[3].trim() : (row.length > 1 ? row[1].trim() : "30");
                caps.add(capStr.isEmpty() ? 30 : Integer.parseInt(capStr));
            }
        }
    }

    private void loadCourseData(String path, List<String> courses, List<String> depts) {
        List<String[]> rows = readCSV(path);
        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length > 0 && !row[0].isEmpty()) {
                courses.add(row[0].trim());
                depts.add(row.length > 2 ? row[2].trim() : (row.length > 1 ? row[1].trim() : "General"));
            }
        }
    }

    private List<String[]> readCSV(String path) {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            rows = parseCSVFile(sb.toString());
        } catch (IOException e) {
            System.err.println("Warning: Could not read " + path);
        }
        return rows;
    }

    private List<String[]> parseCSVFile(String content) {
        List<String[]> rows = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim().replaceAll("^\"|\"$", ""));
                sb.setLength(0);
            } else if ((c == '\n' || c == '\r') && !inQuotes) {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') i++;
                tokens.add(sb.toString().trim().replaceAll("^\"|\"$", ""));
                sb.setLength(0);
                if (!tokens.isEmpty() && !(tokens.size() == 1 && tokens.get(0).isEmpty())) {
                    rows.add(tokens.toArray(new String[0]));
                }
                tokens = new ArrayList<>();
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            tokens.add(sb.toString().trim().replaceAll("^\"|\"$", ""));
            rows.add(tokens.toArray(new String[0]));
        }
        return rows;
    }
}