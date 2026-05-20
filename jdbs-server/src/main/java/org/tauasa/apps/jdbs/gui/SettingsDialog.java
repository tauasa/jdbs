package org.tauasa.apps.jdbs.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.tauasa.apps.jdbs.server.ServerConfig;

/**
 * Modal settings dialog (Edit → Settings).
 *
 * <p>Saves changes to the {@link ServerConfig} and persists them via
 * {@link ServerConfig#save()}.  A server restart is required for port
 * changes to take effect; a message is shown when this is the case.
 */
public class SettingsDialog {

    private final Stage       owner;
    private final ServerConfig config;
    private final boolean      serverRunning;

    SettingsDialog(Window owner, ServerConfig config, boolean serverRunning) {
        this.owner         = (Stage) owner;
        this.config        = config;
        this.serverRunning = serverRunning;
    }

    void showAndWait() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Settings");
        dialog.setHeaderText("Server Settings");

        // ── Controls ─────────────────────────────────────────────────────────────
        TextField portField = new TextField(String.valueOf(config.getPort()));
        portField.setMaxWidth(100);
        portField.setPromptText("1024–65535");

        Spinner<Integer> maxClientsSpinner = new Spinner<>(1, 500, config.getMaxClients());
        maxClientsSpinner.setEditable(true);
        maxClientsSpinner.setPrefWidth(100);

        Label portNote = new Label();
        portNote.setStyle("-fx-text-fill: #888; -fx-font-size: 11;");
        if (serverRunning) {
            portNote.setText("⚠ Server restart required to change port");
        }

        // ── Layout ───────────────────────────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Port:"),        0, 0);
        grid.add(portField,                 1, 0);
        grid.add(new Label("Max Clients:"), 0, 1);
        grid.add(maxClientsSpinner,         1, 1);
        if (serverRunning) grid.add(portNote, 0, 2, 2, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Style the OK button
        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDefaultButton(true);

        // ── Validation ───────────────────────────────────────────────────────────
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException e) {
                showValidationError("Port must be a number between 1024 and 65535.");
                event.consume();
                return;
            }
            if (port < 1024 || port > 65535) {
                showValidationError("Port must be between 1024 and 65535.");
                event.consume();
            }
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                int newPort = Integer.parseInt(portField.getText().trim());
                config.setPort(newPort);
                config.setMaxClients(maxClientsSpinner.getValue());
                config.save();
            }
        });
    }

    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.initOwner(owner);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
