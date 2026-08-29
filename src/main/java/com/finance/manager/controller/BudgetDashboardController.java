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
 * Controller layer that keeps the monthly-budget UI responsive while the
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
    private void handleLogout() {
        invokeParent("handleLogout");
    }

    @FXML
    private void handleAddTransaction() {
        invokeParent("handleAddTransaction");
    }

    @FXML
    private void handleExportCsv() {
        invokeParent("handleExportCsv");
    }

    @FXML
    private void handleSaveBudget() {
        try {
            TextField budgetField = field("budgetField", TextField.class);
            Label statusLabel = field("statusLabel", Label.class);
            Button saveButton = field("saveBudgetButton", Button.class);
            FirestoreBudgetRepository repository = field("budgetRepository", FirestoreBudgetRepository.class);
            double amount = Double.parseDouble(budgetField.getText().trim());

            if (amount < 0) {
                throw new NumberFormatException();
            }

            // Update the local model/UI immediately. The Firestore operation is
            // asynchronous, so the dashboard must not wait for it to refresh.
            setPrivateDouble("monthlyBudget", amount);
            invokeParent("updateBudgetProgress");

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
                // Ignore UI lookup failure during initialization.
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not save monthly budget.", e);
        }
    }

    private void invokeParent(String methodName) {
        try {
            Method method = DashboardController.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(this);
        } catch (Exception e) {
            throw new RuntimeException("Could not invoke dashboard action: " + methodName, e);
        }
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
