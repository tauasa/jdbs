package org.tauasa.apps.jdbs;

import org.tauasa.apps.jdbs.cli.CliRunner;
import org.tauasa.apps.jdbs.gui.JdbsApp;
import org.tauasa.apps.jdbs.server.ServerConfig;

import java.util.Arrays;
import java.util.List;

/**
 * JDBS entry point.
 *
 * <p>Usage:
 * <pre>
 *   # GUI mode (default when a display is available)
 *   java -jar jdbs-server.jar [--port=6218] [--max-clients=10]
 *
 *   # CLI mode (headless / CI / Docker)
 *   java -jar jdbs-server.jar --cli [--port=6218] [--max-clients=10]
 * </pre>
 */
public class Main {

    public static final String APP_NAME    = "JDBS – Java Debug Bridge Server";
    public static final String APP_VERSION = "1.0.0";
    public static final String COPYRIGHT   = "© 2024 Tauasa Timoteo";
    public static final String GITHUB_URL  = "https://github.com/tauasa/jdbs";

    public static void main(String[] args) {
        List<String> argList = Arrays.asList(args);
        ServerConfig config  = buildConfig(argList);

        if (argList.contains("--cli")) {
            new CliRunner(config).run();
        } else {
            // GUI mode
            JdbsApp.launchApp(args, config);
        }
    }

    /** Apply command-line overrides on top of the persisted config. */
    private static ServerConfig buildConfig(List<String> args) {
        ServerConfig cfg = ServerConfig.load();
        for (String arg : args) {
            if (arg.startsWith("--port=")) {
                try { cfg.setPort(Integer.parseInt(arg.substring(7))); }
                catch (NumberFormatException ignored) {}
            } else if (arg.startsWith("--max-clients=")) {
                try { cfg.setMaxClients(Integer.parseInt(arg.substring(14))); }
                catch (NumberFormatException ignored) {}
            }
        }
        return cfg;
    }
}
