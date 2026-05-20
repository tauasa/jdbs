package org.tauasa.apps.jdbs.gui;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.tauasa.apps.jdbs.Main;
import org.tauasa.apps.jdbs.model.LogEvent;
import org.tauasa.apps.jdbs.server.JdbsServer;
import org.tauasa.apps.jdbs.server.JdbsServer.ConnectionStats;
import org.tauasa.apps.jdbs.server.ServerConfig;

import java.io.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Primary JDBS application window.
 *
 * <h3>Status bar layout</h3>
 * <pre>
 *   [status message …]          Now: N | Peak: N | Total: N | Events: N | Port: N
 * </pre>
 */
public class MainWindow {

    // ── Constants ────────────────────────────────────────────────────────────────
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private static final int MAX_DISPLAY_EVENTS = 20_000;

    // ── Dependencies ─────────────────────────────────────────────────────────────
    private final Stage        stage;
    private final ServerConfig config;
    private final HostServices hostServices;
    private       JdbsServer   server;

    // ── Data ─────────────────────────────────────────────────────────────────────
    private final ObservableList<LogEvent> allEvents     = FXCollections.observableArrayList();
    private       FilteredList<LogEvent>   filteredEvents;

    // ── UI – top controls ─────────────────────────────────────────────────────────
    private Scene            scene;
    private TableView<LogEvent> tableView;
    private ComboBox<String> levelFilter;
    private TextField        searchField;
    private MenuItem         toggleThemeItem;
    private boolean          autoScroll = true;
    private boolean          darkMode;

    // ── UI – status bar ───────────────────────────────────────────────────────────
    private Label statusLabel;        // left side:  last server event message
    private Label nowLabel;           // "Now: N"    – clients connected right now
    private Label peakLabel;          // "Peak: N"   – most concurrent at any one time
    private Label totalLabel;         // "Total: N"  – cumulative client connections
    private Label eventCountLabel;    // "Events: N"
    private Label portLabel;          // "Port: N"

    // ── UI – detail pane ─────────────────────────────────────────────────────────
    private TextArea  detailArea;
    private ImageView imagePreview;

    // ── Constructor ──────────────────────────────────────────────────────────────
    MainWindow(Stage stage, ServerConfig config, HostServices hostServices) {
        this.stage        = stage;
        this.config       = config;
        this.hostServices = hostServices;
        this.darkMode     = config.isDarkMode();
    }

    // ── Public API ───────────────────────────────────────────────────────────────

    void show() {
        stage.setTitle(Main.APP_NAME + "  v" + Main.APP_VERSION);

        BorderPane root = new BorderPane();
        root.setTop(buildTop());
        root.setCenter(buildCenter());
        root.setBottom(buildStatusBar());

        scene = new Scene(root, 1280, 800);
        applyTheme();

        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(500);
        stage.setOnCloseRequest(e -> { e.consume(); shutdown(); });
        stage.show();

        startServer();
    }

    // ── Layout builders ───────────────────────────────────────────────────────────

    private VBox buildTop() {
        return new VBox(buildMenuBar(), buildToolBar());
    }

    private MenuBar buildMenuBar() {
        // File
        MenuItem saveItem = new MenuItem("_Save…");
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
        saveItem.setOnAction(e -> saveLog());

        MenuItem exitItem = new MenuItem("E_xit");
        exitItem.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
        exitItem.setOnAction(e -> shutdown());

        Menu fileMenu = new Menu("_File");
        fileMenu.getItems().addAll(saveItem, new SeparatorMenuItem(), exitItem);

        // Edit
        MenuItem settingsItem = new MenuItem("_Settings…");
        settingsItem.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN));
        settingsItem.setOnAction(e -> showSettings());

        Menu editMenu = new Menu("_Edit");
        editMenu.getItems().add(settingsItem);

        // View
        toggleThemeItem = new MenuItem(darkMode ? "Light Mode" : "Dark Mode");
        toggleThemeItem.setOnAction(e -> toggleTheme());

        MenuItem clearItem = new MenuItem("_Clear Log");
        clearItem.setAccelerator(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN));
        clearItem.setOnAction(e -> clearLog());

        CheckMenuItem autoScrollItem = new CheckMenuItem("Auto-Scroll");
        autoScrollItem.setSelected(true);
        autoScrollItem.selectedProperty().addListener((obs, o, n) -> autoScroll = n);

        Menu viewMenu = new Menu("_View");
        viewMenu.getItems().addAll(toggleThemeItem, new SeparatorMenuItem(), clearItem, autoScrollItem);

        // Help
        MenuItem aboutItem = new MenuItem("_About JDBS…");
        aboutItem.setOnAction(e -> showAbout());

        Menu helpMenu = new Menu("_Help");
        helpMenu.getItems().add(aboutItem);

        MenuBar bar = new MenuBar(fileMenu, editMenu, viewMenu, helpMenu);
        bar.setUseSystemMenuBar(true);
        return bar;
    }

    private ToolBar buildToolBar() {
        levelFilter = new ComboBox<>();
        levelFilter.getItems().addAll("ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR");
        levelFilter.setValue("ALL");
        levelFilter.setTooltip(new Tooltip("Minimum log level to display"));
        levelFilter.setOnAction(e -> applyFilter());

        searchField = new TextField();
        searchField.setPromptText("Search messages / logger…");
        searchField.setPrefWidth(260);
        searchField.textProperty().addListener((obs, o, n) -> applyFilter());
        searchField.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) searchField.clear(); });

        Button clearBtn = new Button("Clear");
        clearBtn.setTooltip(new Tooltip("Clear all log events (Ctrl+K)"));
        clearBtn.setOnAction(e -> clearLog());

        Button tailBtn = new Button("↓ Tail");
        tailBtn.setTooltip(new Tooltip("Scroll to latest event"));
        tailBtn.setOnAction(e -> scrollToBottom());

        return new ToolBar(
                new Label("Level:"), levelFilter,
                new Separator(),
                new Label("Search:"), searchField,
                new Separator(),
                clearBtn, tailBtn
        );
    }

    private SplitPane buildCenter() {
        // ── Filtered table ────────────────────────────────────────────────────
        filteredEvents = new FilteredList<>(allEvents, ev -> true);
        tableView = new TableView<>(filteredEvents);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableView.setTableMenuButtonVisible(true);
        tableView.setPlaceholder(new Label("No log events yet – connect a JDBS client"));

        // Time
        TableColumn<LogEvent, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(d ->
                new SimpleStringProperty(TIME_FMT.format(Instant.ofEpochMilli(d.getValue().getTimestamp()))));
        timeCol.setPrefWidth(100); timeCol.setMinWidth(90); timeCol.setMaxWidth(120);

        // Level – colour badge
        TableColumn<LogEvent, String> levelCol = new TableColumn<>("Level");
        levelCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getLevel()));
        levelCol.setPrefWidth(70); levelCol.setMinWidth(60); levelCol.setMaxWidth(80);
        levelCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String lvl, boolean empty) {
                super.updateItem(lvl, empty);
                if (empty || lvl == null) { setText(null); setStyle(""); return; }
                setText(lvl);
                setFont(Font.font(null, FontWeight.BOLD, 11));
                setAlignment(Pos.CENTER);
                setStyle(levelBadgeStyle(lvl));
            }
        });

        // Logger (abbreviated)
        TableColumn<LogEvent, String> loggerCol = new TableColumn<>("Logger");
        loggerCol.setCellValueFactory(d -> new SimpleStringProperty(abbreviate(d.getValue().getLoggerName())));
        loggerCol.setPrefWidth(200); loggerCol.setMinWidth(100);

        // Thread
        TableColumn<LogEvent, String> threadCol = new TableColumn<>("Thread");
        threadCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getThread()));
        threadCol.setPrefWidth(130); threadCol.setMinWidth(80);

        // Message
        TableColumn<LogEvent, String> msgCol = new TableColumn<>("Message");
        msgCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getMessage()));
        msgCol.setMinWidth(200);

        // Image indicator
        TableColumn<LogEvent, String> imgCol = new TableColumn<>("📷");
        imgCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().hasImage() ? "●" : ""));
        imgCol.setPrefWidth(36); imgCol.setMinWidth(30); imgCol.setMaxWidth(40);

        tableView.getColumns().addAll(timeCol, levelCol, loggerCol, threadCol, msgCol, imgCol);

        // Row-level background colouring
        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(LogEvent item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeIf(c -> c.startsWith("row-"));
                if (!empty && item != null && item.getLevel() != null)
                    getStyleClass().add("row-" + item.getLevel().toLowerCase());
            }
        });

        tableView.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> updateDetailPane(sel));

        // ── Detail pane ───────────────────────────────────────────────────────
        VBox detailPane = buildDetailPane();

        SplitPane split = new SplitPane(tableView, detailPane);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.72);
        return split;
    }

    private VBox buildDetailPane() {
        Label header = new Label("Event Details");
        header.setFont(Font.font(null, FontWeight.SEMI_BOLD, 12));
        header.setPadding(new Insets(6, 8, 2, 8));

        detailArea = new TextArea();
        detailArea.setEditable(false);
        detailArea.setWrapText(true);
        detailArea.setFont(Font.font("Monospaced", 12));
        HBox.setHgrow(detailArea, Priority.ALWAYS);

        imagePreview = new ImageView();
        imagePreview.setPreserveRatio(true);
        imagePreview.setFitHeight(160);
        imagePreview.setFitWidth(220);
        imagePreview.setSmooth(true);

        VBox imgBox = new VBox(4, new Label("Image:"), imagePreview);
        imgBox.setPadding(new Insets(0, 4, 0, 4));
        imgBox.setAlignment(Pos.TOP_LEFT);
        imgBox.managedProperty().bind(imagePreview.imageProperty().isNotNull());
        imgBox.visibleProperty().bind(imagePreview.imageProperty().isNotNull());

        HBox body = new HBox(8, detailArea, imgBox);
        body.setPadding(new Insets(4, 8, 8, 8));
        VBox.setVgrow(body, Priority.ALWAYS);

        return new VBox(header, new Separator(), body);
    }

    /**
     * Status bar:
     * <pre>
     *   [status message …]   Now: N | Peak: N | Total: N | Events: N | Port: N
     * </pre>
     * "Now", "Peak", and "Total" give a complete picture of client connection
     * activity since the server was started.
     */
    private HBox buildStatusBar() {
        statusLabel     = new Label("Initialising…");

        // ── Connection counters ───────────────────────────────────────────────
        nowLabel   = styledStatLabel("Now: 0");
        nowLabel.setTooltip(new Tooltip("Clients connected right now"));

        peakLabel  = styledStatLabel("Peak: 0");
        peakLabel.setTooltip(new Tooltip("Maximum concurrent clients since server start"));

        totalLabel = styledStatLabel("Total: 0");
        totalLabel.setTooltip(new Tooltip("Cumulative clients that have ever connected"));

        // ── Other counters ────────────────────────────────────────────────────
        eventCountLabel = styledStatLabel("Events: 0");
        eventCountLabel.setTooltip(new Tooltip("Log events received in this session"));

        portLabel = styledStatLabel("Port: " + config.getPort());
        portLabel.setTooltip(new Tooltip("Server listening port"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(8,
                statusLabel, spacer,
                nowLabel,
                new Separator(Orientation.VERTICAL),
                peakLabel,
                new Separator(Orientation.VERTICAL),
                totalLabel,
                new Separator(Orientation.VERTICAL),
                eventCountLabel,
                new Separator(Orientation.VERTICAL),
                portLabel
        );
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 10, 4, 10));
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    /** Small helper – consistent style for all status-bar stat labels. */
    private Label styledStatLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 11;");
        return l;
    }

    // ── Event handlers ────────────────────────────────────────────────────────────

    /** Add a log event from any thread – always marshals to the JavaFX thread. */
    public void addEvent(LogEvent event) {
        Platform.runLater(() -> {
            if (allEvents.size() >= MAX_DISPLAY_EVENTS) {
                allEvents.remove(0, 1_000); // drop oldest 1 000 when full
            }
            allEvents.add(event);
            eventCountLabel.setText("Events: " + allEvents.size());
            if (autoScroll && !tableView.getItems().isEmpty())
                tableView.scrollTo(tableView.getItems().size() - 1);
        });
    }

    /** Update all three client-counter labels from a {@link ConnectionStats} snapshot. */
    private void updateStats(ConnectionStats stats) {
        Platform.runLater(() -> {
            nowLabel.setText("Now: "   + stats.current());
            peakLabel.setText("Peak: " + stats.peak());
            totalLabel.setText("Total: "+ stats.total());
        });
    }

    private void applyFilter() {
        String levelSel = levelFilter.getValue();
        String search   = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        int    minOrd   = levelOrdinal(levelSel);

        filteredEvents.setPredicate(ev -> {
            if (minOrd >= 0 && levelOrdinal(ev.getLevel()) < minOrd) return false;
            if (!search.isEmpty()) {
                boolean inMsg    = ev.getMessage()    != null && ev.getMessage().toLowerCase().contains(search);
                boolean inLogger = ev.getLoggerName() != null && ev.getLoggerName().toLowerCase().contains(search);
                boolean inThread = ev.getThread()     != null && ev.getThread().toLowerCase().contains(search);
                if (!inMsg && !inLogger && !inThread) return false;
            }
            return true;
        });
    }

    private void updateDetailPane(LogEvent ev) {
        if (ev == null) { detailArea.clear(); imagePreview.setImage(null); return; }
        detailArea.setText(String.format(
                "Time:    %s%n" +
                "Level:   %s%n" +
                "Logger:  %s%n" +
                "Thread:  %s%n%n" +
                "Message:%n%s",
                TIME_FMT.format(Instant.ofEpochMilli(ev.getTimestamp())),
                ev.getLevel(), ev.getLoggerName(), ev.getThread(), ev.getMessage()));

        if (ev.hasImage()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(ev.getImageBase64());
                imagePreview.setImage(new Image(new ByteArrayInputStream(bytes)));
            } catch (Exception e) {
                imagePreview.setImage(null);
            }
        } else {
            imagePreview.setImage(null);
        }
    }

    private void clearLog() {
        allEvents.clear();
        eventCountLabel.setText("Events: 0");
        detailArea.clear();
        imagePreview.setImage(null);
    }

    private void scrollToBottom() {
        if (!tableView.getItems().isEmpty())
            tableView.scrollTo(tableView.getItems().size() - 1);
    }

    private void saveLog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Log Events");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text / Log files", "*.txt", "*.log"));
        chooser.setInitialFileName("jdbs-export.txt");
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;

        try (PrintWriter w = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            ConnectionStats stats = server != null ? server.getStats()
                    : new ConnectionStats(0, 0, 0);
            w.printf("# JDBS Log Export%n");
            w.printf("# Exported:       %s%n", Instant.now());
            w.printf("# Port:           %d%n", config.getPort());
            w.printf("# Events:         %d%n", allEvents.size());
            w.printf("# Clients (now):  %d%n", stats.current());
            w.printf("# Clients (peak): %d%n", stats.peak());
            w.printf("# Clients (total):%d%n%n", stats.total());

            for (LogEvent ev : allEvents) {
                w.printf("[%s] %-5s [%s] [%s] %s%n",
                        TIME_FMT.format(Instant.ofEpochMilli(ev.getTimestamp())),
                        ev.getLevel(), ev.getLoggerName(), ev.getThread(), ev.getMessage());
                if (ev.hasImage())
                    w.printf("        <image: %s, %d chars base64>%n",
                            ev.getImageFormat(), ev.getImageBase64().length());
            }
            statusLabel.setText("Saved " + allEvents.size() + " events → " + file.getName());
        } catch (IOException e) {
            showError("Save Failed", "Could not write log file:\n" + e.getMessage());
        }
    }

    private void showSettings() {
        new SettingsDialog(stage, config, server != null && server.isRunning()).showAndWait();
        portLabel.setText("Port: " + config.getPort());
    }

    private void showAbout() {
        new AboutDialog(stage, hostServices).showAndWait();
    }

    private void toggleTheme() {
        darkMode = !darkMode;
        config.setDarkMode(darkMode);
        config.save();
        toggleThemeItem.setText(darkMode ? "Light Mode" : "Dark Mode");
        applyTheme();
    }

    private void applyTheme() {
        scene.getStylesheets().clear();
        var url = getClass().getResource(darkMode ? "/dark.css" : "/light.css");
        if (url != null) scene.getStylesheets().add(url.toExternalForm());
    }

    private void startServer() {
        server = new JdbsServer(config);
        server.setEventListener(this::addEvent);
        server.setStatusListener(msg  -> Platform.runLater(() -> statusLabel.setText(msg)));
        server.setStatsListener(this::updateStats);

        try {
            server.start();
        } catch (IOException e) {
            Platform.runLater(() ->
                    showError("Server Error",
                            "Cannot bind to port " + config.getPort() + ":\n" + e.getMessage()));
        }
    }

    private void shutdown() {
        if (server != null) server.stop();
        config.save();
        Platform.exit();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    /** Abbreviates a dotted logger name: com.example.foo.Bar → c.e.f.Bar */
    private String abbreviate(String name) {
        if (name == null) return "";
        String[] parts = name.split("\\.");
        if (parts.length <= 2) return name;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) sb.append(parts[i].charAt(0)).append('.');
        sb.append(parts[parts.length - 1]);
        return sb.toString();
    }

    private int levelOrdinal(String level) {
        if (level == null || "ALL".equals(level)) return -1;
        return switch (level.toUpperCase()) {
            case "TRACE" -> 0; case "DEBUG" -> 1; case "INFO" -> 2;
            case "WARN"  -> 3; case "ERROR" -> 4; default -> -1;
        };
    }

    private String levelBadgeStyle(String level) {
        if (darkMode) return switch (level.toUpperCase()) {
            case "TRACE" -> "-fx-background-color:#3a3a3a;-fx-text-fill:#888;";
            case "DEBUG" -> "-fx-background-color:#1a3a5c;-fx-text-fill:#7ec8e3;";
            case "INFO"  -> "-fx-background-color:#1a3a1a;-fx-text-fill:#6fcf97;";
            case "WARN"  -> "-fx-background-color:#4a3800;-fx-text-fill:#f2c94c;";
            case "ERROR" -> "-fx-background-color:#4a1a1a;-fx-text-fill:#eb5757;";
            default      -> "";
        };
        return switch (level.toUpperCase()) {
            case "TRACE" -> "-fx-background-color:#e8e8e8;-fx-text-fill:#777;";
            case "DEBUG" -> "-fx-background-color:#d4e6f1;-fx-text-fill:#1a6b99;";
            case "INFO"  -> "-fx-background-color:#d4efdf;-fx-text-fill:#1a7040;";
            case "WARN"  -> "-fx-background-color:#fef9e7;-fx-text-fill:#856404;";
            case "ERROR" -> "-fx-background-color:#fadbd8;-fx-text-fill:#c0392b;";
            default      -> "";
        };
    }

    private void showError(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.initOwner(stage); a.setTitle(title); a.setHeaderText(null); a.showAndWait();
    }
}
