package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.repository.FirestoreUserRepository;
import com.finance.manager.service.FirebaseAuthService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.concurrent.CompletionException;

public class MainController {

    public Label userLabel;
    public Label profileLabel;

    private final FirebaseAuthService authService = new FirebaseAuthService();
    private final FirestoreUserRepository userRepository =
            new FirestoreUserRepository();

    public void initialize() {
        AuthSession session = authService.getCurrentSession();

        if (session == null) {
            userLabel.setText("Not signed in");
            if (profileLabel != null) {
                profileLabel.setText("Please log in again.");
            }
            return;
        }

        userLabel.setText("Signed in as " + session.getEmail());

        if (profileLabel != null) {
            profileLabel.setText("Loading profile...");
        }

        userRepository.getUserName(session)
                .thenAccept(name -> Platform.runLater(() -> {
                    if (profileLabel != null) {
                        profileLabel.setText("Welcome, " + name + "!");
                    }
                }))
                .exceptionally(throwable -> {
                    throwable.printStackTrace();
                    Platform.runLater(() -> {
                        if (profileLabel != null) {
                            profileLabel.setText("Welcome!");
                        }
                    });
                    return null;
                });
    }

    public void handleLogout(ActionEvent event) throws IOException {
        authService.logout();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 900, 600);
        scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(scene);
        stage.show();
    }
}
