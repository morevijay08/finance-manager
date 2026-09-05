package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.model.AdminUser;
import com.finance.manager.repository.FirestoreAdminAuditLogRepository;
import com.finance.manager.repository.FirestoreUserRepository;
import com.finance.manager.service.FirebaseAuthService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class UserManagementController {
    public Label adminEmailLabel, totalUsersLabel, activeUsersLabel, disabledUsersLabel, statusLabel;
    public TextField userSearchField;
    public TableView<AdminUser> usersTable;
    public TableColumn<AdminUser, String> nameColumn, emailColumn, roleColumn, statusColumn;
    public TableColumn<AdminUser, Void> actionColumn;

    private final FirebaseAuthService authService = new FirebaseAuthService();
    private final FirestoreUserRepository userRepository = new FirestoreUserRepository();
    private final FirestoreAdminAuditLogRepository auditLogRepository = new FirestoreAdminAuditLogRepository();
    private List<AdminUser> allUsers = List.of();

    public void initialize() {
        AuthSession session = authService.getCurrentSession();
        if (session != null && session.getEmail() != null) adminEmailLabel.setText(session.getEmail());

        nameColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().displayName()));
        emailColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().email()));
        roleColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().displayRole()));
        statusColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().displayStatus()));
        actionColumn.setCellFactory(column -> new TableCell<>() {
            private final Button viewButton = new Button("View");
            private final Button toggleButton = new Button();
            private final HBox actions = new HBox(8, viewButton, toggleButton);
            {
                viewButton.setOnAction(event -> openUserDetails(getTableView().getItems().get(getIndex()), event));
                toggleButton.setOnAction(event -> toggleUserStatus(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0) {
                    setGraphic(null);
                    return;
                }
                AdminUser user = getTableView().getItems().get(getIndex());
                toggleButton.setText("ACTIVE".equalsIgnoreCase(user.displayStatus()) ? "Disable" : "Enable");
                viewButton.getStyleClass().setAll("secondary-button");
                toggleButton.getStyleClass().setAll("secondary-button");
                setGraphic(actions);
            }
        });

        userSearchField.textProperty().addListener((obs, oldValue, newValue) -> filterUsers(newValue));
        loadUsers();
    }

    public void handleShowOverview(ActionEvent event) throws IOException {
        switchScene(event, "/fxml/AdminDashboard.fxml", "Khatabook Admin");
    }

    public void handleShowAuditLogs(ActionEvent event) throws IOException {
        switchScene(event, "/fxml/AuditLogs.fxml", "Khatabook Admin - Audit Logs");
    }

    public void handleRefresh() {
        loadUsers();
    }

    public void handleLogout(ActionEvent event) throws IOException {
        authService.logout();
        switchScene(event, "/fxml/Login.fxml", "Khatabook Finance Manager");
    }

    private void loadUsers() {
        AuthSession session = authService.getCurrentSession();
        if (session == null) return;
        statusLabel.setText("Loading users...");
        userRepository.listUsers(session).thenAccept(users -> Platform.runLater(() -> {
            allUsers = List.copyOf(users);
            filterUsers(userSearchField.getText());
            updateCounts(users);
            statusLabel.setText(users.size() + " users loaded");
        })).exceptionally(error -> {
            Platform.runLater(() -> statusLabel.setText("Unable to load users: " + rootMessage(error)));
            return null;
        });
    }

    private void filterUsers(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<AdminUser> filtered = allUsers.stream().filter(user -> q.isBlank()
                || user.displayName().toLowerCase().contains(q)
                || user.email().toLowerCase().contains(q)
                || user.displayRole().toLowerCase().contains(q)
                || user.displayStatus().toLowerCase().contains(q)).toList();
        usersTable.setItems(FXCollections.observableArrayList(filtered));
    }

    private void updateCounts(List<AdminUser> users) {
        long active = users.stream().filter(u -> "ACTIVE".equalsIgnoreCase(u.displayStatus())).count();
        long disabled = users.stream().filter(u -> "DISABLED".equalsIgnoreCase(u.displayStatus())).count();
        totalUsersLabel.setText(String.valueOf(users.size()));
        activeUsersLabel.setText(String.valueOf(active));
        disabledUsersLabel.setText(String.valueOf(disabled));
    }

    private void toggleUserStatus(AdminUser user) {
        AuthSession session = authService.getCurrentSession();
        if (session == null) return;
        if (user.id().equals(session.getLocalId())) {
            statusLabel.setText("You cannot disable your own admin account.");
            return;
        }
        String newStatus = "ACTIVE".equalsIgnoreCase(user.displayStatus()) ? "DISABLED" : "ACTIVE";
        String action = "DISABLED".equals(newStatus) ? "DISABLE_USER" : "ENABLE_USER";
        statusLabel.setText(("DISABLED".equals(newStatus) ? "Disabling " : "Enabling ") + user.displayName() + "...");
        userRepository.updateUserStatus(session, user.id(), newStatus)
                .thenCompose(ignored -> auditLogRepository.createLog(session, action, user.email()))
                .thenRun(() -> Platform.runLater(this::loadUsers))
                .exceptionally(error -> {
                    Platform.runLater(() -> statusLabel.setText("Unable to update user: " + rootMessage(error)));
                    return null;
                });
    }

    private void openUserDetails(AdminUser user, ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/UserDetails.fxml"));
            Parent root = loader.load();
            UserDetailsController controller = loader.getController();
            controller.setUser(user);
            Scene scene = new Scene(root, 1200, 800);
            addStylesheets(scene);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Khatabook Admin - User Details");
            stage.show();
        } catch (IOException e) {
            statusLabel.setText("Unable to open user details.");
        }
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
