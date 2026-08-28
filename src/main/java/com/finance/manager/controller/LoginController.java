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

public class LoginController {

    public TextField emailField;
    public PasswordField passwordField;
    public Label errorLabel;

    private final FirebaseAuthService authService = new FirebaseAuthService();

    public void handleLogin(ActionEvent event) {
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty()) {
            showError("Please enter your email.");
            return;
        }

        if (!isValidEmail(email)) {
            showError("Please enter a valid email.");
            return;
        }

        if (password.isEmpty()) {
            showError("Please enter your password.");
            return;
        }

        errorLabel.setText("Signing in...");
        authService.signIn(email, password)
                .thenAccept(session -> Platform.runLater(() -> {
                    try {
                        switchScene(event, "/fxml/Main.fxml");
                    } catch (IOException e) {
                        showError("Unable to open the application.");
                    }
                }))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> showError(authenticationMessage(throwable)));
                    return null;
                });
    }

    public void handleRegister(ActionEvent event) throws IOException {
        switchScene(event, "/fxml/Register.fxml");
    }

    public void handleForgotPassword(ActionEvent event) throws IOException {
        switchScene(event, "/fxml/ForgotPassword.fxml");
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
        return "Unable to sign in. Check your Firebase configuration and try again.";
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
