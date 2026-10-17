package scheduler;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class CSVDataLoader {

    // Nested data transfer object to hold everything together
    public static class ScheduleData {
        public List<String> classrooms = new ArrayList<>();
        public List<String> courses = new ArrayList<>();
        public List<String> students = new ArrayList<>();
        public List<String> timeslots = new ArrayList<>();
    }

    public ScheduleData loadAllDatasets() {
        ScheduleData data = new ScheduleData();
        
        String classroomsPath = "dataset/classrooms.csv";
        String coursesPath    = "dataset/courses.csv";
        String studentsPath    = "dataset/students.csv";
        String timeslotPath    = "dataset/timeslot.csv";

        try {
            // 1. Classrooms
            try (Reader r = new FileReader(classroomsPath); CSVParser p = new CSVParser(r, CSVFormat.EXCEL.builder().setHeader().setSkipHeaderRecord(true).build())) {
                for (CSVRecord rec : p) data.classrooms.add(rec.get("classroom_id").trim());
            }
            // 2. Courses
            try (Reader r = new FileReader(coursesPath); CSVParser p = new CSVParser(r, CSVFormat.EXCEL.builder().setHeader().setSkipHeaderRecord(true).build())) {
                for (CSVRecord rec : p) data.courses.add(rec.get("course_id").trim());
            }
            // 4. Students
            try (Reader r = new FileReader(studentsPath); CSVParser p = new CSVParser(r, CSVFormat.EXCEL.builder().setHeader().setSkipHeaderRecord(true).build())) {
                for (CSVRecord rec : p) data.students.add(rec.get("student_id").trim());
            }
            // 5. Timeslots
            try (Reader r = new FileReader(timeslotPath); CSVParser p = new CSVParser(r, CSVFormat.EXCEL.builder().setHeader().setSkipHeaderRecord(true).build())) {
                for (CSVRecord rec : p) data.timeslots.add(rec.get("timeslot_id").trim());
            }
            
            System.out.println("✅ All CSV files safely loaded into memory!");
        } catch (IOException e) {
            System.err.println("❌ Critical Error loading files: " + e.getMessage());
        }

        return data;
    }
}
