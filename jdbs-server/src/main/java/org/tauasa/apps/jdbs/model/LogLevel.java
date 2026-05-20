package org.tauasa.apps.jdbs.model;

/**
 * JDBS log levels in ascending severity order (TRACE → ERROR).
 */
public enum LogLevel {
    TRACE(0),
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4);

    private final int ordinalValue;

    LogLevel(int ordinalValue) {
        this.ordinalValue = ordinalValue;
    }

    public int getOrdinalValue() {
        return ordinalValue;
    }

    /** Returns true if this level is at least as severe as {@code threshold}. */
    public boolean isAtLeast(LogLevel threshold) {
        return this.ordinalValue >= threshold.ordinalValue;
    }

    /** Parse case-insensitively, defaulting to DEBUG on failure. */
    public static LogLevel parse(String name, LogLevel defaultLevel) {
        if (name == null || name.isBlank()) return defaultLevel;
        try {
            return LogLevel.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultLevel;
        }
    }

    /** Map from an integer ordinal to the corresponding LogLevel. */
    public static LogLevel fromOrdinal(int ordinal) {
        for (LogLevel l : values()) {
            if (l.ordinalValue == ordinal) return l;
        }
        return DEBUG;
    }
}
