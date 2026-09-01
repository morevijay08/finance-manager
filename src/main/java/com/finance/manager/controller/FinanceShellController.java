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

/** Application shell: fixed navbar + one functional finance page at a time. */
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
            Platform.runLater(() -> showPage(dashboardPage, dashboardNav));
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

    /**
     * A page VBox is the content of a ScrollPane. We must toggle the ScrollPane,
     * not the VBox, otherwise the empty ScrollPane remains over the other pages.
     */
    private Node pageRoot(Node page) {
        Node current = page;
        while (current != null && current.getParent() != pageContainer) {
            current = current.getParent();
        }
        return current;
    }

    private void showPage(Node page, Button activeButton) {
        if (pageContainer == null || page == null) return;
        Node selectedRoot = pageRoot(page);
        if (selectedRoot == null) return;

        // Only the selected ScrollPane is visible/clickable.
        for (Node root : pageContainer.getChildren()) {
            boolean active = root == selectedRoot;
            root.setVisible(active);
            root.setManaged(active);
            root.setMouseTransparent(!active);
        }

        Button[] buttons = {dashboardNav, analyticsNav, notificationsNav, reportsNav, goalsNav, budgetNav, transactionsNav};
        for (Button button : buttons) {
            if (button != null) {
                button.getStyleClass().remove("nav-button-active");
                button.setDisable(false);
                button.setMouseTransparent(false);
            }
        }
        if (activeButton != null && !activeButton.getStyleClass().contains("nav-button-active")) {
            activeButton.getStyleClass().add("nav-button-active");
        }

        closeProfile();
        Platform.runLater(() -> {
            if (selectedRoot instanceof javafx.scene.control.ScrollPane scroll) {
                scroll.setVvalue(0);
            }
            pageContainer.requestLayout();
        });
    }

    private void closeProfile() {
        if (profileCard != null) {
            profileCard.setVisible(false);
            profileCard.setManaged(false);
            profileCard.setMouseTransparent(true);
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
        profileCard.setMouseTransparent(!visible);
        if (visible) {
            setupProfile();
            profileCard.toFront();
        }
    }

    @FXML private void handleLogout(ActionEvent event) { invokeDashboardAction("handleLogout", event); }
    @FXML private void handleAddTransaction(ActionEvent event) { invokeDashboardAction("handleAddTransaction", event); }
    @FXML private void handleExportCsv(ActionEvent event) { invokeDashboardAction("handleExportCsv", event); }
    @FXML private void handleSaveBudget() { invokeBudgetAction("handleSaveBudget"); }

    private void invokeDashboardAction(String name, ActionEvent event) {
        try {
            Method method = DashboardController.class.getDeclaredMethod(name, ActionEvent.class);
            method.setAccessible(true);
            method.invoke(this, event);
        } catch (Exception e) {
            throw new RuntimeException("Could not execute dashboard action: " + name, e);
        }
    }

    private void invokeBudgetAction(String name) {
        try {
            Method method = BudgetDashboardController.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(this);
        } catch (Exception e) {
            throw new RuntimeException("Could not execute budget action: " + name, e);
        }
    }
}
