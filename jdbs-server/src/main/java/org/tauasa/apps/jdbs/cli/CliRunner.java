package org.tauasa.apps.jdbs.cli;

import org.tauasa.apps.jdbs.model.LogEvent;
import org.tauasa.apps.jdbs.server.JdbsServer;
import org.tauasa.apps.jdbs.server.JdbsServer.ConnectionStats;
import org.tauasa.apps.jdbs.server.ServerConfig;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * CLI mode runner – starts the server and prints received {@link LogEvent}s
 * to stdout with ANSI colour coding.
 *
 * <p>Usage: {@code java -jar jdbs-server.jar --cli [--port=6218] [--max-clients=10]}
 */
public class CliRunner {

    // ANSI codes
    private static final String RESET  = "\033[0m";
    private static final String BOLD   = "\033[1m";
    private static final String GREY   = "\033[90m";
    private static final String CYAN   = "\033[36m";
    private static final String GREEN  = "\033[32m";
    private static final String YELLOW = "\033[33m";
    private static final String RED    = "\033[31m";

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final ServerConfig config;

    public CliRunner(ServerConfig config) {
        this.config = config;
    }

    public void run() {
        printBanner();

        JdbsServer server = new JdbsServer(config);
        server.setEventListener(this::printEvent);
        server.setStatusListener(msg ->
                System.out.printf("%s[STATUS] %s%s%n", GREY, msg, RESET));
        server.setStatsListener(this::printStats);

        try {
            server.start();
        } catch (IOException e) {
            System.err.printf("%s[ERROR] Cannot start server on port %d: %s%s%n",
                    RED, config.getPort(), e.getMessage(), RESET);
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n" + GREY + "[JDBS] Shutting down…" + RESET);
            server.stop();
        }));

        System.out.printf("%s[JDBS] Listening on port %d – press Ctrl-C to exit%s%n%n",
                GREEN, config.getPort(), RESET);

        try { Thread.currentThread().join(); } catch (InterruptedException ignored) {}
    }

    private void printEvent(LogEvent e) {
        String time   = TIME_FMT.format(Instant.ofEpochMilli(e.getTimestamp()));
        String level  = String.format("%-5s", e.getLevel());
        String logger = shortenLogger(e.getLoggerName());
        String imgTag = e.hasImage() ? " " + GREY + "[" + e.getImageFormat() + "]" + RESET : "";
        String colour = levelColour(e.getLevel());

        System.out.printf("%s%s%s %s%s%s %s[%s]%s %s[%s]%s %s%s%s%s%n",
                GREY, time, RESET,
                colour + BOLD, level, RESET,
                GREY, logger, RESET,
                GREY, e.getThread(), RESET,
                colour, e.getMessage(), RESET,
                imgTag);
    }

    /** Print connection statistics whenever they change. */
    private void printStats(ConnectionStats stats) {
        System.out.printf("%s[CLIENTS] Now: %d | Peak: %d | Total: %d%s%n",
                GREY, stats.current(), stats.peak(), stats.total(), RESET);
    }

    private String levelColour(String level) {
        if (level == null) return RESET;
        return switch (level.toUpperCase()) {
            case "TRACE" -> GREY;
            case "DEBUG" -> CYAN;
            case "INFO"  -> GREEN;
            case "WARN"  -> YELLOW;
            case "ERROR" -> RED;
            default      -> RESET;
        };
    }

    private String shortenLogger(String name) {
        if (name == null) return "?";
        String[] parts = name.split("\\.");
        if (parts.length <= 2) return name;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) sb.append(parts[i].charAt(0)).append('.');
        sb.append(parts[parts.length - 1]);
        return sb.toString();
    }

    private void printBanner() {
        System.out.println(CYAN + BOLD + """
                ╔═══════════════════════════════════════════════╗
                ║   JDBS – Java Debug Bridge Server  v1.0.0    ║
                ║   github.com/tauasa/jdbs                      ║
                ╚═══════════════════════════════════════════════╝
                """ + RESET);
    }
}
