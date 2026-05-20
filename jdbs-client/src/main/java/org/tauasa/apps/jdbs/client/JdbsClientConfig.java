package org.tauasa.apps.jdbs.client;

/**
 * Configuration for a JDBS client connection.
 *
 * <p>Instances are built by the appender plugin classes (Log4j2 / Logback) from
 * their respective configuration files, or programmatically via the builder.
 */
public class JdbsClientConfig {

    public static final String DEFAULT_HOST             = "localhost";
    public static final int    DEFAULT_PORT             = 6218;
    public static final String DEFAULT_LEVEL            = "DEBUG";
    public static final long   DEFAULT_RECONNECT_DELAY  = 5_000L;  // ms

    private final String host;
    private final int    port;
    private final String level;             // minimum level to forward to the server
    private final long   reconnectDelayMs;  // delay before reconnect attempt on disconnect

    private JdbsClientConfig(Builder b) {
        this.host             = b.host;
        this.port             = b.port;
        this.level            = b.level.toUpperCase();
        this.reconnectDelayMs = b.reconnectDelayMs;
    }

    // ── Getters ───────────────────────────────────────────────────────────────────

    public String getHost()              { return host; }
    public int    getPort()              { return port; }
    public String getLevel()             { return level; }
    public long   getReconnectDelayMs()  { return reconnectDelayMs; }

    // ── Builder ───────────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String host             = DEFAULT_HOST;
        private int    port             = DEFAULT_PORT;
        private String level            = DEFAULT_LEVEL;
        private long   reconnectDelayMs = DEFAULT_RECONNECT_DELAY;

        public Builder host(String host)                          { this.host = host; return this; }
        public Builder port(int port)                             { this.port = port; return this; }
        public Builder level(String level)                        { this.level = level; return this; }
        public Builder reconnectDelayMs(long reconnectDelayMs)    { this.reconnectDelayMs = reconnectDelayMs; return this; }

        public JdbsClientConfig build() { return new JdbsClientConfig(this); }
    }

    @Override
    public String toString() {
        return String.format("JdbsClientConfig{host='%s', port=%d, level='%s', reconnectDelayMs=%d}",
                host, port, level, reconnectDelayMs);
    }
}
