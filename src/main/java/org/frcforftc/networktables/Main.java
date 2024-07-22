package org.frcforftc.networktables;

import java.awt.event.ActionListener;
import java.net.URI;
import java.net.URISyntaxException;

public class Main {
    public static void main(String[] args) {
        NetworkTablesInstance instance = NetworkTablesInstance.getDefaultInstance();
        instance.startServer();
//
        try {
            NT4Client client = new NT4Client(new URI("ws://localhost:5810/nt/"));
            client.connect();
            System.out.println("Connected client");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down server...");
                client.closeConnection(1000, "closed");
                client.close();
            }));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }


    }
}
