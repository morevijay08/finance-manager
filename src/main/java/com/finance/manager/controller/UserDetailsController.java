package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.model.AdminUser;
import com.finance.manager.repository.FirestoreAdminAuditLogRepository;
import com.finance.manager.service.FirebaseAuthService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;

public class UserDetailsController {
    public Label adminEmailLabel;
    public Label userNameLabel;
    public Label userEmailLabel;
    public Label userRoleLabel;
    public Label userStatusLabel;
    public Label userIdLabel;
    public Label accessLabel;
    public Label statusLabel;

    private final FirebaseAuthService authService = new FirebaseAuthService();
    private final FirestoreAdminAuditLogRepository auditLogRepository = new FirestoreAdminAuditLogRepository();
    private AdminUser user;

    public void setUser(AdminUser user) {
        this.user = user;
        if (user == null) {
            statusLabel.setText("User information is unavailable.");
            return;
        }
        userNameLabel.setText(user.displayName());
        userEmailLabel.setText(user.email());
        userRoleLabel.setText(user.displayRole());
        userStatusLabel.setText(user.displayStatus());
        userIdLabel.setText(user.id());
        accessLabel.setText("ACTIVE".equalsIgnoreCase(user.displayStatus()) ? "Active account" : "Access disabled");
    }

    public void initialize() {
        AuthSession session = authService.getCurrentSession();
        if (session != null && session.getEmail() != null) {
            adminEmailLabel.setText(session.getEmail());
        }
    }

    public void handleSendPasswordReset() {
        if (user == null || user.email().isBlank()) {
            statusLabel.setText("A valid user email is required.");
            return;
        }

        AuthSession adminSession = authService.getCurrentSession();
        if (adminSession == null) {
            statusLabel.setText("No active administrator session.");
            return;
        }
        if (user.id().equals(adminSession.getLocalId())) {
            statusLabel.setText("Use the normal password reset flow for your own account.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Send Password Reset");
        confirmation.setHeaderText("Send password reset email?");
        confirmation.setContentText("A password reset email will be sent to " + user.email() + ".");

        confirmation.showAndWait().ifPresent(result -> {
            if (result != javafx.scene.control.ButtonType.OK) return;

            statusLabel.setText("Sending password reset email...");
            authService.sendPasswordResetEmail(user.email())
                    .thenCompose(ignored -> auditLogRepository.createLog(
                            adminSession, "SEND_PASSWORD_RESET", user.email()))
                    .thenRun(() -> Platform.runLater(() -> {
                        statusLabel.setText("Password reset email sent to " + user.email() + ".");
                        showInfo("Password Reset Sent", "The password reset email was sent successfully.");
                    }))
                    .exceptionally(error -> {
                        Platform.runLater(() -> statusLabel.setText(
                                "Unable to send password reset email: " + rootMessage(error)));
                        return null;
                    });
        });
    }

    public void handleDashboard(ActionEvent event) throws IOException {
        switchScene(event, "/fxml/AdminDashboard.fxml", "Khatabook Admin");
    }

    public void handleUsers(ActionEvent event) throws IOException {
        switchScene(event, "/fxml/AdminDashboard.fxml", "Khatabook Admin");
    }

    public void handleAuditLogs(ActionEvent event) throws IOException {
        switchScene(event, "/fxml/AuditLogs.fxml", "Khatabook Admin - Audit Logs");
    }

    public void handleLogout(ActionEvent event) throws IOException {
        authService.logout();
        switchScene(event, "/fxml/Login.fxml", "Khatabook Finance Manager");
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "Unknown error" : current.getMessage();
    }

    private void switchScene(ActionEvent event, String resource, String title) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(resource));
        Parent root = loader.load();
        Scene scene = new Scene(root, 1200, 800);
        java.net.URL stylesheet = getClass().getResource("/css/application.css");
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
        java.net.URL adminStylesheet = getClass().getResource("/css/admin.css");
        if (adminStylesheet != null) scene.getStylesheets().add(adminStylesheet.toExternalForm());
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(scene);
        stage.setTitle(title);
        stage.show();
    }
}
