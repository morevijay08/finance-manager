package com.finance.manager.controller;

import com.finance.manager.firebase.FirebaseAuthException;
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

public class RegisterController {

    public TextField nameField;
    public TextField emailField;
    public PasswordField passwordField;
    public PasswordField confirmPasswordField;
    public Label errorLabel;

    private final FirebaseAuthService authService = new FirebaseAuthService();

    public void handleRegister(ActionEvent event) {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (name.isEmpty()) {
            showError("Please enter your name.");
            return;
        }

        if (email.isEmpty() || !isValidEmail(email)) {
            showError("Please enter a valid email.");
            return;
        }

        if (password.isEmpty()) {
            showError("Please enter a password.");
            return;
        }

        if (password.length() < 6) {
            showError("Password must contain at least 6 characters.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match.");
            return;
        }

        errorLabel.setText("Creating account...");
        authService.register(name, email, password)
                .thenAccept(session -> Platform.runLater(() -> {
                    try {
                        switchScene(event, "/fxml/Main.fxml");
                    } catch (IOException e) {
                        showError("Account created, but the application could not be opened.");
                    }
                }))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> showError(authenticationMessage(throwable)));
                    return null;
                });
    }

    public void handleLogin(ActionEvent event) throws IOException {
        switchScene(event, "/fxml/Login.fxml");
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
        if (cause instanceof IllegalStateException) {
            return cause.getMessage();
        }
       return "Unable to create the account. Please try again.";
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    private void switchScene(ActionEvent event, String resource) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(resource));
        Parent root = loader.load();
        Scene scene = new Scene(root, 900, 600);
        scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(scene);
        stage.show();
    }
}
