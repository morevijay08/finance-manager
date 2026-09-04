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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class FinanceShellController extends BudgetDashboardController {
    @FXML private VBox profileCard;
    @FXML private Label profileEmailLabel;
    @FXML private VBox sidebar;
    @FXML private Button menuToggle;
    @FXML private ScrollPane dashboardScrollPane;
    @FXML private Button dashboardNav, analyticsNav, notificationsNav, reportsNav, goalsNav, budgetNav, transactionsNav, addTransactionNav;
    private final FirebaseAuthService shellAuthService = new FirebaseAuthService();
    private Stage peopleStage;
    private PeopleController peopleController;

    @FXML
    protected void initialize() {
        super.initialize();
        setupSidebar();
        setupPeopleFeature();
        setupProfile();
        addSettingsToProfileMenu();
        setupDashboardActions();
        setupTransactionEditNavigation();
        showSection("dashboardSection");
        activate(dashboardNav);
    }

    private void setupPeopleFeature() {
        installPersonFields();
        if (sidebar == null) return;
        Button peopleButton = new Button("👥  People");
        peopleButton.setMaxWidth(Double.MAX_VALUE);
        peopleButton.setPrefHeight(44);
        peopleButton.setMinHeight(44);
        peopleButton.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        peopleButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #dbeafe; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 10px; -fx-border-color: transparent; -fx-padding: 0 14px;");
        peopleButton.setOnMouseEntered(e -> peopleButton.setStyle("-fx-background-color: rgba(59,130,246,0.14); -fx-text-fill: #ffffff; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 10px; -fx-padding: 0 14px;"));
        peopleButton.setOnMouseExited(e -> { if (peopleStage == null || !peopleStage.isShowing()) peopleButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #dbeafe; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 10px; -fx-border-color: transparent; -fx-padding: 0 14px;"); });
        peopleButton.setOnAction(e -> openPeopleWindow());
        int insertAt = sidebar.getChildren().size();
        for (int i = 0; i < sidebar.getChildren().size(); i++) {
            Node node = sidebar.getChildren().get(i);
            if (node instanceof Label label && "QUICK ACTION".equals(label.getText())) { insertAt = i; break; }
        }
        sidebar.getChildren().add(insertAt, peopleButton);
    }

    private void installPersonFields() {
        try {
            Field personFieldRef = DashboardController.class.getDeclaredField("personField");
            Field flowFieldRef = DashboardController.class.getDeclaredField("personFlowCombo");
            personFieldRef.setAccessible(true); flowFieldRef.setAccessible(true);
            TextField personField = new TextField();
            personField.setPromptText("Friend / Person name (optional)");
            personField.setPrefWidth(190);
            ComboBox<String> flowCombo = new ComboBox<>();
            flowCombo.setItems(javafx.collections.FXCollections.observableArrayList("I GAVE", "I RECEIVED"));
            flowCombo.setValue("I GAVE"); flowCombo.setPrefWidth(150);
            personFieldRef.set(this, personField); flowFieldRef.set(this, flowCombo);

            Field sectionRef = DashboardController.class.getDeclaredField("addTransactionSection");
            sectionRef.setAccessible(true);
            Object sectionObject = sectionRef.get(this);
            if (!(sectionObject instanceof VBox section)) return;
            GridPane grid = null;
            for (Node child : section.getChildren()) if (child instanceof GridPane existing) { grid = existing; break; }
            if (grid == null) return;
            Label personLabel = new Label("Person / Friend"); personLabel.getStyleClass().add("field-label"); GridPane.setColumnIndex(personLabel, 0); GridPane.setRowIndex(personLabel, 2);
            Label flowLabel = new Label("Money flow"); flowLabel.getStyleClass().add("field-label"); GridPane.setColumnIndex(flowLabel, 1); GridPane.setRowIndex(flowLabel, 2);
            GridPane.setColumnIndex(personField, 0); GridPane.setRowIndex(personField, 3);
            GridPane.setColumnIndex(flowCombo, 1); GridPane.setRowIndex(flowCombo, 3);
            grid.getChildren().addAll(personLabel, flowLabel, personField, flowCombo);
        } catch (Exception e) {
            throw new RuntimeException("Could not configure person transaction fields.", e);
        }
    }

    private void openPeopleWindow() {
        try {
            if (peopleStage != null && peopleStage.isShowing()) { peopleStage.toFront(); peopleController.refresh(); return; }
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/People.fxml"));
            Parent root = loader.load();
            peopleController = loader.getController();
            peopleStage = new Stage();
            peopleStage.setTitle("People & Friends - Hisaabi Finance Manager");
            peopleStage.initModality(Modality.WINDOW_MODAL);
            Stage owner = dashboardScrollPane == null ? null : (Stage) dashboardScrollPane.getScene().getWindow();
            if (owner != null) peopleStage.initOwner(owner);
            peopleStage.setScene(new Scene(root, 1100, 700));
            peopleStage.setMinWidth(900); peopleStage.setMinHeight(600);
            peopleStage.show();
            peopleStage.setMaximized(true);
            peopleController.refresh();
        } catch (Exception e) { throw new RuntimeException("Could not open People ledger.", e); }
    }

    @Override
    protected void onTransactionsChanged() {
        if (peopleController != null) peopleController.refresh();
    }

    /** Convert the existing FXML sidebar into a permanent, polished left column. */
    private void setupSidebar() {
        if (sidebar == null) return;
        sidebar.setVisible(true); sidebar.setManaged(true); sidebar.setMouseTransparent(false); sidebar.setMaxHeight(Double.MAX_VALUE); sidebar.setTranslateY(0);
        sidebar.setStyle("-fx-background-color: linear-gradient(to bottom, #0b1224 0%, #111c3d 48%, #172554 100%);-fx-padding: 22px 14px 18px 14px;-fx-spacing: 8px;-fx-border-color: transparent #263453 transparent transparent;-fx-border-width: 0 1px 0 0;");
        if (menuToggle != null) { menuToggle.setVisible(false); menuToggle.setManaged(false); menuToggle.setMouseTransparent(true); }
        if (dashboardScrollPane != null && dashboardScrollPane.getParent() instanceof BorderPane borderPane) { if (sidebar.getParent() instanceof Pane parent) parent.getChildren().remove(sidebar); borderPane.setLeft(sidebar); }
        for (Node node : sidebar.getChildren()) {
            if (node instanceof Button button) {
                button.setMaxWidth(Double.MAX_VALUE); button.setPrefHeight(44); button.setMinHeight(44); button.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                if (!button.getText().contains("People")) button.setStyle("-fx-background-color: transparent; -fx-text-fill: #dbeafe; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 10px; -fx-border-color: transparent; -fx-padding: 0 14px;");
            } else if (node instanceof Label label) {
                String text = label.getText();
                if ("NAVIGATION".equals(text) || "QUICK ACTION".equals(text)) label.setStyle("-fx-text-fill: #64748b; -fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 12px 10px 4px 10px;");
                else if ("MENU".equals(text)) label.setStyle("-fx-text-fill: #60a5fa; -fx-font-size: 9px; -fx-font-weight: bold;");
            }
        }
        if (!sidebar.getChildren().isEmpty() && sidebar.getChildren().get(0) instanceof Pane header) {
            header.setStyle("-fx-padding: 2px 6px 17px 6px; -fx-border-color: transparent transparent #263453 transparent; -fx-border-width: 0 0 1px 0;");
            for (Node child : header.getChildren()) if (child instanceof VBox brandBox) for (Node brandNode : brandBox.getChildren()) if (brandNode instanceof Label label && "Hisaabi".equals(label.getText())) label.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 21px; -fx-font-weight: 800;"); else if (brandNode instanceof Label label) label.setStyle("-fx-text-fill: #60a5fa; -fx-font-size: 9px; -fx-font-weight: bold; -fx-letter-spacing: 0.5px;");
        }
        if (addTransactionNav != null) addTransactionNav.setStyle("-fx-background-color: linear-gradient(to right, #2563eb, #4f46e5); -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 10px; -fx-padding: 0 14px; -fx-effect: dropshadow(gaussian, rgba(37,99,235,0.35), 10, 0.18, 0, 3);");
    }

    private void setupProfile() { AuthSession session = shellAuthService.getCurrentSession(); if (profileEmailLabel != null) profileEmailLabel.setText(session == null || session.getEmail() == null ? "Not signed in" : session.getEmail()); }
    private void addSettingsToProfileMenu() { if (profileCard == null) return; for (Node node : profileCard.getChildren()) if (node instanceof Button button && "⚙  Settings".equals(button.getText())) return; Button settingsButton = new Button("⚙  Settings"); settingsButton.setMaxWidth(Double.MAX_VALUE); settingsButton.getStyleClass().add("profile-settings"); settingsButton.setOnAction(this::handleSettings); int logoutIndex = -1; for (int i = 0; i < profileCard.getChildren().size(); i++) if (profileCard.getChildren().get(i) instanceof Button button && "Logout".equals(button.getText())) { logoutIndex = i; break; } if (logoutIndex >= 0) profileCard.getChildren().add(logoutIndex, settingsButton); else profileCard.getChildren().add(settingsButton); }
    private void setupDashboardActions() { Node dashboard = getSection("dashboardSection"); if (dashboard instanceof Pane pane) installDashboardButtonHandlers(pane); }
    private void setupTransactionEditNavigation() { try { Field field = DashboardController.class.getDeclaredField("transactionTable"); field.setAccessible(true); Object value = field.get(this); if (!(value instanceof TableView<?> table)) return; table.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> { Node target = event.getPickResult().getIntersectedNode(); while (target != null && target != table) { if (target instanceof Button button && "Edit".equals(button.getText())) { javafx.application.Platform.runLater(() -> { activate(null); if (addTransactionNav != null) addTransactionNav.getStyleClass().add("navbar-add-button-active"); showSection("addTransactionSection"); if (dashboardScrollPane != null) dashboardScrollPane.setVvalue(0); }); break; } target = target.getParent(); } }); } catch (Exception e) { throw new RuntimeException("Could not configure transaction editing.", e); } }
    private void installDashboardButtonHandlers(Pane parent) { for (Node node : parent.getChildren()) { if (node instanceof Button button) { String text = button.getText(); if (text == null) continue; if (text.contains("Add Transaction")) button.addEventFilter(ActionEvent.ACTION, e -> { e.consume(); handleAddTransactionNav(e); }); else if (text.contains("Recent Activity") || text.contains("View all")) button.addEventFilter(ActionEvent.ACTION, e -> { e.consume(); activate(transactionsNav); showSection("transactionsSection"); }); else if (text.contains("Analytics")) button.addEventFilter(ActionEvent.ACTION, e -> { e.consume(); activate(analyticsNav); showSection("analyticsSection"); }); else if (text.contains("Reports")) button.addEventFilter(ActionEvent.ACTION, e -> { e.consume(); activate(reportsNav); showSection("reportsSection"); }); else if (text.contains("Goals")) button.addEventFilter(ActionEvent.ACTION, e -> { e.consume(); activate(goalsNav); showSection("goalsSection"); }); else if (text.contains("Alerts")) button.addEventFilter(ActionEvent.ACTION, e -> { e.consume(); activate(notificationsNav); showSection("notificationsSection"); }); else if (text.contains("Budget")) button.addEventFilter(ActionEvent.ACTION, e -> { e.consume(); activate(budgetNav); showSection("budgetSection"); }); } else if (node instanceof Pane child) installDashboardButtonHandlers(child); } }
    private void activate(Button active) { Button[] buttons = {dashboardNav, analyticsNav, notificationsNav, reportsNav, goalsNav, budgetNav, transactionsNav}; for (Button button : buttons) if (button != null) button.getStyleClass().remove("nav-button-active"); if (addTransactionNav != null) addTransactionNav.getStyleClass().remove("navbar-add-button-active"); if (active != null && !active.getStyleClass().contains("nav-button-active")) active.getStyleClass().add("nav-button-active"); applyActiveStyle(active); closeProfile(); }
    private void applyActiveStyle(Button active) { Button[] buttons = {dashboardNav, analyticsNav, notificationsNav, reportsNav, goalsNav, budgetNav, transactionsNav}; for (Button button : buttons) { if (button == null) continue; if (button == active) button.setStyle("-fx-background-color: linear-gradient(to right, #2563eb, #4f46e5); -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 10px; -fx-padding: 0 14px; -fx-effect: dropshadow(gaussian, rgba(37,99,235,0.38), 12, 0.18, 0, 3);"); else button.setStyle("-fx-background-color: transparent; -fx-text-fill: #dbeafe; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 10px; -fx-border-color: transparent; -fx-padding: 0 14px;"); } }
    private void showSection(String selectedField) { String[] sections = {"dashboardSection", "analyticsSection", "notificationsSection", "reportsSection", "goalsSection", "budgetSection", "addTransactionSection", "transactionsSection"}; for (String name : sections) { Node section = getSection(name); if (section != null) { boolean selected = name.equals(selectedField); section.setVisible(selected); section.setManaged(selected); section.setMouseTransparent(!selected); } } Node selected = getSection(selectedField); if (selected != null) selected.toFront(); }
    private Node getSection(String fieldName) { try { Field field = BudgetDashboardController.class.getDeclaredField(fieldName); field.setAccessible(true); return (Node) field.get(this); } catch (Exception e) { throw new RuntimeException("Could not find dashboard section: " + fieldName, e); } }
    @FXML private void handleSidebarToggle() { if (sidebar != null) { sidebar.setVisible(true); sidebar.setManaged(true); sidebar.setMouseTransparent(false); } }
    @FXML private void handleDashboardNav(ActionEvent event) { activate(dashboardNav); showSection("dashboardSection"); }
    @FXML private void handleAnalyticsNav(ActionEvent event) { activate(analyticsNav); showSection("analyticsSection"); }
    @FXML private void handleNotificationsNav(ActionEvent event) { activate(notificationsNav); showSection("notificationsSection"); }
    @FXML private void handleReportsNav(ActionEvent event) { activate(reportsNav); showSection("reportsSection"); }
    @FXML private void handleGoalsNav(ActionEvent event) { activate(goalsNav); showSection("goalsSection"); }
    @FXML private void handleBudgetNav(ActionEvent event) { activate(budgetNav); showSection("budgetSection"); }
    @FXML private void handleTransactionsNav(ActionEvent event) { activate(transactionsNav); showSection("transactionsSection"); }
    @FXML private void handleAddTransactionNav(ActionEvent event) { activate(null); if (addTransactionNav != null) addTransactionNav.getStyleClass().add("navbar-add-button-active"); showSection("addTransactionSection"); }
    @FXML private void handleProfileMenu() { if (profileCard == null) return; boolean show = !profileCard.isVisible(); profileCard.setVisible(show); profileCard.setManaged(show); profileCard.setMouseTransparent(!show); if (show) { setupProfile(); profileCard.toFront(); } }
    @FXML private void handleSettings(ActionEvent event) { closeProfile(); try { FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Settings.fxml")); Parent settingsRoot = loader.load(); Stage settingsStage = new Stage(); settingsStage.setTitle("Settings - Hisaabi Finance Manager"); settingsStage.initModality(Modality.WINDOW_MODAL); Stage owner = (Stage) ((Node) event.getSource()).getScene().getWindow(); settingsStage.initOwner(owner); settingsStage.setScene(new Scene(settingsRoot, 620, 520)); settingsStage.setResizable(false); settingsStage.showAndWait(); } catch (Exception e) { throw new RuntimeException("Could not open Settings.", e); } }
    private void closeProfile() { if (profileCard != null) { profileCard.setVisible(false); profileCard.setManaged(false); profileCard.setMouseTransparent(true); } }
    @FXML protected void handleLogout(ActionEvent event) { shellAuthService.logout(); closeProfile(); try { FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml")); Parent loginRoot = loader.load(); Scene loginScene = new Scene(loginRoot, 900, 600); loginScene.getStylesheets().add(MainStylesheetHolder.getStylesheet()); Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow(); stage.setScene(loginScene); stage.setTitle("Hisaabi Finance Manager"); stage.setMinWidth(800); stage.setMinHeight(500); stage.show(); maximizeStage(stage); javafx.application.Platform.runLater(() -> maximizeStage(stage)); javafx.application.Platform.runLater(() -> javafx.application.Platform.runLater(() -> maximizeStage(stage))); } catch (Exception e) { e.printStackTrace(); } }
    private void maximizeStage(Stage stage) { if (stage == null) return; stage.setIconified(false); stage.setMaximized(true); if (stage.isShowing() && !stage.isMaximized()) { javafx.geometry.Rectangle2D bounds = javafx.stage.Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()).stream().findFirst().orElse(javafx.stage.Screen.getPrimary()).getVisualBounds(); stage.setX(bounds.getMinX()); stage.setY(bounds.getMinY()); stage.setWidth(bounds.getWidth()); stage.setHeight(bounds.getHeight()); } }
    @FXML private void handleAddTransaction(ActionEvent event) { invokeDashboardAction("handleAddTransaction"); }
    @FXML private void handleExportCsv(ActionEvent event) { invokeDashboardAction("handleExportCsv"); }
    @FXML protected void handleSaveBudget() { invokeBudgetAction("handleSaveBudget"); }
    private void invokeDashboardAction(String methodName) { try { Method method = DashboardController.class.getDeclaredMethod(methodName); method.setAccessible(true); method.invoke(this); } catch (Exception e) { throw new RuntimeException("Could not execute action: " + methodName, e); } }
    private void invokeBudgetAction(String methodName) { try { Method method = BudgetDashboardController.class.getDeclaredMethod(methodName); method.setAccessible(true); method.invoke(this); } catch (Exception e) { throw new RuntimeException("Could not execute budget action: " + methodName, e); } }
    private static final class MainStylesheetHolder { private static String getStylesheet() { return FinanceShellController.class.getResource("/css/application.css").toExternalForm(); } }
}
