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
        public String[] rooms;
        public int[] examStudents;
    }

    public ScheduleData loadAllDatasets() {
        ScheduleData data = new ScheduleData();
        
        List<String> classroomsList = loadSingleColumn("dataset/classrooms.csv", "classroom_id");
        List<String> coursesList    = loadSingleColumn("dataset/courses.csv", "course_id");
        List<String> timeslotsList  = loadSingleColumn("dataset/timeslot.csv", "timeslot_id");
        List<String> studentsList   = loadSingleColumn("dataset/students.csv", "student_id");

        data.rooms = classroomsList.toArray(new String[0]);
        data.exams = coursesList.toArray(new String[0]);
        data.timeslots = timeslotsList.toArray(new String[0]);
        
        data.examStudents = new int[data.exams.length];
        for (int i = 0; i < data.examStudents.length; i++) {
            data.examStudents[i] = 30; 
        }

        System.out.println("✅ All CSV files safely loaded into memory!");
        return data;
    }

    private List<String> loadSingleColumn(String filePath, String idColumnName) {
        List<String> records = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            StringBuilder fileContent = new StringBuilder();
            String currentLine;
            while ((currentLine = br.readLine()) != null) {
                fileContent.append(currentLine).append("\n");
            }

            List<String[]> rows = parseCSVFile(fileContent.toString());
            if (rows.isEmpty()) return records;

            String[] headers = rows.get(0);
            int targetIndex = 0;
            for (int i = 0; i < headers.length; i++) {
                if (headers[i].trim().equalsIgnoreCase(idColumnName)) {
                    targetIndex = i;
                    break;
                }
            }

            for (int r = 1; r < rows.size(); r++) {
                String[] values = rows.get(r);
                if (values.length > targetIndex) {
                    String val = values[targetIndex].trim().replaceAll("^\"|\"$", "");
                    if (!val.isEmpty()) {
                        records.add(val);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("❌ Error reading file " + filePath + ": " + e.getMessage());
        }
        return records;
    }

    // Fully handles multi-line fields and commas nested inside quotes
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
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else if ((c == '\n' || c == '\r') && !inQuotes) {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    i++; 
                }
                tokens.add(sb.toString().trim());
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
            tokens.add(sb.toString().trim());
            rows.add(tokens.toArray(new String[0]));
        }
        return rows;
    }
}