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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PeopleController {
    @FXML private ListView<String> peopleList;
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

    @FXML
    private void initialize() {
        setupTable();
        peopleList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> showPerson(newValue));
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
        repository.getTransactions(session).thenAccept(list -> Platform.runLater(() -> { allTransactions.setAll(list); rebuildPeople(); })).exceptionally(error -> { Platform.runLater(() -> selectedPersonLabel.setText("Could not load people ledger.")); return null; });
    }

    public void refresh() { load(); }

    private void rebuildPeople() {
        String selected = peopleList.getSelectionModel().getSelectedItem();
        Map<String, Double> balances = new LinkedHashMap<>();
        for (Transaction transaction : allTransactions) {
            String person = transaction.getPersonName();
            if (person == null || person.isBlank()) continue;
            double delta = transaction.getType() == Transaction.Type.EXPENSE ? transaction.getAmount() : -transaction.getAmount();
            balances.merge(person.trim(), delta, Double::sum);
        }
        peopleList.setItems(FXCollections.observableArrayList(balances.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()));
        if (selected != null && balances.containsKey(selected)) peopleList.getSelectionModel().select(selected);
        else if (!peopleList.getItems().isEmpty()) peopleList.getSelectionModel().select(0);
        else clearDetails();
    }

    private void showPerson(String person) {
        if (person == null || person.isBlank()) { clearDetails(); return; }
        selectedPersonLabel.setText(person);
        selectedTransactions.setAll(allTransactions.stream().filter(t -> person.equalsIgnoreCase(t.getPersonName())).sorted((a,b) -> b.getDate().compareTo(a.getDate())).toList());
        double given = selectedTransactions.stream().filter(t -> t.getType() == Transaction.Type.EXPENSE).mapToDouble(Transaction::getAmount).sum();
        double received = selectedTransactions.stream().filter(t -> t.getType() == Transaction.Type.INCOME).mapToDouble(Transaction::getAmount).sum();
        givenLabel.setText(formatMoney(given));
        receivedLabel.setText(formatMoney(received));
        balanceLabel.setText(formatMoney(given - received));
    }

    private void clearDetails() { selectedPersonLabel.setText("Select a person"); givenLabel.setText(formatMoney(0)); receivedLabel.setText(formatMoney(0)); balanceLabel.setText(formatMoney(0)); selectedTransactions.clear(); }
    private String formatMoney(double amount) { return String.format(java.util.Locale.US, "₹%,.2f", amount); }
}
