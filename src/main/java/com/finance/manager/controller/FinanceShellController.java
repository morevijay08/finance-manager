package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.service.FirebaseAuthService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.lang.reflect.Method;

/**
 * Application shell: keeps the navbar fixed while showing one finance section at a time.
 * The existing BudgetDashboardController remains responsible for the finance data operations.
 */
public class FinanceShellController extends BudgetDashboardController {
    @FXML private StackPane pageContainer;
    @FXML private VBox dashboardPage;
    @FXML private VBox analyticsPage;
    @FXML private VBox notificationsPage;
    @FXML private VBox reportsPage;
    @FXML private VBox goalsPage;
    @FXML private VBox budgetPage;
    @FXML private VBox addTransactionPage;
    @FXML private VBox transactionsPage;
    @FXML private VBox profileCard;
    @FXML private Label profileEmailLabel;
    @FXML private Button dashboardNav;
    @FXML private Button analyticsNav;
    @FXML private Button notificationsNav;
    @FXML private Button reportsNav;
    @FXML private Button goalsNav;
    @FXML private Button budgetNav;
    @FXML private Button transactionsNav;

    private final FirebaseAuthService shellAuthService = new FirebaseAuthService();

    @FXML
    private void initialize() {
        try {
            Method method = BudgetDashboardController.class.getDeclaredMethod("initialize");
            method.setAccessible(true);
            method.invoke(this);
            setupProfile();
            showPage(dashboardPage, dashboardNav);
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize finance application shell.", e);
        }
    }

    private void setupProfile() {
        AuthSession session = shellAuthService.getCurrentSession();
        if (profileEmailLabel != null) {
            profileEmailLabel.setText(session == null || session.getEmail() == null ? "Not signed in" : session.getEmail());
        }
    }

    private void showPage(Node page, Button activeButton) {
        Node[] pages = {dashboardPage, analyticsPage, notificationsPage, reportsPage, goalsPage, budgetPage, addTransactionPage, transactionsPage};
        for (Node item : pages) {
            if (item != null) {
                boolean active = item == page;
                item.setVisible(active);
                item.setManaged(active);
            }
        }
        Button[] buttons = {dashboardNav, analyticsNav, notificationsNav, reportsNav, goalsNav, budgetNav, transactionsNav};
        for (Button button : buttons) {
            if (button != null) button.getStyleClass().remove("nav-button-active");
        }
        if (activeButton != null && !activeButton.getStyleClass().contains("nav-button-active")) {
            activeButton.getStyleClass().add("nav-button-active");
        }
        if (pageContainer != null) pageContainer.setOpacity(0);
        Platform.runLater(() -> {
            if (pageContainer != null) pageContainer.setOpacity(1);
        });
        if (profileCard != null) {
            profileCard.setVisible(false);
            profileCard.setManaged(false);
        }
    }

    @FXML private void handleDashboardNav(ActionEvent e) { showPage(dashboardPage, dashboardNav); }
    @FXML private void handleAnalyticsNav(ActionEvent e) { showPage(analyticsPage, analyticsNav); }
    @FXML private void handleNotificationsNav(ActionEvent e) { showPage(notificationsPage, notificationsNav); }
    @FXML private void handleReportsNav(ActionEvent e) { showPage(reportsPage, reportsNav); }
    @FXML private void handleGoalsNav(ActionEvent e) { showPage(goalsPage, goalsNav); }
    @FXML private void handleBudgetNav(ActionEvent e) { showPage(budgetPage, budgetNav); }
    @FXML private void handleTransactionsNav(ActionEvent e) { showPage(transactionsPage, transactionsNav); }
    @FXML private void handleAddTransactionNav(ActionEvent e) { showPage(addTransactionPage, null); }

    @FXML
    private void handleProfileMenu() {
        if (profileCard == null) return;
        boolean visible = !profileCard.isVisible();
        profileCard.setVisible(visible);
        profileCard.setManaged(visible);
        if (visible) setupProfile();
    }

    @FXML private void handleLogout() { invokeParentAction("handleLogout"); }
    @FXML private void handleAddTransaction() { invokeParentAction("handleAddTransaction"); }
    @FXML private void handleExportCsv() { invokeParentAction("handleExportCsv"); }
    @FXML private void handleSaveBudget() { invokeParentAction("handleSaveBudget"); }

    private void invokeParentAction(String name) {
        try {
            Method method = BudgetDashboardController.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(this);
        } catch (Exception e) {
            throw new RuntimeException("Could not execute action: " + name, e);
        }
    }
}
