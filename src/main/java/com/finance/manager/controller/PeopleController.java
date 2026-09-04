package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.model.Transaction;
import com.finance.manager.repository.FirestoreTransactionRepository;
import com.finance.manager.service.FirebaseAuthService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.prefs.Preferences;

public class PeopleController {
    @FXML private ListView<String> peopleList;
    @FXML private Button addPersonButton;
    @FXML private Label selectedPersonLabel;
    @FXML private Label givenLabel;
    @FXML private Label receivedLabel;
    @FXML private Label balanceLabel;
    @FXML private TableView<Transaction> transactionTable;
    @FXML private TableColumn<Transaction, LocalDate> dateColumn;
    @FXML private TableColumn<Transaction, String> directionColumn;
    @FXML private TableColumn<Transaction, Number> amountColumn;
    @FXML private TableColumn<Transaction, String> descriptionColumn;

    private final FirebaseAuthService authService = new FirebaseAuthService();
    private final FirestoreTransactionRepository repository = new FirestoreTransactionRepository();
    private final ObservableList<Transaction> allTransactions = FXCollections.observableArrayList();
    private final ObservableList<Transaction> selectedTransactions = FXCollections.observableArrayList();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final String PEOPLE_KEY_PREFIX = "savedPeople.";
    private String peopleKey;

    @FXML
    private void initialize() {
        setupTable();
        peopleList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> showPerson(newValue));
        if (addPersonButton != null) addPersonButton.setOnAction(event -> handleAddPerson());
        load();
    }

    private void setupTable() {
        dateColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getDate()));
        dateColumn.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(LocalDate item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? null : item.format(DATE_FORMAT)); }
        });
        directionColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getType() == Transaction.Type.EXPENSE ? "I GAVE" : "I RECEIVED"));
        amountColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleDoubleProperty(data.getValue().getAmount()));
        descriptionColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDescription()));
        transactionTable.setItems(selectedTransactions);
    }

    private void load() {
        AuthSession session = authService.getCurrentSession();
        if (session == null) { selectedPersonLabel.setText("Please sign in again"); return; }
        peopleKey = PEOPLE_KEY_PREFIX + session.getEmail().toLowerCase(Locale.ROOT);
        repository.getTransactions(session).thenAccept(list -> Platform.runLater(() -> { allTransactions.setAll(list); rebuildPeople(); })).exceptionally(error -> { Platform.runLater(() -> selectedPersonLabel.setText("Could not load people ledger.")); return null; });
    }

    public void refresh() { load(); }

    private void handleAddPerson() {
        AuthSession session = authService.getCurrentSession();
        if (session == null) { showMessage("Please sign in again.", Alert.AlertType.WARNING); return; }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Person");
        dialog.setHeaderText("Add a friend or person");
        dialog.setContentText("Person name:");
        dialog.showAndWait().ifPresent(input -> {
            String name = input == null ? "" : input.trim();
            if (name.isBlank()) { showMessage("Please enter a name.", Alert.AlertType.WARNING); return; }
            Set<String> names = loadSavedPeople();
            String existing = names.stream().filter(n -> n.equalsIgnoreCase(name)).findFirst().orElse(null);
            if (existing != null) { peopleList.getSelectionModel().select(existing); return; }
            names.add(name);
            savePeople(names);
            rebuildPeople();
            peopleList.getSelectionModel().select(name);
        });
    }

    private Set<String> loadSavedPeople() {
        Set<String> names = new LinkedHashSet<>();
        if (peopleKey == null) return names;
        String saved = Preferences.userNodeForPackage(PeopleController.class).get(peopleKey, "");
        if (!saved.isBlank()) for (String name : saved.split("\\|", -1)) if (!name.isBlank()) names.add(name.trim());
        return names;
    }

    private void savePeople(Set<String> names) {
        if (peopleKey == null) return;
        Preferences.userNodeForPackage(PeopleController.class).put(peopleKey, String.join("|", names));
    }

    private void rebuildPeople() {
        String selected = peopleList.getSelectionModel().getSelectedItem();
        Set<String> names = loadSavedPeople();
        for (Transaction transaction : allTransactions) {
            String person = transaction.getPersonName();
            if (person != null && !person.isBlank()) names.add(person.trim());
        }
        savePeople(names);
        List<String> sorted = names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        peopleList.setItems(FXCollections.observableArrayList(sorted));
        if (selected != null && names.stream().anyMatch(n -> n.equalsIgnoreCase(selected))) {
            String exact = names.stream().filter(n -> n.equalsIgnoreCase(selected)).findFirst().orElse(selected);
            peopleList.getSelectionModel().select(exact);
        } else if (!peopleList.getItems().isEmpty()) peopleList.getSelectionModel().select(0);
        else clearDetails();
    }

    private void showPerson(String person) {
        if (person == null || person.isBlank()) { clearDetails(); return; }
        selectedPersonLabel.setText(person);
        selectedTransactions.setAll(allTransactions.stream().filter(t -> person.equalsIgnoreCase(t.getPersonName())).sorted((a,b) -> {
            LocalDate ad = a.getDate() == null ? LocalDate.MIN : a.getDate();
            LocalDate bd = b.getDate() == null ? LocalDate.MIN : b.getDate();
            return bd.compareTo(ad);
        }).toList());
        double given = selectedTransactions.stream().filter(t -> t.getType() == Transaction.Type.EXPENSE).mapToDouble(Transaction::getAmount).sum();
        double received = selectedTransactions.stream().filter(t -> t.getType() == Transaction.Type.INCOME).mapToDouble(Transaction::getAmount).sum();
        givenLabel.setText(formatMoney(given));
        receivedLabel.setText(formatMoney(received));
        balanceLabel.setText(formatMoney(given - received));
    }

    private void clearDetails() { selectedPersonLabel.setText("Select a person"); givenLabel.setText(formatMoney(0)); receivedLabel.setText(formatMoney(0)); balanceLabel.setText(formatMoney(0)); selectedTransactions.clear(); }
    private void showMessage(String message, Alert.AlertType type) { Alert alert = new Alert(type, message, ButtonType.OK); alert.setHeaderText(null); alert.showAndWait(); }
    private String formatMoney(double amount) { return String.format(Locale.US, "₹%,.2f", amount); }
}
