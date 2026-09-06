package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.firebase.FirebaseAuthService;
import com.finance.manager.model.Transaction;
import com.finance.manager.repository.FirestoreBudgetRepository;
import com.finance.manager.repository.FirestoreTransactionRepository;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class FinancialReportsController {
    @FXML private ComboBox<String> reportMonthCombo;
    @FXML private Label incomeLabel, expenseLabel, savingsLabel, budgetLabel, remainingLabel;
    @FXML private Label transactionCountLabel, averageExpenseLabel, largestExpenseLabel, topCategoryLabel, statusLabel;
    @FXML private BarChart<String, Number> categoryChart;
    @FXML private TableView<Transaction> reportTable;
    @FXML private TableColumn<Transaction, LocalDate> dateColumn;
    @FXML private TableColumn<Transaction, String> typeColumn;
    @FXML private TableColumn<Transaction, String> categoryColumn;
    @FXML private TableColumn<Transaction, String> descriptionColumn;
    @FXML private TableColumn<Transaction, Number> amountColumn;

    private final FirebaseAuthService authService = new FirebaseAuthService();
    private final FirestoreTransactionRepository transactionRepository = new FirestoreTransactionRepository();
    private final FirestoreBudgetRepository budgetRepository = new FirestoreBudgetRepository();
    private final List<Transaction> transactions = new ArrayList<>();
    private final DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private double currentBudget;

    @FXML private void initialize() {
        setupTable();
        reportMonthCombo.setOnAction(e -> refreshReport());
        AuthSession session = authService.getCurrentSession();
        if (session == null) {
            statusLabel.setText("Please log in again to view reports.");
            return;
        }
        statusLabel.setText("Loading report data...");
        transactionRepository.getTransactions(session).thenAccept(list -> Platform.runLater(() -> {
            transactions.clear();
            transactions.addAll(list);
            populateMonths();
            loadBudget(session);
            refreshReport();
        })).exceptionally(error -> { Platform.runLater(() -> statusLabel.setText("Could not load report data.")); return null; });
    }

    private void setupTable() {
        dateColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getDate()));
        typeColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getType() == null ? "" : c.getValue().getType().name()));
        categoryColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(value(c.getValue().getCategory(), "Other")));
        descriptionColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(value(c.getValue().getDescription(), "—")));
        amountColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleDoubleProperty(c.getValue().getAmount()));
        amountColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else { Transaction t = getTableView().getItems().get(getIndex()); setText((t.getType() == Transaction.Type.INCOME ? "+ " : "- ") + money(item.doubleValue())); }
            }
        });
        reportTable.setPlaceholder(new Label("No transactions recorded for this month."));
    }

    private void populateMonths() {
        Set<YearMonth> months = transactions.stream().filter(t -> t.getDate() != null).map(t -> YearMonth.from(t.getDate())).collect(Collectors.toCollection(TreeSet::new));
        months.add(YearMonth.now());
        List<String> labels = months.stream().sorted(Comparator.reverseOrder()).map(monthFormatter::format).toList();
        reportMonthCombo.setItems(FXCollections.observableArrayList(labels));
        reportMonthCombo.getSelectionModel().select(monthFormatter.format(YearMonth.now()));
    }

    private void loadBudget(AuthSession session) {
        budgetRepository.getMonthlyBudget(session, YearMonth.now()).thenAccept(amount -> Platform.runLater(() -> { currentBudget = amount; refreshReport(); })).exceptionally(error -> null);
    }

    private YearMonth selectedMonth() {
        try { return YearMonth.parse(reportMonthCombo.getValue(), monthFormatter); }
        catch (Exception e) { return YearMonth.now(); }
    }

    private void refreshReport() {
        if (reportMonthCombo == null || reportMonthCombo.getValue() == null) return;
        YearMonth month = selectedMonth();
        List<Transaction> selected = transactions.stream().filter(t -> t.getDate() != null && YearMonth.from(t.getDate()).equals(month)).sorted(Comparator.comparing(Transaction::getDate).reversed()).toList();
        double income = selected.stream().filter(t -> t.getType() == Transaction.Type.INCOME).mapToDouble(Transaction::getAmount).sum();
        double expense = selected.stream().filter(t -> t.getType() == Transaction.Type.EXPENSE).mapToDouble(Transaction::getAmount).sum();
        double savings = income - expense;
        double remaining = currentBudget - expense;
        incomeLabel.setText(money(income)); expenseLabel.setText(money(expense)); savingsLabel.setText(money(savings)); budgetLabel.setText(money(currentBudget)); remainingLabel.setText(money(remaining));
        transactionCountLabel.setText(String.valueOf(selected.size()));
        long expenseCount = selected.stream().filter(t -> t.getType() == Transaction.Type.EXPENSE).count();
        averageExpenseLabel.setText(money(expenseCount == 0 ? 0 : expense / expenseCount));
        Transaction largest = selected.stream().filter(t -> t.getType() == Transaction.Type.EXPENSE).max(Comparator.comparingDouble(Transaction::getAmount)).orElse(null);
        largestExpenseLabel.setText(largest == null ? "—" : money(largest.getAmount()));
        Map<String, Double> categories = selected.stream().filter(t -> t.getType() == Transaction.Type.EXPENSE).collect(Collectors.groupingBy(t -> value(t.getCategory(), "Other"), LinkedHashMap::new, Collectors.summingDouble(Transaction::getAmount)));
        topCategoryLabel.setText(categories.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("—"));
        reportTable.setItems(FXCollections.observableArrayList(selected));
        categoryChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Expense");
        categories.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed()).forEach(e -> series.getData().add(new XYChart.Data<>(e.getKey(), e.getValue())));
        categoryChart.getData().add(series);
        statusLabel.setText(selected.isEmpty() ? "No transactions found for " + monthFormatter.format(month) + "." : "Report updated for " + monthFormatter.format(month) + ".");
    }

    private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String money(double amount) { return String.format(Locale.US, "₹%,.2f", amount); }
}
