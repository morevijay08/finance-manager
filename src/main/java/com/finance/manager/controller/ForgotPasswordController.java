package com.finance.manager.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class ForgotPasswordController {

    public TextField emailField;
    public Label messageLabel;

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

        // Firebase Authentication will be connected in Phase 4.
        messageLabel.setText("Password reset is not connected yet.");
    }

    public void handleLogin(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
        Parent root = loader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root, 900, 600));
        stage.show();
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }
}
