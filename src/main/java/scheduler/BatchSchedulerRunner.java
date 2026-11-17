package scheduler;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

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

                // Run both methods on exactly the same data and conflict groups
                ScheduleResult greedy = GreedyScheduler.solve(data, groups, name);
                ScheduleResult cp = ScehdulingSolver.solve(data, groups, name, timeLimit, workers);

                // Same bound and same independent checker for both, so the comparison is fair.
                // (Runtime was already recorded inside each solve, so this adds nothing to it.)
                greedy.computeExtras(data, groups);
                cp.computeExtras(data, groups);

                System.out.println(greedy);
                System.out.println(cp);
                System.out.println("  -> " + verdict(greedy, cp) + "\n");

                out.println(greedy.toCsvRow());
                out.println(cp.toCsvRow());
                out.flush();
            }
        }
        System.out.println("Wrote results.csv");
    }

    /** One-line summary of who did better on the main quality measures. */
    private static String verdict(ScheduleResult greedy, ScheduleResult cp) {
        if (cp.scheduled > greedy.scheduled) {
            return "CP-SAT placed " + (cp.scheduled - greedy.scheduled) + " more exams than greedy";
        }
        if (cp.scheduled < greedy.scheduled) {
            return "greedy placed more exams (CP-SAT status: " + cp.status + ")";
        }
        int diff = greedy.timeslotsUsed - cp.timeslotsUsed;
        String proof = "OPTIMAL".equals(cp.status) ? ", proven optimal" : "";
        if (diff > 0) return "CP-SAT used " + diff + " fewer timeslots" + proof;
        if (diff < 0) return "greedy used " + (-diff) + " fewer timeslots (CP-SAT status: " + cp.status + ")";
        return "tie on timeslots" + proof;
    }
}