package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.model.AdminAuditLog;
import com.finance.manager.repository.FirestoreAdminAuditLogRepository;
import com.finance.manager.service.FirebaseAuthService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class AuditLogsController {
    public Label adminEmailLabel;
    public Label statusLabel;
    public TextField searchField;
    public TableView<AdminAuditLog> auditLogsTable;
    public TableColumn<AdminAuditLog, String> auditAdminColumn;
    public TableColumn<AdminAuditLog, String> auditActionColumn;
    public TableColumn<AdminAuditLog, String> auditTargetColumn;
    public TableColumn<AdminAuditLog, String> auditTimeColumn;

    private final FirebaseAuthService authService = new FirebaseAuthService();
    private final FirestoreAdminAuditLogRepository auditLogRepository = new FirestoreAdminAuditLogRepository();
    private List<AdminAuditLog> allLogs = List.of();

    public void initialize() {
        AuthSession session = authService.getCurrentSession();
        if (session != null && session.getEmail() != null) {
            adminEmailLabel.setText(session.getEmail());
        }

        auditAdminColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().adminEmail()));
        auditActionColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().action()));
        auditTargetColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().targetUserEmail()));
        auditTimeColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().timestamp()));

        searchField.textProperty().addListener((obs, oldValue, newValue) -> filterLogs(newValue));
        loadLogs();
    }

    private void loadLogs() {
        AuthSession session = authService.getCurrentSession();
        if (session == null) {
            statusLabel.setText("No authenticated admin session.");
            return;
        }

        statusLabel.setText("Loading audit logs...");
        auditLogRepository.listLogs(session)
                .thenAccept(logs -> Platform.runLater(() -> {
                    allLogs = logs;
                    filterLogs(searchField.getText());
                    statusLabel.setText(logs.size() + " audit log entries");
                }))
                .exceptionally(error -> {
                    Platform.runLater(() ->
                            statusLabel.setText("Unable to load audit logs: " + rootMessage(error)));
                    return null;
                });
    }

    private void filterLogs(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<AdminAuditLog> filtered = allLogs.stream()
                .filter(log -> q.isBlank()
                        || log.adminEmail().toLowerCase().contains(q)
                        || log.action().toLowerCase().contains(q)
                        || log.targetUserEmail().toLowerCase().contains(q)
                        || log.timestamp().toLowerCase().contains(q))
                .toList();
        auditLogsTable.setItems(FXCollections.observableArrayList(filtered));
    }

    public void handleRefresh() {
        loadLogs();
    }

    public void handleShowDashboard(ActionEvent event) throws IOException {
        switchScene(event, "/fxml/AdminDashboard.fxml", "Khatabook Admin");
    }

    public void handleShowUsers(ActionEvent event) throws IOException {
        switchScene(event, "/fxml/AdminDashboard.fxml", "Khatabook Admin");
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

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "Unknown error" : current.getMessage();
    }
}
