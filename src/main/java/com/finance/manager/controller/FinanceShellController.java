package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.service.FirebaseAuthService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.lang.reflect.Method;

/** Keeps the original full dashboard intact while providing a functional navbar and profile menu. */
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
            addSettingsToProfileMenu();
        } catch (Exception e) { throw new RuntimeException("Could not initialize finance dashboard.", e); }
    }

    private void setupProfile() {
        AuthSession session = shellAuthService.getCurrentSession();
        if (profileEmailLabel != null) profileEmailLabel.setText(session == null || session.getEmail() == null ? "Not signed in" : session.getEmail());
    }

    /** Adds Settings as the last account option before Logout without changing the dashboard layout. */
    private void addSettingsToProfileMenu() {
        if (profileCard == null) return;
        for (javafx.scene.Node node : profileCard.getChildren()) {
            if (node instanceof Button button && "⚙  Settings".equals(button.getText())) return;
        }
        Button settingsButton = new Button("⚙  Settings");
        settingsButton.setMaxWidth(Double.MAX_VALUE);
        settingsButton.getStyleClass().add("profile-settings");
        settingsButton.setOnAction(this::handleSettings);
        int logoutIndex = -1;
        for (int i = 0; i < profileCard.getChildren().size(); i++) {
            if (profileCard.getChildren().get(i) instanceof Button button && "Logout".equals(button.getText())) {
                logoutIndex = i;
                break;
            }
        }
        if (logoutIndex >= 0) profileCard.getChildren().add(logoutIndex, settingsButton);
        else profileCard.getChildren().add(settingsButton);
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
        profileCard.setVisible(visible);
        profileCard.setManaged(visible);
        profileCard.setMouseTransparent(!visible);
        if (visible) { setupProfile(); profileCard.toFront(); }
    }

    @FXML private void handleSettings(ActionEvent event) {
        closeProfile();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Settings.fxml"));
            Parent settingsRoot = loader.load();
            Stage settingsStage = new Stage();
            settingsStage.setTitle("Settings - Khatabook Finance Manager");
            settingsStage.initModality(Modality.WINDOW_MODAL);
            Stage owner = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
            settingsStage.initOwner(owner);
            settingsStage.setScene(new Scene(settingsRoot, 620, 520));
            settingsStage.setResizable(false);
            settingsStage.showAndWait();
        } catch (Exception e) {
            throw new RuntimeException("Could not open Settings.", e);
        }
    }

    private void closeProfile() { if (profileCard != null) { profileCard.setVisible(false); profileCard.setManaged(false); profileCard.setMouseTransparent(true); } }

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
