package org.frcforftc.networktables;

public class NetworkTablesInstance {
    private static NetworkTablesInstance m_instance = new NetworkTablesInstance();
    private NT4Server m_server = null;

    public void startServer() {
        this.m_server = NT4Server.createInstance();

        Runnable serverTask = () -> {
            m_server.start();
            System.out.println("Server started...");
        };

        // Start the server task in a new thread
        Thread serverThread = new Thread(serverTask);
        serverThread.start();

        m_instance = this;
    }

    public void closeServer() {
        try {
            this.m_server.stop();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static NetworkTablesInstance getDefaultInstance() {
        return m_instance;
    }
}
