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
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.concurrent.CompletionException;

public class ForgotPasswordController {

    public TextField emailField;
    public Label messageLabel;

    private final FirebaseAuthService authService = new FirebaseAuthService();

    public void handleResetPassword(ActionEvent event) {
        String email = emailField.getText().trim();

        if (email.isEmpty()) {
            messageLabel.setText("Please enter your email.");
            return;
        }

        if (!isValidEmail(email)) {
            messageLabel.setText("Please enter a valid email.");
            return;
        }

        messageLabel.setText("Sending reset email...");
        authService.sendPasswordResetEmail(email)
                .thenRun(() -> Platform.runLater(() ->
                        messageLabel.setText("Password reset email sent. Check your inbox.")))
                .exceptionally(throwable -> {
                    Platform.runLater(() -> messageLabel.setText(authenticationMessage(throwable)));
                    return null;
                });
    }

    public void handleLogin(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
        Parent root = loader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root, 900, 600));
        stage.show();
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
        return "Unable to send the reset email. Please try again.";
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }
}
