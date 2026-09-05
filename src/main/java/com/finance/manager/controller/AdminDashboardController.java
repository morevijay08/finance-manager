package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.model.AdminUser;
import com.finance.manager.repository.FirestoreUserRepository;
import com.finance.manager.service.FirebaseAuthService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public class AdminDashboardController {
    public Label adminEmailLabel;
    public Label totalUsersLabel;
    public Label activeUsersLabel;
    public Label disabledUsersLabel;
    public Label statusLabel;
    public TextField userSearchField;
    public TableView<AdminUser> usersTable;
    public TableColumn<AdminUser, String> nameColumn;
    public TableColumn<AdminUser, String> emailColumn;
    public TableColumn<AdminUser, String> roleColumn;
    public TableColumn<AdminUser, String> statusColumn;
    public TableColumn<AdminUser, Void> actionColumn;

    private final FirebaseAuthService authService = new FirebaseAuthService();
    private final FirestoreUserRepository userRepository = new FirestoreUserRepository();
    private final ObservableList<AdminUser> allUsers = FXCollections.observableArrayList();

    public void initialize() {
        AuthSession session = authService.getCurrentSession();
        if (session != null && session.getEmail() != null) adminEmailLabel.setText(session.getEmail());
        nameColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().displayName()));
        emailColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().email()));
        roleColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().displayRole()));
        statusColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().displayStatus()));
        actionColumn.setCellFactory(column -> new TableCell<>() {
            private final Button button = new Button();
            { button.setOnAction(event -> toggleUserStatus(getTableView().getItems().get(getIndex()))); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0) setGraphic(null);
                else {
                    AdminUser user = getTableView().getItems().get(getIndex());
                    button.setText("ACTIVE".equalsIgnoreCase(user.displayStatus()) ? "Disable" : "Enable");
                    button.getStyleClass().setAll("secondary-button");
                    setGraphic(button);
                }
            }
        });
        userSearchField.textProperty().addListener((obs, oldValue, newValue) -> filterUsers(newValue));
        loadUsers();
    }

    private void loadUsers() {
        AuthSession session = authService.getCurrentSession();
        if (session == null) return;
        Platform.runLater(() -> statusLabel.setText("Loading users..."));
        userRepository.listUsers(session).thenAccept(users -> Platform.runLater(() -> {
            allUsers.setAll(users);
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
        List<AdminUser> filtered = allUsers.stream()
                .filter(user -> q.isBlank() || user.displayName().toLowerCase().contains(q)
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
        statusLabel.setText(("DISABLED".equals(newStatus) ? "Disabling " : "Enabling ") + user.displayName() + "...");
        userRepository.updateUserStatus(session, user.id(), newStatus)
                .thenRun(() -> Platform.runLater(this::loadUsers))
                .exceptionally(error -> {
                    Platform.runLater(() -> statusLabel.setText("Unable to update user: " + rootMessage(error)));
                    return null;
                });
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "Unknown error" : current.getMessage();
    }

    public void handleLogout(ActionEvent event) throws IOException {
        authService.logout();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 900, 600);
        java.net.URL stylesheet = getClass().getResource("/css/application.css");
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(scene);
        stage.setTitle("Khatabook Finance Manager");
        stage.show();
    }
}
