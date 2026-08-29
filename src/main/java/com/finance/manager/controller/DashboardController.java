package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.model.Transaction;
import com.finance.manager.repository.FirestoreTransactionRepository;
import com.finance.manager.service.FirebaseAuthService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDate;

public class DashboardController {

    @FXML private Label welcomeLabel;
    @FXML private Label emailLabel;
    @FXML private Label balanceLabel;
    @FXML private Label incomeLabel;
    @FXML private Label expenseLabel;
    @FXML private Label statusLabel;
    @FXML private ComboBox<String> typeCombo;
    @FXML private TextField amountField;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private TextField descriptionField;
    @FXML private DatePicker datePicker;
    @FXML private Button addButton;
    @FXML private TableView<Transaction> transactionTable;
    @FXML private TableColumn<Transaction, String> typeColumn;
    @FXML private TableColumn<Transaction, Number> amountColumn;
    @FXML private TableColumn<Transaction, String> categoryColumn;
    @FXML private TableColumn<Transaction, String> descriptionColumn;
    @FXML private TableColumn<Transaction, LocalDate> dateColumn;
    @FXML private TableColumn<Transaction, Void> actionColumn;

    private final FirebaseAuthService authService = new FirebaseAuthService();
    private final FirestoreTransactionRepository transactionRepository = new FirestoreTransactionRepository();
    private final ObservableList<Transaction> transactions = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        AuthSession session = authService.getCurrentSession();
        if (session == null) {
            welcomeLabel.setText("Session expired");
            emailLabel.setText("Please log in again.");
            return;
        }

        typeCombo.setItems(FXCollections.observableArrayList("INCOME", "EXPENSE"));
        typeCombo.setValue("EXPENSE");
        categoryCombo.setItems(FXCollections.observableArrayList(
                "Food", "Transport", "Shopping", "Bills", "Salary", "Business", "Health", "Education", "Other"));
        categoryCombo.setValue("Other");
        datePicker.setValue(LocalDate.now());

        typeColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getType().name()));
        amountColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleDoubleProperty(data.getValue().getAmount()));
        categoryColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getCategory()));
        descriptionColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDescription()));
        dateColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getDate()));
        actionColumn.setCellFactory(column -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            private final Button deleteButton = new Button("Delete");
            private final javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(6, editButton, deleteButton);

            {
                editButton.setOnAction(event -> editTransaction(getTableView().getItems().get(getIndex())));
                deleteButton.setOnAction(event -> deleteTransaction(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
        transactionTable.setItems(transactions);

        emailLabel.setText(session.getEmail());
        welcomeLabel.setText("Welcome");
        loadTransactions(session);
    }

    private void loadTransactions(AuthSession session) {
        statusLabel.setText("Loading your finance data...");
        transactionRepository.getTransactions(session).thenAccept(list -> Platform.runLater(() -> {
            transactions.setAll(list);
            updateSummary();
            statusLabel.setText("Ready");
        })).exceptionally(error -> {
            Platform.runLater(() -> statusLabel.setText("Could not load transactions."));
            return null;
        });
    }

    @FXML
    private void handleAddTransaction() {
        AuthSession session = authService.getCurrentSession();
        if (session == null) {
            statusLabel.setText("Please log in again.");
            return;
        }

        try {
            double amount = Double.parseDouble(amountField.getText().trim());
            if (amount <= 0) throw new NumberFormatException();
            LocalDate date = datePicker.getValue();
            if (date == null) throw new IllegalArgumentException("Select a date.");

            Transaction transaction = new Transaction(null,
                    Transaction.Type.valueOf(typeCombo.getValue()), amount,
                    categoryCombo.getValue(), descriptionField.getText().trim(), date);

            addButton.setDisable(true);
            statusLabel.setText("Saving transaction...");
            transactionRepository.addTransaction(session, transaction).thenAccept(saved -> Platform.runLater(() -> {
                transactions.add(0, saved);
                updateSummary();
                clearForm();
                addButton.setDisable(false);
                statusLabel.setText("Transaction added successfully.");
            })).exceptionally(error -> {
                Platform.runLater(() -> {
                    addButton.setDisable(false);
                    statusLabel.setText("Could not save transaction.");
                });
                return null;
            });
        } catch (NumberFormatException e) {
            statusLabel.setText("Enter a valid amount greater than 0.");
        } catch (IllegalArgumentException e) {
            statusLabel.setText(e.getMessage());
        }
    }

    private void editTransaction(Transaction transaction) {
        typeCombo.setValue(transaction.getType().name());
        amountField.setText(String.valueOf(transaction.getAmount()));
        categoryCombo.setValue(transaction.getCategory());
        descriptionField.setText(transaction.getDescription());
        datePicker.setValue(transaction.getDate());
        addButton.setText("Update");
        addButton.setDisable(false);
        addButton.setOnAction(event -> handleUpdateTransaction());
        statusLabel.setText("Editing transaction. Change the fields and click Update.");
    }

    private void handleUpdateTransaction() {
        AuthSession session = authService.getCurrentSession();
        Transaction selected = transactionTable.getSelectionModel().getSelectedItem();
        if (session == null || selected == null) {
            statusLabel.setText("Select a transaction to edit.");
            resetAddButton();
            return;
        }

        try {
            double amount = Double.parseDouble(amountField.getText().trim());
            if (amount <= 0 || datePicker.getValue() == null) throw new NumberFormatException();

            selected.setType(Transaction.Type.valueOf(typeCombo.getValue()));
            selected.setAmount(amount);
            selected.setCategory(categoryCombo.getValue());
            selected.setDescription(descriptionField.getText().trim());
            selected.setDate(datePicker.getValue());

            addButton.setDisable(true);
            statusLabel.setText("Updating transaction...");
            transactionRepository.updateTransaction(session, selected).thenAccept(updated -> Platform.runLater(() -> {
                transactionTable.refresh();
                updateSummary();
                resetFormAndButton();
                statusLabel.setText("Transaction updated successfully.");
            })).exceptionally(error -> {
                Platform.runLater(() -> {
                    addButton.setDisable(false);
                    statusLabel.setText("Could not update transaction.");
                });
                return null;
            });
        } catch (NumberFormatException e) {
            statusLabel.setText("Enter a valid amount and date.");
        }
    }

    private void deleteTransaction(Transaction transaction) {
        if (transaction == null || transaction.getId() == null || transaction.getId().isBlank()) {
            statusLabel.setText("This transaction cannot be deleted.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Transaction");
        confirmation.setHeaderText("Delete this transaction?");
        confirmation.setContentText(String.format("%s ₹%.2f — %s", transaction.getType(), transaction.getAmount(), transaction.getCategory()));

        confirmation.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;
            AuthSession session = authService.getCurrentSession();
            if (session == null) {
                statusLabel.setText("Please log in again.");
                return;
            }

            statusLabel.setText("Deleting transaction...");
            transactionRepository.deleteTransaction(session, transaction.getId()).thenRun(() -> Platform.runLater(() -> {
                transactions.remove(transaction);
                updateSummary();
                statusLabel.setText("Transaction deleted successfully.");
            })).exceptionally(error -> {
                Platform.runLater(() -> statusLabel.setText("Could not delete transaction."));
                return null;
            });
        });
    }

    private void clearForm() {
        amountField.clear();
        descriptionField.clear();
        typeCombo.setValue("EXPENSE");
        categoryCombo.setValue("Other");
        datePicker.setValue(LocalDate.now());
    }

    private void resetFormAndButton() {
        clearForm();
        resetAddButton();
    }

    private void resetAddButton() {
        addButton.setText("Add");
        addButton.setOnAction(event -> handleAddTransaction());
        addButton.setDisable(false);
    }

    private void updateSummary() {
        double income = transactions.stream().filter(t -> t.getType() == Transaction.Type.INCOME)
                .mapToDouble(Transaction::getAmount).sum();
        double expense = transactions.stream().filter(t -> t.getType() == Transaction.Type.EXPENSE)
                .mapToDouble(Transaction::getAmount).sum();
        balanceLabel.setText(formatMoney(income - expense));
        incomeLabel.setText(formatMoney(income));
        expenseLabel.setText(formatMoney(expense));
    }

    private String formatMoney(double value) {
        return String.format("₹ %.2f", value);
    }

    @FXML
    private void handleLogout(ActionEvent event) throws IOException {
        authService.logout();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 1100, 700);
        scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(scene);
        stage.show();
    }
}
