package org.frcforftc.networktables;

@FunctionalInterface
public interface EventListenerMethod {
    void apply(NetworkTablesEvent event);
}
