package org.tauasa.apps.jdbs.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tauasa.apps.jdbs.model.LogEvent;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * TCP server – accepts JDBS client connections and dispatches {@link LogEvent}s.
 *
 * <h3>Connection statistics</h3>
 * <ul>
 *   <li><b>current</b>  – clients connected right now</li>
 *   <li><b>peak</b>     – maximum concurrent connections since start</li>
 *   <li><b>total</b>    – cumulative count of all ever-connected clients</li>
 * </ul>
 *
 * <h3>Forced termination</h3>
 * Call {@link #terminateClient(String)} with a client's UUID to close it
 * immediately from the GUI.
 */
public class JdbsServer {

    private static final Logger log = LoggerFactory.getLogger(JdbsServer.class);

    // ── Nested types ─────────────────────────────────────────────────────────────

    /**
     * Immutable snapshot of one connected client's metadata, safe to hand to
     * the UI thread without further synchronisation.
     */
    public record ClientInfo(
            String  id,
            String  remoteAddress,
            Instant connectedAt,
            long    eventCount) {}

    /**
     * Snapshot of aggregate connection counters delivered to the stats listener.
     */
    public record ConnectionStats(int current, int peak, int total) {
        @Override public String toString() {
            return "Now: " + current + "  Peak: " + peak + "  Total: " + total;
        }
    }

    // ── Fields ───────────────────────────────────────────────────────────────────

    private final ServerConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    private ServerSocket    serverSocket;
    private ExecutorService pool;
    private volatile boolean running = false;

    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    private final AtomicInteger currentClients = new AtomicInteger(0);
    private final AtomicInteger peakClients    = new AtomicInteger(0);
    private final AtomicInteger totalClients   = new AtomicInteger(0);

    // ── Callbacks ─────────────────────────────────────────────────────────────────

    private Consumer<LogEvent>         eventListener;
    private Consumer<String>           statusListener;
    private Consumer<ConnectionStats>  statsListener;
    /** Fires whenever the active-client list changes (connect or disconnect). */
    private Consumer<List<ClientInfo>> clientListListener;
    /** Fires when a new client connects AND beep-on-connect is enabled. */
    private Runnable                   beepListener;

    // ── Constructor ───────────────────────────────────────────────────────────────

    public JdbsServer(ServerConfig config) {
        this.config = config;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────────

    public void start() throws IOException {
        serverSocket = new ServerSocket(config.getPort());
        pool         = Executors.newFixedThreadPool(config.getMaxClients());
        running      = true;

        Thread t = new Thread(this::acceptLoop, "jdbs-accept");
        t.setDaemon(true);
        t.start();

        log.info("JDBS server started on port {}", config.getPort());
        notifyStatus("Server listening on port " + config.getPort());
        notifyStats();
        notifyClientList();
    }

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
        notifyClientList();
        log.info("JDBS server stopped");
    }

    /**
     * Forcefully closes the connection for the client with the given UUID.
     *
     * @param clientId the {@link ClientInfo#id()} of the target client
     * @return {@code true} if a matching client was found and closed
     */
    public boolean terminateClient(String clientId) {
        for (ClientHandler h : clients) {
            if (h.getId().equals(clientId)) {
                log.info("Forcefully terminating client {} ({})", clientId, h.getRemoteAddress());
                h.close(); // causes the read-loop to exit → onClientDisconnected fires
                return true;
            }
        }
        return false;
    }

    public boolean isRunning()             { return running; }
    public int     getCurrentClientCount() { return currentClients.get(); }
    public int     getPeakClientCount()    { return peakClients.get(); }
    public int     getTotalClientCount()   { return totalClients.get(); }

    public ConnectionStats getStats() {
        return new ConnectionStats(currentClients.get(), peakClients.get(), totalClients.get());
    }

    /** Returns an immutable snapshot of all currently connected clients. */
    public List<ClientInfo> getClientInfos() {
        return clients.stream()
                .map(h -> new ClientInfo(
                        h.getId(), h.getRemoteAddress(), h.getConnectedAt(), h.getEventCount()))
                .collect(Collectors.toUnmodifiableList());
    }

    // ── Internal ──────────────────────────────────────────────────────────────────

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();

                if (currentClients.get() >= config.getMaxClients()) {
                    log.warn("Max clients ({}) reached – rejecting {}",
                            config.getMaxClients(), socket.getInetAddress().getHostAddress());
                    socket.close();
                    notifyStatus("Connection rejected – max clients ("
                            + config.getMaxClients() + ") reached");
                    continue;
                }

                ClientHandler handler = new ClientHandler(socket, this, mapper);
                clients.add(handler);

                int now = currentClients.incrementAndGet();
                totalClients.incrementAndGet();
                peakClients.accumulateAndGet(now, Math::max);

                pool.submit(handler);

                log.info("Client connected: {} (now={} peak={} total={})",
                        socket.getInetAddress().getHostAddress(),
                        now, peakClients.get(), totalClients.get());
                notifyStatus("Client connected: " + socket.getInetAddress().getHostAddress());
                notifyStats();
                notifyClientList();

                // Beep (only if setting is enabled)
                if (config.isBeepOnConnect() && beepListener != null) {
                    beepListener.run();
                }

            } catch (IOException e) {
                if (running) log.error("Accept-loop error", e);
            }
        }
    }

    void dispatchEvent(LogEvent event) {
        if (eventListener != null) eventListener.accept(event);
    }

    void onClientDisconnected(ClientHandler handler) {
        clients.remove(handler);
        int now = currentClients.decrementAndGet();
        log.info("Client disconnected: {} (now={} total-events={})",
                handler.getRemoteAddress(), now, handler.getEventCount());
        notifyStatus("Client disconnected: " + handler.getRemoteAddress());
        notifyStats();
        notifyClientList();
    }

    // ── Notification helpers ──────────────────────────────────────────────────────

    private void notifyStatus(String msg)     { if (statusListener     != null) statusListener.accept(msg); }
    private void notifyStats()                { if (statsListener      != null) statsListener.accept(getStats()); }
    private void notifyClientList()           { if (clientListListener != null) clientListListener.accept(getClientInfos()); }

    // ── Callback setters ──────────────────────────────────────────────────────────

    public void setEventListener(Consumer<LogEvent>         l) { this.eventListener      = l; }
    public void setStatusListener(Consumer<String>          l) { this.statusListener     = l; }
    public void setStatsListener(Consumer<ConnectionStats>  l) { this.statsListener      = l; }
    public void setClientListListener(Consumer<List<ClientInfo>> l) { this.clientListListener = l; }
    public void setBeepListener(Runnable                    l) { this.beepListener       = l; }
}
