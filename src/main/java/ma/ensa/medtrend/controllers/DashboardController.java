package ma.ensa.medtrend.controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;

import ma.ensa.medtrend.dao.DatabaseManager;
import ma.ensa.medtrend.dao.LeadDaoImpl;
import ma.ensa.medtrend.models.Lead;
import ma.ensa.medtrend.services.CyberAuditServiceImpl;
import ma.ensa.medtrend.services.ICyberAuditService;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Controller for Dashboard.fxml.
 * Handles audit rapide, CSV import/export, table display, and DB operations.
 */
public class DashboardController {

    // ── FXML Injected Elements ────────────────────────────────
    @FXML private TextField urlField;
    @FXML private Button btnAuditRapide;
    @FXML private Button btnChargerCsv;
    @FXML private Button btnExporterCsv;
    @FXML private Button btnViderBdd;
    @FXML private Label leadCountLabel;

    @FXML private TableView<Lead> leadsTable;
    @FXML private TableColumn<Lead, String> colUrl;
    @FXML private TableColumn<Lead, String> colEmail;
    @FXML private TableColumn<Lead, Boolean> colWhatsApp;
    @FXML private TableColumn<Lead, Boolean> colSslValid;
    @FXML private TableColumn<Lead, String> colServerInfo;
    @FXML private TableColumn<Lead, Integer> colRiskScore;

    // ── Backend Engines ───────────────────────────────────────
    private final ICyberAuditService auditService = new CyberAuditServiceImpl();
    private LeadDaoImpl leadDao;
    private final ObservableList<Lead> leadsList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Initialize the database and DAO
        DatabaseManager.getInstance().initDatabase();
        leadDao = new LeadDaoImpl();

        // ── PropertyValueFactory for each column ──────────────
        colUrl.setCellValueFactory(new PropertyValueFactory<>("url"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colWhatsApp.setCellValueFactory(new PropertyValueFactory<>("hasWhatsApp"));
        colSslValid.setCellValueFactory(new PropertyValueFactory<>("sslValid"));
        colServerInfo.setCellValueFactory(new PropertyValueFactory<>("serverInfo"));
        colRiskScore.setCellValueFactory(new PropertyValueFactory<>("riskScore"));

        // ── CellFactory: Boolean display for WhatsApp ─────────
        colWhatsApp.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(value ? "Oui" : "Non");
                    setStyle(value
                            ? "-fx-text-fill: #2ecc71; -fx-font-weight: bold;"
                            : "-fx-text-fill: #7a7a8e;");
                }
            }
        });

        // ── CellFactory: Boolean display for SSL Valid ────────
        colSslValid.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(value ? "Valide" : "Invalide");
                    setStyle(value
                            ? "-fx-text-fill: #2ecc71; -fx-font-weight: bold;"
                            : "-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                }
            }
        });

        // ── CRITICAL: CellFactory for Risk Score ──────────────
        //    Score > 50 → RED + BOLD   |   Score <= 50 → GREEN
        colRiskScore.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Integer score, boolean empty) {
                super.updateItem(score, empty);
                if (empty || score == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(score + " / 100");
                    if (score > 50) {
                        setTextFill(Color.web("#e94560"));
                        setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
                    } else {
                        setTextFill(Color.web("#2ecc71"));
                        setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
                    }
                }
            }
        });

        // ── CellFactory: Null-safe email display ──────────────
        colEmail.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String email, boolean empty) {
                super.updateItem(email, empty);
                if (empty || email == null || email.isBlank()) {
                    setText("Non trouvé");
                    setStyle("-fx-text-fill: #535370; -fx-font-style: italic;");
                } else {
                    setText(email);
                    setStyle("-fx-text-fill: #3498db;");
                }
            }
        });

        // Bind table to observable list
        leadsTable.setItems(leadsList);

        // Load existing leads from DB
        refreshTableFromDb();
    }

    // ── Handle "Audit Rapide" button ──────────────────────────
    @FXML
    private void handleAuditRapide() {
        String url = urlField.getText();
        if (url == null || url.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Champ vide", "Veuillez entrer une URL cible.");
            return;
        }

        btnAuditRapide.setDisable(true);
        btnAuditRapide.setText("Audit en cours...");

        Task<Lead> task = new Task<>() {
            @Override
            protected Lead call() {
                Lead lead = new Lead(url.trim());
                lead.setEmail("N/A");
                lead.setHasWhatsApp(false);
                auditService.performAudit(Collections.singletonList(lead));
                leadDao.batchInsert(Collections.singletonList(lead));
                return lead;
            }
        };

        task.setOnSucceeded(event -> Platform.runLater(() -> {
            leadsList.add(task.getValue());
            urlField.clear();
            btnAuditRapide.setDisable(false);
            btnAuditRapide.setText("Audit Rapide");
            updateLeadCount();
        }));

        task.setOnFailed(event -> Platform.runLater(() -> {
            btnAuditRapide.setDisable(false);
            btnAuditRapide.setText("Audit Rapide");
            showAlert(Alert.AlertType.ERROR, "Erreur", "Échec de l'audit : " + task.getException().getMessage());
            task.getException().printStackTrace();
        }));

        Thread auditThread = new Thread(task, "MedTrend-AuditRapide-Thread");
        auditThread.setDaemon(true);
        auditThread.start();
    }

    // ── Handle "Charger CSV" button ───────────────────────────
    @FXML
    private void handleChargerCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Charger un fichier CSV");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fichiers CSV", "*.csv")
        );

        File file = fileChooser.showOpenDialog(leadsTable.getScene().getWindow());
        if (file == null) return;

        btnChargerCsv.setDisable(true);
        btnChargerCsv.setText("Chargement...");

        Task<List<Lead>> task = new Task<>() {
            @Override
            protected List<Lead> call() throws Exception {
                List<Lead> parsedLeads = new ArrayList<>();

                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line;
                    boolean isHeader = true;

                    while ((line = br.readLine()) != null) {
                        // Skip header line
                        if (isHeader) {
                            isHeader = false;
                            continue;
                        }

                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) continue;

                        // CSV format: url,email,hasWhatsApp
                        // or just: url (one URL per line)
                        String[] parts = trimmed.split(",");
                        Lead lead = new Lead(parts[0].trim());

                        if (parts.length > 1) {
                            lead.setEmail(parts[1].trim());
                        } else {
                            lead.setEmail("N/A");
                        }

                        if (parts.length > 2) {
                            lead.setHasWhatsApp(Boolean.parseBoolean(parts[2].trim()));
                        }

                        parsedLeads.add(lead);
                    }
                }

                // Perform cyber audit on all loaded leads
                auditService.performAudit(parsedLeads);

                // Save to DB
                leadDao.batchInsert(parsedLeads);

                return parsedLeads;
            }
        };

        task.setOnSucceeded(event -> Platform.runLater(() -> {
            List<Lead> results = task.getValue();
            leadsList.addAll(results);
            btnChargerCsv.setDisable(false);
            btnChargerCsv.setText("Charger CSV");
            updateLeadCount();
            showAlert(Alert.AlertType.INFORMATION, "Succès",
                    results.size() + " lead(s) chargé(s) et audité(s) avec succès.");
        }));

        task.setOnFailed(event -> Platform.runLater(() -> {
            btnChargerCsv.setDisable(false);
            btnChargerCsv.setText("Charger CSV");
            showAlert(Alert.AlertType.ERROR, "Erreur CSV",
                    "Erreur lors du chargement : " + task.getException().getMessage());
            task.getException().printStackTrace();
        }));

        new Thread(task, "MedTrend-CSVLoad-Thread").start();
    }

    // ── Handle "Exporter CSV" button ──────────────────────────
    @FXML
    private void handleExporterCsv() {
        if (leadsList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Aucune donnée", "Aucun lead à exporter.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter en CSV");
        fileChooser.setInitialFileName("medtrend_export.csv");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fichiers CSV", "*.csv")
        );

        File file = fileChooser.showSaveDialog(leadsTable.getScene().getWindow());
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            // Header
            pw.println("URL,Email,WhatsApp,SSL,Serveur,RiskScore");

            for (Lead lead : leadsList) {
                pw.printf("%s,%s,%s,%s,%s,%d%n",
                        escapeCsv(lead.getUrl()),
                        escapeCsv(lead.getEmail()),
                        lead.isHasWhatsApp(),
                        lead.isSslValid(),
                        escapeCsv(lead.getServerInfo()),
                        lead.getRiskScore());
            }

            showAlert(Alert.AlertType.INFORMATION, "Export réussi",
                    leadsList.size() + " lead(s) exporté(s) vers " + file.getName());

        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Erreur d'export", e.getMessage());
        }
    }

    // ── Handle "Vider la BDD" button ──────────────────────────
    @FXML
    private void handleViderBdd() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmation");
        confirm.setHeaderText("Vider la base de données ?");
        confirm.setContentText("Toutes les données seront supprimées définitivement.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                leadDao.deleteAll();
                leadsList.clear();
                updateLeadCount();
            }
        });
    }

    // ── Helper Methods ────────────────────────────────────────

    private void refreshTableFromDb() {
        List<Lead> dbLeads = leadDao.getAllLeads();
        leadsList.setAll(dbLeads);
        updateLeadCount();
    }

    private void updateLeadCount() {
        leadCountLabel.setText(leadsList.size() + " lead(s) en base");
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
