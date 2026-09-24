import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;

public class App {
    public static void main(String[] args) {
        // 1. Call this FIRST before using any OR-Tools objects
        Loader.loadNativeLibraries();

        // 2. Now you can use OR-Tools normally
        CpModel model = new CpModel();
        // ... build and solve your model ...
    }
}
