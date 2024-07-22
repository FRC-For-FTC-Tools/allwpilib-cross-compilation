package org.frcforftc.networktables;


public class NetworkTablesEventListener {
    private final NetworkTablesEvent m_eventType;
    private final TopicListener m_listener;

    public NetworkTablesEventListener(NetworkTablesEvent eventType, TopicListener listener) {
        this.m_listener = listener;
        this.m_eventType = eventType;
    }

    public void apply(NetworkTablesEntry entry, NetworkTablesValue value) {
        m_listener.apply(entry, value);
    }

    public NetworkTablesEvent getEventType() {
        return m_eventType;
    }
}