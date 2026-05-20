package org.tauasa.apps.jdbs.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tauasa.apps.jdbs.model.LogEvent;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * TCP server that accepts JDBS client connections and dispatches {@link LogEvent}s.
 *
 * <h3>Connection statistics</h3>
 * <ul>
 *   <li><b>current</b>  – clients connected right now</li>
 *   <li><b>peak</b>     – maximum concurrent connections since the server started</li>
 *   <li><b>total</b>    – cumulative count of every client that has ever connected</li>
 * </ul>
 *
 * All three are surfaced via the {@link #setStatsListener(Consumer) stats listener}
 * callback so the GUI and CLI can display them without polling.
 */
public class JdbsServer {

    private static final Logger log = LoggerFactory.getLogger(JdbsServer.class);

    // ── Connection statistics ────────────────────────────────────────────────────
    /**
     * Snapshot of the three connection counters, delivered to the stats listener
     * every time any one of them changes.
     */
    public record ConnectionStats(int current, int peak, int total) {
        @Override
        public String toString() {
            return String.format("Connected: %d | Peak: %d | Total: %d", current, peak, total);
        }
    }

    // ── Fields ───────────────────────────────────────────────────────────────────
    private final ServerConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    private ServerSocket      serverSocket;
    private ExecutorService   pool;
    private volatile boolean  running = false;

    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    /** Clients connected right now – derived from clients.size(), kept as AtomicInt for speed. */
    private final AtomicInteger currentClients = new AtomicInteger(0);
    /** Highest value currentClients has ever reached. */
    private final AtomicInteger peakClients    = new AtomicInteger(0);
    /** Cumulative number of clients that have ever connected (never decremented). */
    private final AtomicInteger totalClients   = new AtomicInteger(0);

    // ── Callbacks ────────────────────────────────────────────────────────────────
    private Consumer<LogEvent>        eventListener;
    private Consumer<String>          statusListener;
    private Consumer<ConnectionStats> statsListener;

    // ── Constructor ──────────────────────────────────────────────────────────────
    public JdbsServer(ServerConfig config) {
        this.config = config;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────

    /**
     * Starts the server on the configured port.
     *
     * @throws IOException if the port is already in use or cannot be opened.
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(config.getPort());
        pool         = Executors.newFixedThreadPool(config.getMaxClients());
        running      = true;

        Thread acceptThread = new Thread(this::acceptLoop, "jdbs-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        log.info("JDBS server started on port {}", config.getPort());
        notifyStatus("Server listening on port " + config.getPort());
        notifyStats(); // publish zeroed stats on start
    }

    /** Gracefully shuts down the server and all active client connections. */
    public void stop() {
        if (!running) return;
        running = false;

        try { serverSocket.close(); } catch (IOException ignored) {}
        clients.forEach(ClientHandler::close);
        clients.clear();

        pool.shutdown();
        try {
            if (!pool.awaitTermination(3, TimeUnit.SECONDS)) pool.shutdownNow();
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        currentClients.set(0);
        notifyStatus("Server stopped");
        notifyStats();
        log.info("JDBS server stopped");
    }

    public boolean isRunning() { return running; }

    // ── Internal ─────────────────────────────────────────────────────────────────

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();

                if (currentClients.get() >= config.getMaxClients()) {
                    log.warn("Max clients ({}) reached – rejecting {}", config.getMaxClients(),
                            socket.getInetAddress().getHostAddress());
                    socket.close();
                    notifyStatus("Connection rejected – max clients (" + config.getMaxClients() + ") reached");
                    continue;
                }

                ClientHandler handler = new ClientHandler(socket, this, mapper);
                clients.add(handler);

                int now   = currentClients.incrementAndGet();
                int total = totalClients.incrementAndGet();
                // Update peak only if current exceeds previous peak (compare-and-set loop)
                peakClients.accumulateAndGet(now, Math::max);

                pool.submit(handler);

                log.info("Client connected: {} (now={} peak={} total={})",
                        socket.getInetAddress().getHostAddress(),
                        now, peakClients.get(), total);
                notifyStatus("Client connected: " + socket.getInetAddress().getHostAddress());
                notifyStats();

            } catch (IOException e) {
                if (running) log.error("Accept-loop error", e);
            }
        }
    }

    /** Called by {@link ClientHandler} on the handler's thread after each parsed event. */
    void dispatchEvent(LogEvent event) {
        if (eventListener != null) eventListener.accept(event);
    }

    /** Called by {@link ClientHandler} when the client's socket closes. */
    void onClientDisconnected(ClientHandler handler) {
        clients.remove(handler);
        int now = currentClients.decrementAndGet();
        log.info("Client disconnected: {} (now={} peak={} total={})",
                handler.getRemoteAddress(), now, peakClients.get(), totalClients.get());
        notifyStatus("Client disconnected: " + handler.getRemoteAddress());
        notifyStats();
    }

    // ── Notification helpers ──────────────────────────────────────────────────────

    private void notifyStatus(String msg) {
        if (statusListener != null) statusListener.accept(msg);
    }

    private void notifyStats() {
        if (statsListener != null) {
            statsListener.accept(new ConnectionStats(
                    currentClients.get(), peakClients.get(), totalClients.get()));
        }
    }

    // ── Public read-only accessors ────────────────────────────────────────────────

    public int getCurrentClientCount() { return currentClients.get(); }
    public int getPeakClientCount()    { return peakClients.get(); }
    public int getTotalClientCount()   { return totalClients.get(); }

    public ConnectionStats getStats() {
        return new ConnectionStats(currentClients.get(), peakClients.get(), totalClients.get());
    }

    // ── Callback setters ──────────────────────────────────────────────────────────

    public void setEventListener(Consumer<LogEvent> listener)        { this.eventListener  = listener; }
    public void setStatusListener(Consumer<String> listener)         { this.statusListener = listener; }
    public void setStatsListener(Consumer<ConnectionStats> listener) { this.statsListener  = listener; }
}
