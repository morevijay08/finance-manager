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
import java.util.List;

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
    @FXML private TableView<Transaction> transactionTable;
    @FXML private TableColumn<Transaction, String> typeColumn;
    @FXML private TableColumn<Transaction, Number> amountColumn;
    @FXML private TableColumn<Transaction, String> categoryColumn;
    @FXML private TableColumn<Transaction, String> descriptionColumn;
    @FXML private TableColumn<Transaction, LocalDate> dateColumn;

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
        transactionTable.setItems(transactions);

        emailLabel.setText(session.getEmail());
        loadProfileAndTransactions(session);
    }

    private void loadProfileAndTransactions(AuthSession session) {
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
            String description = descriptionField.getText().trim();
            String category = categoryCombo.getValue();
            LocalDate date = datePicker.getValue();
            Transaction.Type type = Transaction.Type.valueOf(typeCombo.getValue());

            Transaction transaction = new Transaction(null, type, amount, category, description, date);
            statusLabel.setText("Saving transaction...");

            transactionRepository.addTransaction(session, transaction).thenAccept(saved -> Platform.runLater(() -> {
                transactions.add(0, saved);
                updateSummary();
                amountField.clear();
                descriptionField.clear();
                datePicker.setValue(LocalDate.now());
                statusLabel.setText("Transaction added successfully.");
            })).exceptionally(error -> {
                Platform.runLater(() -> statusLabel.setText("Could not save transaction."));
                return null;
            });
        } catch (NumberFormatException e) {
            statusLabel.setText("Enter a valid amount greater than 0.");
        }
    }

    private void updateSummary() {
        double income = transactions.stream().filter(t -> t.getType() == Transaction.Type.INCOME)
                .mapToDouble(Transaction::getAmount).sum();
        double expense = transactions.stream().filter(t -> t.getType() == Transaction.Type.EXPENSE)
                .mapToDouble(Transaction::getAmount).sum();
        double balance = income - expense;

        incomeLabel.setText(formatMoney(income));
        expenseLabel.setText(formatMoney(expense));
        balanceLabel.setText(formatMoney(balance));
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
