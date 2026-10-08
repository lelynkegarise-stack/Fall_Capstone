import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
public class CSVdataORtools{
        static class DataRow {
        int weight;
        int value;

        DataRow(int weight, int value) {
            this.weight = weight;
            this.value = value;
        }
    }

public class App {
    public static void main(String[] args) {
        // 1. Call this FIRST before using any OR-Tools objects
        Loader.loadNativeLibraries();
        String classrooms='dataset/classrooms.csv';
        String courses='dataset/courses.csv';
        String schedules='dataset/schedule.csv';
        String students='dataset/students.csv'
        String timeslot='dataset/timeslot.csv'
        List<DataRow> dataset = new ArrayList<>();

        // 2. Safely parse the Excel-formatted CSV file
        try (Reader reader = new FileReader(csvFilePath);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.EXCEL.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .build())) {

            for (CSVRecord record : csvParser) {
                // Fetch fields by their exact Excel column header names
                int weight = Integer.parseInt(record.get("weight").trim());
                int value = Integer.parseInt(record.get("value").trim());
                dataset.add(new DataRow(weight, value));
            }

        // 2. Now you can use OR-Tools normally
        CpModel model = new CpModel();
        // ... build and solve your model ...
    }
}
