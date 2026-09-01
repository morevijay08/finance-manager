package com.finance.manager.controller;

import com.finance.manager.model.Transaction;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
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

/** Dashboard-specific overview UI. Detailed finance features remain on their navbar pages. */
public class BudgetDashboardController extends DashboardController {
    @FXML private Node dashboardSection;
    @FXML private Node analyticsSection, notificationsSection, reportsSection, goalsSection, budgetSection, addTransactionSection, transactionsSection;
    private Label dashboardSavingsLabel, dashboardSavingsRateLabel, dashboardHealthLabel;
    private Label dashboardMonthlyIncomeLabel, dashboardMonthlyExpenseLabel, dashboardMonthlySavingsLabel;
    private VBox recentActivityBox;

    @FXML
    private void initialize() {
        try {
            Method parent = DashboardController.class.getDeclaredMethod("initialize");
            parent.setAccessible(true);
            parent.invoke(this);
            buildDashboardOverview();
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize dashboard.", e);
        }
    }

    private void buildDashboardOverview() {
        if (!(dashboardSection instanceof VBox root)) return;
        root.getChildren().addAll(createMetrics(), createCashFlow(), createRecentActivity());
        refreshDashboardOverview();
    }

    private Node createMetrics() {
        HBox row = new HBox(14);
        row.getStyleClass().add("dashboard-extra-row");
        VBox savings = metricCard("NET SAVINGS", "₹0.00", "Income minus expenses", "dashboard-savings-card");
        VBox rate = metricCard("SAVINGS RATE", "0%", "Saving efficiency this month", "dashboard-rate-card");
        VBox health = metricCard("FINANCIAL HEALTH", "● Good", "Based on your cash flow", "dashboard-health-card");
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
        VBox cash = new VBox(12); cash.getStyleClass().addAll("summary-card", "dashboard-cashflow-card");
        Label title = new Label("This Month at a Glance"); title.getStyleClass().add("section-title");
        Label hint = new Label("See how much came in, went out and stayed with you."); hint.getStyleClass().add("subtitle");
        dashboardMonthlyIncomeLabel = snapshotLine(cash, "Money received", "₹0.00", "dashboard-income-text");
        dashboardMonthlyExpenseLabel = snapshotLine(cash, "Money spent", "₹0.00", "dashboard-expense-text");
        dashboardMonthlySavingsLabel = snapshotLine(cash, "Money saved", "₹0.00", "dashboard-savings-text");
        cash.getChildren().addAll(0, List.of(hint, title));

        VBox actions = new VBox(10); actions.getStyleClass().addAll("summary-card", "dashboard-health-panel");
        Label actionTitle = new Label("Quick Actions"); actionTitle.getStyleClass().add("section-title");
        Label actionHint = new Label("Keep your records up to date with one click."); actionHint.getStyleClass().add("subtitle");
        actionHint.setWrapText(true);
        Button add = new Button("＋ Add Transaction"); add.getStyleClass().add("primary-button"); add.setMaxWidth(Double.MAX_VALUE); add.setOnAction(e -> handleAddTransactionNav());
        Button view = new Button("View Recent Activity  →"); view.getStyleClass().add("secondary-button"); view.setMaxWidth(Double.MAX_VALUE); view.setOnAction(e -> handleTransactionsNav());
        actions.getChildren().addAll(actionTitle, actionHint, add, view);
        row.getChildren().addAll(cash, actions); HBox.setHgrow(cash, Priority.ALWAYS); HBox.setHgrow(actions, Priority.ALWAYS);
        return row;
    }

    private Label snapshotLine(VBox box, String name, String value, String style) {
        HBox line = new HBox(10); line.getStyleClass().add("dashboard-snapshot-row");
        Label nameLabel = new Label(name); nameLabel.getStyleClass().add("snapshot-name");
        Label spacer = new Label(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label valueLabel = new Label(value); valueLabel.getStyleClass().add(style);
        line.getChildren().addAll(nameLabel, spacer, valueLabel); box.getChildren().add(line); return valueLabel;
    }

    private Node createRecentActivity() {
        VBox card = new VBox(10); card.getStyleClass().addAll("summary-card", "dashboard-activity-card");
        HBox header = new HBox(8); header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox heading = new VBox(2); Label title = new Label("Recent Activity"); title.getStyleClass().add("section-title"); Label subtitle = new Label("Your latest 3 transactions"); subtitle.getStyleClass().add("subtitle"); heading.getChildren().addAll(title, subtitle); HBox.setHgrow(heading, Priority.ALWAYS);
        Button viewAll = new Button("View all  →"); viewAll.getStyleClass().add("text-action-button"); viewAll.setOnAction(e -> handleTransactionsNav());
        header.getChildren().addAll(heading, viewAll);
        recentActivityBox = new VBox(5); card.getChildren().addAll(header, recentActivityBox); return card;
    }

    private void refreshDashboardOverview() {
        try {
            @SuppressWarnings("unchecked") ObservableList<Transaction> list = (ObservableList<Transaction>) privateField("transactions");
            if (list == null) return;
            double income = list.stream().filter(t -> t.getType() == Transaction.Type.INCOME).mapToDouble(Transaction::getAmount).sum();
            double expense = list.stream().filter(t -> t.getType() == Transaction.Type.EXPENSE).mapToDouble(Transaction::getAmount).sum();
            double savings = income - expense;
            YearMonth month = YearMonth.now();
            double monthIncome = list.stream().filter(t -> isMonth(t, month) && t.getType() == Transaction.Type.INCOME).mapToDouble(Transaction::getAmount).sum();
            double monthExpense = list.stream().filter(t -> isMonth(t, month) && t.getType() == Transaction.Type.EXPENSE).mapToDouble(Transaction::getAmount).sum();
            double monthSavings = monthIncome - monthExpense;
            double rate = monthIncome > 0 ? monthSavings / monthIncome * 100 : 0;
            dashboardSavingsLabel.setText(formatMoney(savings)); dashboardSavingsRateLabel.setText(String.format(Locale.US, "%.1f%%", rate));
            dashboardMonthlyIncomeLabel.setText(formatMoney(monthIncome)); dashboardMonthlyExpenseLabel.setText(formatMoney(monthExpense)); dashboardMonthlySavingsLabel.setText(formatMoney(monthSavings));
            dashboardHealthLabel.setText(monthIncome == 0 && monthExpense == 0 ? "● Ready" : monthSavings >= 0 ? "● Good" : "● Review spending");
            recentActivityBox.getChildren().clear();
            List<Transaction> recent = list.stream().sorted((a,b) -> { if (a.getDate() == null) return 1; if (b.getDate() == null) return -1; return b.getDate().compareTo(a.getDate()); }).limit(3).toList();
            if (recent.isEmpty()) {
                Label empty = new Label("No activity yet. Add a transaction to get started."); empty.getStyleClass().add("activity-empty"); recentActivityBox.getChildren().add(empty);
            } else recent.forEach(this::addActivityRow);
        } catch (Exception ignored) { }
    }

    private void addActivityRow(Transaction transaction) {
        HBox row = new HBox(12); row.getStyleClass().add("dashboard-activity-row"); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label icon = new Label(transaction.getType() == Transaction.Type.INCOME ? "↑" : "↓"); icon.getStyleClass().add(transaction.getType() == Transaction.Type.INCOME ? "dashboard-income-icon" : "dashboard-expense-icon");
        VBox info = new VBox(3); HBox.setHgrow(info, Priority.ALWAYS);
        String name = transaction.getDescription(); if (name == null || name.isBlank()) name = transaction.getCategory(); if (name == null || name.isBlank()) name = "Transaction";
        Label nameLabel = new Label(name); nameLabel.getStyleClass().add("activity-title");
        String category = transaction.getCategory() == null ? "General" : transaction.getCategory();
        String date = transaction.getDate() == null ? "" : transaction.getDate().toString();
        Label meta = new Label(category + (date.isBlank() ? "" : "  •  " + date)); meta.getStyleClass().add("activity-meta"); info.getChildren().addAll(nameLabel, meta);
        Label amount = new Label((transaction.getType() == Transaction.Type.INCOME ? "+ " : "- ") + formatMoney(transaction.getAmount())); amount.getStyleClass().add(transaction.getType() == Transaction.Type.INCOME ? "income-value-small" : "expense-value-small");
        row.getChildren().addAll(icon, info, amount); recentActivityBox.getChildren().add(row);
    }

    private Object privateField(String name) throws Exception { Field field = DashboardController.class.getDeclaredField(name); field.setAccessible(true); return field.get(this); }
    private boolean isMonth(Transaction transaction, YearMonth month) { return transaction.getDate() != null && YearMonth.from(transaction.getDate()).equals(month); }

    @FXML private void handleDashboardNav() { show(dashboardSection); }
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

    @FXML private void handleLogout() { invoke("handleLogout"); }
    @FXML private void handleAddTransaction() { invoke("handleAddTransaction"); }
    @FXML private void handleExportCsv() { invoke("handleExportCsv"); }

    private void invoke(String name) { try { Method m = DashboardController.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(this); } catch (Exception e) { throw new RuntimeException("Could not execute " + name, e); } }
    private String formatMoney(double amount) { return String.format(Locale.US, "₹%,.2f", amount); }
}
