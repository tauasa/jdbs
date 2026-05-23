package org.tauasa.apps.jdbs.gui;

import org.tauasa.apps.jdbs.Main;

import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Window;

/**
 * "Help → About JDBS" modal dialog.
 */
public class AboutDialog {

    private final Window       owner;
    private final HostServices hostServices;

    AboutDialog(Window owner, HostServices hostServices) {
        this.owner        = owner;
        this.hostServices = hostServices;
    }

    void showAndWait() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("About JDBS");

        // ── Content ──────────────────────────────────────────────────────────────
        Label nameLabel = new Label(Main.APP_NAME);
        nameLabel.setFont(Font.font(null, FontWeight.BOLD, 20));

        Label versionLabel = new Label("Version " + Main.APP_VERSION);
        versionLabel.setStyle("-fx-text-fill: #666;");

        Separator sep = new Separator();

        Label descLabel = new Label(
                "A lightweight remote debug logging server.\n" +
                "Clients connect over TCP and send structured\n" +
                "JSON log events including optional images.");
        descLabel.setWrapText(true);

        Label copyrightLabel = new Label(Main.COPYRIGHT);
        copyrightLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 11;");

        Hyperlink githubLink = new Hyperlink(Main.GITHUB_URL);
        githubLink.setOnAction(e -> {
            if (hostServices != null) {
                hostServices.showDocument(Main.GITHUB_URL);
            } else {
                // Fallback for environments without HostServices
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(Main.GITHUB_URL));
                } catch (Exception ex) {
                    // Silently ignore if browser cannot be opened
                }
            }
        });
        githubLink.setStyle("-fx-font-size: 11;");

        VBox content = new VBox(10,
                nameLabel, versionLabel, sep,
                descLabel, copyrightLabel, githubLink);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPrefWidth(320);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.showAndWait();
    }
}
