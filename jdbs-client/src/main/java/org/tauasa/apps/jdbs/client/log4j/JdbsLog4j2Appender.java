package org.tauasa.apps.jdbs.client.log4j;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.tauasa.apps.jdbs.client.JdbsClientConfig;
import org.tauasa.apps.jdbs.client.JdbsClientConnection;
import org.tauasa.apps.jdbs.client.JdbsClientEvent;

import java.io.Serializable;

/**
 * Log4j2 appender plugin that forwards log events to a JDBS server.
 *
 * <h3>log4j2.xml configuration</h3>
 * <pre>{@code
 * <Configuration packages="org.tauasa.apps.jdbs.client.log4j">
 *   <Appenders>
 *     <Jdbs name="jdbs"
 *           host="localhost"
 *           port="6218"
 *           level="DEBUG"
 *           reconnectDelayMs="5000"/>
 *   </Appenders>
 *   <Loggers>
 *     <Root level="debug">
 *       <AppenderRef ref="jdbs"/>
 *     </Root>
 *   </Loggers>
 * </Configuration>
 * }</pre>
 *
 * <h3>Attaching images</h3>
 * <pre>{@code
 * // Attach a PNG before the next log call on this thread:
 * JdbsClientConnection.attachImage(pngBytes, "PNG");
 * logger.info("Screenshot captured");
 * }</pre>
 */
@Plugin(name = "Jdbs", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public final class JdbsLog4j2Appender extends AbstractAppender {

    private final JdbsClientConnection connection;
    private final int                  minLevelInt; // Log4j2 Level int value

    private JdbsLog4j2Appender(String name,
                                Filter filter,
                                JdbsClientConnection connection,
                                int minLevelInt) {
        super(name, filter, null, true, Property.EMPTY_ARRAY);
        this.connection  = connection;
        this.minLevelInt = minLevelInt;
    }

    /**
     * Log4j2 factory method – called by the framework when parsing config.
     *
     * @param name              appender name (required, must be unique in the config)
     * @param host              JDBS server hostname (default: localhost)
     * @param port              JDBS server port (default: 6218)
     * @param level             minimum level to forward; events below this are dropped
     *                          (default: DEBUG)
     * @param reconnectDelayMs  reconnect delay in milliseconds on connection loss (default: 5000)
     * @param filter            optional Log4j2 filter element
     */
    @PluginFactory
    public static JdbsLog4j2Appender createAppender(
            @PluginAttribute(value = "name", defaultString = "Jdbs")   String name,
            @PluginAttribute(value = "host", defaultString = "localhost") String host,
            @PluginAttribute(value = "port", defaultInt = 6218)           int port,
            @PluginAttribute(value = "level", defaultString = "DEBUG")    String level,
            @PluginAttribute(value = "reconnectDelayMs", defaultLong = 5000L) long reconnectDelayMs,
            @PluginElement("Filter")                                        Filter filter) {

        JdbsClientConfig cfg = JdbsClientConfig.builder()
                .host(host)
                .port(port)
                .level(level)
                .reconnectDelayMs(reconnectDelayMs)
                .build();

        JdbsClientConnection conn = new JdbsClientConnection(cfg);
        conn.start();

        // Convert the configured level string to a Log4j2 Level int for fast comparison
        Level log4jLevel = Level.toLevel(level, Level.DEBUG);

        return new JdbsLog4j2Appender(name, filter, conn, log4jLevel.intLevel());
    }

    // ── Appender lifecycle ────────────────────────────────────────────────────────

    @Override
    public void start() {
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        connection.close();
    }

    // ── Core append ───────────────────────────────────────────────────────────────

    @Override
    public void append(LogEvent event) {
        // Log4j2 uses inverted int scale: higher int = lower severity (TRACE=600, ERROR=200)
        // We want to skip events BELOW our minimum, i.e. int > minLevelInt
        if (event.getLevel().intLevel() > minLevelInt) return;

        JdbsClientEvent ce = JdbsClientEvent.of(
                event.getTimeMillis(),
                event.getLevel().name(),
                event.getLoggerName(),
                event.getThreadName(),
                event.getMessage().getFormattedMessage()
        );

        connection.send(ce);
    }
}
