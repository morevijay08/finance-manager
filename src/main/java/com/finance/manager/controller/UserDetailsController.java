package com.finance.manager.controller;

import com.finance.manager.model.AdminUser;
import com.finance.manager.service.FirebaseAuthService;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
        if (authService.getCurrentSession() != null && authService.getCurrentSession().getEmail() != null) {
            adminEmailLabel.setText(authService.getCurrentSession().getEmail());
        }
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
