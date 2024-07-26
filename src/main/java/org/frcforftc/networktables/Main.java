package org.frcforftc.networktables;

public class Main {
    public static void main(String[] args) {
        NetworkTablesInstance inst = NetworkTablesInstance.getDefaultInstance();
        inst.startNT4Server();
        inst.putNumberArray("test1", new double[]{1.0, 2.0, 3.0});
        inst.putStringArray("test2", new String[]{"t", "b", "c"});
    }
}
