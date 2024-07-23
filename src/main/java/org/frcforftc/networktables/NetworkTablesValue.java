package org.frcforftc.networktables;

import java.util.function.Supplier;

public class NetworkTablesValue {
    private final Supplier<?> m_value;
    private final NetworkTablesValueType m_type;

    public NetworkTablesValue(Object value, NetworkTablesValueType type) {
        this(() -> value, type);
    }

    public NetworkTablesValue(Object value, String type) {
        this(() -> value, NetworkTablesValueType.getFromString(type));
    }

    public NetworkTablesValue(Supplier<?> value, String type) {
        this(value, NetworkTablesValueType.getFromString(type));
    }

    public NetworkTablesValue(Supplier<?> getter, NetworkTablesValueType type) {
        this.m_value = getter;
        this.m_type = type;
    }

    public <T> T getAs() {
        return (T) m_value.get();
    }

    public Object get() {
        return m_value.get();
    }

    public Supplier<?> getSupplier() {
        return m_value;
    }

    public NetworkTablesValueType getType() {
        return m_type;
    }
}
