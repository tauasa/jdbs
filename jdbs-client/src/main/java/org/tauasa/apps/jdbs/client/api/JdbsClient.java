package org.tauasa.apps.jdbs.client.api;

import org.tauasa.apps.jdbs.client.JdbsClientConfig;
import org.tauasa.apps.jdbs.client.JdbsClientConnection;
import org.tauasa.apps.jdbs.client.JdbsClientEvent;

/**
 * Lightweight JDBS client API.
 *
 * <p>Use this class when you want to send events to a JDBS server directly from
 * application code, <em>without</em> configuring a logging framework appender.
 * This is the class bundled in the {@code jdbs-client-lite} fat jar.
 *
 * <h3>Basic usage</h3>
 * <pre>{@code
 * JdbsClient client = JdbsClient.connect("localhost", 6218);
 *
 * client.debug("com.example.MyService", "Application starting up");
 * client.info ("com.example.MyService", "Processing 1 234 records");
 * client.warn ("com.example.MyService", "Retry attempt 2/3");
 * client.error("com.example.MyService", "Connection pool exhausted");
 *
 * client.close();
 * }</pre>
 *
 * <h3>Sending images</h3>
 * <pre>{@code
 * byte[] chartPng = renderChartToPng(...);   // your own rendering logic
 * client.info("com.example.Dashboard", "Weekly sales chart", chartPng, "PNG");
 * }</pre>
 *
 * <h3>Full configuration</h3>
 * <pre>{@code
 * JdbsClient client = JdbsClient.builder()
 *         .host("my-dev-machine")
 *         .port(6218)
 *         .minLevel("INFO")          // drop TRACE/DEBUG events before sending
 *         .reconnectDelayMs(3_000)
 *         .build();
 * }</pre>
 */
public final class JdbsClient implements AutoCloseable {

    private final JdbsClientConnection connection;

    private JdbsClient(JdbsClientConfig config) {
        this.connection = new JdbsClientConnection(config);
        this.connection.start();
    }

    // ── Factory methods ───────────────────────────────────────────────────────────

    /** Connect to a JDBS server with default settings (DEBUG level, 5 s reconnect). */
    public static JdbsClient connect(String host, int port) {
        return new JdbsClient(JdbsClientConfig.builder().host(host).port(port).build());
    }

    /** Connect to {@code localhost:6218} with all defaults. */
    public static JdbsClient connectLocal() {
        return connect(JdbsClientConfig.DEFAULT_HOST, JdbsClientConfig.DEFAULT_PORT);
    }

    /** Full-configuration builder entry point. */
    public static Builder builder() { return new Builder(); }

    // ── Logging methods ───────────────────────────────────────────────────────────

    public void trace(String logger, String message) {
        send("TRACE", logger, message, null, null);
    }

    public void debug(String logger, String message) {
        send("DEBUG", logger, message, null, null);
    }

    public void info(String logger, String message) {
        send("INFO", logger, message, null, null);
    }

    public void warn(String logger, String message) {
        send("WARN", logger, message, null, null);
    }

    public void error(String logger, String message) {
        send("ERROR", logger, message, null, null);
    }

    // ── Methods with image attachment ─────────────────────────────────────────────

    /** Send a DEBUG event with an embedded image. */
    public void debug(String logger, String message, byte[] imageBytes, String format) {
        send("DEBUG", logger, message, imageBytes, format);
    }

    /** Send an INFO event with an embedded image. */
    public void info(String logger, String message, byte[] imageBytes, String format) {
        send("INFO", logger, message, imageBytes, format);
    }

    /** Send a WARN event with an embedded image. */
    public void warn(String logger, String message, byte[] imageBytes, String format) {
        send("WARN", logger, message, imageBytes, format);
    }

    /** Send an ERROR event with an embedded image. */
    public void error(String logger, String message, byte[] imageBytes, String format) {
        send("ERROR", logger, message, imageBytes, format);
    }

    /**
     * Low-level send – build and dispatch a {@link JdbsClientEvent} on the
     * current thread.
     *
     * @param level      log level string (TRACE|DEBUG|INFO|WARN|ERROR)
     * @param logger     logger / category name (conventionally a class name)
     * @param message    the log message
     * @param imageBytes optional raw image bytes; {@code null} for no image
     * @param format     {@code "PNG"} or {@code "JPG"}; ignored when imageBytes is null
     */
    public void send(String level, String logger, String message,
                     byte[] imageBytes, String format) {
        JdbsClientEvent event = JdbsClientEvent.of(
                System.currentTimeMillis(),
                level,
                logger,
                Thread.currentThread().getName(),
                message);

        if (imageBytes != null && imageBytes.length > 0) {
            event.setImageBytes(imageBytes, format);
        }

        connection.send(event);
    }

    /**
     * Closes the connection to the JDBS server.
     * Implements {@link AutoCloseable} for use in try-with-resources.
     */
    @Override
    public void close() {
        connection.close();
    }

    // ── Builder ───────────────────────────────────────────────────────────────────

    public static final class Builder {
        private String host             = JdbsClientConfig.DEFAULT_HOST;
        private int    port             = JdbsClientConfig.DEFAULT_PORT;
        private String minLevel         = JdbsClientConfig.DEFAULT_LEVEL;
        private long   reconnectDelayMs = JdbsClientConfig.DEFAULT_RECONNECT_DELAY;

        public Builder host(String host)                       { this.host = host; return this; }
        public Builder port(int port)                          { this.port = port; return this; }
        public Builder minLevel(String level)                  { this.minLevel = level; return this; }
        public Builder reconnectDelayMs(long ms)               { this.reconnectDelayMs = ms; return this; }

        public JdbsClient build() {
            return new JdbsClient(JdbsClientConfig.builder()
                    .host(host).port(port).level(minLevel)
                    .reconnectDelayMs(reconnectDelayMs).build());
        }
    }
}
