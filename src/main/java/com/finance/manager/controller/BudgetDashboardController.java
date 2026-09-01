package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.model.Transaction;
import com.finance.manager.repository.FirestoreBudgetRepository;
import com.finance.manager.repository.FirestoreTransactionRepository;
import com.finance.manager.service.FirebaseAuthService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Dashboard controller extension for the monthly-budget UI, monthly report and sidebar navigation.
 */
public class BudgetDashboardController extends DashboardController {

    @FXML private ComboBox<String> reportMonthCombo;
    @FXML private Label reportIncomeLabel;
    @FXML private Label reportExpenseLabel;
    @FXML private Label reportSavingsLabel;
    @FXML private Label reportBudgetLabel;
    @FXML private Label reportRemainingLabel;
    @FXML private PieChart reportExpenseChart;

    @FXML private ScrollPane dashboardScrollPane;
    @FXML private Node dashboardSection;
    @FXML private Node analyticsSection;
    @FXML private Node notificationsSection;
    @FXML private Node reportsSection;
    @FXML private Node goalsSection;
    @FXML private Node budgetSection;
    @FXML private Node addTransactionSection;
    @FXML private Node transactionsSection;

    private final FirebaseAuthService reportAuthService = new FirebaseAuthService();
    private final FirestoreTransactionRepository reportTransactionRepository = new FirestoreTransactionRepository();
    private final FirestoreBudgetRepository reportBudgetRepository = new FirestoreBudgetRepository();
    private final ObservableList<Transaction> reportTransactions = FXCollections.observableArrayList();
    private final Map<String, YearMonth> reportMonths = new LinkedHashMap<>();

    @FXML
    private void initialize() {
        try {
            Method parentInitialize = DashboardController.class.getDeclaredMethod("initialize");
            parentInitialize.setAccessible(true);
            parentInitialize.invoke(this);
            setupMonthlyReport();
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize dashboard.", e);
        }
    }

    private void setupMonthlyReport() {
        YearMonth current = YearMonth.now();
        for (int i = 0; i < 12; i++) {
            YearMonth month = current.minusMonths(i);
            String label = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    + " " + month.getYear();
            reportMonths.put(label, month);
        }
        reportMonthCombo.setItems(FXCollections.observableArrayList(reportMonths.keySet()));
        String currentLabel = reportMonths.keySet().iterator().next();
        reportMonthCombo.setValue(currentLabel);
        reportMonthCombo.valueProperty().addListener((obs, oldValue, newValue) -> refreshMonthlyReport());

        AuthSession session = reportAuthService.getCurrentSession();
        if (session == null) return;

        reportTransactionRepository.getTransactions(session)
                .thenAccept(list -> Platform.runLater(() -> {
                    reportTransactions.setAll(list);
                    refreshMonthlyReport();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> setReportError("Could not load monthly report."));
                    return null;
                });
    }

    private void refreshMonthlyReport() {
        YearMonth month = reportMonths.get(reportMonthCombo.getValue());
        if (month == null) return;

        double income = reportTransactions.stream()
                .filter(t -> isMonth(t, month) && t.getType() == Transaction.Type.INCOME)
                .mapToDouble(Transaction::getAmount)
                .sum();
        double expense = reportTransactions.stream()
                .filter(t -> isMonth(t, month) && t.getType() == Transaction.Type.EXPENSE)
                .mapToDouble(Transaction::getAmount)
                .sum();
        double savings = income - expense;

        reportIncomeLabel.setText(formatMoney(income));
        reportExpenseLabel.setText(formatMoney(expense));
        reportSavingsLabel.setText(formatMoney(savings));

        Map<String, Double> categoryTotals = new LinkedHashMap<>();
        reportTransactions.stream()
                .filter(t -> isMonth(t, month) && t.getType() == Transaction.Type.EXPENSE)
                .forEach(t -> {
                    String category = t.getCategory() == null || t.getCategory().isBlank()
                            ? "Other" : t.getCategory();
                    categoryTotals.merge(category, t.getAmount(), Double::sum);
                });

        ObservableList<PieChart.Data> chartData = FXCollections.observableArrayList();
        categoryTotals.forEach((category, amount) -> chartData.add(new PieChart.Data(category, amount)));
        reportExpenseChart.setData(chartData);
        reportExpenseChart.setTitle("Expense by Category — " + month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));

        AuthSession session = reportAuthService.getCurrentSession();
        if (session == null) {
            reportBudgetLabel.setText(formatMoney(0));
            reportRemainingLabel.setText(formatMoney(savings));
            return;
        }

        reportBudgetLabel.setText("Loading...");
        reportBudgetRepository.getMonthlyBudget(session, month)
                .thenAccept(budget -> Platform.runLater(() -> {
                    double remaining = budget - expense;
                    reportBudgetLabel.setText(formatMoney(budget));
                    reportRemainingLabel.setText(formatMoney(remaining));
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        reportBudgetLabel.setText(formatMoney(0));
                        reportRemainingLabel.setText(formatMoney(-expense));
                    });
                    return null;
                });
    }

    private boolean isMonth(Transaction transaction, YearMonth month) {
        return transaction.getDate() != null && YearMonth.from(transaction.getDate()).equals(month);
    }

    private void setReportError(String message) {
        reportIncomeLabel.setText("-");
        reportExpenseLabel.setText("-");
        reportSavingsLabel.setText("-");
        reportBudgetLabel.setText("-");
        reportRemainingLabel.setText("-");
        reportExpenseChart.setData(FXCollections.observableArrayList());
        try {
            field("statusLabel", Label.class).setText(message);
        } catch (Exception ignored) {
        }
    }

    // ---------- Sidebar navigation ----------

    @FXML
    private void handleDashboardNav() {
        scrollTo(dashboardSection);
    }

    @FXML
    private void handleAnalyticsNav() {
        scrollTo(analyticsSection);
    }

    @FXML
    private void handleNotificationsNav() {
        scrollTo(notificationsSection);
    }

    @FXML
    private void handleReportsNav() {
        scrollTo(reportsSection);
    }

    @FXML
    private void handleGoalsNav() {
        scrollTo(goalsSection);
    }

    @FXML
    private void handleBudgetNav() {
        scrollTo(budgetSection);
    }

    @FXML
    private void handleAddTransactionNav() {
        scrollTo(addTransactionSection);
    }

    @FXML
    private void handleTransactionsNav() {
        scrollTo(transactionsSection);
    }

    private void scrollTo(Node target) {
        if (dashboardScrollPane == null || target == null) return;

        Platform.runLater(() -> {
            Node content = dashboardScrollPane.getContent();
            if (content == null) return;

            double contentHeight = content.getBoundsInLocal().getHeight();
            double viewportHeight = dashboardScrollPane.getViewportBounds().getHeight();
            double maxScroll = contentHeight - viewportHeight;

            if (maxScroll <= 0) {
                dashboardScrollPane.setVvalue(0);
                return;
            }

            double targetY = target.localToScene(target.getBoundsInLocal()).getMinY();
            double contentY = content.localToScene(content.getBoundsInLocal()).getMinY();
            double relativeY = Math.max(0, targetY - contentY - 12);
            dashboardScrollPane.setVvalue(Math.min(1, relativeY / maxScroll));
        });
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

            if (amount < 0) throw new NumberFormatException();

            setPrivateDouble("monthlyBudget", amount);
            invokeParent("updateBudgetProgress");
            saveButton.setDisable(true);
            statusLabel.setText("Saving monthly budget...");

            AuthSession session = reportAuthService.getCurrentSession();
            if (session == null) {
                saveButton.setDisable(false);
                statusLabel.setText("Please log in again.");
                return;
            }

            repository.saveMonthlyBudget(session, YearMonth.now(), amount)
                    .thenRun(() -> Platform.runLater(() -> {
                        saveButton.setDisable(false);
                        refreshMonthlyReport();
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

    private String formatMoney(double value) {
        return String.format(Locale.US, "₹ %.2f", value);
    }
}
