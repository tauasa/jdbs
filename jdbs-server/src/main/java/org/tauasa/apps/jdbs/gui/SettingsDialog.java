package org.tauasa.apps.jdbs.gui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.tauasa.apps.jdbs.server.ServerConfig;

/**
 * Modal "Edit → Settings" dialog.
 *
 * <p>Settings:
 * <ul>
 *   <li>Port number</li>
 *   <li>Max Clients</li>
 *   <li>Beep on new client connection</li>
 * </ul>
 *
 * All values are validated before being committed to {@link ServerConfig} and
 * persisted via {@link ServerConfig#save()}.
 */
public class SettingsDialog {

    private final Stage        owner;
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

        // ── Controls ──────────────────────────────────────────────────────────────
        TextField portField = new TextField(String.valueOf(config.getPort()));
        portField.setMaxWidth(120);
        portField.setPromptText("1024–65535");

        Spinner<Integer> maxClientsSpinner = new Spinner<>(1, 500, config.getMaxClients());
        maxClientsSpinner.setEditable(true);
        maxClientsSpinner.setMaxWidth(120);

        CheckBox beepCheck = new CheckBox("Play a beep when a client connects");
        beepCheck.setSelected(config.isBeepOnConnect());
        beepCheck.setTooltip(new Tooltip(
                "Uses java.awt.Toolkit.beep() – requires an audio device"));

        Label portNote = new Label(serverRunning
                ? "⚠  Port change requires a server restart to take effect." : "");
        portNote.setStyle("-fx-text-fill: #e07000; -fx-font-size: 11;");
        portNote.setWrapText(true);

        // ── Grid layout ───────────────────────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 24, 8, 24));

        // Let the right column expand
        ColumnConstraints col0 = new ColumnConstraints();
        col0.setMinWidth(110);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col0, col1);

        int row = 0;
        grid.add(new Label("Port:"),                   0, row);
        grid.add(portField,                             1, row++);
        grid.add(new Label("Max Clients:"),             0, row);
        grid.add(maxClientsSpinner,                     1, row++);

        // Separator row for Audio section
        grid.add(new Separator(),                       0, row++, 2, 1);
        grid.add(new Label("Audio"),                    0, row++, 2, 1);
        grid.add(beepCheck,                             0, row++, 2, 1);

        if (serverRunning) {
            grid.add(new Separator(),                   0, row++, 2, 1);
            grid.add(portNote,                          0, row,   2, 1);
        }

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setMinWidth(380);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);

        // ── Validation ────────────────────────────────────────────────────────────
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            int port;
            try {
                port = Integer.parseInt(portField.getText().trim());
            } catch (NumberFormatException e) {
                showError("Port must be a number between 1024 and 65535.");
                event.consume();
                return;
            }
            if (port < 1024 || port > 65535) {
                showError("Port must be between 1024 and 65535.");
                event.consume();
            }
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                config.setPort(Integer.parseInt(portField.getText().trim()));
                config.setMaxClients(maxClientsSpinner.getValue());
                config.setBeepOnConnect(beepCheck.isSelected());
                config.save();
            }
        });
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.initOwner(owner);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
