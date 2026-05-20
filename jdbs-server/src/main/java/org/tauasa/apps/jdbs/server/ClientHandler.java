package org.tauasa.apps.jdbs.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tauasa.apps.jdbs.model.LogEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

/**
 * Handles a single JDBS client connection.
 *
 * <p>Protocol: one JSON object per line (newline-delimited JSON / NDJSON).
 * Each line is deserialized into a {@link LogEvent} and forwarded to the server.
 */
class ClientHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket       socket;
    private final JdbsServer   server;
    private final ObjectMapper mapper;
    private final String       remoteAddress;

    ClientHandler(Socket socket, JdbsServer server, ObjectMapper mapper) {
        this.socket        = socket;
        this.server        = server;
        this.mapper        = mapper;
        this.remoteAddress = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }

    @Override
    public void run() {
        log.info("Client connected: {}", remoteAddress);
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    LogEvent event = mapper.readValue(line, LogEvent.class);
                    server.dispatchEvent(event);
                } catch (Exception e) {
                    log.warn("Malformed JSON from {}: {}", remoteAddress, e.getMessage());
                }
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                log.debug("Client {} disconnected: {}", remoteAddress, e.getMessage());
            }
        } finally {
            close();
            server.onClientDisconnected(this);
            log.info("Client disconnected: {}", remoteAddress);
        }
    }

    void close() {
        try {
            socket.close();
        } catch (IOException ignored) {}
    }

    String getRemoteAddress() {
        return remoteAddress;
    }
}
