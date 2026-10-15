import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.IntVar;

/**
 * A minimal test to verify that Google OR-Tools is installed and working in Java.
 * It finds an integer value for 'x' between 0 and 10 that satisfies: x > 5.
 */
public class OrToolsTest {
  public static void main(String[] args) {
    // 1. Always load the native libraries first
    Loader.loadNativeLibraries();

    // 2. Create the CP-SAT model
    CpModel model = new CpModel();

    // 3. Create a variable: x in [0, 10]
    IntVar x = model.newIntVar(0, 10, "x");

    // 4. Add a simple constraint: x > 5  (written as x >= 6)
    model.addGreaterThan(x, 5);

    // 5. Create a solver and solve the model
    CpSolver solver = new CpSolver();
    CpSolverStatus status = solver.solve(model);

    // 6. Print the results
    if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
      System.out.println("Google OR-Tools is working perfectly!");
      System.out.println("Found value for x = " + solver.value(x));
    } else {
      System.out.println("Could not find a valid solution.");
    }
  }
}