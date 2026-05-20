package org.tauasa.apps.jdbs.server;

import java.util.prefs.Preferences;

/**
 * Runtime configuration for the JDBS server.
 * Settings are persisted via {@link Preferences} and survive restarts.
 */
public class ServerConfig {

    public static final int DEFAULT_PORT        = 6218;
    public static final int DEFAULT_MAX_CLIENTS = 10;

    private static final String PREF_PORT        = "port";
    private static final String PREF_MAX_CLIENTS = "maxClients";
    private static final String PREF_DARK_MODE   = "darkMode";

    private final Preferences prefs;

    private int     port;
    private int     maxClients;
    private boolean darkMode;

    private ServerConfig(Preferences prefs) {
        this.prefs      = prefs;
        this.port       = prefs.getInt(PREF_PORT,        DEFAULT_PORT);
        this.maxClients = prefs.getInt(PREF_MAX_CLIENTS, DEFAULT_MAX_CLIENTS);
        this.darkMode   = prefs.getBoolean(PREF_DARK_MODE, false);
    }

    /** Load persisted config (or defaults on first run). */
    public static ServerConfig load() {
        Preferences prefs = Preferences.userNodeForPackage(ServerConfig.class);
        return new ServerConfig(prefs);
    }

    /** Persist current values to the OS preference store. */
    public void save() {
        prefs.putInt(PREF_PORT,        port);
        prefs.putInt(PREF_MAX_CLIENTS, maxClients);
        prefs.putBoolean(PREF_DARK_MODE, darkMode);
    }

    // ── Getters / setters ───────────────────────────────────────────────────────

    public int getPort()            { return port; }
    public void setPort(int v)      { this.port = v; }

    public int getMaxClients()            { return maxClients; }
    public void setMaxClients(int v)      { this.maxClients = v; }

    public boolean isDarkMode()           { return darkMode; }
    public void setDarkMode(boolean v)    { this.darkMode = v; }
}
