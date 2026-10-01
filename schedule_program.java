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

public class App {
    public static void main(String[] args) {
        // 1. Call this FIRST before using any OR-Tools objects
        Loader.loadNativeLibraries();
        try (Reader classrooms= new FileReader('dataset/classrooms.csv')
        // 2. Now you can use OR-Tools normally
        CpModel model = new CpModel();
        // ... build and solve your model ...
    }
}
