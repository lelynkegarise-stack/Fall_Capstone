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
        public int[] examInstructors;
        public String[] courseDepartments;
    }

    public ScheduleData loadAllDatasets() {
        ScheduleData data = new ScheduleData();

        // 1. Load Timeslots (ID, Day, Time)
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

        // 4. Load Supporting Lists for Calculations
        List<String> studentPrograms = loadSingleColumn("dataset/students.csv", "program_name");
        List<String> instructorDepts = loadSingleColumn("dataset/instructors.csv", "department");

        // 5. Calculate Exam Students & Assign Instructors
        data.examStudents = new int[data.exams.length];
        data.examInstructors = new int[data.exams.length];
        int numInstructors = Math.max(1, instructorDepts.size());

        for (int i = 0; i < data.exams.length; i++) {
            String dept = data.courseDepartments[i].toLowerCase().trim();
            
            // Calculate student size
            int count = 0;
            for (String prog : studentPrograms) {
                String pLower = prog.toLowerCase().trim();
                if (pLower.equals(dept) || pLower.contains("in " + dept)) count++;
            }
            if (count == 0 || count > 45) {
                count = 15 + (Math.abs(dept.hashCode() + i) % 26);
            }
            data.examStudents[i] = count;

            // Assign Instructor ID based on department hash
            data.examInstructors[i] = Math.abs(dept.hashCode()) % numInstructors;
        }

        System.out.println("Dataset successfully loaded and parsed!");
        return data;
    }

    // --- Helper Loaders ---

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

    private List<String> loadSingleColumn(String path, String colName) {
        List<String> list = new ArrayList<>();
        List<String[]> rows = readCSV(path);
        if (rows.isEmpty()) return list;
        
        int targetIdx = 0;
        String[] headers = rows.get(0);
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(colName)) {
                targetIdx = i;
                break;
            }
        }

        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length > targetIdx) {
                String val = row[targetIdx].trim().replaceAll("^\"|\"$", "");
                if (!val.isEmpty()) list.add(val);
            }
        }
        return list;
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