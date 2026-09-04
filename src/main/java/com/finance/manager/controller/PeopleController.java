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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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
    @FXML private Button addPersonButton, addTransactionButton, editPersonButton, deletePersonButton, settleButton;
    @FXML private TextField peopleSearchField;
    @FXML private Label peopleCountLabel, selectedPersonLabel, givenLabel, receivedLabel, balanceLabel, balanceHintLabel, hintLabel;
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
    private String selectedPerson;

    @FXML private void initialize(){
        setupTable();
        peopleList.getSelectionModel().selectedItemProperty().addListener((o,a,b)->showPerson(b));
        peopleSearchField.textProperty().addListener((o,a,b)->rebuildPeople());
        addPersonButton.setOnAction(e->addPerson()); addTransactionButton.setOnAction(e->openTransactionDialog(null));
        editPersonButton.setOnAction(e->editPerson()); deletePersonButton.setOnAction(e->deletePerson()); settleButton.setOnAction(e->settleUp());
        load();
    }
    private void setupTable(){
        dateColumn.setCellValueFactory(d->new javafx.beans.property.SimpleObjectProperty<>(d.getValue().getDate()));
        dateColumn.setCellFactory(c->new TableCell<>(){protected void updateItem(LocalDate v,boolean e){super.updateItem(v,e);setText(e||v==null?null:v.format(DATE));}});
        directionColumn.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getType()==Transaction.Type.EXPENSE?"I GAVE":"I RECEIVED"));
        directionColumn.setCellFactory(c->new TableCell<>(){protected void updateItem(String v,boolean e){super.updateItem(v,e);setText(e?null:v);if(!e)setStyle(v.equals("I GAVE")?"-fx-text-fill:#dc2626;-fx-font-weight:bold;":"-fx-text-fill:#16a34a;-fx-font-weight:bold;");}});
        amountColumn.setCellValueFactory(d->new javafx.beans.property.SimpleDoubleProperty(d.getValue().getAmount()));
        amountColumn.setCellFactory(c->new TableCell<>(){protected void updateItem(Number v,boolean e){super.updateItem(v,e);setText(e||v==null?null:String.format(Locale.US,"₹%,.2f",v.doubleValue()));}});
        descriptionColumn.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(d.getValue().getDescription()==null||d.getValue().getDescription().isBlank()?"No comment":d.getValue().getDescription()));
        attachmentColumn.setCellValueFactory(d->new javafx.beans.property.SimpleStringProperty(attachmentText(d.getValue())));
        attachmentColumn.setCellFactory(c->new TableCell<>(){private final Button open=new Button("Open");{open.setOnAction(e->{Transaction t=getTableView().getItems().get(getIndex());openAttachments(t);});}protected void updateItem(String v,boolean e){super.updateItem(v,e);if(e||v==null||v.equals("—")){setText(v);setGraphic(null);}else{setText(v);setGraphic(open);}}});
        actionColumn.setCellFactory(c->new TableCell<>(){final Button edit=new Button("Edit"),del=new Button("Delete");final HBox box=new HBox(6,edit,del);{edit.setOnAction(e->openTransactionDialog(getTableView().getItems().get(getIndex())));del.setOnAction(e->deleteTransaction(getTableView().getItems().get(getIndex())));}protected void updateItem(Void v,boolean e){super.updateItem(v,e);setGraphic(e?null:box);}});
        transactionTable.setItems(selectedTransactions); transactionTable.setPlaceholder(new Label("No transactions yet — use Add Transaction to start this person's ledger."));
    }
    private void load(){
        AuthSession s=authService.getCurrentSession();if(s==null){selectedPersonLabel.setText("Please sign in again");return;}peopleKey=KEY+s.getLocalId();
        repository.getTransactions(s).thenAccept(list->Platform.runLater(()->{allTransactions.setAll(list);rebuildPeople();})).exceptionally(e->{Platform.runLater(()->selectedPersonLabel.setText("Could not load people ledger."));return null;});
    }
    public void refresh(){load();}
    private Set<String> savedPeople(){Set<String> n=new LinkedHashSet<>();if(peopleKey==null)return n;String v=Preferences.userNodeForPackage(PeopleController.class).get(peopleKey,"");if(!v.isBlank())for(String x:v.split("\\|",-1))if(!x.isBlank())n.add(x.trim());return n;}
    private void savePeople(Set<String> n){if(peopleKey!=null)Preferences.userNodeForPackage(PeopleController.class).put(peopleKey,String.join("|",n));}
    private void rebuildPeople(){
        if(peopleKey==null)return; String previous=selectedPerson; Set<String> names=savedPeople(); for(Transaction t:allTransactions)if(t.getPersonName()!=null&&!t.getPersonName().isBlank())names.add(t.getPersonName().trim()); savePeople(names);
        String search=peopleSearchField==null?"":peopleSearchField.getText().trim().toLowerCase(Locale.ROOT); List<String> visible=names.stream().filter(n->search.isBlank()||n.toLowerCase(Locale.ROOT).contains(search)).sorted(String.CASE_INSENSITIVE_ORDER).toList(); peopleCountLabel.setText(String.valueOf(names.size()));
        peopleList.setItems(FXCollections.observableArrayList(visible)); peopleList.setCellFactory(list->new ListCell<>(){protected void updateItem(String name,boolean empty){super.updateItem(name,empty);if(empty||name==null){setGraphic(null);setText(null);return;}double given=personTotal(name,Transaction.Type.EXPENSE),received=personTotal(name,Transaction.Type.INCOME),balance=given-received;VBox box=new VBox(2);Label n=new Label(name);n.setStyle("-fx-font-size:13px;-fx-font-weight:800;-fx-text-fill:#0f172a;");Label b=new Label(balance>0?"You will get "+money(balance):balance<0?"You owe "+money(-balance):"Settled");b.setStyle("-fx-font-size:10px;-fx-font-weight:bold;-fx-text-fill:"+(balance>0?"#2563eb":balance<0?"#dc2626":"#16a34a")+";");box.getChildren().addAll(n,b);setGraphic(box);setStyle("-fx-background-color:transparent;-fx-padding:9px 10px;-fx-background-radius:10px;");}});
        String toSelect=previous!=null&&visible.stream().anyMatch(n->n.equalsIgnoreCase(previous))?visible.stream().filter(n->n.equalsIgnoreCase(previous)).findFirst().orElse(previous):(visible.isEmpty()?null:visible.get(0));if(toSelect!=null)peopleList.getSelectionModel().select(toSelect);else clearDetails();
    }
    private double personTotal(String person,Transaction.Type type){return allTransactions.stream().filter(t->person.equalsIgnoreCase(t.getPersonName())&&t.getType()==type).mapToDouble(Transaction::getAmount).sum();}
    private void addPerson(){TextInputDialog d=new TextInputDialog();d.setTitle("Add Person");d.setHeaderText("Add a friend or person");d.setContentText("Person name:");d.showAndWait().ifPresent(v->{String name=v.trim();if(name.isBlank()){error("Please enter a name.");return;}Set<String> n=savedPeople();if(n.stream().anyMatch(x->x.equalsIgnoreCase(name))){peopleList.getSelectionModel().select(n.stream().filter(x->x.equalsIgnoreCase(name)).findFirst().orElse(name));return;}n.add(name);savePeople(n);selectedPerson=name;rebuildPeople();});}
    private void editPerson(){String old=peopleList.getSelectionModel().getSelectedItem();if(old==null){error("Select a person first.");return;}TextInputDialog d=new TextInputDialog(old);d.setTitle("Edit Person");d.setHeaderText("Rename person");d.setContentText("Name:");d.showAndWait().ifPresent(v->{String name=v.trim();if(name.isBlank())return;if(!name.equalsIgnoreCase(old)&&savedPeople().stream().anyMatch(x->x.equalsIgnoreCase(name))){error("That person already exists.");return;}Set<String> n=savedPeople();n.removeIf(x->x.equalsIgnoreCase(old));n.add(name);savePeople(n);List<Transaction> updates=allTransactions.stream().filter(t->old.equalsIgnoreCase(t.getPersonName())).toList();for(Transaction t:updates){t.setPersonName(name);repository.updateTransaction(authService.getCurrentSession(),t);}selectedPerson=name;rebuildPeople();peopleList.getSelectionModel().select(name);showPerson(name);});}
    private void deletePerson(){String person=peopleList.getSelectionModel().getSelectedItem();if(person==null)return;long count=allTransactions.stream().filter(t->person.equalsIgnoreCase(t.getPersonName())).count();if(count>0){error("This person has "+count+" transaction(s). Delete those transactions first if you want to remove the person completely.");return;}Set<String> n=savedPeople();n.removeIf(x->x.equalsIgnoreCase(person));savePeople(n);selectedPerson=null;rebuildPeople();}
    private void showPerson(String person){
        selectedPerson=person;if(person==null||person.isBlank()){clearDetails();return;}selectedPersonLabel.setText(person);hintLabel.setText("Every payment, comment and bill for "+person+" is kept in this ledger.");addTransactionButton.setDisable(false);editPersonButton.setDisable(false);deletePersonButton.setDisable(false);
        selectedTransactions.setAll(allTransactions.stream().filter(t->person.equalsIgnoreCase(t.getPersonName())).sorted((a,b)->safeDate(b).compareTo(safeDate(a))).toList());double given=selectedTransactions.stream().filter(t->t.getType()==Transaction.Type.EXPENSE).mapToDouble(Transaction::getAmount).sum(),received=selectedTransactions.stream().filter(t->t.getType()==Transaction.Type.INCOME).mapToDouble(Transaction::getAmount).sum(),balance=given-received;givenLabel.setText(money(given));receivedLabel.setText(money(received));balanceLabel.setText(money(Math.abs(balance)));balanceHintLabel.setText(balance>0?person+" owes you":balance<0?"You owe "+person:"Settled");settleButton.setDisable(Math.abs(balance)<0.005);}
    private LocalDate safeDate(Transaction t){return t.getDate()==null?LocalDate.MIN:t.getDate();}
    private void clearDetails(){selectedPerson=null;selectedPersonLabel.setText("Select a person");hintLabel.setText("Choose a person to see their ledger.");balanceHintLabel.setText("Select a person");givenLabel.setText(money(0));receivedLabel.setText(money(0));balanceLabel.setText(money(0));selectedTransactions.clear();addTransactionButton.setDisable(true);editPersonButton.setDisable(true);deletePersonButton.setDisable(true);settleButton.setDisable(true);}

    private void settleUp(){String person=selectedPerson;if(person==null)return;double given=personTotal(person,Transaction.Type.EXPENSE),received=personTotal(person,Transaction.Type.INCOME),balance=given-received;if(Math.abs(balance)<0.005)return;TextInputDialog d=new TextInputDialog(String.format(Locale.US,"%.2f",Math.abs(balance)));d.setTitle("Settle Up — "+person);d.setHeaderText(balance>0?person+" owes you "+money(balance):"You owe "+person+" "+money(-balance));d.setContentText("Settlement amount:");d.showAndWait().ifPresent(v->{try{double amount=Double.parseDouble(v.trim());if(amount<=0||amount>Math.abs(balance)+0.005)throw new IllegalArgumentException("Enter an amount up to the current balance.");Transaction.Type type=balance>0?Transaction.Type.INCOME:Transaction.Type.EXPENSE;Transaction t=new Transaction(null,type,amount,"Settlement","Settlement with "+person,LocalDate.now(),person);repository.addTransaction(authService.getCurrentSession(),t).thenAccept(x->Platform.runLater(()->{allTransactions.add(0,x);showPerson(person);rebuildPeople();peopleList.getSelectionModel().select(person);}));}catch(Exception e){error(e.getMessage());}});}
    private void openTransactionDialog(Transaction existing){String person=peopleList.getSelectionModel().getSelectedItem();if(person==null){error("Select a person first.");return;}Dialog<ButtonType>d=new Dialog<>();d.setTitle(existing==null?"Add Transaction — "+person:"Edit Transaction — "+person);d.setHeaderText(existing==null?"Record money given or received":"Update transaction");ButtonType ok=new ButtonType(existing==null?"Save":"Update",ButtonBar.ButtonData.OK_DONE);d.getDialogPane().getButtonTypes().addAll(ok,ButtonType.CANCEL);ComboBox<String> flow=new ComboBox<>(FXCollections.observableArrayList("I GAVE","I RECEIVED"));flow.setValue(existing!=null&&existing.getType()==Transaction.Type.INCOME?"I RECEIVED":"I GAVE");TextField amount=new TextField(existing==null?"":String.valueOf(existing.getAmount()));TextField category=new TextField(existing==null?"Other":existing.getCategory());DatePicker date=new DatePicker(existing==null?LocalDate.now():existing.getDate());TextArea comment=new TextArea(existing==null?"":existing.getDescription());comment.setPromptText("Comment / bill details / notes...");comment.setPrefRowCount(3);Label files=new Label(existing==null?"No attachments":"Existing: "+attachmentText(existing));List<File> chosen=new ArrayList<>();Button attach=new Button("＋ Add photo / PDF");attach.setOnAction(e->{FileChooser fc=new FileChooser();fc.setTitle("Attach bills or documents (max 4)");fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images and PDF","*.png","*.jpg","*.jpeg","*.webp","*.pdf"));List<File> p=fc.showOpenMultipleDialog(d.getOwner());if(p!=null){for(File f:p){if(chosen.size()>=4)break;chosen.add(f);}files.setText(chosen.size()+" new attachment(s) selected");}});GridPane g=new GridPane();g.setHgap(12);g.setVgap(10);g.setPadding(new Insets(8));g.addRow(0,new Label("Person"),new Label(person));g.addRow(1,new Label("Money flow"),flow);g.addRow(2,new Label("Amount"),amount);g.addRow(3,new Label("Category"),category);g.addRow(4,new Label("Date"),date);g.addRow(5,new Label("Comment / Details"),comment);g.addRow(6,new Label("Attachments"),attach);g.add(files,1,7);d.getDialogPane().setContent(g);d.setResultConverter(b->b==ok?b:null);if(d.showAndWait().isEmpty())return;try{double v=Double.parseDouble(amount.getText().trim());if(v<=0)throw new IllegalArgumentException("Amount must be greater than 0.");Transaction t=existing==null?new Transaction(null,"I RECEIVED".equals(flow.getValue())?Transaction.Type.INCOME:Transaction.Type.EXPENSE,v,category.getText().trim(),comment.getText().trim(),date.getValue(),person):existing;t.setType("I RECEIVED".equals(flow.getValue())?Transaction.Type.INCOME:Transaction.Type.EXPENSE);t.setAmount(v);t.setCategory(category.getText().trim().isBlank()?"Other":category.getText().trim());t.setDescription(comment.getText().trim());t.setDate(date.getValue());t.setPersonName(person);if(!chosen.isEmpty()){Path dir=attachmentDirectory();Files.createDirectories(dir);List<String> names=new ArrayList<>(t.getAttachmentNames()),paths=new ArrayList<>(t.getAttachmentPaths());for(File f:chosen){if(names.size()>=4)break;Path target=dir.resolve(System.currentTimeMillis()+"_"+sanitize(f.getName()));Files.copy(f.toPath(),target,StandardCopyOption.REPLACE_EXISTING);names.add(f.getName());paths.add(target.toString());}t.setAttachmentNames(names);t.setAttachmentPaths(paths);}AuthSession s=authService.getCurrentSession();if(existing==null)repository.addTransaction(s,t).thenAccept(x->Platform.runLater(()->{allTransactions.add(0,x);rebuildPeople();peopleList.getSelectionModel().select(person);}));else repository.updateTransaction(s,t).thenAccept(x->Platform.runLater(()->{rebuildPeople();peopleList.getSelectionModel().select(person);}));}catch(Exception e){error(e.getMessage());}}
    private void deleteTransaction(Transaction t){Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Delete this transaction?",ButtonType.YES,ButtonType.NO);a.setTitle("Delete Transaction");a.showAndWait().ifPresent(b->{if(b==ButtonType.YES)repository.deleteTransaction(authService.getCurrentSession(),t.getId()).thenRun(()->Platform.runLater(()->{allTransactions.remove(t);showPerson(t.getPersonName());rebuildPeople();}));});}
    private Path attachmentDirectory(){return Paths.get(System.getProperty("user.home"),".hisaabi","attachments",authService.getCurrentSession().getLocalId());}
    private String sanitize(String s){return s.replaceAll("[^a-zA-Z0-9._-]","_");}private String attachmentText(Transaction t){return t.getAttachmentNames()==null||t.getAttachmentNames().isEmpty()?"—":t.getAttachmentNames().size()+" file(s)";}private void openAttachments(Transaction t){if(t.getAttachmentPaths()==null||t.getAttachmentPaths().isEmpty())return;try{if(!Desktop.isDesktopSupported()){error("Opening attachments is not supported on this system.");return;}for(String p:t.getAttachmentPaths()){File f=new File(p);if(f.exists()){Desktop.getDesktop().open(f);break;}}}catch(IOException e){error("Could not open attachment.");}}
    private void error(String s){new Alert(Alert.AlertType.ERROR,s==null?"Operation failed.":s,ButtonType.OK).showAndWait();}private String money(double n){return String.format(Locale.US,"₹%,.2f",n);}
}
