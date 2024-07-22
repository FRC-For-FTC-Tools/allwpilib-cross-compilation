package org.frcforftc.networktables;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton class representing the NetworkTables instance.
 * Provides methods to start and stop the NT4 client and server.
 */
public class NetworkTablesInstance {
    // Singleton instance of NetworkTablesInstance
    private static NetworkTablesInstance m_instance = new NetworkTablesInstance();
    private final Map<String, NetworkTablesEntry> m_entries = new ConcurrentHashMap<>();
    private NT4Client m_client = null;
    private NT4Server m_server = null;

    /**
     * Returns the default singleton instance of NetworkTablesInstance.
     *
     * @return the singleton instance of NetworkTablesInstance
     */
    public static NetworkTablesInstance getDefaultInstance() {
        return m_instance;
    }

    public Map<String, NetworkTablesEntry> getEntries() {
        return m_entries;
    }

    /**
     * Starts the NT4 client and server.
     * This method sets the singleton instance to the current instance.
     */
    public void start() {
        startNT4Server();
        startNT4Client();

        m_instance = this;
    }

    /**
     * Starts the NT4 server.
     * Creates an instance of NT4Server and starts it.
     */
    public void startNT4Server() {
        this.m_server = NT4Server.createInstance();
        m_server.start();
    }

    /**
     * Starts the NT4 client.
     * Creates an instance of NT4Client and connects it to the server.
     *
     * @throws RuntimeException if the URI syntax is invalid
     */
    public void startNT4Client() {
        try {
            this.m_client = new NT4Client(new URI("ws://localhost:5810/nt/"));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        m_client.connect();
    }

    /**
     * Stops the NT4 server.
     * Attempts to stop the server and throws a RuntimeException if interrupted.
     */
    public void closeServer() {
        try {
            this.m_server.stop();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Closes the NT4 client connection.
     * Closes the client connection with a code and reason, then closes the client.
     */
    public void closeClient() {
        this.m_client.closeConnection(1000, "Closed client");
        this.m_client.close();
    }
}
