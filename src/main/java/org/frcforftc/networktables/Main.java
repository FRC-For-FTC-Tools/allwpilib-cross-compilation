package org.frcforftc.networktables;

public class Main {
    public static void main(String[] args) {
        NetworkTablesInstance instance = NetworkTablesInstance.getDefaultInstance();
        instance.start();
    }
}
