package org.frcforftc.networktables;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Objects;

public enum NetworkTablesValueType {
    Boolean(0, "boolean"),
    Double(1, "double"),
    Int(2, "int"),
    Float(3, "float"),
    String(4, "string"),
    Raw(5, "raw"),
    BooleanArray(16, "boolean[]"),
    DoubleArray(17, "double[]"),
    IntArray(18, "int[]"),
    FloatArray(19, "float[]"),
    StringArray(20, "string[]"),
    Unknown(-1, "unknown");

    public final int id;
    public final String typeString;

    NetworkTablesValueType(int id, String s) {
        this.id = id;
        this.typeString = s;
    }

    static NetworkTablesValueType getFromString(@NonNull String s) {
        for (var val : NetworkTablesValueType.values()) {
            if (Objects.equals(val.typeString, s)) {
                return val;
            }
        }
        return NetworkTablesValueType.Unknown;
    }

    static NetworkTablesValueType getFromId(int id) {
        for (var val : NetworkTablesValueType.values()) {
            if (val.id == id) {
                return val;
            }
        }
        return NetworkTablesValueType.Unknown;
    }

    static NetworkTablesValueType determineType(Object value) {
        if (value instanceof Integer) {
            return Int;
        } else if (value instanceof Double) {
            return Double;
        } else if (value instanceof Float) {
            return Float;
        } else if (value instanceof String) {
            return String;
        } else if (value instanceof Boolean) {
            return Boolean;
        } else if (value instanceof byte[]) { // Raw data is usually represented as a byte array
            return Raw;
        } else if (value instanceof boolean[]) {
            return BooleanArray;
        } else if (value instanceof double[]) {
            return DoubleArray;
        } else if (value instanceof int[]) {
            return IntArray;
        } else if (value instanceof float[]) {
            return FloatArray;
        } else if (value instanceof String[]) {
            return StringArray;
        } else {
            return Unknown;
        }
    }
}
