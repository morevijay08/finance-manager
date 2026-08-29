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
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Dashboard alerts for budget usage and upcoming recurring transactions.
 * Alerts are generated from the user's current Firestore data and are not persisted.
 */
public class NotificationsController {

    @FXML private VBox notificationsBox;
    @FXML private Label notificationStatusLabel;

    private final FirebaseAuthService authService = new FirebaseAuthService();
    private final FirestoreTransactionRepository transactionRepository = new FirestoreTransactionRepository();
    private final FirestoreBudgetRepository budgetRepository = new FirestoreBudgetRepository();
    private final FirestoreRecurringTransactionRepository recurringRepository = new FirestoreRecurringTransactionRepository();

    @FXML
    private void initialize() {
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
                    Platform.runLater(() -> notificationStatusLabel.setText("Could not load alerts."));
                    return null;
                });
    }

    private void renderAlerts(AlertData data) {
        List<String> alerts = new ArrayList<>();
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.now();

        double spent = data.transactions.stream()
                .filter(t -> t.getType() == Transaction.Type.EXPENSE)
                .filter(t -> t.getDate() != null && YearMonth.from(t.getDate()).equals(currentMonth))
                .mapToDouble(Transaction::getAmount)
                .sum();

        if (data.budget > 0) {
            double percentage = spent / data.budget * 100.0;
            if (percentage >= 100.0) {
                alerts.add(String.format(Locale.US,
                        "⚠ Budget exceeded: you have spent ₹%.2f of your ₹%.2f monthly budget.", spent, data.budget));
            } else if (percentage >= 80.0) {
                alerts.add(String.format(Locale.US,
                        "⚠ Budget warning: %.0f%% of your monthly budget has been used (₹%.2f / ₹%.2f).",
                        percentage, spent, data.budget));
            }
        }

        data.recurring.stream()
                .filter(RecurringTransaction::isActive)
                .filter(item -> item.getNextDate() != null)
                .sorted(Comparator.comparing(RecurringTransaction::getNextDate))
                .forEach(item -> {
                    long days = ChronoUnit.DAYS.between(today, item.getNextDate());
                    String name = item.getDescription() == null || item.getDescription().isBlank()
                            ? item.getCategory() : item.getDescription();
                    if (item.getNextDate().isBefore(today)) {
                        alerts.add(String.format(Locale.US,
                                "🔔 Overdue recurring %s: ₹%.2f was due on %s.",
                                name, item.getAmount(), item.getNextDate()));
                    } else if (days == 0) {
                        alerts.add(String.format(Locale.US,
                                "🔔 Recurring %s is due today: ₹%.2f.", name, item.getAmount()));
                    } else if (days <= 3) {
                        alerts.add(String.format(Locale.US,
                                "🔔 Upcoming %s: ₹%.2f is due on %s.",
                                name, item.getAmount(), item.getNextDate()));
                    }
                });

        notificationsBox.getChildren().clear();
        if (alerts.isEmpty()) {
            Label empty = new Label("✓ No important alerts right now.");
            empty.getStyleClass().add("subtitle");
            notificationsBox.getChildren().add(empty);
            notificationStatusLabel.setText("All clear");
            return;
        }

        for (String message : alerts) {
            Label alert = new Label(message);
            alert.setWrapText(true);
            alert.getStyleClass().add("message-label");
            notificationsBox.getChildren().add(alert);
        }
        notificationStatusLabel.setText(alerts.size() + " alert(s)");
    }

    private static final class AlertData {
        private final List<Transaction> transactions;
        private final double budget;
        private List<RecurringTransaction> recurring = List.of();

        private AlertData(List<Transaction> transactions, double budget) {
            this.transactions = transactions;
            this.budget = budget;
        }
    }
}
