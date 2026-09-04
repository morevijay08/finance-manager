package com.finance.manager.ui;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.stage.Window;

/** Central application branding. Keeps user-facing product text consistent. */
public final class Branding {
    public static final String PRODUCT_NAME = "Khatabook";
    public static final String APP_TITLE = PRODUCT_NAME + " Finance Manager";

    private Branding() {
    }

    public static void apply(Node root) {
        if (root == null) return;
        applyText(root);

        Window window = root.getScene() == null ? null : root.getScene().getWindow();
        if (window != null) {
            window.setOnShown(event -> window.setOnShown(null));
            String title = window.getUserData() instanceof String
                    ? (String) window.getUserData()
                    : window instanceof javafx.stage.Stage stage ? stage.getTitle() : null;
            if (title != null) {
                window.setUserData(replace(title));
                if (window instanceof javafx.stage.Stage stage) stage.setTitle(replace(title));
            }
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
