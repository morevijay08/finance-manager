package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.repository.FirestoreBudgetRepository;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.YearMonth;

/**
 * Small controller layer that keeps the monthly-budget UI responsive while the
 * Firestore write runs asynchronously. The existing DashboardController keeps
 * the rest of the dashboard implementation unchanged.
 */
public class BudgetDashboardController extends DashboardController {

    @FXML
    private void initialize() {
        try {
            Method parentInitialize = DashboardController.class.getDeclaredMethod("initialize");
            parentInitialize.setAccessible(true);
            parentInitialize.invoke(this);
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize dashboard.", e);
        }
    }

    @FXML
    private void handleSaveBudget() {
        try {
            TextField budgetField = field("budgetField", TextField.class);
            Label statusLabel = field("statusLabel", Label.class);
            Button saveButton = field("saveBudgetButton", Button.class);
            ProgressBar progressBar = field("budgetProgressBar", ProgressBar.class);
            Label spentLabel = field("budgetSpentLabel", Label.class);
            Label remainingLabel = field("budgetRemainingLabel", Label.class);
            FirestoreBudgetRepository repository = field("budgetRepository", FirestoreBudgetRepository.class);
            double amount = Double.parseDouble(budgetField.getText().trim());

            if (amount < 0) {
                throw new NumberFormatException();
            }

            // Update the model/UI immediately. The Firestore operation below is
            // asynchronous and must not be allowed to leave the dashboard stale.
            setPrivateDouble("monthlyBudget", amount);
            updateBudgetProgress(progressBar, spentLabel, remainingLabel, amount);

            saveButton.setDisable(true);
            statusLabel.setText("Saving monthly budget...");
            AuthSession session = new com.finance.manager.service.FirebaseAuthService().getCurrentSession();
            if (session == null) {
                saveButton.setDisable(false);
                statusLabel.setText("Please log in again.");
                return;
            }

            repository.saveMonthlyBudget(session, YearMonth.now(), amount)
                    .thenRun(() -> Platform.runLater(() -> {
                        saveButton.setDisable(false);
                        statusLabel.setText("Monthly budget saved successfully.");
                    }))
                    .exceptionally(error -> {
                        Platform.runLater(() -> {
                            saveButton.setDisable(false);
                            Throwable cause = error.getCause() != null ? error.getCause() : error;
                            String message = cause.getMessage();
                            statusLabel.setText(message == null || message.isBlank()
                                    ? "Could not save monthly budget."
                                    : "Could not save monthly budget: " + message);
                        });
                        return null;
                    });
        } catch (NumberFormatException e) {
            try {
                field("statusLabel", Label.class).setText("Enter a valid budget amount (0 or greater).");
            } catch (Exception ignored) {
                // The dashboard is already being initialized; no UI update is possible here.
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not save monthly budget.", e);
        }
    }

    private void updateBudgetProgress(ProgressBar progressBar, Label spentLabel,
                                      Label remainingLabel, double budget) throws Exception {
        Method update = DashboardController.class.getDeclaredMethod("updateBudgetProgress");
        update.setAccessible(true);
        update.invoke(this);
    }

    @SuppressWarnings("unchecked")
    private <T> T field(String name, Class<T> type) throws Exception {
        Field field = DashboardController.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(this);
    }

    private void setPrivateDouble(String name, double value) throws Exception {
        Field field = DashboardController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setDouble(this, value);
    }
}
