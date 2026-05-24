package org.tauasa.apps.jdbs.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tauasa.apps.jdbs.model.LogEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles one JDBS client TCP connection.
 *
 * <p>Each handler is assigned a unique {@link #id} at construction time so the
 * GUI can target a specific connection for forced termination.
 */
class ClientHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    /** Unique identifier for this connection (used to target forced disconnect). */
    private final String        id;
    private final Socket        socket;
    private final JdbsServer    server;
    private final ObjectMapper  mapper;
    private final String        remoteAddress;   // host:port  e.g. "192.168.1.5:54321"
    private final String        remoteHost;      // host only  e.g. "192.168.1.5"
    private final Instant       connectedAt;
    private final AtomicLong    eventCount = new AtomicLong(0);

    ClientHandler(Socket socket, JdbsServer server, ObjectMapper mapper) {
        this.id            = UUID.randomUUID().toString();
        this.socket        = socket;
        this.server        = server;
        this.mapper        = mapper;
        this.remoteHost    = socket.getInetAddress().getHostAddress();
        this.remoteAddress = remoteHost + ":" + socket.getPort();
        this.connectedAt   = Instant.now();
    }

    @Override
    public void run() {
        log.info("Client connected: {} (id={})", remoteAddress, id);
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    LogEvent event = mapper.readValue(line, LogEvent.class);
                    event.setClientIp(remoteHost);   // authoritative – always from the socket
                    eventCount.incrementAndGet();
                    server.dispatchEvent(event);
                } catch (Exception e) {
                    log.warn("Malformed JSON from {}: {}", remoteAddress, e.getMessage());
                }
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                log.debug("Client {} read error: {}", remoteAddress, e.getMessage());
            }
        } finally {
            close();
            server.onClientDisconnected(this);
            log.info("Client disconnected: {} (events={})", remoteAddress, eventCount.get());
        }
    }

    /** Close the underlying socket, causing the read loop to exit. */
    void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }

    // ── Accessors ────────────────────────────────────────────────────────────────

    String    getId()             { return id; }
    String    getRemoteAddress()  { return remoteAddress; }
    Instant   getConnectedAt()    { return connectedAt; }
    long      getEventCount()     { return eventCount.get(); }
}
