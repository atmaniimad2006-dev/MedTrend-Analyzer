package ma.ensa.medtrend.controllers;

import com.opencsv.CSVWriter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import ma.ensa.medtrend.models.Business;
import ma.ensa.medtrend.services.ILeadGeneratorService;
import ma.ensa.medtrend.services.LeadGeneratorServiceImpl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Controller for LeadGenerator.fxml — Module 1.
 *
 * THREADING MODEL:
 * - All Selenium scraping runs inside a {@link javafx.concurrent.Task} on a daemon thread.
 * - The Task fires a callback per lead, which uses {@link Platform#runLater(Runnable)}
 *   to safely update the TableView, status label, and progress indicator.
 * - The UI remains perfectly fluid during the entire extraction process.
 * - Start button is disabled during extraction and re-enabled on completion/failure.
 */
public class LeadGeneratorController {

    // ── FXML Injected Elements ───────────────────────────────────
    @FXML private TextField nicheField;
    @FXML private TextField locationField;
    @FXML private Spinner<Integer> maxLeadsSpinner;

    @FXML private Button btnStartScraping;
    @FXML private Button btnClearResults;
    @FXML private Button btnExportCsv;

    @FXML private javafx.scene.layout.HBox progressBar;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label statusLabel;
    @FXML private Label countLabel;
    @FXML private Label totalLabel;

    @FXML private TableView<Business> resultsTable;
    @FXML private TableColumn<Business, String> colCompanyName;
    @FXML private TableColumn<Business, String> colWebsite;

    // ── Backend Service ──────────────────────────────────────────
    private final ILeadGeneratorService leadService = new LeadGeneratorServiceImpl();
    private final ObservableList<Business> businessList = FXCollections.observableArrayList();

    /** Reference to the currently running scraping task (for cancellation support). */
    private Task<List<Business>> currentTask;

    @FXML
    public void initialize() {
        // ── Configure Spinner: 1–500 leads, default 20 ──────────
        SpinnerValueFactory<Integer> valueFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 500, 20, 5);
        maxLeadsSpinner.setValueFactory(valueFactory);
        maxLeadsSpinner.setEditable(true);

        // ── Bind TableView columns to Business properties ───────
        colCompanyName.setCellValueFactory(new PropertyValueFactory<>("companyName"));
        colWebsite.setCellValueFactory(new PropertyValueFactory<>("website"));

        // ── Custom CellFactory: Website column styled as link ───
        colWebsite.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String website, boolean empty) {
                super.updateItem(website, empty);
                if (empty || website == null || website.isBlank()) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(website);
                    getStyleClass().removeAll("website-link");
                    getStyleClass().add("website-link");
                }
            }
        });

        // ── Custom CellFactory: Company name with accent color ──
        colCompanyName.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null || name.isBlank()) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(name);
                    setStyle("-fx-font-weight: 600; -fx-text-fill: #e8eaf6;");
                }
            }
        });

        // ── Bind table to observable list ────────────────────────
        resultsTable.setItems(businessList);
    }

    // ══════════════════════════════════════════════════════════════
    //  HANDLE START EXTRACTION
    // ══════════════════════════════════════════════════════════════

    @FXML
    private void handleStartScraping() {
        // ── Validate inputs ─────────────────────────────────────
        String niche = nicheField.getText();
        String location = locationField.getText();

        if (niche == null || niche.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Missing Input",
                    "Please enter a niche or sector to search.");
            return;
        }
        if (location == null || location.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Missing Input",
                    "Please enter a target location.");
            return;
        }

        int maxLeads = maxLeadsSpinner.getValue();

        // ── Lock UI controls during extraction ──────────────────
        setExtractionMode(true);
        businessList.clear();
        updateCountLabel(0);
        statusLabel.setText("Initializing Selenium WebDriver...");

        // ══════════════════════════════════════════════════════════
        //  BACKGROUND TASK — Selenium scraping runs OFF the FX Thread
        // ══════════════════════════════════════════════════════════
        currentTask = new Task<>() {
            @Override
            protected List<Business> call() {
                return leadService.scrapeGoogleMaps(niche, location, maxLeads,
                        (count, business) -> {
                            // ── Callback: update UI from the FX thread ──
                            Platform.runLater(() -> {
                                businessList.add(business);
                                updateCountLabel(count);
                                statusLabel.setText("Extracting... Found: " +
                                        business.getCompanyName());
                            });
                        });
            }
        };

        // ── On Success: finalize UI ─────────────────────────────
        currentTask.setOnSucceeded(event -> Platform.runLater(() -> {
            List<Business> results = currentTask.getValue();
            int total = results != null ? results.size() : 0;

            statusLabel.setText("Extraction complete!");
            updateCountLabel(total);
            totalLabel.setText("Total: " + total + " leads");
            setExtractionMode(false);

            if (total == 0) {
                showAlert(Alert.AlertType.INFORMATION, "No Results",
                        "No businesses with websites were found for \"" +
                                niche + "\" in \"" + location + "\".\n\n" +
                                "Try broadening your search terms.");
            }
        }));

        // ── On Failure: show error and unlock UI ────────────────
        currentTask.setOnFailed(event -> Platform.runLater(() -> {
            Throwable ex = currentTask.getException();
            statusLabel.setText("Extraction failed!");
            setExtractionMode(false);

            String errorMsg = ex != null ? ex.getMessage() : "Unknown error";
            showAlert(Alert.AlertType.ERROR, "Extraction Error",
                    "An error occurred during extraction:\n" + errorMsg);

            if (ex != null) ex.printStackTrace();
        }));

        // ── On Cancelled ────────────────────────────────────────
        currentTask.setOnCancelled(event -> Platform.runLater(() -> {
            statusLabel.setText("Extraction cancelled.");
            setExtractionMode(false);
        }));

        // ── Launch on daemon thread ─────────────────────────────
        Thread taskThread = new Thread(currentTask, "MedTrend-LeadGen-Thread");
        taskThread.setDaemon(true);
        taskThread.start();
    }

    // ══════════════════════════════════════════════════════════════
    //  HANDLE EXPORT TO CSV
    // ══════════════════════════════════════════════════════════════

    @FXML
    private void handleExportCsv() {
        if (businessList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Data",
                    "No leads to export. Run an extraction first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Leads to CSV");
        fileChooser.setInitialFileName("medtrend_leads.csv");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = fileChooser.showSaveDialog(resultsTable.getScene().getWindow());
        if (file == null) return;

        try (CSVWriter writer = new CSVWriter(new FileWriter(file))) {
            // Write header
            writer.writeNext(new String[]{"Company Name", "Website"});

            // Write data rows
            for (Business b : businessList) {
                writer.writeNext(new String[]{
                        b.getCompanyName(),
                        b.getWebsite()
                });
            }

            showAlert(Alert.AlertType.INFORMATION, "Export Successful",
                    businessList.size() + " leads exported to:\n" + file.getName());

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Export Error",
                    "Failed to write CSV: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  HANDLE AUDIT EXTRACTED LEADS
    // ══════════════════════════════════════════════════════════════

    @FXML
    private void handleAuditExtractedLeads() {
        if (businessList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Data",
                    "No leads to audit. Run an extraction first.");
            return;
        }

        List<String> urlsToAudit = businessList.stream()
                .map(Business::getWebsite)
                .filter(url -> url != null && !url.isBlank())
                .toList();

        if (urlsToAudit.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Valid URLs",
                    "None of the extracted leads contain a valid website URL.");
            return;
        }

        // Pass control and data to the MainController mediator
        MainController.getInstance().switchToAuditorWithUrls(urlsToAudit);
    }

    // ══════════════════════════════════════════════════════════════
    //  HANDLE CLEAR RESULTS
    // ══════════════════════════════════════════════════════════════

    @FXML
    private void handleClearResults() {
        businessList.clear();
        updateCountLabel(0);
        totalLabel.setText("Total: 0 leads");
        statusLabel.setText("Ready");
    }

    // ══════════════════════════════════════════════════════════════
    //  UI STATE HELPERS
    // ══════════════════════════════════════════════════════════════

    /**
     * Toggles the UI between "idle" and "extracting" states.
     * Disables input controls during extraction to prevent conflicts.
     *
     * @param extracting True to enter extraction mode, false to exit.
     */
    private void setExtractionMode(boolean extracting) {
        btnStartScraping.setDisable(extracting);
        btnStartScraping.setText(extracting ? "⏳  Extracting..." : "🔍  Start Extraction");
        nicheField.setDisable(extracting);
        locationField.setDisable(extracting);
        maxLeadsSpinner.setDisable(extracting);
        btnExportCsv.setDisable(extracting);

        // Show/hide progress bar
        progressBar.setVisible(extracting);
        progressBar.setManaged(extracting);
        progressIndicator.setProgress(-1); // Indeterminate
    }

    /**
     * Updates the count label and badge with the current lead count.
     */
    private void updateCountLabel(int count) {
        countLabel.setText(count + " leads found");
        totalLabel.setText("Total: " + count + " leads");
    }

    /**
     * Shows a styled alert dialog.
     */
    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        // Apply dark theme to alert dialog
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/style.css").toExternalForm());

        alert.showAndWait();
    }
}
