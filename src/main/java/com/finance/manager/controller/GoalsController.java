package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.model.FinancialGoal;
import com.finance.manager.repository.FirestoreGoalRepository;
import com.finance.manager.service.FirebaseAuthService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Locale;

public class GoalsController {
    @FXML private TextField goalNameField;
    @FXML private TextField targetField;
    @FXML private TextField savedField;
    @FXML private Button addGoalButton;
    @FXML private VBox goalsBox;
    @FXML private Label goalStatusLabel;

    private final FirebaseAuthService authService = new FirebaseAuthService();
    private final FirestoreGoalRepository goalRepository = new FirestoreGoalRepository();
    private final ObservableList<FinancialGoal> goals = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        AuthSession session = authService.getCurrentSession();
        if (session == null) {
            goalStatusLabel.setText("Please log in again.");
            addGoalButton.setDisable(true);
            return;
        }
        loadGoals(session);
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
        for (FinancialGoal goal : goals) {
            goalsBox.getChildren().add(createGoalCard(goal));
        }
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

    private String formatMoney(double value) {
        return String.format(Locale.US, "₹ %.2f", value);
    }
}
