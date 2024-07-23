package org.frcforftc.networktables;

public class Main {
    public static void main(String[] args) {
        NT4Server server = NT4Server.createInstance();
        server.start();
    }
}
