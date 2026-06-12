package ma.ensa.medtrend;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * JavaFX Application entry point for MedTrend Analyzer.
 * Loads the MainLayout (sidebar + content area) and applies the premium dark theme.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(
                Objects.requireNonNull(getClass().getResource("/views/MainLayout.fxml"))
        );

        Scene scene = new Scene(root, 1280, 800);

        // Apply the premium dark stylesheet
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/style.css")).toExternalForm()
        );

        primaryStage.setTitle("MedTrend Analyzer — B2B Extraction & Network Security Audit");
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(650);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
