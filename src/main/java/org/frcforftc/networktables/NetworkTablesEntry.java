package org.frcforftc.networktables;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

public class NetworkTablesEntry {
    private final String m_topic;
    private final Map<NetworkTablesEvent, List<NetworkTablesEventListener>> m_listeners = new HashMap<>();
    public int id = 0;
    private NetworkTablesValue m_localValue;
    private ObjectNode m_jsonData;

    public NetworkTablesEntry(String topic, ObjectNode data, NetworkTablesValue localValue) {
        setJsonData(data);
        this.m_topic = topic;
        this.m_localValue = localValue;
    }

    public void addListener(NetworkTablesEventListener l) {
        EnumSet<NetworkTablesEvent> eventTypes = l.getEventTypes();

        for (NetworkTablesEvent type : eventTypes) {
            if (!m_listeners.containsKey(type)) {
                ArrayList<NetworkTablesEventListener> events = new ArrayList<>();
                events.add(l);
                m_listeners.put(type, events);
            } else {
                m_listeners.get(type).add(l);
            }
        }
    }

    public void setJsonData(ObjectNode data) {
        this.m_jsonData = data;
    }

    public NetworkTablesValue getValue() {
        return m_localValue;
    }

    public void setValue(NetworkTablesValue newValue) {
        this.m_localValue = newValue;
    }

    public void update(Object val) {
        m_localValue = new NetworkTablesValue(val, m_localValue.getType());
    }

    void callListenersOfEventType(NetworkTablesEvent eventTypes, NetworkTablesEntry entry, NetworkTablesValue value) {
        for (NetworkTablesEventListener e : m_listeners.get(eventTypes)) {
            e.apply(eventTypes);
        }
    }

    public String getTopic() {
        return m_topic;
    }
}
