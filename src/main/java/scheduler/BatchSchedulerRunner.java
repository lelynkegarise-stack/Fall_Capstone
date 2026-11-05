package scheduler;

import java.util.List;

public class BatchSchedulerRunner {
    public static void main(String[] args) {
        // List the relative paths to your 5 different dataset folders
        String[] datasetPaths = {
            "datasets/ds1",
            //"datasets/ds2",
            //"datasets/ds3",
           // "datasets/ds4",
            //"datasets/ds5"
        };

        System.out.println("==================================================");
        System.out.println("          MULTI-DATASET BATCH RUNNER              ");
        System.out.println("==================================================");

        for (int i = 0; i < datasetPaths.length; i++) {
            System.out.println("\n--- Processing Dataset " + (i + 1) + " (" + datasetPaths[i] + ") ---");
            
            try {
                // Pass the specific dataset path to your updated loader
                CSVDataLoader loader = new CSVDataLoader(datasetPaths[i]);
                CSVDataLoader.ScheduleData data = loader.loadAllDatasets();

                long startTime = System.currentTimeMillis();
                List<GreedyScheduler.ScheduleAssignment> schedule = GreedyScheduler.runGreedyScheduler(data);
                long endTime = System.currentTimeMillis();

                // Calculate metrics
                int totalExams = data.exams.length;
                int scheduledExams = schedule.size();
                double successRate = totalExams > 0 ? ((double) scheduledExams / totalExams) * 100.0 : 0.0;

                System.out.printf("📊 Result -> Scheduled: %d/%d (%.1f%%) | Execution Time: %d ms\n", 
                        scheduledExams, totalExams, successRate, (endTime - startTime));

            } catch (Exception e) {
                System.err.println("❌ Dataset " + (i + 1) + " FAILED. Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
        System.out.println("\n==================================================");
        System.out.println("Batch execution complete!");
    }
}