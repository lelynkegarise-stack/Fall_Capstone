package scheduler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.ortools.Loader;

/**
 * Runs greedy + CP-SAT on every sub-folder of a datasets root and writes results.csv.
 *
 * Expected layout:
 *   datasets/
 *     d1/   timeslot.csv classrooms.csv courses.csv students.csv instructors.csv enrollments.csv
 *     d2/   ...
 *
 * Usage: java scheduler.BatchSchedulerRunner [datasetsRoot] [timeLimitSec] [includeStudents] [resume]
 */
public class BatchSchedulerRunner {

    private static final String OUTPUT_FILE = "results.csv";

    public static void main(String[] args) throws IOException {
        String root = args.length > 0 ? args[0] : "datasets";
        double timeLimit = args.length > 1 ? Double.parseDouble(args[1]) : 500.0;
        boolean includeStudents = args.length > 2 ? Boolean.parseBoolean(args[2]) : true;
        boolean resume = args.length > 3 ? Boolean.parseBoolean(args[3]) : true;
        int workers = Runtime.getRuntime().availableProcessors();

        File rootDir = new File(root);
        File[] dirs = rootDir.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) {
            System.err.println("No dataset folders found in root directory: " + rootDir.getAbsolutePath());
            return;
        }

        // Natural sort so d2 comes before d10
        Arrays.sort(dirs, Comparator.comparing(File::getName, BatchSchedulerRunner::naturalCompare));

        System.out.println("Root directory: " + rootDir.getAbsolutePath());
        System.out.println("Discovered " + dirs.length + " dataset folders:");
        for (int i = 0; i < dirs.length; i++) {
            System.out.println("  [" + (i + 1) + "] " + dirs[i].getName());
        }
        System.out.flush();

        Set<String> completedDatasets = new HashSet<>();
        File outFile = new File(OUTPUT_FILE);
        boolean append = false;

        if (resume && outFile.exists() && outFile.length() > 0) {
            completedDatasets = loadCompletedDatasets(outFile);
            append = true;
            System.out.println("Resuming from " + OUTPUT_FILE + " (" + completedDatasets.size() + " datasets already recorded).");
            System.out.flush();
        }

        System.out.println("\nLoading Google OR-Tools native libraries...");
        System.out.flush();
        Loader.loadNativeLibraries();

        // Warm up the JIT on the first available dataset
        File warmupTarget = dirs[0];
        for (File d : dirs) {
            if (!completedDatasets.contains(d.getName())) {
                warmupTarget = d;
                break;
            }
        }
        System.out.println("Running JIT warmup on " + warmupTarget.getName() + "...");
        System.out.flush();
        try {
            CSVDataLoader.ScheduleData warm = new CSVDataLoader(warmupTarget.getPath()).loadAllDatasets();
            GreedyScheduler.solve(warm, ConflictGroups.build(warm, includeStudents), "warmup");
        } catch (Exception e) {
            System.err.println("Warning: JIT warmup failed on " + warmupTarget.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }

        int succeeded = 0;
        int failed = 0;
        int skipped = 0;

        try (PrintWriter out = new PrintWriter(new FileWriter(outFile, append))) {
            if (!append) {
                out.println(ScheduleResult.csvHeader());
                out.flush();
            }

            for (int i = 0; i < dirs.length; i++) {
                File dir = dirs[i];
                String name = dir.getName();

                if (resume && completedDatasets.contains(name)) {
                    System.out.println("\nSkipping [" + name + "] (" + (i + 1) + "/" + dirs.length + ") — already in " + OUTPUT_FILE);
                    skipped++;
                    continue;
                }

                System.out.println("\n----------------------------------------");
                System.out.println("Processing dataset [" + name + "] (" + (i + 1) + " of " + dirs.length + ")");
                System.out.println("----------------------------------------");
                System.out.flush();

                try {
                    System.out.println("-> Loading CSV datasets for " + name + "...");
                    System.out.flush();
                    CSVDataLoader.ScheduleData data = new CSVDataLoader(dir.getPath()).loadAllDatasets();

                    System.out.println("-> Building conflict groups (includeStudents = " + includeStudents + ")...");
                    System.out.flush();
                    List<int[]> groups = ConflictGroups.build(data, includeStudents);

                    System.out.println("-> Running Greedy Scheduler...");
                    System.out.flush();
                    ScheduleResult greedy = GreedyScheduler.solve(data, groups, name);

                    System.out.println("-> Running CP-SAT Solver (Time limit: " + timeLimit + "s, Workers: " + workers + ")...");
                    System.out.flush();
                    ScheduleResult cp = ScehdulingSolver.solve(data, groups, name, timeLimit, workers, greedy);

                    greedy.computeExtras(data, groups);
                    cp.computeExtras(data, groups);

                    System.out.println(greedy);
                    System.out.println(cp);
                    System.out.println("  -> " + verdict(greedy, cp));
                    System.out.flush();

                    out.println(greedy.toCsvRow());
                    out.println(cp.toCsvRow());
                    out.flush();
                    succeeded++;
                } catch (Exception | AssertionError ex) {
                    failed++;
                    System.err.println("ERROR processing dataset [" + name + "]: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
                    ex.printStackTrace();
                    System.err.flush();
                }
            }
        }

        System.out.println("\n========================================");
        System.out.println("Batch finished! Succeeded: " + succeeded + ", Failed: " + failed + ", Skipped: " + skipped);
        System.out.println("Results written to: " + outFile.getAbsolutePath());
        System.out.println("========================================");
        System.out.flush();
    }

    /**
     * Reads existing results.csv to find dataset names already written.
     * Assumes the dataset name is the first comma-separated column in each row.
     */
    private static Set<String> loadCompletedDatasets(File csvFile) {
        Set<String> done = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean isHeader = true;
            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split(",", 2);
                if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                    done.add(parts[0].trim());
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not read existing " + csvFile.getName() + " for resume: " + e.getMessage());
        }
        return done;
    }

    private static int naturalCompare(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startA = i;
                int startB = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                long numA = Long.parseLong(a.substring(startA, i));
                long numB = Long.parseLong(b.substring(startB, j));
                if (numA != numB) {
                    return Long.compare(numA, numB);
                }
            } else {
                if (ca != cb) {
                    return Character.compare(ca, cb);
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length(), b.length());
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