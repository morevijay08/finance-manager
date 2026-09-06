package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.model.RecurringTransaction;
import com.finance.manager.model.Transaction;
import com.finance.manager.repository.FirestoreBudgetRepository;
import com.finance.manager.repository.FirestoreRecurringTransactionRepository;
import com.finance.manager.repository.FirestoreTransactionRepository;
import com.finance.manager.service.FirebaseAuthService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Dashboard alerts for budget usage and upcoming recurring transactions. */
public class NotificationsController {

    @FXML private VBox notificationsBox;
    @FXML private Label notificationStatusLabel;
    @FXML private Label alertCountLabel;
    @FXML private Label budgetStatusLabel;
    @FXML private Label recurringStatusLabel;

    private final FirebaseAuthService authService = new FirebaseAuthService();
    private final FirestoreTransactionRepository transactionRepository = new FirestoreTransactionRepository();
    private final FirestoreBudgetRepository budgetRepository = new FirestoreBudgetRepository();
    private final FirestoreRecurringTransactionRepository recurringRepository = new FirestoreRecurringTransactionRepository();

    @FXML
    private void initialize() {
        loadAlerts();
    }

    private void loadAlerts() {
        AuthSession session = authService.getCurrentSession();
        if (session == null) {
            notificationStatusLabel.setText("Please log in again.");
            return;
        }

        notificationStatusLabel.setText("Checking alerts...");
        transactionRepository.getTransactions(session)
                .thenCombine(budgetRepository.getMonthlyBudget(session, YearMonth.now()), AlertData::new)
                .thenCombine(recurringRepository.getAll(session), (data, recurring) -> {
                    data.recurring = recurring;
                    return data;
                })
                .thenAccept(data -> Platform.runLater(() -> renderAlerts(data)))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        notificationStatusLabel.setText("Could not load alerts.");
                        alertCountLabel.setText("—");
                        budgetStatusLabel.setText("Budget data unavailable.");
                        recurringStatusLabel.setText("Recurring-payment data unavailable.");
                    });
                    return null;
                });
    }

    private void renderAlerts(AlertData data) {
        List<AlertItem> alerts = new ArrayList<>();
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.now();

        double spent = data.transactions.stream()
                .filter(t -> t != null && t.getType() == Transaction.Type.EXPENSE)
                .filter(t -> t.getDate() != null && YearMonth.from(t.getDate()).equals(currentMonth))
                .mapToDouble(Transaction::getAmount)
                .sum();

        if (data.budget > 0) {
            double percentage = spent / data.budget * 100.0;
            double remaining = data.budget - spent;
            if (percentage >= 100.0) {
                budgetStatusLabel.setText(String.format(Locale.US, "Over budget by ₹%.2f", Math.abs(remaining)));
                budgetStatusLabel.setStyle(statusStyle("#dc2626"));
                alerts.add(new AlertItem("BUDGET", "Budget exceeded", String.format(Locale.US,
                        "You have spent ₹%.2f of your ₹%.2f monthly budget.", spent, data.budget), "⚠", "#fee2e2", "#dc2626"));
            } else if (percentage >= 80.0) {
                budgetStatusLabel.setText(String.format(Locale.US, "%.0f%% used • ₹%.2f remaining", percentage, remaining));
                budgetStatusLabel.setStyle(statusStyle("#d97706"));
                alerts.add(new AlertItem("BUDGET", "Budget warning", String.format(Locale.US,
                        "%.0f%% of your monthly budget is already used (₹%.2f / ₹%.2f).", percentage, spent, data.budget), "!", "#fef3c7", "#d97706"));
            } else {
                budgetStatusLabel.setText(String.format(Locale.US, "%.0f%% used • ₹%.2f remaining", percentage, remaining));
                budgetStatusLabel.setStyle(statusStyle("#15803d"));
            }
        } else {
            budgetStatusLabel.setText("No monthly budget is set.");
            budgetStatusLabel.setStyle(statusStyle("#64748b"));
        }

        List<RecurringTransaction> upcoming = data.recurring.stream()
                .filter(RecurringTransaction::isActive)
                .filter(item -> item.getNextDate() != null)
                .sorted(Comparator.comparing(RecurringTransaction::getNextDate))
                .toList();

        long dueSoonCount = upcoming.stream().filter(item -> {
            long days = ChronoUnit.DAYS.between(today, item.getNextDate());
            return days >= 0 && days <= 3;
        }).count();
        long overdueCount = upcoming.stream().filter(item -> item.getNextDate().isBefore(today)).count();

        if (overdueCount > 0) {
            recurringStatusLabel.setText(overdueCount + " overdue payment" + (overdueCount == 1 ? "" : "s"));
            recurringStatusLabel.setStyle(statusStyle("#dc2626"));
        } else if (dueSoonCount > 0) {
            recurringStatusLabel.setText(dueSoonCount + " payment" + (dueSoonCount == 1 ? "" : "s") + " due within 3 days");
            recurringStatusLabel.setStyle(statusStyle("#d97706"));
        } else if (upcoming.isEmpty()) {
            recurringStatusLabel.setText("No active recurring payments.");
            recurringStatusLabel.setStyle(statusStyle("#64748b"));
        } else {
            recurringStatusLabel.setText(upcoming.size() + " active payment" + (upcoming.size() == 1 ? "" : "s") + " scheduled.");
            recurringStatusLabel.setStyle(statusStyle("#15803d"));
        }

        for (RecurringTransaction item : upcoming) {
            long days = ChronoUnit.DAYS.between(today, item.getNextDate());
            String name = value(item.getDescription(), item.getCategory(), "Recurring payment");
            if (item.getNextDate().isBefore(today)) {
                alerts.add(new AlertItem("RECURRING", "Overdue payment", String.format(Locale.US,
                        "%s • ₹%.2f was due on %s.", name, item.getAmount(), item.getNextDate()), "!", "#fee2e2", "#dc2626"));
            } else if (days == 0) {
                alerts.add(new AlertItem("RECURRING", "Payment due today", String.format(Locale.US,
                        "%s • ₹%.2f is due today.", name, item.getAmount()), "↻", "#fef3c7", "#d97706"));
            } else if (days <= 3) {
                alerts.add(new AlertItem("RECURRING", "Upcoming payment", String.format(Locale.US,
                        "%s • ₹%.2f is due on %s.", name, item.getAmount(), item.getNextDate()), "↻", "#eff6ff", "#2563eb"));
            }
        }

        notificationsBox.getChildren().clear();
        alertCountLabel.setText(String.valueOf(alerts.size()));

        if (alerts.isEmpty()) {
            VBox empty = new VBox(5);
            empty.setAlignment(Pos.CENTER);
            empty.setStyle("-fx-background-color: #f0fdf4; -fx-background-radius: 14px; -fx-border-color: #bbf7d0; -fx-border-radius: 14px; -fx-padding: 28px 18px;");
            Label icon = new Label("✓");
            icon.setStyle("-fx-font-size: 26px; -fx-font-weight: 800; -fx-text-fill: #16a34a;");
            Label title = new Label("You're all clear");
            title.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #166534;");
            Label detail = new Label("No budget warnings or urgent recurring payments right now.");
            detail.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
            detail.setWrapText(true);
            empty.getChildren().addAll(icon, title, detail);
            notificationsBox.getChildren().add(empty);
            notificationStatusLabel.setText("All clear • No action needed");
            return;
        }

        for (AlertItem alert : alerts) {
            notificationsBox.getChildren().add(createAlertCard(alert));
        }
        notificationStatusLabel.setText(alerts.size() + " alert" + (alerts.size() == 1 ? "" : "s") + " need" + (alerts.size() == 1 ? "s" : "") + " your attention");
    }

    private VBox createAlertCard(AlertItem alert) {
        VBox card = new VBox(4);
        card.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 12px; -fx-border-color: #e2e8f0; -fx-border-radius: 12px; -fx-padding: 12px 14px;");

        Label icon = new Label(alert.icon());
        icon.setMinSize(36, 36);
        icon.setMaxSize(36, 36);
        icon.setAlignment(Pos.CENTER);
        icon.setStyle("-fx-background-color: " + alert.iconBackground() + "; -fx-background-radius: 10px; -fx-text-fill: " + alert.iconText() + "; -fx-font-size: 16px; -fx-font-weight: 800;");

        VBox text = new VBox(3);
        HBox.setHgrow(text, javafx.scene.layout.Priority.ALWAYS);
        Label category = new Label(alert.category());
        category.setStyle("-fx-font-size: 9px; -fx-font-weight: 800; -fx-text-fill: " + alert.iconText() + ";");
        Label title = new Label(alert.title());
        title.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");
        Label detail = new Label(alert.detail());
        detail.setWrapText(true);
        detail.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        text.getChildren().addAll(category, title, detail);

        HBox row = new HBox(12, icon, text);
        row.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(row);
        return card;
    }

    private String statusStyle(String textColor) {
        return "-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: " + textColor + ";";
    }

    private String value(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return fallback;
    }

    private record AlertItem(String category, String title, String detail, String icon,
                             String iconBackground, String iconText) { }

    private static final class AlertData {
        private final List<Transaction> transactions;
        private final double budget;
        private List<RecurringTransaction> recurring = List.of();

        private AlertData(List<Transaction> transactions, double budget) {
            this.transactions = transactions == null ? List.of() : transactions;
            this.budget = budget;
        }
    }
}
