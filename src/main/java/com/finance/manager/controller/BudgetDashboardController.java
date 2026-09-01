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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dashboard controller extension for monthly budget, reports and an overview-only dashboard. */
public class BudgetDashboardController extends DashboardController {
    @FXML private ComboBox<String> reportMonthCombo;
    @FXML private Label reportIncomeLabel, reportExpenseLabel, reportSavingsLabel, reportBudgetLabel, reportRemainingLabel;
    @FXML private PieChart reportExpenseChart;
    @FXML private ScrollPane dashboardScrollPane;
    @FXML private Node dashboardSection, analyticsSection, notificationsSection, reportsSection, goalsSection, budgetSection, addTransactionSection, transactionsSection;

    private final FirebaseAuthService reportAuthService = new FirebaseAuthService();
    private final FirestoreTransactionRepository reportTransactionRepository = new FirestoreTransactionRepository();
    private final FirestoreBudgetRepository reportBudgetRepository = new FirestoreBudgetRepository();
    private final ObservableList<Transaction> reportTransactions = FXCollections.observableArrayList();
    private final Map<String, YearMonth> reportMonths = new LinkedHashMap<>();
    private Label dashboardSavingsLabel, dashboardSavingsRateLabel, dashboardHealthLabel, dashboardMonthlyIncomeLabel, dashboardMonthlyExpenseLabel, dashboardMonthlySavingsLabel;
    private VBox recentActivityBox;

    @FXML private void initialize() {
        try {
            Method parentInitialize = DashboardController.class.getDeclaredMethod("initialize");
            parentInitialize.setAccessible(true);
            parentInitialize.invoke(this);
            buildDashboardOverview();
            setupMonthlyReport();
        } catch (Exception e) { throw new RuntimeException("Could not initialize dashboard.", e); }
    }

    private void buildDashboardOverview() {
        if (!(dashboardSection instanceof VBox root)) return;
        root.getChildren().addAll(createDashboardMetrics(), createDashboardSnapshot(), createRecentActivity());
        refreshDashboardOverview();
    }

    private Node createDashboardMetrics() {
        HBox row = new HBox(14);
        row.getStyleClass().add("dashboard-extra-row");
        VBox savings = metricCard("NET SAVINGS", "₹0.00", "Income minus expenses", "dashboard-savings-card");
        dashboardSavingsLabel = (Label) savings.getChildren().get(1);
        VBox rate = metricCard("SAVINGS RATE", "0%", "Your saving efficiency", "dashboard-rate-card");
        dashboardSavingsRateLabel = (Label) rate.getChildren().get(1);
        VBox health = metricCard("FINANCIAL HEALTH", "● Good", "Based on this month's cash flow", "dashboard-health-card");
        dashboardHealthLabel = (Label) health.getChildren().get(1);
        row.getChildren().addAll(savings, rate, health);
        for (Node n : row.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        return row;
    }

    private VBox metricCard(String title, String value, String caption, String style) {
        VBox card = new VBox(6);
        card.getStyleClass().addAll("summary-card", style);
        Label t = new Label(title); t.getStyleClass().add("card-title");
        Label v = new Label(value); v.getStyleClass().add("dashboard-extra-value");
        Label c = new Label(caption); c.getStyleClass().add("card-caption");
        card.getChildren().addAll(t, v, c);
        return card;
    }

    private Node createDashboardSnapshot() {
        HBox row = new HBox(14);
        row.getStyleClass().add("dashboard-extra-row");
        VBox cash = new VBox(10); cash.getStyleClass().addAll("summary-card", "dashboard-cashflow-card");
        Label title = new Label("Monthly Cash Flow"); title.getStyleClass().add("section-title");
        Label hint = new Label("A simple view of your current month's money movement."); hint.getStyleClass().add("subtitle");
        dashboardMonthlyIncomeLabel = snapshotLine(cash, "Income", "₹0.00", "dashboard-income-text");
        dashboardMonthlyExpenseLabel = snapshotLine(cash, "Expense", "₹0.00", "dashboard-expense-text");
        dashboardMonthlySavingsLabel = snapshotLine(cash, "Savings", "₹0.00", "dashboard-savings-text");
        cash.getChildren().add(0, hint); cash.getChildren().add(0, title);

        VBox actions = new VBox(10); actions.getStyleClass().addAll("summary-card", "dashboard-health-panel");
        Label healthTitle = new Label("Financial Health"); healthTitle.getStyleClass().add("section-title");
        Label healthHint = new Label("Your dashboard gives you a quick answer: are you earning more than you spend?"); healthHint.getStyleClass().add("subtitle"); healthHint.setWrapText(true);
        Button add = new Button("＋ Add Transaction"); add.setOnAction(e -> handleAddTransactionNav()); add.getStyleClass().add("primary-button");
        Button tx = new Button("View Recent Activity  →"); tx.setOnAction(e -> handleTransactionsNav()); tx.getStyleClass().add("secondary-button");
        actions.getChildren().addAll(healthTitle, healthHint, add, tx);
        row.getChildren().addAll(cash, actions); HBox.setHgrow(cash, Priority.ALWAYS); HBox.setHgrow(actions, Priority.ALWAYS);
        return row;
    }

    private Label snapshotLine(VBox box, String name, String value, String style) {
        HBox line = new HBox(8); Label n = new Label(name); n.getStyleClass().add("card-caption"); Label spacer = new Label(); HBox.setHgrow(spacer, Priority.ALWAYS); Label v = new Label(value); v.getStyleClass().add(style); line.getChildren().addAll(n, spacer, v); box.getChildren().add(line); return v;
    }

    private Node createRecentActivity() {
        VBox card = new VBox(10); card.getStyleClass().addAll("summary-card", "dashboard-activity-card");
        HBox heading = new HBox(); Label title = new Label("Recent Activity"); title.getStyleClass().add("section-title"); Label spacer = new Label(); HBox.setHgrow(spacer, Priority.ALWAYS); Button view = new Button("View all  →"); view.setOnAction(e -> handleTransactionsNav()); view.getStyleClass().add("text-action-button"); heading.getChildren().addAll(title, spacer, view);
        recentActivityBox = new VBox(6); card.getChildren().addAll(heading, recentActivityBox); return card;
    }

    private void refreshDashboardOverview() {
        try {
            @SuppressWarnings("unchecked") ObservableList<Transaction> list = (ObservableList<Transaction>) getPrivateField("transactions");
            if (list == null) return;
            double income = list.stream().filter(t -> t.getType() == Transaction.Type.INCOME).mapToDouble(Transaction::getAmount).sum();
            double expense = list.stream().filter(t -> t.getType() == Transaction.Type.EXPENSE).mapToDouble(Transaction::getAmount).sum();
            double savings = income - expense;
            YearMonth month = YearMonth.now();
            double monthIncome = list.stream().filter(t -> isMonth(t, month) && t.getType() == Transaction.Type.INCOME).mapToDouble(Transaction::getAmount).sum();
            double monthExpense = list.stream().filter(t -> isMonth(t, month) && t.getType() == Transaction.Type.EXPENSE).mapToDouble(Transaction::getAmount).sum();
            double monthSavings = monthIncome - monthExpense;
            double rate = monthIncome > 0 ? (monthSavings / monthIncome) * 100 : 0;
            if (dashboardSavingsLabel != null) dashboardSavingsLabel.setText(formatMoney(savings));
            if (dashboardSavingsRateLabel != null) dashboardSavingsRateLabel.setText(String.format(Locale.US, "%.1f%%", rate));
            if (dashboardMonthlyIncomeLabel != null) dashboardMonthlyIncomeLabel.setText(formatMoney(monthIncome));
            if (dashboardMonthlyExpenseLabel != null) dashboardMonthlyExpenseLabel.setText(formatMoney(monthExpense));
            if (dashboardMonthlySavingsLabel != null) dashboardMonthlySavingsLabel.setText(formatMoney(monthSavings));
            if (dashboardHealthLabel != null) dashboardHealthLabel.setText(monthIncome == 0 && monthExpense == 0 ? "● Ready" : monthSavings >= 0 ? "● Good" : "● Needs attention");
            if (recentActivityBox != null) {
                recentActivityBox.getChildren().clear();
                List<Transaction> recent = list.stream().sorted((a,b) -> {
                    if (a.getDate() == null) return 1; if (b.getDate() == null) return -1; return b.getDate().compareTo(a.getDate());
                }).limit(3).toList();
                if (recent.isEmpty()) { Label empty = new Label("No transactions yet. Add your first transaction to see activity here."); empty.getStyleClass().add("subtitle"); recentActivityBox.getChildren().add(empty); }
                else for (Transaction t : recent) addActivityRow(t);
            }
        } catch (Exception ignored) { }
    }

    private void addActivityRow(Transaction t) {
        HBox row = new HBox(10); row.getStyleClass().add("dashboard-activity-row");
        Label type = new Label(t.getType() == Transaction.Type.INCOME ? "↑" : "↓"); type.getStyleClass().add(t.getType() == Transaction.Type.INCOME ? "dashboard-income-icon" : "dashboard-expense-icon");
        VBox info = new VBox(2); HBox.setHgrow(info, Priority.ALWAYS); Label desc = new Label(t.getDescription() == null || t.getDescription().isBlank() ? (t.getCategory() == null ? "Transaction" : t.getCategory()) : t.getDescription()); desc.getStyleClass().add("activity-title"); Label date = new Label(t.getDate() == null ? "" : t.getDate().toString()); date.getStyleClass().add("card-caption"); info.getChildren().addAll(desc, date);
        Label amount = new Label((t.getType() == Transaction.Type.INCOME ? "+ " : "- ") + formatMoney(t.getAmount())); amount.getStyleClass().add(t.getType() == Transaction.Type.INCOME ? "income-value-small" : "expense-value-small"); row.getChildren().addAll(type, info, amount); recentActivityBox.getChildren().add(row);
    }

    private Object getPrivateField(String name) throws Exception { Field f = DashboardController.class.getDeclaredField(name); f.setAccessible(true); return f.get(this); }
    private boolean isMonth(Transaction transaction, YearMonth month) { return transaction.getDate() != null && YearMonth.from(transaction.getDate()).equals(month); }

    private void setupMonthlyReport() {
        YearMonth current = YearMonth.now();
        for (int i = 0; i < 12; i++) { YearMonth month = current.minusMonths(i); reportMonths.put(month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear(), month); }
        reportMonthCombo.setItems(FXCollections.observableArrayList(reportMonths.keySet())); reportMonthCombo.setValue(reportMonths.keySet().iterator().next()); reportMonthCombo.valueProperty().addListener((obs,o,n) -> refreshMonthlyReport());
        AuthSession session = reportAuthService.getCurrentSession(); if (session == null) return;
        reportTransactionRepository.getTransactions(session).thenAccept(list -> Platform.runLater(() -> { reportTransactions.setAll(list); refreshMonthlyReport(); refreshDashboardOverview(); })).exceptionally(error -> { Platform.runLater(() -> setReportError("Could not load monthly report.")); return null; });
    }

    private void refreshMonthlyReport() {
        YearMonth month = reportMonths.get(reportMonthCombo.getValue()); if (month == null) return;
        double income = reportTransactions.stream().filter(t -> isMonth(t, month) && t.getType() == Transaction.Type.INCOME).mapToDouble(Transaction::getAmount).sum();
        double expense = reportTransactions.stream().filter(t -> isMonth(t, month) && t.getType() == Transaction.Type.EXPENSE).mapToDouble(Transaction::getAmount).sum();
        double savings = income - expense; reportIncomeLabel.setText(formatMoney(income)); reportExpenseLabel.setText(formatMoney(expense)); reportSavingsLabel.setText(formatMoney(savings));
        Map<String, Double> categoryTotals = new LinkedHashMap<>(); reportTransactions.stream().filter(t -> isMonth(t, month) && t.getType() == Transaction.Type.EXPENSE).forEach(t -> categoryTotals.merge(t.getCategory() == null || t.getCategory().isBlank() ? "Other" : t.getCategory(), t.getAmount(), Double::sum));
        ObservableList<PieChart.Data> chartData = FXCollections.observableArrayList(); categoryTotals.forEach((category, amount) -> chartData.add(new PieChart.Data(category, amount))); reportExpenseChart.setData(chartData); reportExpenseChart.setTitle("Expense by Category — " + month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
        AuthSession session = reportAuthService.getCurrentSession(); if (session == null) { reportBudgetLabel.setText(formatMoney(0)); reportRemainingLabel.setText(formatMoney(savings)); return; }
        reportBudgetLabel.setText("Loading..."); reportBudgetRepository.getMonthlyBudget(session, month).thenAccept(budget -> Platform.runLater(() -> { reportBudgetLabel.setText(formatMoney(budget)); reportRemainingLabel.setText(formatMoney(budget - expense)); })).exceptionally(error -> { Platform.runLater(() -> { reportBudgetLabel.setText(formatMoney(0)); reportRemainingLabel.setText(formatMoney(-expense)); }); return null; });
    }

    private void setReportError(String message) { reportIncomeLabel.setText("-"); reportExpenseLabel.setText("-"); reportSavingsLabel.setText("-"); reportBudgetLabel.setText("-"); reportRemainingLabel.setText("-"); reportExpenseChart.setData(FXCollections.observableArrayList()); }

    @FXML private void handleDashboardNav() { scrollTo(dashboardSection); }
    @FXML private void handleAnalyticsNav() { scrollTo(analyticsSection); }
    @FXML private void handleNotificationsNav() { scrollTo(notificationsSection); }
    @FXML private void handleReportsNav() { scrollTo(reportsSection); }
    @FXML private void handleGoalsNav() { scrollTo(goalsSection); }
    @FXML private void handleBudgetNav() { scrollTo(budgetSection); }
    @FXML private void handleAddTransactionNav() { scrollTo(addTransactionSection); }
    @FXML private void handleTransactionsNav() { scrollTo(transactionsSection); }

    private void scrollTo(Node target) { if (dashboardScrollPane == null || target == null) return; Platform.runLater(() -> { Node content = dashboardScrollPane.getContent(); if (content == null) return; double max = content.getBoundsInLocal().getHeight() - dashboardScrollPane.getViewportBounds().getHeight(); if (max <= 0) { dashboardScrollPane.setVvalue(0); return; } double targetY = target.localToScene(target.getBoundsInLocal()).getMinY(); double contentY = content.localToScene(content.getBoundsInLocal()).getMinY(); dashboardScrollPane.setVvalue(Math.min(1, Math.max(0, (targetY - contentY - 12) / max))); }); }

    @FXML private void handleLogout() { invokeParent("handleLogout"); }
    @FXML private void handleAddTransaction() { invokeParent("handleAddTransaction"); }
    @FXML private void handleExportCsv() { invokeParent("handleExportCsv"); }

    @FXML private void handleSaveBudget() {
        try {
            TextField budgetField = field("budgetField", TextField.class); Label statusLabel = field("statusLabel", Label.class); Button saveButton = field("saveBudgetButton", Button.class); FirestoreBudgetRepository repository = field("budgetRepository", FirestoreBudgetRepository.class); double amount = Double.parseDouble(budgetField.getText().trim()); if (amount < 0) throw new NumberFormatException();
            setPrivateDouble("monthlyBudget", amount); invokeParent("updateBudgetProgress"); saveButton.setDisable(true); statusLabel.setText("Saving monthly budget..."); AuthSession session = reportAuthService.getCurrentSession(); if (session == null) { saveButton.setDisable(false); statusLabel.setText("Please log in again."); return; }
            repository.saveMonthlyBudget(session, YearMonth.now(), amount).thenRun(() -> Platform.runLater(() -> { saveButton.setDisable(false); refreshMonthlyReport(); statusLabel.setText("Monthly budget saved successfully."); })).exceptionally(error -> { Platform.runLater(() -> { saveButton.setDisable(false); statusLabel.setText("Could not save monthly budget."); }); return null; });
        } catch (NumberFormatException e) { try { field("statusLabel", Label.class).setText("Enter a valid budget amount (0 or greater)."); } catch (Exception ignored) { } } catch (Exception e) { throw new RuntimeException("Could not save monthly budget.", e); }
    }

    private void invokeParent(String methodName) { try { Method method = DashboardController.class.getDeclaredMethod(methodName); method.setAccessible(true); method.invoke(this); } catch (Exception e) { throw new RuntimeException("Could not invoke dashboard action: " + methodName, e); } }
    @SuppressWarnings("unchecked") private <T> T field(String name, Class<T> type) throws Exception { Field field = DashboardController.class.getDeclaredField(name); field.setAccessible(true); return (T) field.get(this); }
    private void setPrivateDouble(String name, double value) throws Exception { Field field = DashboardController.class.getDeclaredField(name); field.setAccessible(true); field.setDouble(this, value); }
    private String formatMoney(double value) { return String.format(Locale.US, "₹ %.2f", value); }
}
