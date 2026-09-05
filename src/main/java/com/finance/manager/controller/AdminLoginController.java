package com.finance.manager.controller;

import com.finance.manager.firebase.FirebaseAuthException;
import com.finance.manager.repository.FirestoreUserRepository;
import com.finance.manager.service.FirebaseAuthService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.concurrent.CompletionException;

public class AdminLoginController {
    public TextField emailField;
    public PasswordField passwordField;
    public Label errorLabel;

    private final FirebaseAuthService authService = new FirebaseAuthService();
    private final FirestoreUserRepository userRepository = new FirestoreUserRepository();

    public void handleAdminLogin(ActionEvent event) {
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || !isValidEmail(email)) {
            showError("Please enter a valid email.");
            return;
        }
        if (password.isEmpty()) {
            showError("Please enter your password.");
            return;
        }

        errorLabel.setText("Checking admin access...");

        authService.signIn(email, password)
                .thenCompose(session -> userRepository.getUserRole(session)
                        .thenApply(role -> new LoginResult(session, role)))
                .thenAccept(result -> Platform.runLater(() -> {
                    if (!isAdminRole(result.role())) {
                        authService.logout();
                        showError("Access denied. This account is not an administrator.");
                        return;
                    }

                    try {
                        switchScene(event, "/fxml/AdminDashboard.fxml");
                    } catch (IOException e) {
                        authService.logout();
                        showError("Could not open the admin dashboard.");
                    }
                }))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> showError(authenticationMessage(throwable)));
                    return null;
                });
    }

    public void handleBackToLogin(ActionEvent event) throws IOException {
        switchScene(event, "/fxml/Login.fxml");
    }

    private boolean isAdminRole(String role) {
        return "ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role);
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    private void showError(String message) {
        errorLabel.setText(message);
    }

    private String authenticationMessage(Throwable throwable) {
        Throwable cause = throwable;
        if (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof RuntimeException && cause.getCause() instanceof FirebaseAuthException authException) {
            return authException.getMessage();
        }
        if (cause instanceof RuntimeException && cause.getMessage() != null) {
            return cause.getMessage();
        }
        return "Unable to sign in as administrator. Please try again.";
    }

    private void switchScene(ActionEvent event, String resource) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(resource));
        Parent root = loader.load();
        Scene scene = new Scene(root, 1200, 800);
        java.net.URL stylesheet = getClass().getResource("/css/application.css");
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
        java.net.URL adminStylesheet = getClass().getResource("/css/admin.css");
        if (adminStylesheet != null) scene.getStylesheets().add(adminStylesheet.toExternalForm());
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(scene);
        stage.setTitle("Khatabook Admin");
        stage.show();
    }

    private record LoginResult(com.finance.manager.firebase.AuthSession session, String role) {}
}
