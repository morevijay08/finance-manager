package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.model.FinancialGoal;
import com.finance.manager.model.RecurringTransaction;
import com.finance.manager.model.Transaction;
import com.finance.manager.repository.FirestoreGoalRepository;
import com.finance.manager.repository.FirestoreRecurringTransactionRepository;
import com.finance.manager.repository.FirestoreTransactionRepository;
import com.finance.manager.service.FirebaseAuthService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.Locale;

public class GoalsController {
    @FXML private TextField goalNameField;
    @FXML private TextField targetField;
    @FXML private TextField savedField;
    @FXML private Button addGoalButton;
    @FXML private VBox goalsBox;
    @FXML private Label goalStatusLabel;

    @FXML private ComboBox<String> recurringTypeCombo;
    @FXML private TextField recurringAmountField;
    @FXML private ComboBox<String> recurringCategoryCombo;
    @FXML private TextField recurringDescriptionField;
    @FXML private ComboBox<String> recurringFrequencyCombo;
    @FXML private DatePicker recurringNextDatePicker;
    @FXML private Button addRecurringButton;
    @FXML private VBox recurringBox;
    @FXML private Label recurringStatusLabel;

    private final FirebaseAuthService authService = new FirebaseAuthService();
    private final FirestoreGoalRepository goalRepository = new FirestoreGoalRepository();
    private final FirestoreRecurringTransactionRepository recurringRepository = new FirestoreRecurringTransactionRepository();
    private final FirestoreTransactionRepository transactionRepository = new FirestoreTransactionRepository();
    private final ObservableList<FinancialGoal> goals = FXCollections.observableArrayList();
    private final ObservableList<RecurringTransaction> recurringTransactions = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        setupRecurringForm();
        AuthSession session = authService.getCurrentSession();
        if (session == null) {
            goalStatusLabel.setText("Please log in again.");
            recurringStatusLabel.setText("Please log in again.");
            addGoalButton.setDisable(true);
            addRecurringButton.setDisable(true);
            return;
        }
        loadGoals(session);
        loadRecurringTransactions(session);
    }

    private void setupRecurringForm() {
        recurringTypeCombo.setItems(FXCollections.observableArrayList("EXPENSE", "INCOME"));
        recurringTypeCombo.setValue("EXPENSE");
        recurringCategoryCombo.setItems(FXCollections.observableArrayList(
                "Food", "Transport", "Shopping", "Bills", "Salary", "Business", "Health", "Education", "Other"));
        recurringCategoryCombo.setValue("Bills");
        recurringFrequencyCombo.setItems(FXCollections.observableArrayList("DAILY", "WEEKLY", "MONTHLY", "YEARLY"));
        recurringFrequencyCombo.setValue("MONTHLY");
        recurringNextDatePicker.setValue(LocalDate.now());
    }

    @FXML
    private void handleAddGoal() {
        AuthSession session = authService.getCurrentSession();
        if (session == null) {
            goalStatusLabel.setText("Please log in again.");
            return;
        }

        String name = goalNameField.getText().trim();
        if (name.isEmpty()) {
            goalStatusLabel.setText("Enter a goal name.");
            return;
        }

        try {
            double target = Double.parseDouble(targetField.getText().trim());
            double saved = savedField.getText().trim().isEmpty() ? 0 : Double.parseDouble(savedField.getText().trim());
            if (target <= 0 || saved < 0) throw new NumberFormatException();
            if (saved > target) {
                goalStatusLabel.setText("Saved amount cannot exceed the target.");
                return;
            }

            addGoalButton.setDisable(true);
            goalStatusLabel.setText("Saving goal...");
            goalRepository.addGoal(session, new FinancialGoal(null, name, target, saved))
                    .thenAccept(goal -> Platform.runLater(() -> {
                        goals.add(0, goal);
                        renderGoals();
                        goalNameField.clear();
                        targetField.clear();
                        savedField.clear();
                        addGoalButton.setDisable(false);
                        goalStatusLabel.setText("Financial goal added successfully.");
                    }))
                    .exceptionally(error -> {
                        Platform.runLater(() -> {
                            addGoalButton.setDisable(false);
                            goalStatusLabel.setText("Could not save financial goal.");
                        });
                        return null;
                    });
        } catch (NumberFormatException e) {
            goalStatusLabel.setText("Enter valid target and saved amounts.");
        }
    }

    private void loadGoals(AuthSession session) {
        goalStatusLabel.setText("Loading goals...");
        goalRepository.getGoals(session)
                .thenAccept(result -> Platform.runLater(() -> {
                    goals.setAll(result);
                    renderGoals();
                    goalStatusLabel.setText(result.isEmpty() ? "No financial goals yet." : "Ready");
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> goalStatusLabel.setText("Could not load financial goals."));
                    return null;
                });
    }

    private void renderGoals() {
        goalsBox.getChildren().clear();
        for (FinancialGoal goal : goals) goalsBox.getChildren().add(createGoalCard(goal));
    }

    private VBox createGoalCard(FinancialGoal goal) {
        VBox card = new VBox(7);
        card.getStyleClass().add("summary-card");

        HBox header = new HBox(10);
        Label name = new Label(goal.getName());
        name.getStyleClass().add("card-title");
        Label target = new Label(formatMoney(goal.getTargetAmount()));
        target.getStyleClass().add("balance-value");
        HBox.setHgrow(name, javafx.scene.layout.Priority.ALWAYS);
        header.getChildren().addAll(name, target);

        ProgressBar progress = new ProgressBar(goal.getProgress());
        progress.setMaxWidth(Double.MAX_VALUE);
        Label details = new Label(String.format(Locale.US, "Saved %s  •  Remaining %s  •  %.1f%%",
                formatMoney(goal.getSavedAmount()), formatMoney(goal.getRemainingAmount()), goal.getProgress() * 100));
        details.getStyleClass().add("subtitle");
        Button delete = new Button("Delete");
        delete.getStyleClass().add("secondary-button");
        delete.setOnAction(event -> deleteGoal(goal));
        card.getChildren().addAll(header, progress, details, delete);
        return card;
    }

    private void deleteGoal(FinancialGoal goal) {
        AuthSession session = authService.getCurrentSession();
        if (session == null) {
            goalStatusLabel.setText("Please log in again.");
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Financial Goal");
        alert.setHeaderText("Delete this goal?");
        alert.setContentText(goal.getName());
        alert.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;
            goalStatusLabel.setText("Deleting goal...");
            goalRepository.deleteGoal(session, goal.getId()).thenRun(() -> Platform.runLater(() -> {
                goals.remove(goal);
                renderGoals();
                goalStatusLabel.setText("Financial goal deleted successfully.");
            })).exceptionally(error -> {
                Platform.runLater(() -> goalStatusLabel.setText("Could not delete financial goal."));
                return null;
            });
        });
    }

    @FXML
    private void handleAddRecurring() {
        AuthSession session = authService.getCurrentSession();
        if (session == null) {
            recurringStatusLabel.setText("Please log in again.");
            return;
        }
        try {
            double amount = Double.parseDouble(recurringAmountField.getText().trim());
            if (amount <= 0 || recurringNextDatePicker.getValue() == null) throw new NumberFormatException();

            RecurringTransaction item = new RecurringTransaction(
                    null, Transaction.Type.valueOf(recurringTypeCombo.getValue()), amount,
                    recurringCategoryCombo.getValue(), recurringDescriptionField.getText().trim(),
                    RecurringTransaction.Frequency.valueOf(recurringFrequencyCombo.getValue()),
                    recurringNextDatePicker.getValue(), true);

            addRecurringButton.setDisable(true);
            recurringStatusLabel.setText("Saving recurring transaction...");
            recurringRepository.add(session, item)
                    .thenAccept(saved -> Platform.runLater(() -> {
                        recurringTransactions.add(saved);
                        renderRecurringTransactions();
                        clearRecurringForm();
                        addRecurringButton.setDisable(false);
                        recurringStatusLabel.setText("Recurring transaction added successfully.");
                    }))
                    .exceptionally(error -> {
                        Platform.runLater(() -> {
                            addRecurringButton.setDisable(false);
                            recurringStatusLabel.setText("Could not save recurring transaction.");
                        });
                        return null;
                    });
        } catch (IllegalArgumentException e) {
            recurringStatusLabel.setText("Enter a valid amount and next date.");
        }
    }

    private void loadRecurringTransactions(AuthSession session) {
        recurringStatusLabel.setText("Loading recurring transactions...");
        recurringRepository.getAll(session)
                .thenCompose(items -> processDueTransactions(session, items).thenApply(v -> items))
                .thenAccept(items -> Platform.runLater(() -> {
                    recurringTransactions.setAll(items);
                    renderRecurringTransactions();
                    recurringStatusLabel.setText(items.isEmpty()
                            ? "No recurring transactions yet."
                            : "Ready. Due recurring transactions are added automatically when the app opens.");
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> recurringStatusLabel.setText("Could not load recurring transactions."));
                    return null;
                });
    }

    private java.util.concurrent.CompletableFuture<Void> processDueTransactions(AuthSession session,
                                                                                 java.util.List<RecurringTransaction> items) {
        java.util.concurrent.CompletableFuture<Void> chain = java.util.concurrent.CompletableFuture.completedFuture(null);
        LocalDate today = LocalDate.now();
        for (RecurringTransaction item : items) {
            if (!item.isActive() || item.getNextDate() == null || item.getNextDate().isAfter(today)) continue;
            chain = chain.thenCompose(v -> processOneDueItem(session, item, today));
        }
        return chain;
    }

    private java.util.concurrent.CompletableFuture<Void> processOneDueItem(AuthSession session,
                                                                            RecurringTransaction item,
                                                                            LocalDate today) {
        java.util.concurrent.CompletableFuture<Void> chain = java.util.concurrent.CompletableFuture.completedFuture(null);
        LocalDate next = item.getNextDate();
        while (!next.isAfter(today)) {
            LocalDate transactionDate = next;
            Transaction transaction = new Transaction(
                    null, item.getType(), item.getAmount(), item.getCategory(),
                    item.getDescription() == null || item.getDescription().isBlank()
                            ? "Recurring transaction" : item.getDescription(), transactionDate);
            chain = chain.thenCompose(v -> transactionRepository.addTransaction(session, transaction).thenApply(saved -> null));
            next = nextDate(next, item.getFrequency());
        }
        item.setNextDate(next);
        return chain.thenCompose(v -> recurringRepository.update(session, item));
    }

    private LocalDate nextDate(LocalDate date, RecurringTransaction.Frequency frequency) {
        return switch (frequency) {
            case DAILY -> date.plusDays(1);
            case WEEKLY -> date.plusWeeks(1);
            case MONTHLY -> date.plusMonths(1);
            case YEARLY -> date.plusYears(1);
        };
    }

    private void renderRecurringTransactions() {
        recurringBox.getChildren().clear();
        for (RecurringTransaction item : recurringTransactions) recurringBox.getChildren().add(createRecurringCard(item));
    }

    private VBox createRecurringCard(RecurringTransaction item) {
        VBox card = new VBox(7);
        card.getStyleClass().add("summary-card");
        HBox header = new HBox(10);
        Label title = new Label(String.format("%s • %s", item.getType(), formatMoney(item.getAmount())));
        title.getStyleClass().add("card-title");
        Label frequency = new Label(item.getFrequency().name());
        frequency.getStyleClass().add("subtitle");
        HBox.setHgrow(title, javafx.scene.layout.Priority.ALWAYS);
        header.getChildren().addAll(title, frequency);

        String description = item.getDescription() == null || item.getDescription().isBlank()
                ? item.getCategory() : item.getCategory() + " • " + item.getDescription();
        Label details = new Label(String.format("%s  •  Next: %s  •  %s",
                description, item.getNextDate(), item.isActive() ? "Active" : "Paused"));
        details.getStyleClass().add("subtitle");

        Button toggle = new Button(item.isActive() ? "Pause" : "Resume");
        toggle.getStyleClass().add("secondary-button");
        toggle.setOnAction(event -> toggleRecurring(item));
        Button delete = new Button("Delete");
        delete.getStyleClass().add("secondary-button");
        delete.setOnAction(event -> deleteRecurring(item));
        card.getChildren().addAll(header, details, new HBox(8, toggle, delete));
        return card;
    }

    private void toggleRecurring(RecurringTransaction item) {
        AuthSession session = authService.getCurrentSession();
        if (session == null) {
            recurringStatusLabel.setText("Please log in again.");
            return;
        }
        item.setActive(!item.isActive());
        recurringStatusLabel.setText("Updating recurring transaction...");
        recurringRepository.update(session, item).thenRun(() -> Platform.runLater(() -> {
            renderRecurringTransactions();
            recurringStatusLabel.setText(item.isActive() ? "Recurring transaction resumed." : "Recurring transaction paused.");
        })).exceptionally(error -> {
            item.setActive(!item.isActive());
            Platform.runLater(() -> recurringStatusLabel.setText("Could not update recurring transaction."));
            return null;
        });
    }

    private void deleteRecurring(RecurringTransaction item) {
        AuthSession session = authService.getCurrentSession();
        if (session == null) {
            recurringStatusLabel.setText("Please log in again.");
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Recurring Transaction");
        alert.setHeaderText("Delete this recurring transaction?");
        alert.setContentText(String.format("%s ₹%.2f (%s)", item.getType(), item.getAmount(), item.getFrequency()));
        alert.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;
            recurringStatusLabel.setText("Deleting recurring transaction...");
            recurringRepository.delete(session, item.getId()).thenRun(() -> Platform.runLater(() -> {
                recurringTransactions.remove(item);
                renderRecurringTransactions();
                recurringStatusLabel.setText("Recurring transaction deleted successfully.");
            })).exceptionally(error -> {
                Platform.runLater(() -> recurringStatusLabel.setText("Could not delete recurring transaction."));
                return null;
            });
        });
    }

    private void clearRecurringForm() {
        recurringAmountField.clear();
        recurringDescriptionField.clear();
        recurringTypeCombo.setValue("EXPENSE");
        recurringCategoryCombo.setValue("Bills");
        recurringFrequencyCombo.setValue("MONTHLY");
        recurringNextDatePicker.setValue(LocalDate.now());
    }

    private String formatMoney(double value) {
        return String.format(Locale.US, "₹ %.2f", value);
    }
}
