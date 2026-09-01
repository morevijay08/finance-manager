package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.service.FirebaseAuthService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Main application shell.
 *
 * Responsibilities:
 *  - control which navbar page is visible
 *  - keep the profile popup functional
 *  - connect dashboard quick-action buttons to the same navigation
 *    used by the navbar
 *  - delegate existing finance operations to DashboardController
 */
public class FinanceShellController extends BudgetDashboardController {

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
            Method parentInitialize = BudgetDashboardController.class.getDeclaredMethod("initialize");
            parentInitialize.setAccessible(true);
            parentInitialize.invoke(this);

            setupProfile();
            addSettingsToProfileMenu();
            setupDashboardActions();
            showSection("dashboardSection");
            activate(dashboardNav);
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize finance dashboard.", e);
        }
    }

    private void setupProfile() {
        AuthSession session = shellAuthService.getCurrentSession();
        if (profileEmailLabel == null) return;
        profileEmailLabel.setText(
                session == null || session.getEmail() == null
                        ? "Not signed in"
                        : session.getEmail()
        );
    }

    /** Adds Settings immediately above Logout without duplicating it. */
    private void addSettingsToProfileMenu() {
        if (profileCard == null) return;

        for (Node node : profileCard.getChildren()) {
            if (node instanceof Button button && "⚙  Settings".equals(button.getText())) {
                return;
            }
        }

        Button settingsButton = new Button("⚙  Settings");
        settingsButton.setMaxWidth(Double.MAX_VALUE);
        settingsButton.getStyleClass().add("profile-settings");
        settingsButton.setOnAction(this::handleSettings);

        int logoutIndex = -1;
        for (int i = 0; i < profileCard.getChildren().size(); i++) {
            Node node = profileCard.getChildren().get(i);
            if (node instanceof Button button && "Logout".equals(button.getText())) {
                logoutIndex = i;
                break;
            }
        }

        if (logoutIndex >= 0) {
            profileCard.getChildren().add(logoutIndex, settingsButton);
        } else {
            profileCard.getChildren().add(settingsButton);
        }
    }

    /**
     * Dashboard quick-action buttons are created by BudgetDashboardController.
     * They previously called the old scrolling methods directly, which meant
     * the shell could keep other pages visible. Capture those actions here and
     * switch the actual shell section instead.
     */
    private void setupDashboardActions() {
        Node dashboard = getSection("dashboardSection");
        if (!(dashboard instanceof Pane pane)) return;
        installDashboardButtonHandlers(pane);
    }

    private void installDashboardButtonHandlers(Pane parent) {
        for (Node node : parent.getChildren()) {
            if (node instanceof Button button) {
                String text = button.getText();
                if (text == null) continue;

                if (text.contains("Add Transaction")) {
                    button.addEventFilter(ActionEvent.ACTION, event -> {
                        event.consume();
                        activate(null);
                        showSection("addTransactionSection");
                    });
                } else if (text.contains("Recent Activity") || text.contains("View all")) {
                    button.addEventFilter(ActionEvent.ACTION, event -> {
                        event.consume();
                        activate(transactionsNav);
                        showSection("transactionsSection");
                    });
                } else if (text.contains("Analytics")) {
                    button.addEventFilter(ActionEvent.ACTION, event -> {
                        event.consume();
                        activate(analyticsNav);
                        showSection("analyticsSection");
                    });
                } else if (text.contains("Reports")) {
                    button.addEventFilter(ActionEvent.ACTION, event -> {
                        event.consume();
                        activate(reportsNav);
                        showSection("reportsSection");
                    });
                } else if (text.contains("Goals")) {
                    button.addEventFilter(ActionEvent.ACTION, event -> {
                        event.consume();
                        activate(goalsNav);
                        showSection("goalsSection");
                    });
                } else if (text.contains("Alerts")) {
                    button.addEventFilter(ActionEvent.ACTION, event -> {
                        event.consume();
                        activate(notificationsNav);
                        showSection("notificationsSection");
                    });
                } else if (text.contains("Budget")) {
                    button.addEventFilter(ActionEvent.ACTION, event -> {
                        event.consume();
                        activate(budgetNav);
                        showSection("budgetSection");
                    });
                }
            } else if (node instanceof Pane child) {
                installDashboardButtonHandlers(child);
            }
        }
    }

    private void activate(Button active) {
        Button[] navButtons = {
                dashboardNav,
                analyticsNav,
                notificationsNav,
                reportsNav,
                goalsNav,
                budgetNav,
                transactionsNav
        };

        for (Button button : navButtons) {
            if (button != null) {
                button.getStyleClass().remove("nav-button-active");
            }
        }

        if (active != null && !active.getStyleClass().contains("nav-button-active")) {
            active.getStyleClass().add("nav-button-active");
        }

        closeProfile();
    }

    /** Shows exactly one application section at a time. */
    private void showSection(String selectedField) {
        String[] sections = {
                "dashboardSection",
                "analyticsSection",
                "notificationsSection",
                "reportsSection",
                "goalsSection",
                "budgetSection",
                "addTransactionSection",
                "transactionsSection"
        };

        for (String sectionName : sections) {
            Node section = getSection(sectionName);
            if (section == null) continue;

            boolean selected = sectionName.equals(selectedField);
            section.setVisible(selected);
            section.setManaged(selected);
            section.setMouseTransparent(!selected);
        }

        Node selected = getSection(selectedField);
        if (selected != null) selected.toFront();
    }

    private Node getSection(String fieldName) {
        try {
            Field field = BudgetDashboardController.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (Node) field.get(this);
        } catch (Exception e) {
            throw new RuntimeException("Could not find dashboard section: " + fieldName, e);
        }
    }

    @FXML private void handleDashboardNav(ActionEvent event) {
        activate(dashboardNav);
        showSection("dashboardSection");
    }

    @FXML private void handleAnalyticsNav(ActionEvent event) {
        activate(analyticsNav);
        showSection("analyticsSection");
    }

    @FXML private void handleNotificationsNav(ActionEvent event) {
        activate(notificationsNav);
        showSection("notificationsSection");
    }

    @FXML private void handleReportsNav(ActionEvent event) {
        activate(reportsNav);
        showSection("reportsSection");
    }

    @FXML private void handleGoalsNav(ActionEvent event) {
        activate(goalsNav);
        showSection("goalsSection");
    }

    @FXML private void handleBudgetNav(ActionEvent event) {
        activate(budgetNav);
        showSection("budgetSection");
    }

    @FXML private void handleTransactionsNav(ActionEvent event) {
        activate(transactionsNav);
        showSection("transactionsSection");
    }

    @FXML private void handleAddTransactionNav(ActionEvent event) {
        activate(null);
        showSection("addTransactionSection");
    }

    @FXML private void handleProfileMenu() {
        if (profileCard == null) return;

        boolean show = !profileCard.isVisible();
        profileCard.setVisible(show);
        profileCard.setManaged(show);
        profileCard.setMouseTransparent(!show);

        if (show) {
            setupProfile();
            profileCard.toFront();
        }
    }

    @FXML private void handleSettings(ActionEvent event) {
        closeProfile();
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/Settings.fxml")
            );
            Parent settingsRoot = loader.load();

            Stage settingsStage = new Stage();
            settingsStage.setTitle("Settings - Khatabook Finance Manager");
            settingsStage.initModality(Modality.WINDOW_MODAL);

            Stage owner = (Stage) ((Node) event.getSource()).getScene().getWindow();
            settingsStage.initOwner(owner);
            settingsStage.setScene(new Scene(settingsRoot, 620, 520));
            settingsStage.setResizable(false);
            settingsStage.showAndWait();
        } catch (Exception e) {
            throw new RuntimeException("Could not open Settings.", e);
        }
    }

    private void closeProfile() {
        if (profileCard == null) return;
        profileCard.setVisible(false);
        profileCard.setManaged(false);
        profileCard.setMouseTransparent(true);
    }

    @FXML private void handleLogout(ActionEvent event) {
        invokeDashboardActionWithEvent("handleLogout", event);
    }

    @FXML private void handleAddTransaction(ActionEvent event) {
        invokeDashboardAction("handleAddTransaction");
    }

    @FXML private void handleExportCsv(ActionEvent event) {
        invokeDashboardAction("handleExportCsv");
    }

    @FXML private void handleSaveBudget() {
        invokeBudgetAction("handleSaveBudget");
    }

    private void invokeDashboardAction(String methodName) {
        try {
            Method method = DashboardController.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(this);
        } catch (Exception e) {
            throw new RuntimeException("Could not execute action: " + methodName, e);
        }
    }

    private void invokeDashboardActionWithEvent(String methodName, ActionEvent event) {
        try {
            Method method = DashboardController.class.getDeclaredMethod(
                    methodName,
                    ActionEvent.class
            );
            method.setAccessible(true);
            method.invoke(this, event);
        } catch (Exception e) {
            throw new RuntimeException("Could not execute action: " + methodName, e);
        }
    }

    private void invokeBudgetAction(String methodName) {
        try {
            Method method = BudgetDashboardController.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(this);
        } catch (Exception e) {
            throw new RuntimeException("Could not execute budget action: " + methodName, e);
        }
    }
}
