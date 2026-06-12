package ma.ensa.medtrend.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

import java.io.IOException;

/**
 * Controller for MainLayout.fxml.
 * Manages sidebar navigation between the Lead Generator and Network Auditor modules.
 * Dynamically loads sub-views into the content StackPane.
 */
public class MainController {

    private static MainController instance;
    private NetworkAuditorController networkAuditorController;

    @FXML private StackPane contentArea;
    @FXML private Button btnLeadGenerator;
    @FXML private Button btnNetworkAuditor;

    /** Cached views to avoid reloading FXML on every switch. */
    private Node leadGeneratorView;
    private Node networkAuditorView;

    @FXML
    public void initialize() {
        instance = this;
        // Load Lead Generator by default on startup
        handleShowLeadGenerator();
    }

    public static MainController getInstance() {
        return instance;
    }

    // ── Sidebar Navigation Handlers ──────────────────────────────

    @FXML
    private void handleShowLeadGenerator() {
        if (leadGeneratorView == null) {
            leadGeneratorView = loadView("/views/LeadGenerator.fxml");
        }
        setActiveView(leadGeneratorView);
        setActiveButton(btnLeadGenerator);
    }

    @FXML
    private void handleShowNetworkAuditor() {
        if (networkAuditorView == null) {
            networkAuditorView = loadView("/views/NetworkAuditor.fxml");
        }
        setActiveView(networkAuditorView);
        setActiveButton(btnNetworkAuditor);
    }

    // ── Internal Helpers ─────────────────────────────────────────

    /**
     * Replaces the content area with the given view node.
     */
    private void setActiveView(Node view) {
        contentArea.getChildren().clear();
        if (view != null) {
            contentArea.getChildren().add(view);
        }
    }

    /**
     * Updates the active state styling on sidebar buttons.
     */
    private void setActiveButton(Button activeBtn) {
        // Remove active class from all nav buttons
        btnLeadGenerator.getStyleClass().remove("nav-btn-active");
        btnNetworkAuditor.getStyleClass().remove("nav-btn-active");

        // Add active class to the selected button
        if (!activeBtn.getStyleClass().contains("nav-btn-active")) {
            activeBtn.getStyleClass().add("nav-btn-active");
        }
    }

    /**
     * Loads an FXML view from the resources folder.
     *
     * @param fxmlPath Resource path to the FXML file.
     * @return The loaded Node, or null on failure.
     */
    private Node loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node view = loader.load();
            if (fxmlPath.contains("NetworkAuditor.fxml")) {
                networkAuditorController = loader.getController();
            }
            return view;
        } catch (IOException e) {
            System.err.println("[MainController] Failed to load view: " + fxmlPath);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Called by LeadGeneratorController to switch views and trigger an audit.
     */
    public void switchToAuditorWithUrls(java.util.List<String> urls) {
        // 1. Switch the view in the UI smoothly
        handleShowNetworkAuditor();
        
        // 2. Pass the URLs and start the background task
        if (networkAuditorController != null) {
            networkAuditorController.startBatchAudit(urls);
        }
    }
}
