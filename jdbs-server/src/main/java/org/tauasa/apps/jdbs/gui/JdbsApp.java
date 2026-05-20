package org.tauasa.apps.jdbs.gui;

import javafx.application.Application;
import javafx.stage.Stage;
import org.tauasa.apps.jdbs.server.ServerConfig;

/**
 * JavaFX {@link Application} entry-point for JDBS GUI mode.
 *
 * <p>Because {@link Application#launch(Class, String[])} is static and does not
 * allow passing arbitrary objects, we use a static field to ferry the
 * {@link ServerConfig} from {@code main()} into the JavaFX lifecycle.
 */
public class JdbsApp extends Application {

    /** Set by {@link #launchApp} before {@code launch()} is called. */
    private static ServerConfig sharedConfig;

    /**
     * Convenience launcher – stores the config then hands off to JavaFX.
     */
    public static void launchApp(String[] args, ServerConfig config) {
        sharedConfig = config;
        launch(JdbsApp.class, args);
    }

    @Override
    public void start(Stage primaryStage) {
        ServerConfig config = (sharedConfig != null) ? sharedConfig : ServerConfig.load();
        new MainWindow(primaryStage, config, getHostServices()).show();
    }
}
