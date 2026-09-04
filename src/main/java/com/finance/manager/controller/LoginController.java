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
import javafx.stage.Screen;
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
                    } catch (Exception e) {
                        e.printStackTrace();
                        showError("Could not open the dashboard: " + rootCauseMessage(e));
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

    private String rootCauseMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        return message.length() > 180 ? message.substring(0, 180) + "..." : message;
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    private void switchScene(ActionEvent event, String resource) throws IOException {
        java.net.URL resourceUrl = getClass().getResource(resource);
        if (resourceUrl == null) {
            throw new IOException("Missing FXML resource: " + resource);
        }

        FXMLLoader loader = new FXMLLoader(resourceUrl);
        Parent root = loader.load();

        java.net.URL stylesheetUrl = getClass().getResource("/css/application.css");
        Scene scene = new Scene(root, 900, 600);
        if (stylesheetUrl != null) {
            scene.getStylesheets().add(stylesheetUrl.toExternalForm());
        }

        if (event == null || event.getSource() == null) {
            throw new IOException("Login window is unavailable.");
        }

        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(scene);
        stage.show();
        forceMaximized(stage);
        Platform.runLater(() -> forceMaximized(stage));
        Platform.runLater(() -> Platform.runLater(() -> forceMaximized(stage)));
    }

    /**
     * Force the application window to occupy the full available desktop area.
     * This deliberately applies the screen bounds even when JavaFX reports the
     * stage as maximized, because Windows can retain a previous snapped/restored
     * size when only the Scene is replaced.
     */
    private void forceMaximized(Stage stage) {
        if (stage == null) return;

        stage.setIconified(false);
        stage.setMaximized(false);

        javafx.geometry.Rectangle2D bounds = Screen.getScreensForRectangle(
                stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight())
                .stream()
                .findFirst()
                .orElse(Screen.getPrimary())
                .getVisualBounds();

        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        stage.show();
        stage.setMaximized(true);
        stage.toFront();
    }
}
