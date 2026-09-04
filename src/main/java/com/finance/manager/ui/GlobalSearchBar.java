package com.finance.manager.ui;

import javafx.application.Platform;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Global navigation search used by the main dashboard.
 * It searches feature/page names and redirects to the matching section.
 * It also applies a small visual polish layer to the existing dashboard
 * without changing its navigation or business logic.
 */
public class GlobalSearchBar extends TextField {
    private final ContextMenu suggestions = new ContextMenu();
    private final List<SearchTarget> targets = List.of(
            new SearchTarget("Dashboard", "Your financial overview", "dashboard home balance income expense savings money overview"),
            new SearchTarget("Analytics", "Income, expense and spending charts", "analytics chart charts spending category monthly trends"),
            new SearchTarget("Alerts", "Notifications and financial alerts", "alerts alert notifications notification reminders warnings"),
            new SearchTarget("Reports", "Monthly financial reports", "reports report monthly summary statements"),
            new SearchTarget("Goals", "Savings goals and progress", "goals goal savings target progress"),
            new SearchTarget("Budget", "Monthly budget and usage", "budget budgets remaining spent planning"),
            new SearchTarget("Transactions", "Transaction history and filters", "transactions transaction history records income expense search"),
            new SearchTarget("Add Transaction", "Record a new income or expense", "add transaction income expense payment record"),
            new SearchTarget("People", "Money given to or received from people", "people person friend friends gave received lent borrowed settlement settle"),
            new SearchTarget("Settings", "Account and application settings", "settings setting preferences account profile")
    );

    private static final String SEARCH_STYLE = "-fx-background-color: rgba(255,255,255,0.14);-fx-background-radius: 13px;-fx-border-color: rgba(191,219,254,0.52);-fx-border-radius: 13px;-fx-border-width: 1px;-fx-text-fill: white;-fx-prompt-text-fill: #cbd5e1;-fx-font-size: 12px;-fx-padding: 0 16px;-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.20), 14, 0.14, 0, 4);";
    private static final String SEARCH_FOCUS_STYLE = "-fx-background-color: rgba(255,255,255,0.20);-fx-background-radius: 13px;-fx-border-color: #93c5fd;-fx-border-radius: 13px;-fx-border-width: 1.5px;-fx-text-fill: white;-fx-prompt-text-fill: #dbeafe;-fx-font-size: 12px;-fx-padding: 0 16px;-fx-effect: dropshadow(gaussian, rgba(59,130,246,0.34), 18, 0.18, 0, 5);";

    public GlobalSearchBar() {
        setPromptText("Search Khatabook  •  Dashboard, transactions, budget, goals...");
        setPrefWidth(610);
        setMinWidth(360);
        setMaxWidth(700);
        setPrefHeight(44);
        setFocusTraversable(true);
        setStyle(SEARCH_STYLE);

        textProperty().addListener((obs, oldValue, newValue) -> showSuggestions(newValue));
        setOnAction(e -> navigate(getText()));
        focusedProperty().addListener((obs, oldValue, focused) -> {
            setStyle(focused ? SEARCH_FOCUS_STYLE : SEARCH_STYLE);
            if (!focused) suggestions.hide();
            else showSuggestions(getText());
        });

        sceneProperty().addListener((obs, oldScene, scene) -> {
            if (scene != null) {
                Platform.runLater(() -> {
                    Branding.apply(scene.getRoot());
                    polishDashboard(scene.getRoot());
                });
            }
        });
    }

    private void showSuggestions(String value) {
        if (getScene() == null || value == null || value.isBlank()) {
            suggestions.hide();
            return;
        }

        String query = value.trim().toLowerCase(Locale.ROOT);
        List<SearchTarget> matches = new ArrayList<>();
        for (SearchTarget target : targets) {
            if (target.matches(query)) matches.add(target);
            if (matches.size() == 6) break;
        }

        suggestions.getItems().clear();
        for (SearchTarget target : matches) {
            MenuItem item = new MenuItem(target.name + "  —  " + target.description);
            item.setOnAction(e -> {
                setText(target.name);
                navigate(target.name);
            });
            suggestions.getItems().add(item);
        }

        if (matches.isEmpty()) {
            MenuItem none = new MenuItem("No matching Khatabook section");
            none.setDisable(true);
            suggestions.getItems().add(none);
        }

        if (!suggestions.isShowing()) suggestions.show(this, Side.BOTTOM, 0, 5);
    }

    private void navigate(String value) {
        if (getScene() == null || value == null || value.isBlank()) return;
        String query = value.trim().toLowerCase(Locale.ROOT);
        SearchTarget match = bestMatch(query);
        if (match == null) return;

        Node root = getScene().getRoot();
        Button button = findNavigationButton(root, match.name);
        if (button != null) {
            suggestions.hide();
            button.fire();
            clear();
        }
    }

    private SearchTarget bestMatch(String query) {
        SearchTarget exact = null;
        for (SearchTarget target : targets) {
            if (target.name.toLowerCase(Locale.ROOT).equals(query)) return target;
            if (exact == null && target.matches(query)) exact = target;
        }
        return exact;
    }

    private Button findNavigationButton(Node node, String targetName) {
        if (node instanceof Button button) {
            String text = button.getText() == null ? "" : button.getText();
            String normalized = text.toLowerCase(Locale.ROOT);
            if (switch (targetName) {
                case "Dashboard" -> normalized.contains("dashboard");
                case "Analytics" -> normalized.contains("analytics");
                case "Alerts" -> normalized.contains("alerts");
                case "Reports" -> normalized.contains("reports");
                case "Goals" -> normalized.contains("goals");
                case "Budget" -> normalized.contains("budget");
                case "Transactions" -> normalized.contains("transactions");
                case "Add Transaction" -> normalized.contains("add transaction") || normalized.equals("＋ add") || normalized.equals("+ add");
                case "People" -> normalized.contains("people");
                case "Settings" -> normalized.contains("settings");
                default -> false;
            }) return button;
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Button result = findNavigationButton(child, targetName);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void polishDashboard(Parent root) {
        applyStyleToClass(root, "navbar", "-fx-background-color: linear-gradient(to right, #07101f, #172554, #3730a3);-fx-padding: 11px 26px;-fx-min-height: 76px;-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.34), 24, 0.22, 0, 8);");
        applyStyleToClass(root, "welcome-bar", "-fx-background-color: linear-gradient(to right, #ffffff, #f8fbff);-fx-padding: 12px 30px;-fx-border-color: #e2e8f0;-fx-border-width: 0 0 1px 0;-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.055), 9, 0.08, 0, 2);");
        applyStyleToClass(root, "dashboard-content", "-fx-padding: 28px 38px 44px 38px;-fx-background-color: linear-gradient(to bottom, #f8fafc, #f5f7fb);");
        applyStyleToClass(root, "sidebar", "-fx-background-color: linear-gradient(to bottom, #070f20 0%, #101d40 52%, #1e2b5f 100%);-fx-padding: 24px 14px 20px 14px;-fx-spacing: 9px;-fx-border-color: transparent #263453 transparent transparent;-fx-border-width: 0 1px 0 0;-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.25), 20, 0.17, 3, 0);");
        applyStyleToClass(root, "summary-card", "-fx-background-color: rgba(255,255,255,0.98);-fx-background-radius: 18px;-fx-border-color: #e2e8f0;-fx-border-radius: 18px;-fx-padding: 20px;-fx-spacing: 8px;-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.085), 18, 0.13, 0, 6);");
        applyStyleToClass(root, "dashboard-page", "-fx-padding: 3px 0 12px 0;");

        Label title = findLabel(root, "Good to see you 👋");
        if (title != null) title.setStyle("-fx-font-size: 30px;-fx-font-weight: 800;-fx-text-fill: #0f172a;");
        Label subtitle = findLabel(root, "Your personal finance command center");
        if (subtitle != null) subtitle.setStyle("-fx-font-size: 13px;-fx-text-fill: #64748b;");

        polishCards(root);
        polishButtons(root);
    }

    private void polishCards(Node root) {
        for (Node node : collect(root)) {
            if (!node.getStyleClass().contains("summary-card")) continue;

            node.setOnMouseEntered(e -> {
                node.setTranslateY(-2);
                node.setScaleX(1.008);
                node.setScaleY(1.008);
                node.setStyle("-fx-background-color: white;-fx-background-radius: 18px;-fx-border-color: #cbd5e1;-fx-border-radius: 18px;-fx-padding: 20px;-fx-spacing: 8px;-fx-effect: dropshadow(gaussian, rgba(37,99,235,0.13), 22, 0.16, 0, 8);");
            });
            node.setOnMouseExited(e -> {
                node.setTranslateY(0);
                node.setScaleX(1);
                node.setScaleY(1);
                node.setStyle("-fx-background-color: rgba(255,255,255,0.98);-fx-background-radius: 18px;-fx-border-color: #e2e8f0;-fx-border-radius: 18px;-fx-padding: 20px;-fx-spacing: 8px;-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.085), 18, 0.13, 0, 6);");
            });
        }
    }

    private void polishButtons(Node root) {
        for (Node node : collect(root)) {
            if (!(node instanceof Button button)) continue;

            if (button.getStyleClass().contains("primary-button")) {
                button.setStyle("-fx-background-color: linear-gradient(to right, #2563eb, #4f46e5);-fx-background-radius: 10px;-fx-text-fill: white;-fx-font-weight: bold;-fx-padding: 10px 16px;-fx-cursor: hand;-fx-effect: dropshadow(gaussian, rgba(37,99,235,0.20), 10, 0.10, 0, 3);");
            } else if (button.getStyleClass().contains("secondary-button")) {
                button.setStyle("-fx-background-color: #f8fafc;-fx-background-radius: 10px;-fx-border-color: #dbe3ee;-fx-border-radius: 10px;-fx-text-fill: #334155;-fx-font-weight: 600;-fx-padding: 9px 14px;-fx-cursor: hand;");
            }

            if (button.getStyleClass().contains("nav-button")) {
                button.setMinHeight(43);
                button.setPrefHeight(43);
                button.setCursor(Cursor.HAND);
            }
        }
    }

    private void applyStyleToClass(Node root, String className, String style) {
        for (Node node : collect(root)) {
            if (node.getStyleClass().contains(className)) node.setStyle(style);
        }
    }

    private Label findLabel(Node root, String text) {
        for (Node node : collect(root)) {
            if (node instanceof Label label && text.equals(label.getText())) return label;
        }
        return null;
    }

    private List<Node> collect(Node root) {
        List<Node> nodes = new ArrayList<>();
        collectRecursive(root, nodes);
        return nodes;
    }

    private void collectRecursive(Node node, List<Node> nodes) {
        nodes.add(node);
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) collectRecursive(child, nodes);
        }
    }

    private record SearchTarget(String name, String description, String keywords) {
        boolean matches(String query) {
            String q = query.toLowerCase(Locale.ROOT);
            return name.toLowerCase(Locale.ROOT).contains(q)
                    || description.toLowerCase(Locale.ROOT).contains(q)
                    || keywords.toLowerCase(Locale.ROOT).contains(q);
        }
    }
}
