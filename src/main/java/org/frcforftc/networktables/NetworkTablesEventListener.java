package org.frcforftc.networktables;


import java.util.EnumSet;
import java.util.function.Consumer;

public class NetworkTablesEventListener {
    private final Consumer<NetworkTablesEvent> m_listener;
    private final EnumSet<NetworkTablesEvent> m_eventTypes;

    public NetworkTablesEventListener(EnumSet<NetworkTablesEvent> events, Consumer<NetworkTablesEvent> listener) {
        this.m_listener = listener;
        this.m_eventTypes = events;
    }

    public void apply(NetworkTablesEvent event) {
        m_listener.accept(event);
    }

    public EnumSet<NetworkTablesEvent> getEventTypes() {
        return m_eventTypes;
    }
}