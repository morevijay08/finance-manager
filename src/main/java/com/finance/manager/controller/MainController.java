package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.model.Transaction;
import com.finance.manager.repository.FirestoreTransactionRepository;
import com.finance.manager.repository.FirestoreUserRepository;
import com.finance.manager.service.FirebaseAuthService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;

public class MainController {

    public Label userLabel;
    public Label profileLabel;
    public Label balanceLabel;
    public Label incomeLabel;
    public Label expenseLabel;
    public Label transactionCountLabel;
    public Label statusLabel;

    public ComboBox<String> typeComboBox;
    public TextField amountField;
    public ComboBox<String> categoryComboBox;
    public TextField descriptionField;
    public DatePicker datePicker;
    public Button addButton;

    public TableView<Transaction> transactionTable;
    public TableColumn<Transaction, String> dateColumn;
    public TableColumn<Transaction, String> typeColumn;
    public TableColumn<Transaction, String> categoryColumn;
    public TableColumn<Transaction, String> descriptionColumn;
    public TableColumn<Transaction, Number> amountColumn;

    private final FirebaseAuthService authService = new FirebaseAuthService();
    private final FirestoreUserRepository userRepository = new FirestoreUserRepository();
    private final FirestoreTransactionRepository transactionRepository = new FirestoreTransactionRepository();
    private final ObservableList<Transaction> transactions = FXCollections.observableArrayList();
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    public void initialize() {
        setupForm();
        setupTable();

        AuthSession session = authService.getCurrentSession();
        if (session == null) {
            userLabel.setText("Not signed in");
            profileLabel.setText("Please log in again.");
            addButton.setDisable(true);
            return;
        }

        userLabel.setText("Signed in as " + session.getEmail());
        profileLabel.setText("Loading profile...");
        loadProfile(session);
        loadTransactions(session);
    }

    private void setupForm() {
        typeComboBox.setItems(FXCollections.observableArrayList("INCOME", "EXPENSE"));
        typeComboBox.setValue("EXPENSE");
        categoryComboBox.setItems(FXCollections.observableArrayList(
                "Food", "Transport", "Shopping", "Bills", "Education", "Health", "Salary", "Business", "Other"));
        categoryComboBox.setValue("Other");
        datePicker.setValue(LocalDate.now());
    }

    private void setupTable() {
        dateColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getDate() == null ? "" : cell.getValue().getDate().toString()));
        typeColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getType() == null ? "" : cell.getValue().getType().name()));
        categoryColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getCategory()));
        descriptionColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getDescription()));
        amountColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleDoubleProperty(cell.getValue().getAmount()));
        amountColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : currency.format(item.doubleValue()));
            }
        });
        transactionTable.setItems(transactions);
    }

    private void loadProfile(AuthSession session) {
        userRepository.getUserName(session)
                .thenAccept(name -> Platform.runLater(() -> profileLabel.setText("Welcome, " + name + "!")))
                .exceptionally(error -> {
                    Platform.runLater(() -> profileLabel.setText("Welcome!"));
                    return null;
                });
    }

    private void loadTransactions(AuthSession session) {
        statusLabel.setText("Loading transactions...");
        transactionRepository.getTransactions(session)
                .thenAccept(result -> Platform.runLater(() -> {
                    transactions.setAll(result);
                    updateSummary();
                    statusLabel.setText(result.isEmpty() ? "No transactions yet." : "Transactions loaded.");
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> statusLabel.setText("Could not load transactions."));
                    return null;
                });
    }

    public void handleAddTransaction(ActionEvent event) {
        AuthSession session = authService.getCurrentSession();
        if (session == null) {
            statusLabel.setText("Please log in again.");
            return;
        }

        String amountText = amountField.getText().trim();
        double amount;
        try {
            amount = Double.parseDouble(amountText);
        } catch (NumberFormatException e) {
            statusLabel.setText("Enter a valid amount.");
            amountField.requestFocus();
            return;
        }
        if (amount <= 0) {
            statusLabel.setText("Amount must be greater than zero.");
            amountField.requestFocus();
            return;
        }
        if (datePicker.getValue() == null) {
            statusLabel.setText("Select a date.");
            return;
        }

        Transaction transaction = new Transaction(
                Transaction.Type.valueOf(typeComboBox.getValue()),
                amount,
                categoryComboBox.getValue(),
                descriptionField.getText().trim(),
                datePicker.getValue());

        addButton.setDisable(true);
        statusLabel.setText("Saving transaction...");
        transactionRepository.addTransaction(session, transaction)
                .thenRun(() -> Platform.runLater(() -> {
                    clearForm();
                    addButton.setDisable(false);
                    statusLabel.setText("Transaction added successfully.");
                    loadTransactions(session);
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        addButton.setDisable(false);
                        statusLabel.setText("Could not save transaction.");
                    });
                    return null;
                });
    }

    private void clearForm() {
        amountField.clear();
        descriptionField.clear();
        typeComboBox.setValue("EXPENSE");
        categoryComboBox.setValue("Other");
        datePicker.setValue(LocalDate.now());
    }

    private void updateSummary() {
        double income = transactions.stream()
                .filter(t -> t.getType() == Transaction.Type.INCOME)
                .mapToDouble(Transaction::getAmount).sum();
        double expense = transactions.stream()
                .filter(t -> t.getType() == Transaction.Type.EXPENSE)
                .mapToDouble(Transaction::getAmount).sum();
        double balance = income - expense;

        incomeLabel.setText(currency.format(income));
        expenseLabel.setText(currency.format(expense));
        balanceLabel.setText(currency.format(balance));
        transactionCountLabel.setText(transactions.size() + " transactions");
    }

    public void handleLogout(ActionEvent event) throws IOException {
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
