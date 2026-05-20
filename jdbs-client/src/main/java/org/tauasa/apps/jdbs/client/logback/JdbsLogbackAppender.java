package org.tauasa.apps.jdbs.client.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.tauasa.apps.jdbs.client.JdbsClientConfig;
import org.tauasa.apps.jdbs.client.JdbsClientConnection;
import org.tauasa.apps.jdbs.client.JdbsClientEvent;

/**
 * Logback appender that forwards {@link ILoggingEvent}s to a JDBS server.
 *
 * <h3>logback.xml configuration</h3>
 * <pre>{@code
 * <configuration>
 *   <appender name="JDBS" class="org.tauasa.apps.jdbs.client.logback.JdbsLogbackAppender">
 *     <host>localhost</host>
 *     <port>6218</port>
 *     <level>DEBUG</level>
 *     <reconnectDelayMs>5000</reconnectDelayMs>
 *   </appender>
 *
 *   <root level="DEBUG">
 *     <appender-ref ref="JDBS"/>
 *   </root>
 * </configuration>
 * }</pre>
 *
 * <h3>Attaching images</h3>
 * Call {@link JdbsClientConnection#attachImage(byte[], String)} on the current
 * thread immediately before the log statement.  The image is consumed and
 * cleared automatically after the first send.
 * <pre>{@code
 * JdbsClientConnection.attachImage(pngBytes, "PNG");
 * logger.info("Screenshot captured");
 * }</pre>
 */
public class JdbsLogbackAppender extends AppenderBase<ILoggingEvent> {

    // ── Logback JavaBean properties – set by Joran XML parser ─────────────────
    private String host             = JdbsClientConfig.DEFAULT_HOST;
    private int    port             = JdbsClientConfig.DEFAULT_PORT;
    private String level            = JdbsClientConfig.DEFAULT_LEVEL;
    private long   reconnectDelayMs = JdbsClientConfig.DEFAULT_RECONNECT_DELAY;

    // ── Runtime state (initialised in start()) ─────────────────────────────────
    private JdbsClientConnection connection;

    /**
     * Cached minimum level int value for fast comparison on the hot path.
     * Logback level int scale: TRACE=5000, DEBUG=10000, INFO=20000,
     * WARN=30000, ERROR=40000.  Higher int = higher severity.
     */
    private int minLevelInt;

    // ── Logback lifecycle ──────────────────────────────────────────────────────

    @Override
    public void start() {
        JdbsClientConfig cfg = JdbsClientConfig.builder()
                .host(host)
                .port(port)
                .level(level)
                .reconnectDelayMs(reconnectDelayMs)
                .build();

        // Cache the minimum level once so append() pays zero allocation cost
        minLevelInt = parseLevel(level).toInt();

        connection = new JdbsClientConnection(cfg);
        connection.start();

        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        if (connection != null) connection.close();
    }

    // ── Core append ────────────────────────────────────────────────────────────

    @Override
    protected void append(ILoggingEvent event) {
        // Skip events whose severity is below the configured minimum.
        // Both sides use Logback's toInt() scale: higher = more severe.
        if (event.getLevel().toInt() < minLevelInt) return;

        JdbsClientEvent ce = JdbsClientEvent.of(
                event.getTimeStamp(),
                event.getLevel().levelStr,
                event.getLoggerName(),
                event.getThreadName(),
                event.getFormattedMessage()
        );

        connection.send(ce);
    }

    // ── JavaBean setters (Logback/Joran XML parser requires these) ─────────────

    public void setHost(String host)                       { this.host = host; }
    public void setPort(int port)                          { this.port = port; }
    public void setLevel(String level)                     { this.level = level; }
    public void setReconnectDelayMs(long reconnectDelayMs) { this.reconnectDelayMs = reconnectDelayMs; }

    public String getHost()             { return host; }
    public int    getPort()             { return port; }
    public String getLevel()            { return level; }
    public long   getReconnectDelayMs() { return reconnectDelayMs; }

    // ── Internal ───────────────────────────────────────────────────────────────

    private static Level parseLevel(String level) {
        try {
            return Level.valueOf(level.toUpperCase());
        } catch (Exception e) {
            return Level.DEBUG;
        }
    }
}
