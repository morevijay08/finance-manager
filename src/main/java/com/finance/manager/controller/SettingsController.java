package com.finance.manager.controller;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

/** Controls finance application preferences. */
public class SettingsController {
    @FXML private CheckBox notificationsEnabled;
    @FXML private ComboBox<String> currencyCombo;
    @FXML private Label settingsStatus;

    @FXML
    private void initialize() {
        currencyCombo.getItems().setAll("INR (₹)", "USD ($)", "EUR (€),", "GBP (£)");
        currencyCombo.setValue("INR (₹)");
    }

    @FXML
    private void handleSaveSettings() {
        String currency = currencyCombo.getValue() == null ? "INR (₹)" : currencyCombo.getValue();
        String notifications = notificationsEnabled.isSelected() ? "enabled" : "disabled";
        settingsStatus.setText("Settings saved — currency: " + currency + ", notifications: " + notifications + ".");
    }
}
