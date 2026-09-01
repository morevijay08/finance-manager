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
 * Application shell for the finance manager.
 * Keeps the navbar fixed and switches between the individual finance pages.
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
            // Explicitly run the existing dashboard initialization so all existing
            // Firebase, transaction, chart, budget and table functionality remains active.
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
            profileEmailLabel.setText(
                    session == null || session.getEmail() == null
                            ? "Not signed in"
                            : session.getEmail()
            );
        }
    }

    /** Show exactly one page and keep the navbar clickable. */
    private void showPage(Node page, Button activeButton) {
        Node[] pages = {
                dashboardPage,
                analyticsPage,
                notificationsPage,
                reportsPage,
                goalsPage,
                budgetPage,
                addTransactionPage,
                transactionsPage
        };

        for (Node item : pages) {
            if (item == null) continue;

            // Each page is inside a ScrollPane. Hide/show the ScrollPane itself,
            // not only its content, otherwise an invisible page can intercept clicks.
            Node scrollPane = item.getParent();
            if (scrollPane != null) {
                boolean active = item == page;
                scrollPane.setVisible(active);
                scrollPane.setManaged(active);
                scrollPane.setMouseTransparent(!active);
            }
        }

        Button[] buttons = {
                dashboardNav,
                analyticsNav,
                notificationsNav,
                reportsNav,
                goalsNav,
                budgetNav,
                transactionsNav
        };

        for (Button button : buttons) {
            if (button != null) {
                button.getStyleClass().remove("nav-button-active");
                button.setDisable(false);
                button.setMouseTransparent(false);
            }
        }

        if (activeButton != null) {
            if (!activeButton.getStyleClass().contains("nav-button-active")) {
                activeButton.getStyleClass().add("nav-button-active");
            }
        }

        if (pageContainer != null) {
            pageContainer.setMouseTransparent(false);
            pageContainer.setOpacity(0.96);
            Platform.runLater(() -> pageContainer.setOpacity(1));
        }

        closeProfile();
    }

    private void closeProfile() {
        if (profileCard != null) {
            profileCard.setVisible(false);
            profileCard.setManaged(false);
            profileCard.setMouseTransparent(true);
        }
    }

    @FXML private void handleDashboardNav(ActionEvent e) {
        showPage(dashboardPage, dashboardNav);
    }

    @FXML private void handleAnalyticsNav(ActionEvent e) {
        showPage(analyticsPage, analyticsNav);
    }

    @FXML private void handleNotificationsNav(ActionEvent e) {
        showPage(notificationsPage, notificationsNav);
    }

    @FXML private void handleReportsNav(ActionEvent e) {
        showPage(reportsPage, reportsNav);
    }

    @FXML private void handleGoalsNav(ActionEvent e) {
        showPage(goalsPage, goalsNav);
    }

    @FXML private void handleBudgetNav(ActionEvent e) {
        showPage(budgetPage, budgetNav);
    }

    @FXML private void handleTransactionsNav(ActionEvent e) {
        showPage(transactionsPage, transactionsNav);
    }

    @FXML private void handleAddTransactionNav(ActionEvent e) {
        showPage(addTransactionPage, null);
    }

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

    /*
     * These four actions intentionally forward to DashboardController's original
     * ActionEvent-based methods. The old shell called them without an event, which
     * caused reflection to look for a no-argument method and made the buttons appear
     * clickable but do nothing.
     */
    @FXML
    private void handleLogout(ActionEvent event) {
        invokeDashboardAction("handleLogout", event);
    }

    @FXML
    private void handleAddTransaction(ActionEvent event) {
        invokeDashboardAction("handleAddTransaction", event);
    }

    @FXML
    private void handleExportCsv(ActionEvent event) {
        invokeDashboardAction("handleExportCsv", event);
    }

    @FXML
    private void handleSaveBudget() {
        invokeBudgetAction("handleSaveBudget");
    }

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
