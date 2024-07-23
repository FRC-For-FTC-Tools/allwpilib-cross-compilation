package org.frcforftc.networktables;


import java.util.EnumSet;

public class NetworkTablesEventListener {
    private final EventListenerMethod m_listener;
    private final EnumSet<NetworkTablesEvent> m_eventTypes;

    public NetworkTablesEventListener(EnumSet<NetworkTablesEvent> events, EventListenerMethod listener) {
        this.m_listener = listener;
        this.m_eventTypes = events;
    }

    public void apply(NetworkTablesEvent event) {
        m_listener.apply(event);
    }

    public EnumSet<NetworkTablesEvent> getEventTypes() {
        return m_eventTypes;
    }
}