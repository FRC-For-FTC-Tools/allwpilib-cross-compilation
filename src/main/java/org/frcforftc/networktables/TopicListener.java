package org.frcforftc.networktables;

@FunctionalInterface
public interface TopicListener {
    void apply(NetworkTablesEntry entry, NetworkTablesValue value);
}
