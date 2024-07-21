package org.frcforftc.networktables;

public class NetworkTablesInstance {
    private static NetworkTablesInstance m_instance;
    private NT4Server m_server = null;

    public static NetworkTablesInstance create() {
        NetworkTablesInstance instance = new NetworkTablesInstance();
        instance.m_server = NT4Server.createInstance();

        return instance;
    }

    public void start() {
        if (m_server == null) {
            m_server = NT4Server.createInstance();
        }

        Runnable serverTask = () -> {
            m_server.start();
            System.out.println("Server started...");
        };

        // Start the server task in a new thread
        Thread serverThread = new Thread(serverTask);
        serverThread.start();

        m_instance = this;
    }

    public NetworkTablesInstance getInstance() {
        return m_instance;
    }
}
