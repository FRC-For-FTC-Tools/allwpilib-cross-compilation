package org.frcforftc.networktables;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        NetworkTablesEvent eventType = l.getEventType();
        if (!m_listeners.containsKey(eventType)) {
            m_listeners.put(eventType, List.of(l));
        } else {
            m_listeners.get(eventType).add(l);
        }
    }

    public void setJsonData(ObjectNode data) {
        this.m_jsonData = data;
    }

    public NetworkTablesValue getLocalValue() {
        return m_localValue;
    }

    public void setLocalValue(NetworkTablesValue newValue) {
        this.m_localValue = newValue;
    }

    void callListenersOfEventType(NetworkTablesEvent eventType, NetworkTablesEntry entry, NetworkTablesValue value) {
        for (NetworkTablesEventListener e : m_listeners.get(eventType)) {
            e.apply(entry, value);
        }
    }
}
