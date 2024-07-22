package org.frcforftc.networktables;

public class NetworkTablesMessage {
    public long id;
    public long stamp;
    public int dataType;

    public Object dataValue;

    public NetworkTablesMessage(long id, long stamp, int dataType, Object dataValue) {
        this.id = id;
        this.stamp = stamp;
        this.dataType = dataType;
        this.dataValue = dataValue;
    }
}
