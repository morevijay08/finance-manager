package com.finance.manager.model;

import java.time.LocalDate;
import java.time.LocalTime;

public class RecurringTransaction {

    public enum Frequency {
        DAILY,
        WEEKLY,
        MONTHLY,
        YEARLY
    }

    private String id;
    private Transaction.Type type;
    private double amount;
    private String category;
    private String description;
    private Frequency frequency;
    private LocalDate nextDate;
    private LocalTime nextTime;
    private boolean active;

    public RecurringTransaction() {
    }

    public RecurringTransaction(String id, Transaction.Type type, double amount,
                                String category, String description, Frequency frequency,
                                LocalDate nextDate, boolean active) {
        this(id, type, amount, category, description, frequency, nextDate, LocalTime.of(9, 0), active);
    }

    public RecurringTransaction(String id, Transaction.Type type, double amount,
                                String category, String description, Frequency frequency,
                                LocalDate nextDate, LocalTime nextTime, boolean active) {
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.category = category;
        this.description = description;
        this.frequency = frequency;
        this.nextDate = nextDate;
        this.nextTime = nextTime;
        this.active = active;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Transaction.Type getType() { return type; }
    public void setType(Transaction.Type type) { this.type = type; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Frequency getFrequency() { return frequency; }
    public void setFrequency(Frequency frequency) { this.frequency = frequency; }
    public LocalDate getNextDate() { return nextDate; }
    public void setNextDate(LocalDate nextDate) { this.nextDate = nextDate; }
    public LocalTime getNextTime() { return nextTime; }
    public void setNextTime(LocalTime nextTime) { this.nextTime = nextTime; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
