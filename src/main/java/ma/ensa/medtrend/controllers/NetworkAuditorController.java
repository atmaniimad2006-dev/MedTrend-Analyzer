package ma.ensa.medtrend.controllers;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import ma.ensa.medtrend.models.AuditResult;
import ma.ensa.medtrend.services.INetworkAuditService;
import ma.ensa.medtrend.services.NetworkAuditServiceImpl;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for NetworkAuditor.fxml — Module 2.
 *
 * THREADING MODEL:
 * - All network auditing (Jsoup + java.net) runs inside a {@link Task} on a daemon thread.
 * - Uses {@link Platform#runLater(Runnable)} for all UI updates.
 * - Supports both single URL scanning and batch CSV processing.
 */
public class NetworkAuditorController {

    // ── FXML Injected Elements ───────────────────────────────────
    @FXML private TextField urlField;
    @FXML private Button btnScanSingle;
    @FXML private Button btnLoadCsv;
    @FXML private Button btnClearResults;
    @FXML private Button btnExportCsv;

    @FXML private javafx.scene.layout.HBox progressBar;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label statusLabel;
    @FXML private Label countLabel;
    @FXML private Label totalLabel;

    @FXML private TableView<AuditResult> resultsTable;
    @FXML private TableColumn<AuditResult, String>  colUrl;
    @FXML private TableColumn<AuditResult, String>  colEmails;
    @FXML private TableColumn<AuditResult, String>  colPhoneNumbers;
    @FXML private TableColumn<AuditResult, Boolean> colSslStatus;
    @FXML private TableColumn<AuditResult, String>  colServerTech;

    // ── Backend Service ──────────────────────────────────────────
    private final INetworkAuditService auditService = new NetworkAuditServiceImpl();
    private final ObservableList<AuditResult> auditResults = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // ── Bind TableView columns ──────────────────────────────
        colUrl.setCellValueFactory(new PropertyValueFactory<>("url"));
        colEmails.setCellValueFactory(new PropertyValueFactory<>("contactEmails"));
        colPhoneNumbers.setCellValueFactory(new PropertyValueFactory<>("phoneNumbers"));
        colSslStatus.setCellValueFactory(new PropertyValueFactory<>("sslValid"));
        colServerTech.setCellValueFactory(new PropertyValueFactory<>("serverTech"));

        // ── Custom CellFactory: SSL Status with color indicators ─
        colSslStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean valid, boolean empty) {
                super.updateItem(valid, empty);
                getStyleClass().removeAll("ssl-valid", "ssl-invalid");

                if (empty || valid == null) {
                    setText(null);
                    setStyle("");
                } else if (valid) {
                    setText("✅ Valid");
                    getStyleClass().add("ssl-valid");
                } else {
                    setText("❌ Invalid");
                    getStyleClass().add("ssl-invalid");
                }
            }
        });

        // ── Custom CellFactory: Emails with styling ─────────────
        colEmails.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String emails, boolean empty) {
                super.updateItem(emails, empty);
                getStyleClass().removeAll("email-found", "email-none");

                if (empty || emails == null || emails.isBlank()) {
                    setText(null);
                    setStyle("");
                } else if (emails.equals("None found")) {
                    setText("None found");
                    getStyleClass().add("email-none");
                } else {
                    setText(emails);
                    getStyleClass().add("email-found");
                }
            }
        });

        // ── Custom CellFactory: Phone Numbers with styling ─────────
        colPhoneNumbers.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String phones, boolean empty) {
                super.updateItem(phones, empty);
                getStyleClass().removeAll("email-found", "email-none");

                if (empty || phones == null || phones.isBlank()) {
                    setText(null);
                    setStyle("");
                } else if (phones.equals("None found")) {
                    setText("None found");
                    getStyleClass().add("email-none");
                } else {
                    setText(phones);
                    getStyleClass().add("email-found");
                }
            }
        });

        // ── Custom CellFactory: URL as link ─────────────────────
        colUrl.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String url, boolean empty) {
                super.updateItem(url, empty);
                if (empty || url == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(url);
                    getStyleClass().removeAll("website-link");
                    getStyleClass().add("website-link");
                }
            }
        });

        // ── Custom CellFactory: Server Tech ─────────────────────
        colServerTech.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String tech, boolean empty) {
                super.updateItem(tech, empty);
                if (empty || tech == null || tech.isBlank()) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(tech);
                    if (tech.equals("Not exposed")) {
                        setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: 600;");
                    } else if (tech.equals("Unreachable") || tech.equals("SSL Error")) {
                        setStyle("-fx-text-fill: #ff4757; -fx-font-weight: 600;");
                    } else {
                        setStyle("-fx-text-fill: #ffb347; -fx-font-weight: 600;");
                    }
                }
            }
        });

        resultsTable.setItems(auditResults);
    }

    // ══════════════════════════════════════════════════════════════
    //  HANDLE SCAN SINGLE URL
    // ══════════════════════════════════════════════════════════════

    @FXML
    private void handleScanSingle() {
        String url = urlField.getText();
        if (url == null || url.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Missing Input",
                    "Please enter a URL to scan.");
            return;
        }

        setScanMode(true);
        statusLabel.setText("Scanning: " + url + "...");
        countLabel.setText("1 / 1 scanning");

        Task<AuditResult> task = new Task<>() {
            @Override
            protected AuditResult call() {
                return auditService.auditSingleUrl(url.trim());
            }
        };

        task.setOnSucceeded(event -> Platform.runLater(() -> {
            AuditResult result = task.getValue();
            auditResults.add(result);
            urlField.clear();
            statusLabel.setText("Scan complete!");
            countLabel.setText("1 / 1 scanned");
            updateTotal();
            setScanMode(false);
        }));

        task.setOnFailed(event -> Platform.runLater(() -> {
            statusLabel.setText("Scan failed!");
            setScanMode(false);
            Throwable ex = task.getException();
            showAlert(Alert.AlertType.ERROR, "Scan Error",
                    "Error scanning URL:\n" + (ex != null ? ex.getMessage() : "Unknown"));
            if (ex != null) ex.printStackTrace();
        }));

        Thread t = new Thread(task, "MedTrend-SingleAudit-Thread");
        t.setDaemon(true);
        t.start();
    }

    // ══════════════════════════════════════════════════════════════
    //  HANDLE LOAD CSV BATCH
    // ══════════════════════════════════════════════════════════════

    @FXML
    private void handleLoadCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load URLs from CSV");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = fileChooser.showOpenDialog(resultsTable.getScene().getWindow());
        if (file == null) return;

        // ── Parse URLs from CSV ─────────────────────────────────
        List<String> urls = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            List<String[]> rows = reader.readAll();
            boolean isHeader = true;
            int urlIndex = 0; // default to first column

            for (String[] row : rows) {
                if (isHeader) {
                    isHeader = false;
                    // Dynamic Header Parsing
                    for (int i = 0; i < row.length; i++) {
                        String col = row[i].toLowerCase().trim();
                        if (col.equals("website") || col.equals("url")) {
                            urlIndex = i;
                            break;
                        }
                    }
                    continue;
                }
                if (row.length > urlIndex && !row[urlIndex].isBlank()) {
                    urls.add(row[urlIndex].trim());
                }
            }
        } catch (IOException | CsvException e) {
            showAlert(Alert.AlertType.ERROR, "CSV Error",
                    "Failed to read CSV: " + e.getMessage());
            return;
        }

        if (urls.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Empty CSV",
                    "No URLs found in the CSV file.");
            return;
        }

        startBatchAudit(urls);
    }

    // ══════════════════════════════════════════════════════════════
    //  SHARED BATCH AUDIT LOGIC (Called by CSV & MainController)
    // ══════════════════════════════════════════════════════════════

    public void startBatchAudit(List<String> urls) {
        if (urls == null || urls.isEmpty()) return;

        final int totalUrls = urls.size();
        setScanMode(true);
        statusLabel.setText("Auditing " + totalUrls + " URLs...");
        countLabel.setText("0 / " + totalUrls + " scanned");

        Task<List<AuditResult>> task = new Task<>() {
            @Override
            protected List<AuditResult> call() {
                return auditService.auditBatch(urls, (index, result) -> {
                    Platform.runLater(() -> {
                        auditResults.add(result);
                        countLabel.setText(index + " / " + totalUrls + " scanned");
                        statusLabel.setText("Scanning: " + result.getUrl());
                    });
                });
            }
        };

        task.setOnSucceeded(event -> Platform.runLater(() -> {
            statusLabel.setText("Batch audit complete!");
            countLabel.setText(totalUrls + " / " + totalUrls + " scanned");
            updateTotal();
            setScanMode(false);

            showAlert(Alert.AlertType.INFORMATION, "Batch Complete",
                    totalUrls + " URLs audited successfully.");
        }));

        task.setOnFailed(event -> Platform.runLater(() -> {
            statusLabel.setText("Batch audit failed!");
            setScanMode(false);
            Throwable ex = task.getException();
            showAlert(Alert.AlertType.ERROR, "Batch Error",
                    "Error during batch audit:\n" + (ex != null ? ex.getMessage() : "Unknown"));
            if (ex != null) ex.printStackTrace();
        }));

        Thread t = new Thread(task, "MedTrend-BatchAudit-Thread");
        t.setDaemon(true);
        t.start();
    }

    // ══════════════════════════════════════════════════════════════
    //  HANDLE EXPORT REPORT
    // ══════════════════════════════════════════════════════════════

    @FXML
    private void handleExportCsv() {
        if (auditResults.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Data",
                    "No audit results to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Audit Report");
        fileChooser.setInitialFileName("medtrend_audit_report.csv");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = fileChooser.showSaveDialog(resultsTable.getScene().getWindow());
        if (file == null) return;

        try (CSVWriter writer = new CSVWriter(new FileWriter(file))) {
            writer.writeNext(new String[]{
                    "URL", "Contact Emails", "Phone Numbers", "SSL Status", "Server Tech"
            });

            for (AuditResult r : auditResults) {
                writer.writeNext(new String[]{
                        r.getUrl(),
                        r.getContactEmails(),
                        r.getPhoneNumbers(),
                        r.isSslValid() ? "Valid" : "Invalid",
                        r.getServerTech()
                });
            }

            showAlert(Alert.AlertType.INFORMATION, "Export Successful",
                    auditResults.size() + " results exported to:\n" + file.getName());

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Export Error",
                    "Failed to write CSV: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  HANDLE CLEAR RESULTS
    // ══════════════════════════════════════════════════════════════

    @FXML
    private void handleClearResults() {
        auditResults.clear();
        statusLabel.setText("Ready");
        countLabel.setText("0 / 0 scanned");
        totalLabel.setText("Total: 0 URLs audited");
    }

    // ══════════════════════════════════════════════════════════════
    //  UI STATE HELPERS
    // ══════════════════════════════════════════════════════════════

    private void setScanMode(boolean scanning) {
        btnScanSingle.setDisable(scanning);
        btnScanSingle.setText(scanning ? "⏳  Scanning..." : "🔍  Scan Single URL");
        btnLoadCsv.setDisable(scanning);
        urlField.setDisable(scanning);
        btnExportCsv.setDisable(scanning);

        progressBar.setVisible(scanning);
        progressBar.setManaged(scanning);
        progressIndicator.setProgress(-1);
    }

    private void updateTotal() {
        totalLabel.setText("Total: " + auditResults.size() + " URLs audited");
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/css/style.css").toExternalForm());
        alert.showAndWait();
    }
}
