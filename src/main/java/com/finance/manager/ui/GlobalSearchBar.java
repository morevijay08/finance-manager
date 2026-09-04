package com.finance.manager.ui;

import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Global navigation search used by the main dashboard.
 * It searches feature/page names and redirects to the matching section.
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

    public GlobalSearchBar() {
        setPromptText("Search Hisaabi — Dashboard, transactions, budget, goals...");
        setPrefWidth(560);
        setMinWidth(320);
        setMaxWidth(680);
        setPrefHeight(40);
        setFocusTraversable(true);
        setStyle("-fx-background-color: rgba(255,255,255,0.12);-fx-background-radius: 11px;-fx-border-color: rgba(191,219,254,0.42);-fx-border-radius: 11px;-fx-border-width: 1px;-fx-text-fill: white;-fx-prompt-text-fill: #cbd5e1;-fx-font-size: 12px;-fx-padding: 0 14px;-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.16), 10, 0.12, 0, 3);");

        textProperty().addListener((obs, oldValue, newValue) -> showSuggestions(newValue));
        setOnAction(e -> navigate(text));
        focusedProperty().addListener((obs, oldValue, focused) -> {
            if (!focused) suggestions.hide();
            else showSuggestions(getText());
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
            MenuItem none = new MenuItem("No matching Hisaabi section");
            none.setDisable(true);
            suggestions.getItems().add(none);
        }

        if (!suggestions.isShowing()) {
            suggestions.show(this, Side.BOTTOM, 0, 4);
        }
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

        if (node instanceof Pane pane) {
            for (Node child : pane.getChildren()) {
                Button result = findNavigationButton(child, targetName);
                if (result != null) return result;
            }
        }
        return null;
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
