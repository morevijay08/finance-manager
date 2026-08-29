package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.model.Transaction;
import com.finance.manager.repository.FirestoreBudgetRepository;
import com.finance.manager.repository.FirestoreTransactionRepository;
import com.finance.manager.service.FirebaseAuthService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class DashboardController {

    @FXML private Label welcomeLabel;
    @FXML private Label emailLabel;
    @FXML private Label balanceLabel;
    @FXML private Label incomeLabel;
    @FXML private Label expenseLabel;
    @FXML private Label statusLabel;
    @FXML private Label budgetMonthLabel;
    @FXML private Label budgetSpentLabel;
    @FXML private Label budgetRemainingLabel;
    @FXML private TextField budgetField;
    @FXML private ProgressBar budgetProgressBar;
    @FXML private Button saveBudgetButton;
    @FXML private ComboBox<String> typeCombo;
    @FXML private TextField amountField;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private TextField descriptionField;
    @FXML private DatePicker datePicker;
    @FXML private Button addButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterTypeCombo;
    @FXML private ComboBox<String> filterCategoryCombo;
    @FXML private BarChart<String, Number> monthlyChart;
    @FXML private PieChart expenseChart;
    @FXML private TableView<Transaction> transactionTable;
    @FXML private TableColumn<Transaction, String> typeColumn;
    @FXML private TableColumn<Transaction, Number> amountColumn;
    @FXML private TableColumn<Transaction, String> categoryColumn;
    @FXML private TableColumn<Transaction, String> descriptionColumn;
    @FXML private TableColumn<Transaction, LocalDate> dateColumn;
    @FXML private TableColumn<Transaction, Void> actionColumn;

    private final FirebaseAuthService authService = new FirebaseAuthService();
    private final FirestoreTransactionRepository transactionRepository = new FirestoreTransactionRepository();
    private final FirestoreBudgetRepository budgetRepository = new FirestoreBudgetRepository();
    private final ObservableList<Transaction> transactions = FXCollections.observableArrayList();
    private FilteredList<Transaction> filteredTransactions;
    private Transaction editingTransaction;
    private double monthlyBudget;

    @FXML
    private void initialize() {
        setupForm();
        setupTable();
        setupFilters();
        setupCharts();
        setupBudget();

        AuthSession session = authService.getCurrentSession();
        if (session == null) {
            welcomeLabel.setText("Session expired");
            emailLabel.setText("Please log in again.");
            addButton.setDisable(true);
            saveBudgetButton.setDisable(true);
            return;
        }

        emailLabel.setText(session.getEmail());
        welcomeLabel.setText("Welcome");
        loadTransactions(session);
        loadMonthlyBudget(session);
    }

    private void setupForm() {
        typeCombo.setItems(FXCollections.observableArrayList("INCOME", "EXPENSE"));
        typeCombo.setValue("EXPENSE");
        categoryCombo.setItems(FXCollections.observableArrayList(
                "Food", "Transport", "Shopping", "Bills", "Salary", "Business", "Health", "Education", "Other"));
        categoryCombo.setValue("Other");
        datePicker.setValue(LocalDate.now());
    }

    private void setupBudget() {
        YearMonth month = YearMonth.now();
        budgetMonthLabel.setText(month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear());
        budgetSpentLabel.setText(formatMoney(0));
        budgetRemainingLabel.setText(formatMoney(0));
        budgetProgressBar.setProgress(0);
    }

    private void setupTable() {
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
        filteredTransactions = new FilteredList<>(transactions, transaction -> true);
        transactionTable.setItems(filteredTransactions);
    }

    private void setupFilters() {
        filterTypeCombo.setItems(FXCollections.observableArrayList("ALL", "INCOME", "EXPENSE"));
        filterTypeCombo.setValue("ALL");
        filterCategoryCombo.setItems(FXCollections.observableArrayList(
                "ALL", "Food", "Transport", "Shopping", "Bills", "Salary", "Business", "Health", "Education", "Other"));
        filterCategoryCombo.setValue("ALL");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        filterTypeCombo.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        filterCategoryCombo.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
    }

    private void setupCharts() {
        monthlyChart.setLegendVisible(true);
        monthlyChart.setAnimated(false);
        expenseChart.setLegendVisible(true);
        expenseChart.setAnimated(false);
    }

    private void applyFilters() {
        if (filteredTransactions == null) return;
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String type = filterTypeCombo.getValue();
        String category = filterCategoryCombo.getValue();
        filteredTransactions.setPredicate(transaction -> {
            boolean matchesSearch = search.isEmpty()
                    || contains(transaction.getDescription(), search)
                    || contains(transaction.getCategory(), search)
                    || (transaction.getType() != null && transaction.getType().name().toLowerCase().contains(search));
            boolean matchesType = type == null || "ALL".equals(type)
                    || (transaction.getType() != null && transaction.getType().name().equals(type));
            boolean matchesCategory = category == null || "ALL".equals(category)
                    || category.equals(transaction.getCategory());
            return matchesSearch && matchesType && matchesCategory;
        });
    }

    private boolean contains(String value, String search) {
        return value != null && value.toLowerCase().contains(search);
    }

    private void loadTransactions(AuthSession session) {
        statusLabel.setText("Loading your finance data...");
        transactionRepository.getTransactions(session).thenAccept(list -> Platform.runLater(() -> {
            transactions.setAll(list);
            applyFilters();
            updateSummary();
            updateCharts();
            updateBudgetProgress();
            statusLabel.setText("Ready");
        })).exceptionally(error -> {
            Platform.runLater(() -> statusLabel.setText("Could not load transactions."));
            return null;
        });
    }

    private void loadMonthlyBudget(AuthSession session) {
        YearMonth month = YearMonth.now();
        budgetRepository.getMonthlyBudget(session, month).thenAccept(amount -> Platform.runLater(() -> {
            monthlyBudget = amount;
            budgetField.setText(amount > 0 ? String.format(Locale.US, "%.2f", amount) : "");
            updateBudgetProgress();
        })).exceptionally(error -> {
            Platform.runLater(() -> statusLabel.setText("Could not load monthly budget."));
            return null;
        });
    }

    @FXML
    private void handleSaveBudget() {
        AuthSession session = authService.getCurrentSession();
        if (session == null) {
            statusLabel.setText("Please log in again.");
            return;
        }
        try {
            double amount = Double.parseDouble(budgetField.getText().trim());
            if (amount < 0) throw new NumberFormatException();
            saveBudgetButton.setDisable(true);
            statusLabel.setText("Saving monthly budget...");
            YearMonth month = YearMonth.now();
            budgetRepository.saveMonthlyBudget(session, month, amount).thenRun(() -> Platform.runLater(() -> {
                monthlyBudget = amount;
                updateBudgetProgress();
                saveBudgetButton.setDisable(false);
                statusLabel.setText("Monthly budget saved successfully.");
            })).exceptionally(error -> {
                Platform.runLater(() -> {
                    saveBudgetButton.setDisable(false);
                    statusLabel.setText("Could not save monthly budget.");
                });
                return null;
            });
        } catch (NumberFormatException e) {
            statusLabel.setText("Enter a valid budget amount (0 or greater).");
        }
    }

    private void updateBudgetProgress() {
        if (budgetProgressBar == null) return;
        double spent = transactions.stream()
                .filter(t -> t.getType() == Transaction.Type.EXPENSE)
                .filter(t -> t.getDate() != null && YearMonth.from(t.getDate()).equals(YearMonth.now()))
                .mapToDouble(Transaction::getAmount).sum();
        double remaining = monthlyBudget - spent;
        budgetSpentLabel.setText(formatMoney(spent));
        budgetRemainingLabel.setText(formatMoney(remaining));
        if (monthlyBudget <= 0) {
            budgetProgressBar.setProgress(0);
        } else {
            budgetProgressBar.setProgress(Math.min(spent / monthlyBudget, 1.0));
        }
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
                applyFilters();
                updateSummary();
                updateCharts();
                updateBudgetProgress();
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
        editingTransaction = transaction;
        transactionTable.getSelectionModel().select(transaction);
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
        if (session == null || editingTransaction == null) {
            statusLabel.setText("Select a transaction to edit.");
            resetAddButton();
            return;
        }
        try {
            double amount = Double.parseDouble(amountField.getText().trim());
            LocalDate date = datePicker.getValue();
            if (amount <= 0 || date == null) throw new NumberFormatException();
            editingTransaction.setType(Transaction.Type.valueOf(typeCombo.getValue()));
            editingTransaction.setAmount(amount);
            editingTransaction.setCategory(categoryCombo.getValue());
            editingTransaction.setDescription(descriptionField.getText().trim());
            editingTransaction.setDate(date);
            addButton.setDisable(true);
            statusLabel.setText("Updating transaction...");
            transactionRepository.updateTransaction(session, editingTransaction).thenAccept(updated -> Platform.runLater(() -> {
                transactionTable.refresh();
                updateSummary();
                updateCharts();
                updateBudgetProgress();
                applyFilters();
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
                if (editingTransaction == transaction) resetFormAndButton();
                updateSummary();
                updateCharts();
                updateBudgetProgress();
                applyFilters();
                statusLabel.setText("Transaction deleted successfully.");
            })).exceptionally(error -> {
                Platform.runLater(() -> statusLabel.setText("Could not delete transaction."));
                return null;
            });
        });
    }

    @FXML
    private void handleExportCsv() {
        if (filteredTransactions == null || filteredTransactions.isEmpty()) {
            statusLabel.setText("There are no transactions to export.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Transactions as CSV");
        chooser.setInitialFileName("finance-transactions.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = chooser.showSaveDialog(transactionTable.getScene().getWindow());
        if (file == null) return;
        try {
            StringBuilder csv = new StringBuilder("Type,Amount,Category,Description,Date\n");
            for (Transaction transaction : filteredTransactions) {
                csv.append(csvValue(transaction.getType() == null ? "" : transaction.getType().name())).append(',')
                        .append(transaction.getAmount()).append(',')
                        .append(csvValue(transaction.getCategory())).append(',')
                        .append(csvValue(transaction.getDescription())).append(',')
                        .append(csvValue(transaction.getDate() == null ? "" : transaction.getDate().toString())).append('\n');
            }
            Files.writeString(file.toPath(), csv.toString(), StandardCharsets.UTF_8);
            statusLabel.setText("Exported " + filteredTransactions.size() + " transaction(s) successfully.");
        } catch (IOException e) {
            statusLabel.setText("Could not export transactions.");
        }
    }

    private String csvValue(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void updateSummary() {
        double income = transactions.stream().filter(t -> t.getType() == Transaction.Type.INCOME).mapToDouble(Transaction::getAmount).sum();
        double expense = transactions.stream().filter(t -> t.getType() == Transaction.Type.EXPENSE).mapToDouble(Transaction::getAmount).sum();
        balanceLabel.setText(formatMoney(income - expense));
        incomeLabel.setText(formatMoney(income));
        expenseLabel.setText(formatMoney(expense));
    }

    private void updateCharts() {
        updateMonthlyChart();
        updateExpenseChart();
    }

    private void updateMonthlyChart() {
        int year = LocalDate.now().getYear();
        Map<YearMonth, double[]> totals = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month++) totals.put(YearMonth.of(year, month), new double[]{0.0, 0.0});
        for (Transaction transaction : transactions) {
            if (transaction.getDate() == null || transaction.getDate().getYear() != year) continue;
            double[] values = totals.get(YearMonth.from(transaction.getDate()));
            if (transaction.getType() == Transaction.Type.INCOME) values[0] += transaction.getAmount();
            else if (transaction.getType() == Transaction.Type.EXPENSE) values[1] += transaction.getAmount();
        }
        XYChart.Series<String, Number> incomeSeries = new XYChart.Series<>();
        incomeSeries.setName("Income");
        XYChart.Series<String, Number> expenseSeries = new XYChart.Series<>();
        expenseSeries.setName("Expense");
        for (Map.Entry<YearMonth, double[]> entry : totals.entrySet()) {
            String month = entry.getKey().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            incomeSeries.getData().add(new XYChart.Data<>(month, entry.getValue()[0]));
            expenseSeries.getData().add(new XYChart.Data<>(month, entry.getValue()[1]));
        }
        monthlyChart.getData().setAll(incomeSeries, expenseSeries);
        monthlyChart.setTitle("Monthly Income vs Expense (" + year + ")");
    }

    private void updateExpenseChart() {
        Map<String, Double> categoryTotals = new LinkedHashMap<>();
        for (Transaction transaction : transactions) {
            if (transaction.getType() != Transaction.Type.EXPENSE) continue;
            String category = transaction.getCategory() == null || transaction.getCategory().isBlank() ? "Other" : transaction.getCategory();
            categoryTotals.merge(category, transaction.getAmount(), Double::sum);
        }
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        categoryTotals.forEach((category, amount) -> data.add(new PieChart.Data(category, amount)));
        expenseChart.setData(data);
        expenseChart.setTitle("Expense by Category");
    }

    private void clearForm() {
        amountField.clear();
        descriptionField.clear();
        typeCombo.setValue("EXPENSE");
        categoryCombo.setValue("Other");
        datePicker.setValue(LocalDate.now());
    }

    private void resetFormAndButton() {
        editingTransaction = null;
        clearForm();
        resetAddButton();
    }

    private void resetAddButton() {
        addButton.setText("Add");
        addButton.setOnAction(event -> handleAddTransaction());
        addButton.setDisable(false);
    }

    private String formatMoney(double value) {
        return String.format(Locale.US, "₹ %.2f", value);
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
