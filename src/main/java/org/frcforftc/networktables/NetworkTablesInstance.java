package org.frcforftc.networktables;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton class representing the NetworkTables instance.
 * Provides methods to start and stop the {@link NT4Client} and {@link NT4Server}.
 */
public class NetworkTablesInstance {

    /**
     * Singleton instance of {@link NetworkTablesInstance}.
     */
    private static final NetworkTablesInstance m_instance = new NetworkTablesInstance();

    /**
     * A map to store NetworkTables entries.
     */
    private final Map<String, NetworkTablesEntry> m_entries = new ConcurrentHashMap<>();

    /**
     * NT4Client instance.
     */
    private NT4Client m_client = null;

    /**
     * NT4Server instance.
     */
    private NT4Server m_server = null;

    /**
     * Returns the default singleton instance of {@link NetworkTablesInstance}.
     *
     * @return the singleton instance of {@link NetworkTablesInstance}
     */
    public static NetworkTablesInstance getDefaultInstance() {
        return m_instance;
    }

    /**
     * Returns the map of NetworkTables entries.
     *
     * @return the map of NetworkTables entries
     */
    public Map<String, NetworkTablesEntry> getEntries() {
        return m_entries;
    }

    /**
     * Starts the NT4 server.
     * Creates an instance of {@link NT4Server} and starts it.
     */
    public void startNT4Server() {
        this.m_server = NT4Server.createInstance();
        m_server.start();
    }

    /**
     * Starts the NT4 client.
     * Creates an instance of {@link NT4Client} and connects it to the server.
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
     * Puts a value into the specified topic.
     *
     * @param topic the topic name
     * @param value the value to put
     */
    public void put(String topic, Object value) {
        m_server.createTopic(topic, value);
    }

    /**
     * Gets the {@link NetworkTablesEntry} for the specified topic.
     *
     * @param topic the topic name
     * @return the {@link NetworkTablesEntry} for the specified topic
     */
    public NetworkTablesEntry get(String topic) {
        return m_server.getEntries().get(topic);
    }

    /**
     * Stops the NT4 server.
     * Attempts to stop the server and throws a {@link RuntimeException} if interrupted.
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
