package com.finance.manager.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Transaction {
    public enum Type { INCOME, EXPENSE }
    private String id;
    private Type type;
    private double amount;
    private String category;
    private String description;
    private LocalDate date;
    private String personName;
    private List<String> attachmentNames = new ArrayList<>();
    private List<String> attachmentPaths = new ArrayList<>();

    public Transaction() { }
    public Transaction(String id, Type type, double amount, String category, String description, LocalDate date) { this(id,type,amount,category,description,date,""); }
    public Transaction(String id, Type type, double amount, String category, String description, LocalDate date, String personName) {
        this.id=id; this.type=type; this.amount=amount; this.category=category; this.description=description; this.date=date; this.personName=personName==null?"":personName.trim();
    }
    public Transaction(Type type,double amount,String category,String description,LocalDate date){this(null,type,amount,category,description,date,"");}
    public Transaction(Type type,double amount,String category,String description,LocalDate date,String personName){this(null,type,amount,category,description,date,personName);}
    public String getId(){return id;} public void setId(String id){this.id=id;}
    public Type getType(){return type;} public void setType(Type type){this.type=type;}
    public double getAmount(){return amount;} public void setAmount(double amount){this.amount=amount;}
    public String getCategory(){return category;} public void setCategory(String category){this.category=category;}
    public String getDescription(){return description;} public void setDescription(String description){this.description=description;}
    public LocalDate getDate(){return date;} public void setDate(LocalDate date){this.date=date;}
    public String getPersonName(){return personName;} public void setPersonName(String personName){this.personName=personName==null?"":personName.trim();}
    public List<String> getAttachmentNames(){return attachmentNames;}
    public void setAttachmentNames(List<String> names){attachmentNames=names==null?new ArrayList<>():new ArrayList<>(names);}
    public List<String> getAttachmentPaths(){return attachmentPaths;}
    public void setAttachmentPaths(List<String> paths){attachmentPaths=paths==null?new ArrayList<>():new ArrayList<>(paths);}
}
