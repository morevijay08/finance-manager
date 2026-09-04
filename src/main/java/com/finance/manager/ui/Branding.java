package com.finance.manager.ui;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Central application branding. Keeps user-facing product text consistent. */
public final class Branding {
    public static final String PRODUCT_NAME = "Khatabook";
    public static final String APP_TITLE = PRODUCT_NAME + " Finance Manager";
    private static boolean globalListenerInstalled;

    private Branding() {
    }

    public static void apply(Node root) {
        installGlobalWindowBranding();
        if (root == null) return;
        applyText(root);

        if (root.getScene() != null && root.getScene().getWindow() instanceof Stage stage) {
            stage.setTitle(replace(stage.getTitle()));
        }
    }

    private static void installGlobalWindowBranding() {
        if (globalListenerInstalled) return;
        globalListenerInstalled = true;

        for (Window window : Window.getWindows()) {
            brandWindow(window);
        }

        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (Window window : change.getAddedSubList()) {
                        Platform.runLater(() -> brandWindow(window));
                    }
                }
            }
        });
    }

    private static void brandWindow(Window window) {
        if (window == null) return;
        if (window instanceof Stage stage) {
            stage.setTitle(replace(stage.getTitle()));
        }
        if (window.getScene() != null) {
            applyText(window.getScene().getRoot());
        }
    }

    private static void applyText(Node node) {
        if (node instanceof Label label) {
            label.setText(replace(label.getText()));
        } else if (node instanceof Button button) {
            button.setText(replace(button.getText()));
        } else if (node instanceof TextInputControl input) {
            input.setPromptText(replace(input.getPromptText()));
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                applyText(child);
            }
        }
    }

    public static String replace(String value) {
        if (value == null || value.isEmpty()) return value;
        return value.replace("Hisaabi", PRODUCT_NAME)
                .replace("hisaabi", PRODUCT_NAME.toLowerCase())
                .replace("Hissabi", PRODUCT_NAME)
                .replace("hissabi", PRODUCT_NAME.toLowerCase());
    }
}
