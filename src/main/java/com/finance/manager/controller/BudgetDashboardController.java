package com.finance.manager.controller;

import com.finance.manager.model.Transaction;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;

/** Dashboard-only presentation using the live transaction list loaded by DashboardController. */
public class BudgetDashboardController extends DashboardController {
    @FXML private Node dashboardSection;
    @FXML private Node analyticsSection, notificationsSection, reportsSection, goalsSection, budgetSection, addTransactionSection, transactionsSection;

    private Label dashboardSavingsLabel, dashboardSavingsRateLabel, dashboardHealthLabel;
    private Label dashboardMonthlyIncomeLabel, dashboardMonthlyExpenseLabel, dashboardMonthlySavingsLabel;
    private VBox recentActivityBox;
    private ObservableList<Transaction> liveTransactions;

    @FXML
    private void initialize() {
        invokeParent("initialize");
        buildDashboardOverview();
        connectToLiveTransactions();
        refreshDashboardOverview();
        show(dashboardSection);
    }

    @SuppressWarnings("unchecked")
    private void connectToLiveTransactions() {
        try {
            Field field = DashboardController.class.getDeclaredField("transactions");
            field.setAccessible(true);
            liveTransactions = (ObservableList<Transaction>) field.get(this);
            liveTransactions.addListener((javafx.collections.ListChangeListener<Transaction>) change -> refreshDashboardOverview());
        } catch (Exception e) {
            liveTransactions = null;
        }
    }

    private void buildDashboardOverview() {
        if (!(dashboardSection instanceof VBox root)) return;
        root.getChildren().addAll(createMetrics(), createCashFlow(), createRecentActivity());
    }

    private Node createMetrics() {
        HBox row = new HBox(14);
        row.getStyleClass().add("dashboard-extra-row");
        VBox savings = metricCard("NET SAVINGS", "₹0.00", "Total income minus total expense", "dashboard-savings-card");
        VBox rate = metricCard("SAVINGS RATE", "0.0%", "This month's saving efficiency", "dashboard-rate-card");
        VBox health = metricCard("FINANCIAL HEALTH", "● Ready", "Based on your current cash flow", "dashboard-health-card");
        dashboardSavingsLabel = valueLabel(savings);
        dashboardSavingsRateLabel = valueLabel(rate);
        dashboardHealthLabel = valueLabel(health);
        row.getChildren().addAll(savings, rate, health);
        for (Node node : row.getChildren()) HBox.setHgrow(node, Priority.ALWAYS);
        return row;
    }

    private VBox metricCard(String title, String value, String caption, String style) {
        VBox card = new VBox(6);
        card.getStyleClass().addAll("summary-card", style);
        Label titleLabel = new Label(title); titleLabel.getStyleClass().add("card-title");
        Label valueLabel = new Label(value); valueLabel.getStyleClass().add("dashboard-extra-value");
        Label captionLabel = new Label(caption); captionLabel.getStyleClass().add("card-caption");
        card.getChildren().addAll(titleLabel, valueLabel, captionLabel);
        return card;
    }

    private Label valueLabel(VBox card) { return (Label) card.getChildren().get(1); }

    private Node createCashFlow() {
        HBox row = new HBox(14);
        VBox cash = new VBox(10); cash.getStyleClass().addAll("summary-card", "dashboard-cashflow-card");
        Label title = new Label("This Month at a Glance"); title.getStyleClass().add("section-title");
        Label hint = new Label("A simple snapshot of money received, spent and saved."); hint.getStyleClass().add("subtitle"); hint.setWrapText(true);
        dashboardMonthlyIncomeLabel = snapshotLine(cash, "Money received", "₹0.00", "dashboard-income-text");
        dashboardMonthlyExpenseLabel = snapshotLine(cash, "Money spent", "₹0.00", "dashboard-expense-text");
        dashboardMonthlySavingsLabel = snapshotLine(cash, "Money saved", "₹0.00", "dashboard-savings-text");
        cash.getChildren().add(0, hint); cash.getChildren().add(0, title);

        VBox actions = new VBox(10); actions.getStyleClass().addAll("summary-card", "dashboard-health-panel");
        Label actionTitle = new Label("Quick Actions"); actionTitle.getStyleClass().add("section-title");
        Label actionHint = new Label("Keep your financial records up to date."); actionHint.getStyleClass().add("subtitle"); actionHint.setWrapText(true);
        Button add = new Button("＋ Add Transaction"); add.getStyleClass().add("primary-button"); add.setMaxWidth(Double.MAX_VALUE); add.setOnAction(e -> handleAddTransactionNav());
        Button view = new Button("View Recent Activity  →"); view.getStyleClass().add("secondary-button"); view.setMaxWidth(Double.MAX_VALUE); view.setOnAction(e -> handleTransactionsNav());
        actions.getChildren().addAll(actionTitle, actionHint, add, view);
        row.getChildren().addAll(cash, actions); HBox.setHgrow(cash, Priority.ALWAYS); HBox.setHgrow(actions, Priority.ALWAYS);
        return row;
    }

    private Label snapshotLine(VBox box, String name, String value, String style) {
        HBox line = new HBox(10); line.getStyleClass().add("dashboard-snapshot-row"); line.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label nameLabel = new Label(name); nameLabel.getStyleClass().add("snapshot-name");
        Label spacer = new Label(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label valueLabel = new Label(value); valueLabel.getStyleClass().add(style);
        line.getChildren().addAll(nameLabel, spacer, valueLabel); box.getChildren().add(line); return valueLabel;
    }

    private Node createRecentActivity() {
        VBox card = new VBox(10); card.getStyleClass().addAll("summary-card", "dashboard-activity-card");
        HBox header = new HBox(8); header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox heading = new VBox(2); Label title = new Label("Recent Activity"); title.getStyleClass().add("section-title"); Label subtitle = new Label("Latest transactions from your account"); subtitle.getStyleClass().add("subtitle"); heading.getChildren().addAll(title, subtitle); HBox.setHgrow(heading, Priority.ALWAYS);
        Button viewAll = new Button("View all  →"); viewAll.getStyleClass().add("text-action-button"); viewAll.setOnAction(e -> handleTransactionsNav());
        header.getChildren().addAll(heading, viewAll);
        recentActivityBox = new VBox(5); card.getChildren().addAll(header, recentActivityBox); return card;
    }

    private void refreshDashboardOverview() {
        if (liveTransactions == null) return;
        refreshDashboardOverview(List.copyOf(liveTransactions));
    }

    private void refreshDashboardOverview(List<Transaction> list) {
        if (list == null || dashboardSavingsLabel == null || recentActivityBox == null) return;
        double income = list.stream().filter(t -> t.getType() == Transaction.Type.INCOME).mapToDouble(Transaction::getAmount).sum();
        double expense = list.stream().filter(t -> t.getType() == Transaction.Type.EXPENSE).mapToDouble(Transaction::getAmount).sum();
        double savings = income - expense;
        YearMonth month = YearMonth.now();
        double monthIncome = list.stream().filter(t -> isMonth(t, month) && t.getType() == Transaction.Type.INCOME).mapToDouble(Transaction::getAmount).sum();
        double monthExpense = list.stream().filter(t -> isMonth(t, month) && t.getType() == Transaction.Type.EXPENSE).mapToDouble(Transaction::getAmount).sum();
        double monthSavings = monthIncome - monthExpense;
        double rate = monthIncome > 0 ? monthSavings / monthIncome * 100 : 0;

        dashboardSavingsLabel.setText(formatMoney(savings));
        dashboardSavingsRateLabel.setText(String.format(Locale.US, "%.1f%%", rate));
        dashboardMonthlyIncomeLabel.setText(formatMoney(monthIncome));
        dashboardMonthlyExpenseLabel.setText(formatMoney(monthExpense));
        dashboardMonthlySavingsLabel.setText(formatMoney(monthSavings));
        dashboardHealthLabel.setText(monthIncome == 0 && monthExpense == 0 ? "● Ready" : monthSavings >= 0 ? "● Good" : "● Review");

        recentActivityBox.getChildren().clear();
        List<Transaction> recent = list.stream().filter(t -> t != null).sorted((a, b) -> {
            if (a.getDate() == null && b.getDate() == null) return 0;
            if (a.getDate() == null) return 1;
            if (b.getDate() == null) return -1;
            return b.getDate().compareTo(a.getDate());
        }).limit(5).toList();

        if (recent.isEmpty()) {
            Label empty = new Label("No transactions yet. Add your first income or expense to see it here.");
            empty.getStyleClass().add("activity-empty");
            recentActivityBox.getChildren().add(empty);
        } else recent.forEach(this::addActivityRow);
    }

    private void addActivityRow(Transaction transaction) {
        HBox row = new HBox(12); row.getStyleClass().add("dashboard-activity-row"); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label icon = new Label(transaction.getType() == Transaction.Type.INCOME ? "↑" : "↓");
        icon.getStyleClass().add(transaction.getType() == Transaction.Type.INCOME ? "dashboard-income-icon" : "dashboard-expense-icon");
        VBox info = new VBox(3); HBox.setHgrow(info, Priority.ALWAYS);
        String title = firstNonBlank(transaction.getDescription(), transaction.getCategory(), "Transaction");
        Label name = new Label(title); name.getStyleClass().add("activity-title");
        String category = firstNonBlank(transaction.getCategory(), "General");
        String date = transaction.getDate() == null ? "Date not set" : transaction.getDate().toString();
        Label meta = new Label(category + "  •  " + date); meta.getStyleClass().add("activity-meta");
        info.getChildren().addAll(name, meta);
        String prefix = transaction.getType() == Transaction.Type.INCOME ? "+ " : "- ";
        Label amount = new Label(prefix + formatMoney(transaction.getAmount()));
        amount.getStyleClass().add(transaction.getType() == Transaction.Type.INCOME ? "income-value-small" : "expense-value-small");
        row.getChildren().addAll(icon, info, amount);
        recentActivityBox.getChildren().add(row);
    }

    private String firstNonBlank(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value; return ""; }
    private boolean isMonth(Transaction t, YearMonth month) { return t.getDate() != null && YearMonth.from(t.getDate()).equals(month); }
    private String formatMoney(double amount) { return String.format(Locale.US, "₹%,.2f", amount); }

    @FXML private void handleDashboardNav() { show(dashboardSection); refreshDashboardOverview(); }
    @FXML private void handleAnalyticsNav() { show(analyticsSection); }
    @FXML private void handleNotificationsNav() { show(notificationsSection); }
    @FXML private void handleReportsNav() { show(reportsSection); }
    @FXML private void handleGoalsNav() { show(goalsSection); }
    @FXML private void handleBudgetNav() { show(budgetSection); }
    @FXML private void handleAddTransactionNav() { show(addTransactionSection); }
    @FXML private void handleTransactionsNav() { show(transactionsSection); }

    private void show(Node selected) {
        Node[] pages = {dashboardSection, analyticsSection, notificationsSection, reportsSection, goalsSection, budgetSection, addTransactionSection, transactionsSection};
        for (Node page : pages) if (page != null) { boolean active = page == selected; page.setVisible(active); page.setManaged(active); page.setMouseTransparent(!active); }
        if (selected != null) selected.toFront();
    }

    @FXML private void handleLogout() { invokeParent("handleLogout", (Object) null); }
    @FXML private void handleAddTransaction() { invokeParent("handleAddTransaction"); refreshDashboardOverview(); }
    @FXML private void handleExportCsv() { invokeParent("handleExportCsv"); }

    private void invokeParent(String name, Object... args) {
        try {
            Method method = null;
            for (Method candidate : DashboardController.class.getDeclaredMethods()) {
                if (candidate.getName().equals(name) && candidate.getParameterCount() == args.length) { method = candidate; break; }
            }
            if (method == null) throw new NoSuchMethodException(name);
            method.setAccessible(true);
            method.invoke(this, args);
        } catch (Exception e) {
            throw new RuntimeException("Could not execute " + name, e);
        }
    }
}
