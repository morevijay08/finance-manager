package com.finance.manager.ui;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.stage.Window;

/**
 * Reusable navigation button for secondary windows that should return
 * to the already-open main dashboard window.
 */
public class BackToDashboardButton extends Button {
    public BackToDashboardButton() {
        super("←  Back to Dashboard");
        setFocusTraversable(false);
        setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #0f172a; -fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 10px; -fx-padding: 10px 14px;");
        setOnMouseEntered(e -> setStyle("-fx-background-color: #cbd5e1; -fx-text-fill: #0f172a; -fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 10px; -fx-padding: 10px 14px;"));
        setOnMouseExited(e -> setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #0f172a; -fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 10px; -fx-padding: 10px 14px;"));

        sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                Platform.runLater(() -> Branding.apply(scene.getRoot()));
            }
        });

        setOnAction(e -> {
            Window window = getScene() == null ? null : getScene().getWindow();
            if (window != null) {
                window.hide();
            }
        });
    }
}
