package scheduler;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import com.google.ortools.Loader;

/**
 * Runs greedy + CP-SAT on every sub-folder of a datasets root and writes results.csv.
 *
 * Expected layout:
 *   datasets/
 *     small/   timeslot.csv classrooms.csv courses.csv students.csv instructors.csv
 *     medium/  ...
 *     large/   ...
 *
 * Usage: java scheduler.BatchSchedulerRunner [datasetsRoot] [timeLimitSec] [includeStudents]
 */
public class BatchSchedulerRunner {

    public static void main(String[] args) throws IOException {
        String root = args.length > 0 ? args[0] : "datasets";
        double timeLimit = args.length > 1 ? Double.parseDouble(args[1]) : 60.0;
        boolean includeStudents = args.length > 2 ? Boolean.parseBoolean(args[2]) : true;
        int workers = Runtime.getRuntime().availableProcessors();

        Loader.loadNativeLibraries(); // once for the whole run

        File[] dirs = new File(root).listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) {
            System.err.println("No dataset folders found in " + root);
            return;
        }
        Arrays.sort(dirs);

        // Warm up the JIT so greedy timings aren't dominated by class loading
        CSVDataLoader.ScheduleData warm = new CSVDataLoader(dirs[0].getPath()).loadAllDatasets();
        GreedyScheduler.solve(warm, ConflictGroups.build(warm, includeStudents), "warmup");

        try (PrintWriter out = new PrintWriter("results.csv")) {
            out.println(ScheduleResult.csvHeader());

            for (File dir : dirs) {
                String name = dir.getName();
                CSVDataLoader.ScheduleData data = new CSVDataLoader(dir.getPath()).loadAllDatasets();
                List<int[]> groups = ConflictGroups.build(data, includeStudents);

                ScheduleResult greedy = GreedyScheduler.solve(data, groups, name);
                ScheduleResult cp = CpSatScheduler.solve(data, groups, name, timeLimit, workers);

                System.out.println(greedy);
                System.out.println(cp);
                out.println(greedy.toCsvRow());
                out.println(cp.toCsvRow());
                out.flush();
            }
        }
        System.out.println("\nWrote results.csv");
    }
}
