package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.repository.FirestoreUserRepository;
import com.finance.manager.service.FirebaseAuthService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;

public class AdminDashboardController {
    public Label adminEmailLabel, totalUsersLabel, activeUsersLabel, disabledUsersLabel, statusLabel;
    public Button overviewNavButton, usersNavButton, auditNavButton;

    private final FirebaseAuthService authService = new FirebaseAuthService();
    private final FirestoreUserRepository userRepository = new FirestoreUserRepository();

    public void initialize() {
        AuthSession session = authService.getCurrentSession();
        if (session != null && session.getEmail() != null) adminEmailLabel.setText(session.getEmail());
        loadCounts();
    }

    public void handleShowUsers(ActionEvent event) throws IOException {
        switchScene(event, "/fxml/UserManagement.fxml", "Khatabook Admin - User Management");
    }

    public void handleShowAuditLogs(ActionEvent event) throws IOException {
        switchScene(event, "/fxml/AuditLogs.fxml", "Khatabook Admin - Audit Logs");
    }

    public void handleLogout(ActionEvent event) throws IOException {
        authService.logout();
        switchScene(event, "/fxml/Login.fxml", "Khatabook Finance Manager");
    }

    private void loadCounts() {
        AuthSession session = authService.getCurrentSession();
        if (session == null) return;
        statusLabel.setText("Loading system overview...");
        userRepository.listUsers(session).thenAccept(users -> Platform.runLater(() -> {
            long active = users.stream().filter(u -> "ACTIVE".equalsIgnoreCase(u.displayStatus())).count();
            long disabled = users.stream().filter(u -> "DISABLED".equalsIgnoreCase(u.displayStatus())).count();
            totalUsersLabel.setText(String.valueOf(users.size()));
            activeUsersLabel.setText(String.valueOf(active));
            disabledUsersLabel.setText(String.valueOf(disabled));
            statusLabel.setText("System overview updated");
        })).exceptionally(error -> {
            Platform.runLater(() -> statusLabel.setText("Unable to load overview: " + rootMessage(error)));
            return null;
        });
    }

    private void switchScene(ActionEvent event, String resource, String title) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(resource));
        Parent root = loader.load();
        Scene scene = new Scene(root, 1200, 800);
        addStylesheets(scene);
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(scene);
        stage.setTitle(title);
        stage.show();
    }

    private void addStylesheets(Scene scene) {
        java.net.URL stylesheet = getClass().getResource("/css/application.css");
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
        java.net.URL adminStylesheet = getClass().getResource("/css/admin.css");
        if (adminStylesheet != null) scene.getStylesheets().add(adminStylesheet.toExternalForm());
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "Unknown error" : current.getMessage();
    }
}
