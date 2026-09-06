package com.finance.manager.controller;

import javafx.application.Platform;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Keeps the existing People ledger logic, but presents it inside the main
 * finance shell instead of leaving the dashboard in a separate window.
 */
public class InlinePeopleController extends PeopleController implements Initializable {

    private Parent peopleRoot;
    private VBox dashboardContent;
    private final List<Node> originalSections = new ArrayList<>();
    private boolean embedded;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            Method initialize = PeopleController.class.getDeclaredMethod("initialize");
            initialize.setAccessible(true);
            initialize.invoke(this);
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize People ledger.", e);
        }
        Platform.runLater(this::embedIntoMainDashboard);
    }

    private void embedIntoMainDashboard() {
        if (embedded) return;

        Stage peopleStage = findPeopleStage();
        if (peopleStage == null || peopleStage.getOwner() == null) {
            Platform.runLater(this::embedIntoMainDashboard);
            return;
        }

        Window owner = peopleStage.getOwner();
        if (owner.getScene() == null || owner.getScene().getRoot() == null) return;

        Node scrollNode = owner.getScene().getRoot().lookup("#dashboardScrollPane");
        if (!(scrollNode instanceof ScrollPane scrollPane) || !(scrollPane.getContent() instanceof VBox content)) {
            return;
        }

        peopleRoot = peopleStage.getScene().getRoot();
        dashboardContent = content;
        originalSections.clear();
        originalSections.addAll(content.getChildren());

        for (Node section : originalSections) {
            section.setVisible(false);
            section.setManaged(false);
            section.setMouseTransparent(true);
        }

        if (peopleRoot instanceof Node node) {
            node.setVisible(true);
            node.setManaged(true);
            node.setMouseTransparent(false);
            VBox.setVgrow(node, javafx.scene.layout.Priority.ALWAYS);
            content.getChildren().add(node);
        }

        peopleStage.setScene(null);
        peopleStage.hide();
        embedded = true;

        installNavigationRestore(owner.getScene().getRoot());
        scrollPane.setVvalue(0);
    }

    private Stage findPeopleStage() {
        for (Window window : Window.getWindows()) {
            if (window instanceof Stage stage && stage.isShowing()
                    && stage.getTitle() != null
                    && stage.getTitle().startsWith("People & Friends")) {
                return stage;
            }
        }
        return null;
    }

    private void installNavigationRestore(Node mainRoot) {
        mainRoot.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!(event.getTarget() instanceof Button button)) return;
            if (peopleRoot != null && isInside(button, peopleRoot)) return;
            restoreDashboard();
        });
    }

    private boolean isInside(Node node, Node parent) {
        Node current = node;
        while (current != null) {
            if (current == parent) return true;
            current = current.getParent();
        }
        return false;
    }

    private void restoreDashboard() {
        if (!embedded || dashboardContent == null || peopleRoot == null) return;

        dashboardContent.getChildren().remove(peopleRoot);
        for (int i = 0; i < originalSections.size(); i++) {
            Node section = originalSections.get(i);
            boolean dashboard = i == 0;
            section.setVisible(dashboard);
            section.setManaged(dashboard);
            section.setMouseTransparent(!dashboard);
        }
        embedded = false;
    }
}
