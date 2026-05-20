package org.tauasa.apps.jdbs.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe TCP connection to a JDBS server.
 *
 * <h3>Protocol</h3>
 * Each {@link JdbsClientEvent} is serialized to a compact single-line JSON object
 * (no pretty-printing) followed by a newline ({@code \n}) — i.e. newline-delimited
 * JSON (NDJSON).  The server's {@code ClientHandler} splits on newlines to
 * demarcate individual events.
 *
 * <h3>Reconnect</h3>
 * If the connection is lost, a background scheduler retries at the configured
 * {@link JdbsClientConfig#getReconnectDelayMs()} interval.  Calls to
 * {@link #send(JdbsClientEvent)} during a disconnect are silently dropped to
 * avoid blocking application threads.
 *
 * <h3>Image attachment</h3>
 * Images are embedded as Base64-encoded bytes in the {@code img} field.
 * Callers can attach an image to the <em>next</em> log event from the
 * <em>current thread</em> via the MDC-style ThreadLocal helpers:
 * <pre>
 *   JdbsClientConnection.attachImage(pngBytes, "PNG");
 *   logger.info("Chart updated");
 *   // image is automatically cleared after the next send() from this thread
 * </pre>
 */
public class JdbsClientConnection {

    // ── MDC-style thread-local image attachment ───────────────────────────────────
    private static final ThreadLocal<byte[]>  PENDING_IMAGE_BYTES  = new ThreadLocal<>();
    private static final ThreadLocal<String>  PENDING_IMAGE_FORMAT = new ThreadLocal<>();

    /**
     * Attaches an image to the <em>next</em> log event sent by the current thread.
     * The attachment is automatically removed after the send.
     *
     * @param imageBytes raw PNG or JPG bytes
     * @param format     {@code "PNG"} or {@code "JPG"}
     */
    public static void attachImage(byte[] imageBytes, String format) {
        PENDING_IMAGE_BYTES.set(imageBytes);
        PENDING_IMAGE_FORMAT.set(format != null ? format.toUpperCase() : "PNG");
    }

    /** Clears any pending image attachment for the current thread. */
    public static void clearImage() {
        PENDING_IMAGE_BYTES.remove();
        PENDING_IMAGE_FORMAT.remove();
    }

    // ── Fields ────────────────────────────────────────────────────────────────────
    private final JdbsClientConfig config;
    private final ObjectMapper     mapper;

    private final Lock               socketLock  = new ReentrantLock();
    private       Socket             socket;
    private       OutputStream       out;

    private final AtomicBoolean      started     = new AtomicBoolean(false);
    private final AtomicBoolean      closed      = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler;
    private       ScheduledFuture<?>        reconnectFuture;

    // ── Minimum level ordinal (fast numeric comparison on hot path) ────────────
    private final int minLevelOrdinal;

    // ── Constructor ───────────────────────────────────────────────────────────────

    public JdbsClientConnection(JdbsClientConfig config) {
        this.config = config;
        this.mapper = new ObjectMapper()
                .disable(SerializationFeature.INDENT_OUTPUT); // compact / single-line

        this.minLevelOrdinal = levelOrdinal(config.getLevel());

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jdbs-reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────────

    /**
     * Starts the connection.  Safe to call multiple times (idempotent).
     */
    public void start() {
        if (!started.compareAndSet(false, true)) return;
        connect();
    }

    /**
     * Permanently closes the connection.  After calling this, {@link #send}
     * becomes a no-op.
     */
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (reconnectFuture != null) reconnectFuture.cancel(false);
        scheduler.shutdownNow();
        closeSocket();
    }

    // ── Send ──────────────────────────────────────────────────────────────────────

    /**
     * Serialises {@code event} and sends it to the server.
     *
     * <p>A pending image (set via {@link #attachImage}) is consumed and embedded.
     * This method is non-blocking: if the socket is not available, the event is
     * silently dropped.
     *
     * @param event the event to transmit
     */
    public void send(JdbsClientEvent event) {
        if (closed.get()) return;
        if (event.getLevelOrdinal() < minLevelOrdinal) return;

        // Consume any ThreadLocal image attachment
        byte[] imgBytes = PENDING_IMAGE_BYTES.get();
        if (imgBytes != null) {
            event.setImageBytes(imgBytes);
            event.setImageFormat(PENDING_IMAGE_FORMAT.get());
            clearImage();
        }

        socketLock.lock();
        try {
            if (out == null) return; // not connected – drop
            byte[] line = (mapper.writeValueAsString(event) + "\n")
                    .getBytes(StandardCharsets.UTF_8);
            out.write(line);
            out.flush();
        } catch (IOException e) {
            closeSocket();
            scheduleReconnect();
        } finally {
            socketLock.unlock();
        }
    }

    // ── Internal connection management ────────────────────────────────────────────

    private void connect() {
        if (closed.get()) return;
        socketLock.lock();
        try {
            Socket s = new Socket(config.getHost(), config.getPort());
            s.setTcpNoDelay(true);
            s.setKeepAlive(true);
            socket = s;
            out    = s.getOutputStream();
            internalLog("Connected to JDBS server at %s:%d", config.getHost(), config.getPort());
        } catch (IOException e) {
            internalLog("Cannot connect to %s:%d – %s. Retrying in %dms…",
                    config.getHost(), config.getPort(), e.getMessage(), config.getReconnectDelayMs());
            closeSocket();
            scheduleReconnect();
        } finally {
            socketLock.unlock();
        }
    }

    private void closeSocket() {
        socketLock.lock();
        try {
            if (socket != null) { try { socket.close(); } catch (IOException ignored) {} }
            socket = null;
            out    = null;
        } finally {
            socketLock.unlock();
        }
    }

    private synchronized void scheduleReconnect() {
        if (closed.get()) return;
        if (reconnectFuture != null && !reconnectFuture.isDone()) return; // already scheduled
        reconnectFuture = scheduler.schedule(this::connect,
                config.getReconnectDelayMs(), TimeUnit.MILLISECONDS);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private static int levelOrdinal(String level) {
        if (level == null) return 1; // DEBUG
        return switch (level.toUpperCase()) {
            case "TRACE" -> 0;
            case "DEBUG" -> 1;
            case "INFO"  -> 2;
            case "WARN"  -> 3;
            case "ERROR" -> 4;
            default      -> 1;
        };
    }

    /** Writes JDBS internal messages to stderr to avoid recursive logging. */
    private void internalLog(String fmt, Object... args) {
        System.err.printf("[JDBS-client] " + fmt + "%n", args);
    }
}
