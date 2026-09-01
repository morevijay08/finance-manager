package com.finance.manager.controller;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

import java.util.prefs.Preferences;

/** Controls finance application preferences. */
public class SettingsController {
    private static final String KEY_NOTIFICATIONS = "notificationsEnabled";
    private static final String KEY_CURRENCY = "currency";

    @FXML private CheckBox notificationsEnabled;
    @FXML private ComboBox<String> currencyCombo;
    @FXML private Label settingsStatus;

    private final Preferences preferences = Preferences.userNodeForPackage(SettingsController.class);

    @FXML
    private void initialize() {
        currencyCombo.getItems().setAll("INR (₹)", "USD ($)", "EUR (€)", "GBP (£)");
        notificationsEnabled.setSelected(preferences.getBoolean(KEY_NOTIFICATIONS, true));
        currencyCombo.setValue(preferences.get(KEY_CURRENCY, "INR (₹)"));
    }

    @FXML
    private void handleSaveSettings() {
        String currency = currencyCombo.getValue() == null ? "INR (₹)" : currencyCombo.getValue();
        boolean notifications = notificationsEnabled.isSelected();
        preferences.putBoolean(KEY_NOTIFICATIONS, notifications);
        preferences.put(KEY_CURRENCY, currency);
        settingsStatus.setText("Settings saved successfully.");
    }
}
