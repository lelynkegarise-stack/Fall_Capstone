package scheduler;

import java.util.*;

/**
 * Builds "conflict groups": sets of exams that may never share a timeslot
 * (same instructor, and optionally same student). Both CP-SAT and the greedy
 * search consume these, so the comparison uses exactly the same constraints.
 *
 * Uses an inverted index (person -> exams) instead of comparing every pair of
 * exams, and de-duplicates identical groups.
 */
public class ConflictGroups {

    public static List<int[]> build(CSVDataLoader.ScheduleData d, boolean includeStudents) {
        int n = d.exams.length;
        List<int[]> groups = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        Map<String, List<Integer>> byInstructor = new HashMap<>();
        for (int e = 0; e < n; e++) {
            String id = d.examInstructorIds[e];
            if (id == null || id.trim().isEmpty()) continue;
            byInstructor.computeIfAbsent(id.trim().toLowerCase(), k -> new ArrayList<>()).add(e);
        }
        addGroups(byInstructor.values(), groups, seen);

        if (includeStudents) {
            Map<String, List<Integer>> byStudent = new HashMap<>();
            for (int e = 0; e < n; e++) {
                for (String s : d.examStudentLists[e]) {
                    if (s == null || s.trim().isEmpty()) continue;
                    List<Integer> l = byStudent.computeIfAbsent(s.trim(), k -> new ArrayList<>());
                    if (l.isEmpty() || l.get(l.size() - 1) != e) l.add(e); // exams visited in order => sorted
                }
            }
            addGroups(byStudent.values(), groups, seen);
        }
        return groups;
    }

    private static void addGroups(Collection<List<Integer>> lists, List<int[]> out, Set<String> seen) {
        for (List<Integer> l : lists) {
            if (l.size() < 2) continue;
            int[] arr = l.stream().mapToInt(Integer::intValue).toArray();
            if (seen.add(Arrays.toString(arr))) out.add(arr);
        }
    }

    /** Adjacency lists (exam -> conflicting exams), used by the greedy search. */
    public static int[][] adjacency(List<int[]> groups, int n) {
        boolean[][] c = new boolean[n][n];
        for (int[] g : groups)
            for (int i = 0; i < g.length; i++)
                for (int j = i + 1; j < g.length; j++)
                    c[g[i]][g[j]] = c[g[j]][g[i]] = true;
        int[][] adj = new int[n][];
        for (int i = 0; i < n; i++) {
            int cnt = 0;
            for (int j = 0; j < n; j++) if (c[i][j]) cnt++;
            adj[i] = new int[cnt];
            int k = 0;
            for (int j = 0; j < n; j++) if (c[i][j]) adj[i][k++] = j;
        }
        return adj;
    }
}
