package com.finance.manager.model;

public class FinancialGoal {
    private String id;
    private String name;
    private double targetAmount;
    private double savedAmount;

    public FinancialGoal() {
    }

    public FinancialGoal(String id, String name, double targetAmount, double savedAmount) {
        this.id = id;
        this.name = name;
        this.targetAmount = targetAmount;
        this.savedAmount = savedAmount;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getTargetAmount() { return targetAmount; }
    public void setTargetAmount(double targetAmount) { this.targetAmount = targetAmount; }

    public double getSavedAmount() { return savedAmount; }
    public void setSavedAmount(double savedAmount) { this.savedAmount = savedAmount; }

    public double getRemainingAmount() {
        return Math.max(targetAmount - savedAmount, 0);
    }

    public double getProgress() {
        if (targetAmount <= 0) return 0;
        return Math.min(savedAmount / targetAmount, 1.0);
    }
}
