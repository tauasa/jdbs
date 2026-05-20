package org.tauasa.apps.jdbs.client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Base64;

/**
 * The wire object serialised by JDBS client appenders.
 *
 * <p>Short field names keep JSON payload size minimal:
 * <pre>
 *   t    – timestamp (epoch millis)
 *   l    – level string (TRACE|DEBUG|INFO|WARN|ERROR)
 *   n    – logger name
 *   th   – thread name
 *   m    – log message
 *   img  – base64 image (optional)
 *   ifmt – image format "PNG" or "JPG" (optional)
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JdbsClientEvent {

    @JsonProperty("t")  private long   timestamp;
    @JsonProperty("l")  private String level;
    @JsonProperty("n")  private String loggerName;
    @JsonProperty("th") private String thread;
    @JsonProperty("m")  private String message;
    @JsonProperty("img")  private String imageBase64;
    @JsonProperty("ifmt") private String imageFormat;

    /** Cached numeric ordinal for fast level filtering (not serialised). */
    @JsonIgnore private int levelOrdinal;

    // ── Factory ───────────────────────────────────────────────────────────────────

    public static JdbsClientEvent of(long timestamp, String level, String loggerName,
                                      String thread, String message) {
        JdbsClientEvent e = new JdbsClientEvent();
        e.timestamp    = timestamp;
        e.level        = level;
        e.loggerName   = loggerName;
        e.thread       = thread;
        e.message      = message;
        e.levelOrdinal = levelOrdinal(level);
        return e;
    }

    // ── Image helpers ─────────────────────────────────────────────────────────────

    /**
     * Embeds raw image bytes as Base64 in the event payload.
     *
     * @param bytes  raw PNG or JPG bytes
     * @param format {@code "PNG"} or {@code "JPG"}
     */
    public void setImageBytes(byte[] bytes, String format) {
        if (bytes == null || bytes.length == 0) return;
        this.imageBase64 = Base64.getEncoder().encodeToString(bytes);
        this.imageFormat = (format != null ? format : "PNG").toUpperCase();
    }

    /** Convenience overload – call from {@link JdbsClientConnection#send}. */
    void setImageBytes(byte[] bytes) {
        if (bytes == null) return;
        this.imageBase64 = Base64.getEncoder().encodeToString(bytes);
    }

    void setImageFormat(String format) {
        this.imageFormat = format;
    }

    // ── Getters ───────────────────────────────────────────────────────────────────

    public long   getTimestamp()   { return timestamp; }
    public String getLevel()       { return level; }
    public String getLoggerName()  { return loggerName; }
    public String getThread()      { return thread; }
    public String getMessage()     { return message; }
    public String getImageBase64() { return imageBase64; }
    public String getImageFormat() { return imageFormat; }
    public int    getLevelOrdinal(){ return levelOrdinal; }

    // ── Internal ──────────────────────────────────────────────────────────────────

    private static int levelOrdinal(String level) {
        if (level == null) return 1;
        return switch (level.toUpperCase()) {
            case "TRACE" -> 0; case "DEBUG" -> 1; case "INFO" -> 2;
            case "WARN"  -> 3; case "ERROR" -> 4; default -> 1;
        };
    }
}
