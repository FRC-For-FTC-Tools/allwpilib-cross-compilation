package org.frcforftc.networktables.sendable;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.frcforftc.networktables.NetworkTablesValue;
import org.frcforftc.networktables.NetworkTablesValueType;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class SendableBuilder {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ArrayList<NetworkTablesValue> properties = new ArrayList<>();
    private String m_type;

    public void setSmartDashboardType(String type) {
        this.m_type = type;
    }

    public void addDoubleProperty(String key, Supplier<Double> getter, Consumer<Double> setter) {
        addProperty(key, NetworkTablesValueType.Double, getter, setter);
    }

    public void addDoubleArrayProperty(String key, Supplier<Double[]> getter, Consumer<Double[]> setter) {
        addProperty(key, NetworkTablesValueType.DoubleArray, getter, setter);
    }

    public void addBooleanProperty(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        addProperty(key, NetworkTablesValueType.Boolean, getter, setter);
    }

    public void addBooleanArrayProperty(String key, Supplier<Boolean[]> getter, Consumer<Boolean[]> setter) {
        addProperty(key, NetworkTablesValueType.BooleanArray, getter, setter);
    }

    public void addStringProperty(String key, Supplier<String> getter, Consumer<String> setter) {
        addProperty(key, NetworkTablesValueType.String, getter, setter);
    }

    public void addStringArrayProperty(String key, Supplier<String[]> getter, Consumer<String[]> setter) {
        addProperty(key, NetworkTablesValueType.StringArray, getter, setter);
    }

    public void addRawProperty(String key, Supplier<Byte[]> getter, Consumer<Byte[]> setter) {
        addProperty(key, NetworkTablesValueType.Raw, getter, setter);
    }

    public void addIntProperty(String key, Supplier<Integer> getter, Consumer<Integer> setter) {
        addProperty(key, NetworkTablesValueType.Int, getter, setter);
    }

    public void addFloatProperty(String key, Supplier<Float> getter, Consumer<Float> setter) {
        addProperty(key, NetworkTablesValueType.Float, getter, setter);
    }

    public void addIntArrayProperty(String key, Supplier<Integer[]> getter, Consumer<Integer[]> setter) {
        addProperty(key, NetworkTablesValueType.IntArray, getter, setter);
    }

    public void addFloatArrayProperty(String key, Supplier<Float[]> getter, Consumer<Float[]> setter) {
        addProperty(key, NetworkTablesValueType.FloatArray, getter, setter);
    }

    private <T> void addProperty(String key, NetworkTablesValueType type, Supplier<T> getter, Consumer<T> setter) {
        NetworkTablesValue value = new NetworkTablesValue(getter, type);
        properties.add(value);
    }

    public abstract void publishDoubleProperty(String key, Supplier<Double> getter, Consumer<Double> setter);

    public abstract void publishDoubleArrayProperty(String key, Supplier<Double[]> getter, Consumer<Double[]> setter);

    public abstract void publishBooleanProperty(String key, Supplier<Boolean> getter, Consumer<Boolean> setter);

    public abstract void publishBooleanArrayProperty(String key, Supplier<Boolean[]> getter, Consumer<Boolean[]> setter);

    public abstract void publishStringProperty(String key, Supplier<String> getter, Consumer<String> setter);

    public abstract void publishStringArrayProperty(String key, Supplier<String[]> getter, Consumer<String[]> setter);

    public abstract void publishRawProperty(String key, Supplier<Byte[]> getter, Consumer<Byte[]> setter);

    public abstract void publishIntProperty(String key, Supplier<Integer> getter, Consumer<Integer> setter);

    public abstract void publishFloatProperty(String key, Supplier<Float> getter, Consumer<Float> setter);

    public abstract void publishIntArrayProperty(String key, Supplier<Integer[]> getter, Consumer<Integer[]> setter);

    public abstract void publishFloatArrayProperty(String key, Supplier<Float[]> getter, Consumer<Float[]> setter);
}
