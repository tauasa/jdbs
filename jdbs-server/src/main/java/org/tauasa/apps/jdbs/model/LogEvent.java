package org.tauasa.apps.jdbs.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single log event transmitted from a JDBS client to the server.
 *
 * <p>Field names are intentionally short to minimise wire-format JSON size:
 * <pre>
 *   t    – timestamp (epoch millis)
 *   ip   – client IP address (stamped by the server from the accepted socket)
 *   l    – level (TRACE | DEBUG | INFO | WARN | ERROR)
 *   n    – logger name
 *   th   – thread name
 *   m    – log message
 *   img  – base64-encoded image bytes (optional)
 *   ifmt – image format ("PNG" or "JPG")  (optional, only when img is present)
 * </pre>
 *
 * <p>{@code clientIp} is <em>not</em> parsed from the JSON payload — it is
 * always set server-side in {@code ClientHandler} from the accepted
 * {@link java.net.Socket}, so it cannot be spoofed by a client.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogEvent {

    @JsonProperty("t")
    private long timestamp;

    /**
     * IP address of the client that sent this event.
     * Set by the server from the TCP socket — never parsed from the client payload.
     */
    @JsonIgnore
    private String clientIp;

    @JsonProperty("l")
    private String level;

    @JsonProperty("n")
    private String loggerName;

    @JsonProperty("th")
    private String thread;

    @JsonProperty("m")
    private String message;

    /** Base64-encoded image payload (PNG or JPG). May be {@code null}. */
    @JsonProperty("img")
    private String imageBase64;

    /** "PNG" or "JPG" – only meaningful when {@link #imageBase64} is non-null. */
    @JsonProperty("ifmt")
    private String imageFormat;

    // ── Constructors ────────────────────────────────────────────────────────────

    public LogEvent() {}

    public LogEvent(long timestamp, String level, String loggerName, String thread, String message) {
        this.timestamp = timestamp;
        this.level = level;
        this.loggerName = loggerName;
        this.thread = thread;
        this.message = message;
    }

    // ── Getters / setters ───────────────────────────────────────────────────────

    public long getTimestamp()      { return timestamp; }
    public void setTimestamp(long v){ this.timestamp = v; }

    public String getClientIp()          { return clientIp; }
    public void   setClientIp(String v)  { this.clientIp = v; }

    public String getLevel()        { return level; }
    public void setLevel(String v)  { this.level = v; }

    public String getLoggerName()         { return loggerName; }
    public void setLoggerName(String v)   { this.loggerName = v; }

    public String getThread()       { return thread; }
    public void setThread(String v) { this.thread = v; }

    public String getMessage()      { return message; }
    public void setMessage(String v){ this.message = v; }

    public String getImageBase64()        { return imageBase64; }
    public void setImageBase64(String v)  { this.imageBase64 = v; }

    public String getImageFormat()        { return imageFormat; }
    public void setImageFormat(String v)  { this.imageFormat = v; }

    public boolean hasImage() {
        return imageBase64 != null && !imageBase64.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("[%s] [%s] [%s] %s – %s", timestamp, level, clientIp, loggerName, message);
    }
}
