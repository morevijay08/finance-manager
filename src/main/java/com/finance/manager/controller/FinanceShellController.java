package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.service.FirebaseAuthService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.lang.reflect.Method;

/** Keeps the original full dashboard intact while providing a functional navbar. */
public class FinanceShellController extends BudgetDashboardController {
    @FXML private VBox profileCard;
    @FXML private Label profileEmailLabel;
    @FXML private Button dashboardNav, analyticsNav, notificationsNav, reportsNav, goalsNav, budgetNav, transactionsNav;
    private final FirebaseAuthService shellAuthService = new FirebaseAuthService();

    @FXML private void initialize() {
        try {
            Method method = BudgetDashboardController.class.getDeclaredMethod("initialize");
            method.setAccessible(true);
            method.invoke(this);
            setupProfile();
        } catch (Exception e) { throw new RuntimeException("Could not initialize finance dashboard.", e); }
    }

    private void setupProfile() {
        AuthSession session = shellAuthService.getCurrentSession();
        if (profileEmailLabel != null) profileEmailLabel.setText(session == null || session.getEmail() == null ? "Not signed in" : session.getEmail());
    }

    private void activate(Button active) {
        Button[] buttons = {dashboardNav, analyticsNav, notificationsNav, reportsNav, goalsNav, budgetNav, transactionsNav};
        for (Button button : buttons) if (button != null) button.getStyleClass().remove("nav-button-active");
        if (active != null && !active.getStyleClass().contains("nav-button-active")) active.getStyleClass().add("nav-button-active");
        closeProfile();
    }

    private void callInheritedNavigation(String name) {
        try {
            Method method = BudgetDashboardController.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(this);
        } catch (Exception e) { throw new RuntimeException("Could not navigate to: " + name, e); }
    }

    @FXML private void handleDashboardNav(ActionEvent e) { activate(dashboardNav); callInheritedNavigation("handleDashboardNav"); }
    @FXML private void handleAnalyticsNav(ActionEvent e) { activate(analyticsNav); callInheritedNavigation("handleAnalyticsNav"); }
    @FXML private void handleNotificationsNav(ActionEvent e) { activate(notificationsNav); callInheritedNavigation("handleNotificationsNav"); }
    @FXML private void handleReportsNav(ActionEvent e) { activate(reportsNav); callInheritedNavigation("handleReportsNav"); }
    @FXML private void handleGoalsNav(ActionEvent e) { activate(goalsNav); callInheritedNavigation("handleGoalsNav"); }
    @FXML private void handleBudgetNav(ActionEvent e) { activate(budgetNav); callInheritedNavigation("handleBudgetNav"); }
    @FXML private void handleTransactionsNav(ActionEvent e) { activate(transactionsNav); callInheritedNavigation("handleTransactionsNav"); }
    @FXML private void handleAddTransactionNav(ActionEvent e) { closeProfile(); callInheritedNavigation("handleAddTransactionNav"); }

    @FXML private void handleProfileMenu() {
        if (profileCard == null) return;
        boolean visible = !profileCard.isVisible();
        profileCard.setVisible(visible); profileCard.setManaged(visible);
        if (visible) { setupProfile(); profileCard.toFront(); }
    }

    private void closeProfile() { if (profileCard != null) { profileCard.setVisible(false); profileCard.setManaged(false); } }

    @FXML private void handleLogout(ActionEvent event) { invokeDashboardAction("handleLogout", event); }
    @FXML private void handleAddTransaction(ActionEvent event) { invokeDashboardAction("handleAddTransaction", event); }
    @FXML private void handleExportCsv(ActionEvent event) { invokeDashboardAction("handleExportCsv", event); }
    @FXML private void handleSaveBudget() { invokeBudgetAction("handleSaveBudget"); }

    private void invokeDashboardAction(String name, ActionEvent event) {
        try {
            Method method = DashboardController.class.getDeclaredMethod(name, ActionEvent.class);
            method.setAccessible(true); method.invoke(this, event);
        } catch (Exception e) { throw new RuntimeException("Could not execute action: " + name, e); }
    }

    private void invokeBudgetAction(String name) {
        try {
            Method method = BudgetDashboardController.class.getDeclaredMethod(name);
            method.setAccessible(true); method.invoke(this);
        } catch (Exception e) { throw new RuntimeException("Could not execute budget action: " + name, e); }
    }
}
