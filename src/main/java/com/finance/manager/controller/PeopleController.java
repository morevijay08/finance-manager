package com.finance.manager.controller;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.model.Transaction;
import com.finance.manager.repository.FirestoreTransactionRepository;
import com.finance.manager.service.FirebaseAuthService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.prefs.Preferences;

public class PeopleController {
    @FXML private ListView<String> peopleList;
    @FXML private Button addPersonButton, addTransactionButton;
    @FXML private Label selectedPersonLabel, givenLabel, receivedLabel, balanceLabel, hintLabel;
    @FXML private TableView<Transaction> transactionTable;
    @FXML private TableColumn<Transaction, LocalDate> dateColumn;
    @FXML private TableColumn<Transaction, String> directionColumn, descriptionColumn, attachmentColumn;
    @FXML private TableColumn<Transaction, Number> amountColumn;
    @FXML private TableColumn<Transaction, Void> actionColumn;
    private final FirebaseAuthService authService=new FirebaseAuthService();
    private final FirestoreTransactionRepository repository=new FirestoreTransactionRepository();
    private final ObservableList<Transaction> allTransactions=FXCollections.observableArrayList();
    private final ObservableList<Transaction> selectedTransactions=FXCollections.observableArrayList();
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final String KEY="savedPeople.";
    private String peopleKey;

    @FXML private void initialize(){
        setupTable(); peopleList.getSelectionModel().selectedItemProperty().addListener((o,a,b)->showPerson(b));
        addPersonButton.setOnAction(e->addPerson()); addTransactionButton.setOnAction(e->openTransactionDialog(null)); load();
    }
    private void setupTable(){
        dateColumn.setCellValueFactory(d->new javafx.beans.property.SimpleObjectProperty<>(d.getValue().getDate()));
        dateColumn.setCellFactory(c->new TableCell<>(){protected void updateItem(LocalDate v,boolean e){super.updateItem(v,e);setText(e||v==null?null:v.format(DATE));}});
        directionColumn.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getType()==Transaction.Type.EXPENSE?"I GAVE":"I RECEIVED"));
        amountColumn.setCellValueFactory(d->new javafx.beans.property.SimpleDoubleProperty(d.getValue().getAmount()));
        descriptionColumn.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getDescription()));
        attachmentColumn.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(attachmentText(d.getValue())));
        actionColumn.setCellFactory(c->new TableCell<>(){final Button edit=new Button("Edit"),del=new Button("Delete");final javafx.scene.layout.HBox box=new javafx.scene.layout.HBox(6,edit,del);{edit.setOnAction(e->openTransactionDialog(getTableView().getItems().get(getIndex())));del.setOnAction(e->deleteTransaction(getTableView().getItems().get(getIndex())));}protected void updateItem(Void v,boolean e){super.updateItem(v,e);setGraphic(e?null:box);}});
        transactionTable.setItems(selectedTransactions);
    }
    private void load(){
        AuthSession s=authService.getCurrentSession();if(s==null){selectedPersonLabel.setText("Please sign in again");return;}peopleKey=KEY+s.getLocalId();
        repository.getTransactions(s).thenAccept(list->Platform.runLater(()->{allTransactions.setAll(list);rebuildPeople();})).exceptionally(e->{Platform.runLater(()->selectedPersonLabel.setText("Could not load people ledger."));return null;});
    }
    public void refresh(){load();}
    private Set<String> savedPeople(){Set<String> n=new LinkedHashSet<>();String v=Preferences.userNodeForPackage(PeopleController.class).get(peopleKey,"");if(!v.isBlank())for(String x:v.split("\\|",-1))if(!x.isBlank())n.add(x.trim());return n;}
    private void savePeople(Set<String> n){Preferences.userNodeForPackage(PeopleController.class).put(peopleKey,String.join("|",n));}
    private void addPerson(){TextInputDialog d=new TextInputDialog();d.setTitle("Add Person");d.setHeaderText("Add a friend or person");d.setContentText("Person name:");d.showAndWait().ifPresent(v->{String name=v.trim();if(name.isBlank()){error("Please enter a name.");return;}Set<String> n=savedPeople();if(n.stream().anyMatch(x->x.equalsIgnoreCase(name))){peopleList.getSelectionModel().select(n.stream().filter(x->x.equalsIgnoreCase(name)).findFirst().orElse(name));return;}n.add(name);savePeople(n);rebuildPeople();peopleList.getSelectionModel().select(name);});}
    private void rebuildPeople(){String selected=peopleList.getSelectionModel().getSelectedItem();Set<String> n=savedPeople();for(Transaction t:allTransactions)if(t.getPersonName()!=null&&!t.getPersonName().isBlank())n.add(t.getPersonName().trim());savePeople(n);peopleList.setItems(FXCollections.observableArrayList(n.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()));if(selected!=null)peopleList.getSelectionModel().select(selected);else if(!peopleList.getItems().isEmpty())peopleList.getSelectionModel().select(0);else clearDetails();}
    private void showPerson(String person){if(person==null||person.isBlank()){clearDetails();return;}selectedPersonLabel.setText(person);hintLabel.setText("Add or edit payments, comments and attachments for "+person+".");addTransactionButton.setDisable(false);selectedTransactions.setAll(allTransactions.stream().filter(t->person.equalsIgnoreCase(t.getPersonName())).sorted((a,b)->safeDate(b).compareTo(safeDate(a))).toList());double given=selectedTransactions.stream().filter(t->t.getType()==Transaction.Type.EXPENSE).mapToDouble(Transaction::getAmount).sum(),received=selectedTransactions.stream().filter(t->t.getType()==Transaction.Type.INCOME).mapToDouble(Transaction::getAmount).sum();givenLabel.setText(money(given));receivedLabel.setText(money(received));balanceLabel.setText(money(given-received));}
    private LocalDate safeDate(Transaction t){return t.getDate()==null?LocalDate.MIN:t.getDate();}
    private void clearDetails(){selectedPersonLabel.setText("Select a person");hintLabel.setText("Add a person, then record money given or received.");givenLabel.setText(money(0));receivedLabel.setText(money(0));balanceLabel.setText(money(0));selectedTransactions.clear();addTransactionButton.setDisable(true);}

    private void openTransactionDialog(Transaction existing){String person=peopleList.getSelectionModel().getSelectedItem();if(person==null){error("Select a person first.");return;}Dialog<ButtonType>d=new Dialog<>();d.setTitle(existing==null?"Add Transaction — "+person:"Edit Transaction — "+person);d.setHeaderText(existing==null?"Record money given or received":"Update transaction");ButtonType ok=new ButtonType(existing==null?"Save":"Update",ButtonBar.ButtonData.OK_DONE);d.getDialogPane().getButtonTypes().addAll(ok,ButtonType.CANCEL);
        ComboBox<String> flow=new ComboBox<>(FXCollections.observableArrayList("I GAVE","I RECEIVED"));flow.setValue(existing!=null&&existing.getType()==Transaction.Type.INCOME?"I RECEIVED":"I GAVE");TextField amount=new TextField(existing==null?"":String.valueOf(existing.getAmount()));TextField category=new TextField(existing==null?"Other":existing.getCategory());DatePicker date=new DatePicker(existing==null?LocalDate.now():existing.getDate());TextArea comment=new TextArea(existing==null?"":existing.getDescription());comment.setPromptText("Comment / bill details / notes...");comment.setPrefRowCount(3);Label files=new Label(existing==null?"No attachments":"Existing: "+attachmentText(existing));List<File> chosen=new ArrayList<>();Button attach=new Button("＋ Add photo / PDF");attach.setOnAction(e->{FileChooser fc=new FileChooser();fc.setTitle("Attach bills or documents (max 4)");fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images and PDF","*.png","*.jpg","*.jpeg","*.webp","*.pdf"));List<File> p=fc.showOpenMultipleDialog(d.getOwner());if(p!=null){for(File f:p){if(chosen.size()>=4)break;chosen.add(f);}files.setText(chosen.size()+" new attachment(s) selected");}});
        GridPane g=new GridPane();g.setHgap(12);g.setVgap(10);g.setPadding(new Insets(8));g.addRow(0,new Label("Person"),new Label(person));g.addRow(1,new Label("Money flow"),flow);g.addRow(2,new Label("Amount"),amount);g.addRow(3,new Label("Category"),category);g.addRow(4,new Label("Date"),date);g.addRow(5,new Label("Comment / Details"),comment);g.addRow(6,new Label("Attachments"),attach);g.add(files,1,7);d.getDialogPane().setContent(g);d.setResultConverter(b->b==ok?b:null);if(d.showAndWait().isEmpty())return;
        try{double v=Double.parseDouble(amount.getText().trim());if(v<=0)throw new IllegalArgumentException("Amount must be greater than 0.");Transaction t=existing==null?new Transaction(null,"I RECEIVED".equals(flow.getValue())?Transaction.Type.INCOME:Transaction.Type.EXPENSE,v,category.getText().trim(),comment.getText().trim(),date.getValue(),person):existing;t.setType("I RECEIVED".equals(flow.getValue())?Transaction.Type.INCOME:Transaction.Type.EXPENSE);t.setAmount(v);t.setCategory(category.getText().trim().isBlank()?"Other":category.getText().trim());t.setDescription(comment.getText().trim());t.setDate(date.getValue());t.setPersonName(person);if(!chosen.isEmpty()){Path dir=attachmentDirectory();Files.createDirectories(dir);List<String> names=new ArrayList<>(t.getAttachmentNames()),paths=new ArrayList<>(t.getAttachmentPaths());for(File f:chosen){if(names.size()>=4)break;Path target=dir.resolve(System.currentTimeMillis()+"_"+sanitize(f.getName()));Files.copy(f.toPath(),target,StandardCopyOption.REPLACE_EXISTING);names.add(f.getName());paths.add(target.toString());}t.setAttachmentNames(names);t.setAttachmentPaths(paths);}AuthSession s=authService.getCurrentSession();if(existing==null)repository.addTransaction(s,t).thenAccept(x->Platform.runLater(()->{allTransactions.add(0,x);rebuildPeople();peopleList.getSelectionModel().select(person);}));else repository.updateTransaction(s,t).thenAccept(x->Platform.runLater(()->{rebuildPeople();peopleList.getSelectionModel().select(person);}));}catch(Exception e){error(e.getMessage());}}
    private void deleteTransaction(Transaction t){Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Delete this transaction?",ButtonType.YES,ButtonType.NO);a.showAndWait().ifPresent(b->{if(b==ButtonType.YES)repository.deleteTransaction(authService.getCurrentSession(),t.getId()).thenRun(()->Platform.runLater(()->{allTransactions.remove(t);showPerson(t.getPersonName());}));});}
    private Path attachmentDirectory(){return Paths.get(System.getProperty("user.home"),".hisaabi","attachments",authService.getCurrentSession().getLocalId());}
    private String sanitize(String s){return s.replaceAll("[^a-zA-Z0-9._-]","_");} private String attachmentText(Transaction t){return t.getAttachmentNames()==null||t.getAttachmentNames().isEmpty()?"—":t.getAttachmentNames().size()+" file(s)";} private void error(String s){new Alert(Alert.AlertType.ERROR,s==null?"Operation failed.":s,ButtonType.OK).showAndWait();} private String money(double n){return String.format(Locale.US,"₹%,.2f",n);}
}
