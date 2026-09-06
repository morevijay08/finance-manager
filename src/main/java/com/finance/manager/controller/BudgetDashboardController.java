package com.finance.manager.controller;

import com.finance.manager.model.Transaction;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.lang.reflect.Field;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dashboard, analytics and financial reports presentation using the live transaction list loaded by DashboardController. */
public class BudgetDashboardController extends DashboardController {
    @FXML private Node dashboardSection;
    @FXML private Node analyticsSection, notificationsSection, reportsSection, goalsSection, budgetSection, addTransactionSection, transactionsSection;

    @FXML private Label budgetPercentageLabel;
    @FXML private Label budgetSavedLabel;
    @FXML private Button editBudgetButton;
    @FXML private Label dashboardMonthLabel;

    private Label dashboardSavingsLabel, dashboardSavingsRateLabel;
    private Label dashboardMonthlyIncomeLabel, dashboardMonthlyExpenseLabel, dashboardMonthlySavingsLabel;
    private VBox recentActivityBox;
    private ObservableList<Transaction> liveTransactions;

    @FXML private Label analyticsTotalTransactions;
    @FXML private Label analyticsAverageExpense;
    @FXML private Label analyticsHighestCategory;
    @FXML private Label analyticsCashFlow;
    @FXML private Label analyticsInsight;

    @FXML private ComboBox<String> reportMonthCombo;
    @FXML private Label reportIncomeLabel;
    @FXML private Label reportExpenseLabel;
    @FXML private Label reportSavingsLabel;
    @FXML private Label reportBudgetLabel;
    @FXML private Label reportRemainingLabel;
    @FXML private PieChart reportExpenseChart;

    private Label budgetHealthLabel;
    private Label budgetDailyLimitLabel;
    private Label budgetDaysLabel;
    private Label budgetHealthDetailLabel;
    private VBox budgetCategoryBox;

    private static final DateTimeFormatter REPORT_MONTH_FORMATTER =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    @FXML
    protected void initialize() {
        super.initialize();
        buildDashboardOverview();
        connectToLiveTransactions();
        setupBudgetEditing();
        buildBudgetEnhancements();
        setupReports();
        refreshDashboardOverview();
        refreshAnalyticsOverview();
        refreshBudgetStats();
        refreshReport();
        show(dashboardSection);
    }

    @SuppressWarnings("unchecked")
    private void connectToLiveTransactions() {
        try {
            Field field = DashboardController.class.getDeclaredField("transactions");
            field.setAccessible(true);
            liveTransactions = (ObservableList<Transaction>) field.get(this);
            liveTransactions.addListener((javafx.collections.ListChangeListener<Transaction>) change -> {
                refreshDashboardOverview();
                refreshAnalyticsOverview();
                refreshBudgetStats();
                refreshReportMonths();
                refreshReport();
            });
        } catch (Exception e) {
            liveTransactions = null;
        }
    }

    private void setupReports() {
        if (reportMonthCombo == null) return;
        reportMonthCombo.setOnAction(event -> refreshReport());
        refreshReportMonths();
    }

    /** Populate the report month selector from transaction history and always include the current month. */
    private void refreshReportMonths() {
        if (reportMonthCombo == null) return;

        YearMonth previouslySelected = selectedReportMonth();
        List<YearMonth> months = new ArrayList<>();
        months.add(YearMonth.now());

        if (liveTransactions != null) {
            liveTransactions.stream()
                    .filter(t -> t != null && t.getDate() != null)
                    .map(t -> YearMonth.from(t.getDate()))
                    .forEach(month -> {
                        if (!months.contains(month)) months.add(month);
                    });
        }

        months.sort(Comparator.reverseOrder());
        List<String> labels = months.stream()
                .map(REPORT_MONTH_FORMATTER::format)
                .toList();

        reportMonthCombo.setItems(FXCollections.observableArrayList(labels));

        if (previouslySelected != null && months.contains(previouslySelected)) {
            reportMonthCombo.getSelectionModel().select(REPORT_MONTH_FORMATTER.format(previouslySelected));
        } else {
            reportMonthCombo.getSelectionModel().select(REPORT_MONTH_FORMATTER.format(YearMonth.now()));
        }
    }

    private YearMonth selectedReportMonth() {
        if (reportMonthCombo == null || reportMonthCombo.getValue() == null || reportMonthCombo.getValue().isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(reportMonthCombo.getValue(), REPORT_MONTH_FORMATTER);
        } catch (Exception e) {
            return YearMonth.now();
        }
    }

    /** Recalculate the selected month's income, expense, savings, budget and category chart. */
    private void refreshReport() {
        if (reportIncomeLabel == null || reportExpenseLabel == null || reportExpenseChart == null) return;

        YearMonth selectedMonth = selectedReportMonth();
        List<Transaction> list = liveTransactions == null
                ? List.of()
                : liveTransactions.stream()
                        .filter(t -> t != null && isMonth(t, selectedMonth))
                        .toList();

        double income = list.stream()
                .filter(t -> t.getType() == Transaction.Type.INCOME)
                .mapToDouble(Transaction::getAmount)
                .sum();
        double expense = list.stream()
                .filter(t -> t.getType() == Transaction.Type.EXPENSE)
                .mapToDouble(Transaction::getAmount)
                .sum();
        double savings = income - expense;

        TextField budgetInput = getDashboardField("budgetField", TextField.class);
        double budget = parseAmount(budgetInput == null ? null : budgetInput.getText());
        double remaining = budget > 0 ? budget - expense : 0;

        reportIncomeLabel.setText(formatMoney(income));
        reportExpenseLabel.setText(formatMoney(expense));
        if (reportSavingsLabel != null) reportSavingsLabel.setText(formatMoney(savings));
        if (reportBudgetLabel != null) reportBudgetLabel.setText(formatMoney(budget));
        if (reportRemainingLabel != null) reportRemainingLabel.setText(formatMoney(remaining));

        Map<String, Double> categoryTotals = new HashMap<>();
        list.stream()
                .filter(t -> t.getType() == Transaction.Type.EXPENSE)
                .forEach(t -> {
                    String category = firstNonBlank(t.getCategory(), "Other");
                    categoryTotals.merge(category, t.getAmount(), Double::sum);
                });

        List<PieChart.Data> chartData = categoryTotals.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> new PieChart.Data(entry.getKey(), entry.getValue()))
                .toList();

        if (chartData.isEmpty()) {
            reportExpenseChart.setData(FXCollections.observableArrayList(
                    new PieChart.Data("No expenses", 1)
            ));
        } else {
            reportExpenseChart.setData(FXCollections.observableArrayList(chartData));
        }
        reportExpenseChart.setTitle("Selected Month Expense by Category");
    }

    private void setupBudgetEditing() {
        Button saveButton = getDashboardField("saveBudgetButton", Button.class);
        TextField budgetInput = getDashboardField("budgetField", TextField.class);
        Label status = getDashboardField("statusLabel", Label.class);

        if (editBudgetButton != null) {
            editBudgetButton.setOnAction(event -> {
                if (budgetInput != null) {
                    budgetInput.setEditable(true);
                    budgetInput.requestFocus();
                    budgetInput.selectAll();
                }
                if (saveButton != null) saveButton.setDisable(false);
                if (status != null) status.setText("Edit the monthly budget and click Save Budget.");
            });
        }
        if (budgetInput != null) {
            budgetInput.textProperty().addListener((observable, oldValue, newValue) -> {
                refreshBudgetStats();
                refreshReport();
            });
        }
        refreshBudgetStats();
    }

    /** Adds the useful planning information below the main budget card. */
    private void buildBudgetEnhancements() {
        if (!(budgetSection instanceof VBox root)) return;
        if (budgetHealthLabel != null) return;

        HBox overview = new HBox(14);
        overview.setFillHeight(true);

        VBox healthCard = budgetInsightCard("BUDGET HEALTH", "On track", "Your spending position this month.");
        budgetHealthLabel = findValueLabel(healthCard);
        budgetHealthDetailLabel = findCaptionLabel(healthCard);

        VBox dailyCard = budgetInsightCard("SAFE DAILY SPEND", "₹0.00", "Suggested maximum for the remaining days.");
        budgetDailyLimitLabel = findValueLabel(dailyCard);

        VBox daysCard = budgetInsightCard("DAYS REMAINING", "0 days", "Days left in the current month.");
        budgetDaysLabel = findValueLabel(daysCard);

        overview.getChildren().addAll(healthCard, dailyCard, daysCard);
        HBox.setHgrow(healthCard, Priority.ALWAYS);
        HBox.setHgrow(dailyCard, Priority.ALWAYS);
        HBox.setHgrow(daysCard, Priority.ALWAYS);

        VBox categoryCard = new VBox(10);
        categoryCard.setStyle("-fx-background-color: white; -fx-background-radius: 17px; -fx-border-color: #e2e8f0; -fx-border-radius: 17px; -fx-padding: 20px; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.075), 16, 0.12, 0, 5);");
        Label title = new Label("Top Spending Categories");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");
        Label subtitle = new Label("See where this month's budget is going.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
        budgetCategoryBox = new VBox(10);
        categoryCard.getChildren().addAll(title, subtitle, budgetCategoryBox);

        VBox tipCard = new VBox(10);
        tipCard.setStyle("-fx-background-color: linear-gradient(to bottom right, #eff6ff, #eef2ff); -fx-background-radius: 17px; -fx-border-color: #c7d2fe; -fx-border-radius: 17px; -fx-padding: 20px;");
        Label tipTitle = new Label("Budget Tip");
        tipTitle.setStyle("-fx-font-size: 17px; -fx-font-weight: 800; -fx-text-fill: #1e1b4b;");
        Label tipText = new Label("Set a realistic monthly limit, review your largest categories regularly, and leave some room for unexpected expenses.");
        tipText.setWrapText(true);
        tipText.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-line-spacing: 3px;");
        tipCard.getChildren().addAll(tipTitle, tipText);

        HBox lower = new HBox(14);
        lower.getChildren().addAll(categoryCard, tipCard);
        HBox.setHgrow(categoryCard, Priority.ALWAYS);
        HBox.setHgrow(tipCard, Priority.ALWAYS);

        root.getChildren().addAll(overview, lower);
        refreshBudgetEnhancements();
    }

    private VBox budgetInsightCard(String title, String value, String caption) {
        VBox card = new VBox(6);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 17px; -fx-border-color: #e2e8f0; -fx-border-radius: 17px; -fx-padding: 19px; -fx-effect: dropshadow(gaussian, rgba(15,23,42,0.065), 14, 0.10, 0, 4);");
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: #64748b; -fx-letter-spacing: 0.7px;");
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-size: 23px; -fx-font-weight: 800; -fx-text-fill: #0f172a;");
        Label captionLabel = new Label(caption);
        captionLabel.setWrapText(true);
        captionLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        card.getChildren().addAll(titleLabel, valueLabel, captionLabel);
        return card;
    }

    private Label findValueLabel(VBox card) {
        return (Label) card.getChildren().get(1);
    }

    private Label findCaptionLabel(VBox card) {
        return (Label) card.getChildren().get(2);
    }

    private void refreshBudgetEnhancements() {
        if (budgetHealthLabel == null || budgetCategoryBox == null) return;

        TextField budgetInput = getDashboardField("budgetField", TextField.class);
        double budget = parseAmount(budgetInput == null ? null : budgetInput.getText());
        YearMonth month = YearMonth.now();
        double spent = liveTransactions == null ? 0 : liveTransactions.stream()
                .filter(t -> t != null && t.getType() == Transaction.Type.EXPENSE && isMonth(t, month))
                .mapToDouble(Transaction::getAmount)
                .sum();
        double remaining = budget > 0 ? budget - spent : 0;
        double used = budget > 0 ? spent / budget * 100.0 : 0;
        long daysRemaining = ChronoUnit.DAYS.between(java.time.LocalDate.now(), month.atEndOfMonth());
        double dailySafeSpend = remaining > 0 && daysRemaining > 0 ? remaining / daysRemaining : 0;

        if (budget <= 0) {
            budgetHealthLabel.setText("Set a budget");
            budgetHealthDetailLabel.setText("Add a monthly limit to start tracking your budget health.");
        } else if (used >= 100) {
            budgetHealthLabel.setText("Over budget");
            budgetHealthDetailLabel.setText(String.format(Locale.US, "You have exceeded the limit by %s.", formatMoney(Math.abs(remaining))));
        } else if (used >= 80) {
            budgetHealthLabel.setText("Near the limit");
            budgetHealthDetailLabel.setText(String.format(Locale.US, "%.1f%% of the budget is already used.", used));
        } else {
            budgetHealthLabel.setText("On track");
            budgetHealthDetailLabel.setText(String.format(Locale.US, "%.1f%% used — %s remains.", used, formatMoney(remaining)));
        }

        budgetDailyLimitLabel.setText(formatMoney(dailySafeSpend));
        budgetDaysLabel.setText(daysRemaining + (daysRemaining == 1 ? " day" : " days"));

        budgetCategoryBox.getChildren().clear();
        Map<String, Double> categories = new HashMap<>();
        if (liveTransactions != null) {
            liveTransactions.stream()
                    .filter(t -> t != null && t.getType() == Transaction.Type.EXPENSE && isMonth(t, month))
                    .forEach(t -> categories.merge(firstNonBlank(t.getCategory(), "Other"), t.getAmount(), Double::sum));
        }

        if (categories.isEmpty()) {
            Label empty = new Label("No expenses recorded for this month yet.");
            empty.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b; -fx-padding: 6px 0;");
            budgetCategoryBox.getChildren().add(empty);
            return;
        }

        categories.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> {
                    double percent = spent > 0 ? entry.getValue() / spent : 0;
                    HBox row = new HBox(10);
                    row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    Label name = new Label(entry.getKey());
                    name.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #334155;");
                    Label amount = new Label(formatMoney(entry.getValue()));
                    amount.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #0f172a;");
                    Label spacer = new Label();
                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    row.getChildren().addAll(name, spacer, amount);

                    ProgressBar categoryProgress = new ProgressBar(Math.min(percent, 1));
                    categoryProgress.setMaxWidth(Double.MAX_VALUE);
                    categoryProgress.setPrefHeight(7);
                    categoryProgress.setStyle("-fx-accent: #4f46e5; -fx-background-radius: 8px; -fx-padding: 0;");
                    VBox item = new VBox(4, row, categoryProgress);
                    budgetCategoryBox.getChildren().add(item);
                });
    }

    private void refreshBudgetStats() {
        TextField budgetInput = getDashboardField("budgetField", TextField.class);
        if (budgetInput == null) return;
        double budget = parseAmount(budgetInput.getText());
        double spent = liveTransactions == null ? 0 : liveTransactions.stream()
                .filter(t -> t != null && t.getType() == Transaction.Type.EXPENSE)
                .filter(t -> isMonth(t, YearMonth.now()))
                .mapToDouble(Transaction::getAmount).sum();
        double remaining = budget > 0 ? budget - spent : 0;
        double usedPercentage = budget > 0 ? (spent / budget) * 100.0 : 0;
        double monthlySavings = liveTransactions == null ? 0 : liveTransactions.stream()
                .filter(t -> t != null && isMonth(t, YearMonth.now()))
                .mapToDouble(t -> t.getType() == Transaction.Type.INCOME ? t.getAmount() : -t.getAmount())
                .sum();

        Label spentLabel = getDashboardField("budgetSpentLabel", Label.class);
        Label remainingLabel = getDashboardField("budgetRemainingLabel", Label.class);
        ProgressBar progressBar = getDashboardField("budgetProgressBar", ProgressBar.class);
        if (spentLabel != null) spentLabel.setText(formatMoney(spent));
        if (remainingLabel != null) remainingLabel.setText(formatMoney(remaining));
        if (budgetSavedLabel != null) budgetSavedLabel.setText(formatMoney(monthlySavings));
        if (budgetPercentageLabel != null) budgetPercentageLabel.setText(String.format(Locale.US, "%.1f%% used", Math.max(0, usedPercentage)));
        if (progressBar != null) progressBar.setProgress(budget <= 0 ? 0 : Math.min(spent / budget, 1.0));
        refreshBudgetEnhancements();
    }

    @SuppressWarnings("unchecked")
    private <T> T getDashboardField(String name, Class<T> type) {
        try {
            Field field = DashboardController.class.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(this);
            return type.isInstance(value) ? (T) value : null;
        } catch (Exception e) {
            return null;
        }
    }

    private double parseAmount(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            double amount = Double.parseDouble(value.trim());
            return amount >= 0 ? amount : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void buildDashboardOverview() {
        if (!(dashboardSection instanceof VBox root)) return;
        root.getChildren().addAll(createMetrics(), createCashFlow(), createRecentActivity());
    }

    private Node createMetrics() {
        GridPane row = new GridPane();
        row.setHgap(14);
        row.getStyleClass().add("dashboard-extra-row");

        for (int i = 0; i < 3; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(i == 2 ? 33.334 : 33.333);
            column.setHgrow(Priority.ALWAYS);
            row.getColumnConstraints().add(column);
        }

        VBox savings = metricCard("MONTHLY SAVINGS", "₹0.00", "This month's income minus expenses", "dashboard-savings-card");
        VBox rate = metricCard("MONTHLY SAVINGS RATE", "0.0%", "This month's savings as a share of income", "dashboard-rate-card");
        dashboardSavingsLabel = valueLabel(savings);
        dashboardSavingsRateLabel = valueLabel(rate);

        GridPane.setColumnIndex(savings, 0);
        GridPane.setColumnIndex(rate, 1);
        row.getChildren().addAll(savings, rate);
        return row;
    }

    private VBox metricCard(String title, String value, String caption, String style) {
        VBox card = new VBox(6);
        card.getStyleClass().addAll("summary-card", style);

        String icon = title.equals("MONTHLY SAVINGS") ? "▣" : "%";
        String iconBackground = title.equals("MONTHLY SAVINGS") ? "#dbeafe" : "#f3e8ff";
        String iconText = title.equals("MONTHLY SAVINGS") ? "#2563eb" : "#7c3aed";

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-min-width: 54px; -fx-min-height: 54px; -fx-max-width: 54px; -fx-max-height: 54px; -fx-alignment: center; -fx-background-color: " + iconBackground + "; -fx-background-radius: 15px; -fx-text-fill: " + iconText + "; -fx-font-size: 26px; -fx-font-weight: bold;");

        VBox text = new VBox(5);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("dashboard-extra-value");
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("card-caption");
        text.getChildren().addAll(titleLabel, valueLabel, captionLabel);

        HBox content = new HBox(15);
        content.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);
        content.getChildren().addAll(iconLabel, text);
        card.getChildren().add(content);
        return card;
    }

    private Label valueLabel(VBox card) {
        HBox content = (HBox) card.getChildren().get(0);
        VBox text = (VBox) content.getChildren().get(1);
        return (Label) text.getChildren().get(1);
    }

    private Node createCashFlow() {
        HBox row = new HBox(14); VBox cash = new VBox(10); cash.getStyleClass().addAll("summary-card", "dashboard-cashflow-card");
        Label title = new Label("This Month at a Glance"); title.getStyleClass().add("section-title"); Label hint = new Label("A simple snapshot of money received, spent and saved."); hint.getStyleClass().add("subtitle"); hint.setWrapText(true);
        dashboardMonthlyIncomeLabel = snapshotLine(cash, "Money received", "₹0.00", "dashboard-income-text"); dashboardMonthlyExpenseLabel = snapshotLine(cash, "Money spent", "₹0.00", "dashboard-expense-text"); dashboardMonthlySavingsLabel = snapshotLine(cash, "Money saved", "₹0.00", "dashboard-savings-text"); cash.getChildren().add(0, hint); cash.getChildren().add(0, title);
        VBox actions = new VBox(10); actions.getStyleClass().addAll("summary-card", "dashboard-health-panel"); Label actionTitle = new Label("Quick Actions"); actionTitle.getStyleClass().add("section-title"); Label actionHint = new Label("Keep your financial records up to date."); actionHint.getStyleClass().add("subtitle"); actionHint.setWrapText(true);
        Button add = new Button("＋ Add Transaction"); add.getStyleClass().add("primary-button"); add.setMaxWidth(Double.MAX_VALUE); add.setOnAction(e -> handleAddTransactionNav()); Button view = new Button("View Recent Activity  →"); view.getStyleClass().add("secondary-button"); view.setMaxWidth(Double.MAX_VALUE); view.setOnAction(e -> handleTransactionsNav());
        actions.getChildren().addAll(actionTitle, actionHint, add, view); row.getChildren().addAll(cash, actions); HBox.setHgrow(cash, Priority.ALWAYS); HBox.setHgrow(actions, Priority.ALWAYS); return row;
    }

    private Label snapshotLine(VBox box, String name, String value, String style) { HBox line = new HBox(10); line.getStyleClass().add("dashboard-snapshot-row"); line.setAlignment(javafx.geometry.Pos.CENTER_LEFT); Label nameLabel = new Label(name); nameLabel.getStyleClass().add("snapshot-name"); Label spacer = new Label(); HBox.setHgrow(spacer, Priority.ALWAYS); Label valueLabel = new Label(value); valueLabel.getStyleClass().add(style); line.getChildren().addAll(nameLabel, spacer, valueLabel); box.getChildren().add(line); return valueLabel; }

    private Node createRecentActivity() {
        VBox card = new VBox(10); card.getStyleClass().addAll("summary-card", "dashboard-activity-card"); HBox header = new HBox(8); header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox heading = new VBox(2); Label title = new Label("Recent Activity"); title.getStyleClass().add("section-title"); Label subtitle = new Label("Latest transactions from your account"); subtitle.getStyleClass().add("subtitle"); heading.getChildren().addAll(title, subtitle); HBox.setHgrow(heading, Priority.ALWAYS);
        Button viewAll = new Button("View all  →"); viewAll.getStyleClass().add("text-action-button"); viewAll.setOnAction(e -> handleTransactionsNav()); header.getChildren().addAll(heading, viewAll); recentActivityBox = new VBox(5); card.getChildren().addAll(header, recentActivityBox); return card;
    }

    private void refreshDashboardOverview() {
        if (liveTransactions == null) return;
        refreshDashboardOverview(List.copyOf(liveTransactions));
    }

    private void refreshDashboardOverview(List<Transaction> list) {
        if (list == null || dashboardSavingsLabel == null || recentActivityBox == null) return;

        double income = list.stream().filter(t -> t != null && t.getType() == Transaction.Type.INCOME).mapToDouble(Transaction::getAmount).sum();
        double expense = list.stream().filter(t -> t != null && t.getType() == Transaction.Type.EXPENSE).mapToDouble(Transaction::getAmount).sum();
        double savings = income - expense;

        YearMonth month = YearMonth.now();
        double monthIncome = list.stream().filter(t -> isMonth(t, month) && t.getType() == Transaction.Type.INCOME).mapToDouble(Transaction::getAmount).sum();
        double monthExpense = list.stream().filter(t -> isMonth(t, month) && t.getType() == Transaction.Type.EXPENSE).mapToDouble(Transaction::getAmount).sum();
        double monthSavings = monthIncome - monthExpense;
        double rate = monthIncome > 0 ? monthSavings / monthIncome * 100 : 0;

        refreshPrimaryMetrics(monthIncome, monthExpense, income - expense, month);
        dashboardSavingsLabel.setText(formatMoney(monthSavings));
        dashboardSavingsRateLabel.setText(String.format(Locale.US, "%.1f%%", rate));
        dashboardMonthlyIncomeLabel.setText(formatMoney(monthIncome));
        dashboardMonthlyExpenseLabel.setText(formatMoney(monthExpense));
        dashboardMonthlySavingsLabel.setText(formatMoney(monthSavings));

        recentActivityBox.getChildren().clear();
        List<Transaction> recent = list.stream().filter(t -> t != null).sorted((a,b) -> {
            if (a.getDate() == null && b.getDate() == null) return 0;
            if (a.getDate() == null) return 1;
            if (b.getDate() == null) return -1;
            return b.getDate().compareTo(a.getDate());
        }).limit(5).toList();
        if (recent.isEmpty()) {
            Label empty = new Label("No transactions yet. Add your first income or expense to see it here.");
            empty.getStyleClass().add("activity-empty");
            recentActivityBox.getChildren().add(empty);
        } else {
            recent.forEach(this::addActivityRow);
        }
    }

    /** Keep the top dashboard cards semantically consistent: balance is account-wide, income/expense are current-month figures. */
    private void refreshPrimaryMetrics(double monthIncome, double monthExpense, double accountBalance, YearMonth month) {
        Label balance = getDashboardField("balanceLabel", Label.class);
        Label income = getDashboardField("incomeLabel", Label.class);
        Label expense = getDashboardField("expenseLabel", Label.class);
        if (balance != null) balance.setText(formatMoney(accountBalance));
        if (income != null) income.setText(formatMoney(monthIncome));
        if (expense != null) expense.setText(formatMoney(monthExpense));
        if (dashboardMonthLabel != null) {
            dashboardMonthLabel.setText(month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear());
        }
    }

    private void refreshAnalyticsOverview() {
        if (liveTransactions == null || analyticsTotalTransactions == null) return;
        List<Transaction> list = liveTransactions.stream().filter(t -> t != null).toList();
        long expenseCount = list.stream().filter(t -> t.getType() == Transaction.Type.EXPENSE).count();
        double totalExpense = list.stream().filter(t -> t.getType() == Transaction.Type.EXPENSE).mapToDouble(Transaction::getAmount).sum();
        double totalIncome = list.stream().filter(t -> t.getType() == Transaction.Type.INCOME).mapToDouble(Transaction::getAmount).sum();
        double averageExpense = expenseCount > 0 ? totalExpense / expenseCount : 0;
        Map<String, Double> categories = new HashMap<>();
        list.stream().filter(t -> t.getType() == Transaction.Type.EXPENSE).forEach(t -> { String category = t.getCategory() == null || t.getCategory().isBlank() ? "Other" : t.getCategory(); categories.merge(category, t.getAmount(), Double::sum); });
        String topCategory = "—"; double topAmount = 0;
        for (Map.Entry<String, Double> entry : categories.entrySet()) if (entry.getValue() > topAmount) { topCategory = entry.getKey(); topAmount = entry.getValue(); }
        double cashFlow = totalIncome - totalExpense;
        analyticsTotalTransactions.setText(String.valueOf(list.size())); analyticsAverageExpense.setText(formatMoney(averageExpense)); analyticsHighestCategory.setText(topCategory); analyticsCashFlow.setText(formatMoney(cashFlow));
        if (list.isEmpty()) analyticsInsight.setText("No transactions yet. Add a few income and expense entries to unlock useful spending insights.");
        else if (cashFlow > 0 && totalIncome > 0) { double savingsRate = cashFlow / totalIncome * 100; analyticsInsight.setText(String.format(Locale.US, "Good news: you kept %.1f%% of recorded income after expenses. Your highest spending category is %s (%s).", savingsRate, topCategory, formatMoney(topAmount))); }
        else if (cashFlow < 0) analyticsInsight.setText(String.format(Locale.US, "Your expenses are higher than your income by %s. Review '%s' first because it is your largest spending category.", formatMoney(Math.abs(cashFlow)), topCategory));
        else analyticsInsight.setText("Your recorded income and expenses are balanced. Keep adding transactions to build a clearer spending pattern.");
    }

    private void addActivityRow(Transaction transaction) { HBox row = new HBox(12); row.getStyleClass().add("dashboard-activity-row"); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT); Label icon = new Label(transaction.getType() == Transaction.Type.INCOME ? "↑" : "↓"); icon.getStyleClass().add(transaction.getType() == Transaction.Type.INCOME ? "dashboard-income-icon" : "dashboard-expense-icon"); VBox info = new VBox(3); HBox.setHgrow(info, Priority.ALWAYS); String title = firstNonBlank(transaction.getDescription(), transaction.getCategory(), "Transaction"); Label name = new Label(title); name.getStyleClass().add("activity-title"); String category = firstNonBlank(transaction.getCategory(), "General"); String date = transaction.getDate() == null ? "Date not set" : transaction.getDate().toString(); Label meta = new Label(category + "  •  " + date); meta.getStyleClass().add("activity-meta"); info.getChildren().addAll(name, meta); String prefix = transaction.getType() == Transaction.Type.INCOME ? "+ " : "- "; Label amount = new Label(prefix + formatMoney(transaction.getAmount())); amount.getStyleClass().add(transaction.getType() == Transaction.Type.INCOME ? "income-value-small" : "expense-value-small"); row.getChildren().addAll(icon, info, amount); recentActivityBox.getChildren().add(row); }
    private String firstNonBlank(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value; return ""; }
    private boolean isMonth(Transaction t, YearMonth month) { return t != null && t.getDate() != null && YearMonth.from(t.getDate()).equals(month); }
    private String formatMoney(double amount) { return String.format(Locale.US, "₹%,.2f", amount); }

    @FXML private void handleDashboardNav() { show(dashboardSection); refreshDashboardOverview(); }
    @FXML private void handleAnalyticsNav() { show(analyticsSection); refreshAnalyticsOverview(); }
    @FXML private void handleNotificationsNav() { show(notificationsSection); }
    @FXML private void handleReportsNav() { show(reportsSection); refreshReportMonths(); refreshReport(); }
    @FXML private void handleGoalsNav() { show(goalsSection); }
    @FXML private void handleBudgetNav() { show(budgetSection); refreshBudgetStats(); }
    @FXML private void handleAddTransactionNav() { show(addTransactionSection); }
    @FXML private void handleTransactionsNav() { show(transactionsSection); }

    @FXML private void handleLogout() { super.handleLogout(null); }
    @FXML protected void handleAddTransaction() { super.handleAddTransaction(); refreshDashboardOverview(); refreshAnalyticsOverview(); refreshBudgetStats(); refreshReportMonths(); refreshReport(); }
    @FXML protected void handleExportCsv() { super.handleExportCsv(); }
}