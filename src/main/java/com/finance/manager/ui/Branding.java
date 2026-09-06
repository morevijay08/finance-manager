package com.finance.manager.ui;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
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
        styleSidebar(root);

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
            styleSidebar(window.getScene().getRoot());
        }
    }

    private static void styleSidebar(Node root) {
        if (root == null) return;
        Node sidebarNode = root.lookup("#sidebar");
        if (!(sidebarNode instanceof Pane sidebar)) return;

        sidebar.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #081226 0%, #0d1b3a 52%, #14295b 100%);" +
                "-fx-padding: 18px 14px 18px 14px;" +
                "-fx-spacing: 6px;" +
                "-fx-border-color: transparent #263b63 transparent transparent;" +
                "-fx-border-width: 0 1px 0 0;" +
                "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.28), 22, 0.18, 3, 0);"
        );
        sidebar.setPrefWidth(268);
        sidebar.setMinWidth(268);
        sidebar.setMaxWidth(268);

        if (!sidebar.getChildren().isEmpty() && sidebar.getChildren().get(0) instanceof HBox header) {
            header.setSpacing(11);
            header.setStyle(
                    "-fx-padding: 4px 6px 16px 6px;" +
                    "-fx-border-color: transparent transparent #263b63 transparent;" +
                    "-fx-border-width: 0 0 1px 0;"
            );

            // Keep exactly one sidebar logo. Branding can be applied more than once
            // during scene/window initialization, so remove any duplicate ImageViews.
            ImageView logo = null;
            for (Node child : header.getChildren().toArray(new Node[0])) {
                if (child instanceof ImageView imageView) {
                    if (logo == null) {
                        logo = imageView;
                    } else {
                        header.getChildren().remove(imageView);
                    }
                }
            }

            if (logo == null) {
                java.net.URL logoUrl = Branding.class.getResource("/images/khatabook-logo-small.png");
                if (logoUrl != null) {
                    Image image = new Image(logoUrl.toExternalForm(), 46, 46, true, true);
                    logo = new ImageView(image);
                    logo.setFitWidth(46);
                    logo.setFitHeight(46);
                    logo.setPreserveRatio(true);
                    logo.setSmooth(true);
                    header.getChildren().add(0, logo);
                }
            }

            for (Node child : header.getChildren()) {
                if (child instanceof Label label) {
                    if ("MENU".equals(label.getText())) {
                        label.setVisible(false);
                        label.setManaged(false);
                    } else if (label.getText() != null && label.getText().equals(PRODUCT_NAME)) {
                        label.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 18px; -fx-font-weight: 800;");
                    } else {
                        label.setStyle("-fx-text-fill: #60a5fa; -fx-font-size: 9px; -fx-font-weight: bold; -fx-letter-spacing: 0.7px;");
                    }
                }
            }
        }

        for (Node node : sidebar.getChildren()) {
            if (node instanceof Label label) {
                String text = label.getText();
                if ("NAVIGATION".equals(text) || "QUICK ACTION".equals(text)) {
                    label.setStyle(
                            "-fx-text-fill: #7184a8; -fx-font-size: 9px; -fx-font-weight: bold;" +
                            "-fx-letter-spacing: 1.1px; -fx-padding: 13px 10px 5px 10px;"
                    );
                }
            }

            if (node instanceof Button button) {
                styleSidebarButton(button);
            }
        }
    }

    private static void styleSidebarButton(Button button) {
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(42);
        button.setPrefHeight(42);
        button.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        button.setWrapText(false);
        button.setStyle(defaultSidebarButtonStyle());

        button.setOnMouseEntered(event -> {
            if (!button.getStyleClass().contains("nav-button-active")) {
                button.setStyle(hoverSidebarButtonStyle());
            }
        });
        button.setOnMouseExited(event -> {
            if (!button.getStyleClass().contains("nav-button-active")) {
                button.setStyle(defaultSidebarButtonStyle());
            }
        });
    }

    private static String defaultSidebarButtonStyle() {
        return "-fx-background-color: transparent;" +
                "-fx-text-fill: #dbe7ff;" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 11px;" +
                "-fx-border-color: transparent;" +
                "-fx-border-radius: 11px;" +
                "-fx-padding: 0 12px;" +
                "-fx-cursor: hand;";
    }

    private static String hoverSidebarButtonStyle() {
        return "-fx-background-color: rgba(96,165,250,0.12);" +
                "-fx-text-fill: #ffffff;" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 11px;" +
                "-fx-border-color: rgba(147,197,253,0.16);" +
                "-fx-border-radius: 11px;" +
                "-fx-padding: 0 12px;" +
                "-fx-cursor: hand;";
    }

    private static void applyText(Node node) {
        if (node instanceof Label label) {
            if (!label.textProperty().isBound()) {
                label.setText(replace(label.getText()));
            }
        } else if (node instanceof Button button) {
            if (!button.textProperty().isBound()) {
                button.setText(replace(button.getText()));
            }
        } else if (node instanceof TextInputControl input) {
            if (!input.promptTextProperty().isBound()) {
                input.setPromptText(replace(input.getPromptText()));
            }
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
