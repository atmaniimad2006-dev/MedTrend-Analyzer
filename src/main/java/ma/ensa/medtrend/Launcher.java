package ma.ensa.medtrend;

/**
 * Launcher class — bypasses the JavaFX 11+ module system check.
 *
 * When JavaFX 11+ detects that the main class extends Application,
 * it enforces module-path requirements which fail in a classpath-only setup.
 * Using a plain "Launcher" class as the JVM entry point avoids this check,
 * while still correctly delegating to Application.launch().
 *
 * Usage:  mvn exec:java "-Dexec.mainClass=ma.ensa.medtrend.Launcher"
 *    or:  mvn javafx:run
 */
public class Launcher {

    public static void main(String[] args) {
        Main.main(args);
    }
}
