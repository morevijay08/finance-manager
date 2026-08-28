package com.finance.manager.controller;

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

public class RegisterController {

    public TextField nameField;
    public TextField emailField;
    public PasswordField passwordField;
    public PasswordField confirmPasswordField;
    public Label errorLabel;

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

        // Firebase Authentication will be connected in Phase 4.
        showError("Authentication is not connected yet.");
    }

    public void handleLogin(ActionEvent event) throws IOException {
        switchScene(event, "/fxml/Login.fxml");
    }

    private void showError(String message) {
        errorLabel.setText(message);
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    private void switchScene(ActionEvent event, String resource) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(resource));
        Parent root = loader.load();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root, 900, 600));
        stage.show();
    }
}
