package org.frcforftc.networktables;

public class Main {
    public static void main(String[] args) {
        NetworkTablesInstance inst = NetworkTablesInstance.getDefaultInstance();
        inst.startNT4Server();
        inst.put("test1", 1);
//        inst.put("test2", 1);
    }
}
