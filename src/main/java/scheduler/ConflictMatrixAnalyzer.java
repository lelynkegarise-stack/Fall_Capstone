package scheduler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConflictMatrixAnalyzer {

    /**
     * Computes the Primal Student Conflict Matrix for all exams.
     */
    public static int[][] computePrimalConflictMatrix(String[] exams, List<String>[] examStudentLists) {
        int n = exams.length;
        int[][] conflictMatrix = new int[n][n];

        for (int i = 0; i < n; i++) {
            Set<String> studentsI = new HashSet<>(examStudentLists[i]);

            for (int j = 0; j < n; j++) {
                if (i == j) {
                    conflictMatrix[i][j] = studentsI.size();
                } else {
                    Set<String> studentsJ = new HashSet<>(examStudentLists[j]);
                    int sharedCount = 0;
                    for (String studentId : studentsI) {
                        if (studentsJ.contains(studentId)) {
                            sharedCount++;
                        }
                    }
                    conflictMatrix[i][j] = sharedCount;
                }
            }
        }
        return conflictMatrix;
    }

    /**
     * Exports the conflict matrix to a CSV file for Python plotting.
     */
    public static void exportMatrixToCSV(int[][] matrix, String[] exams, String filePath) {
        try (PrintWriter writer = new PrintWriter(new File(filePath))) {
            // Write header row (Exam IDs)
            writer.print("Exam," + String.join(",", exams) + "\n");
            
            // Write rows
            for (int i = 0; i < matrix.length; i++) {
                writer.print(exams[i]);
                for (int j = 0; j < matrix[i].length; j++) {
                    writer.print("," + matrix[i][j]);
                }
                writer.print("\n");
            }
            System.out.println("Successfully exported matrix to CSV for Python!");
        } catch (FileNotFoundException e) {
            System.err.println("Could not export CSV: " + e.getMessage());
        }
    }

    /**
     * Optional main method for testing independently.
     */
    public static void main(String[] args) {
        CSVDataLoader loader = new CSVDataLoader();
        CSVDataLoader.ScheduleData dataset = loader.loadAllDatasets();

        List<String>[] studentListsList = new List[dataset.examStudentLists.length];
        for (int i = 0; i < dataset.examStudentLists.length; i++) {
            List<String> list = new ArrayList<>();
            for (String student : dataset.examStudentLists[i]) {
                if (student != null && !student.trim().isEmpty()) {
                    list.add(student);
                }
            }
            studentListsList[i] = list;
        }

        int[][] matrix = computePrimalConflictMatrix(dataset.exams, studentListsList);
        exportMatrixToCSV(matrix, dataset.exams, "conflict_matrix.csv");
    }
}