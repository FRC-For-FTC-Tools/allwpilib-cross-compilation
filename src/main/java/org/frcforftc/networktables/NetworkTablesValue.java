package org.frcforftc.networktables;

public class NetworkTablesValue {
    private final Object m_value;
    private final NetworkTablesValueType m_type;

    public NetworkTablesValue(Object value, NetworkTablesValueType type) {
        this.m_value = value;
        this.m_type = type;
    }

    public NetworkTablesValue(Object value, String type) {
        this.m_value = value;
        this.m_type = NetworkTablesValueType.getFromString(type);
    }

    public <T> T getAs() {
        return (T) m_value;
    }

    public Object get() {
        return m_value;
    }

    public NetworkTablesValueType getType() {
        return m_type;
    }


}
