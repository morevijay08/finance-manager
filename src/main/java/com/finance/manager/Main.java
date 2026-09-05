package com.finance.manager;

import com.finance.manager.ui.Branding;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                Main.class.getResource("/fxml/Login.fxml")
        );
        Parent root = loader.load();
        Branding.apply(root);

        Scene scene = new Scene(root, 900, 600);
        scene.getStylesheets().add(
                Main.class.getResource("/css/application.css").toExternalForm()
        );

        stage.setTitle(Branding.APP_TITLE);

        // Set the Khatabook logo as the native application/window icon.
        java.net.URL logoUrl = Main.class.getResource("/images/khatabook-logo-small.png");
        if (logoUrl != null) {
            Image appIcon = new Image(logoUrl.toExternalForm(), false);
            if (!appIcon.isError()) {
                stage.getIcons().add(appIcon);
            }
        }

        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(500);
        stage.setMaximized(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
