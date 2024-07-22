package org.frcforftc.networktables;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.WebSocket;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.handshake.ClientHandshake;

public class NT4Server extends WebSocketServer {
    private final Set<WebSocket> connections = new CopyOnWriteArraySet<>();
    private final Map<String, ObjectNode> entries = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocket>> clientSubscriptions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NT4Server(InetSocketAddress address) {
        super(address);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connections.add(conn);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connections.remove(conn);
        for (Set<WebSocket> subscribers : clientSubscriptions.values()) {
            subscribers.remove(conn);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            System.out.println("Raw message received: " + message);
            JsonNode data = objectMapper.readTree(message);
            processMessage(conn, data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        try {
            System.out.println("Raw message received (binary): " + Arrays.toString(message.array()));
            try {
                processMessage(conn, objectMapper.readTree(message.array()));
            } catch (IOException e) {

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String determineType(Object value) {
        if (value instanceof Integer) {
            return "int";
        } else if (value instanceof Double) {
            return "double";
        } else if (value instanceof Float) {
            return "float";
        } else if (value instanceof String) {
            return "string";
        } else if (value instanceof Boolean) {
            return "boolean";
        } else if (value instanceof Byte[]) {
            return "byte[]";
        } else {
            return "unknown";
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Server started successfully!");
    }


    private void processMessage(WebSocket conn, JsonNode data) {
        try {
            // TODO: add ability post topics from outside of the class
            announceTopic("runtime", System.currentTimeMillis());
            if (data == null) return;
            JsonNode typeNode = data.get("type");
            if (typeNode == null) return;
            String type = typeNode.asText();

            switch (type) {
                case "create_entry":
                    createEntry(conn, data);
                    break;
                case "update_entry":
                    updateEntry(conn, data);
                    break;
                case "delete_entry":
                    deleteEntry(conn, data);
                    break;
                case "subscribe":
                    subscribe(conn, data);
                    break;
                default:
                    System.out.println("Unknown message type: " + type);
            }

            Thread.sleep(1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createEntry(WebSocket conn, JsonNode data) {
        try {
            String entryId = UUID.randomUUID().toString();
            ObjectNode entry = objectMapper.createObjectNode();
            entry.put("id", entryId);
            entry.put("key", data.get("key").asText());
            entry.put("type", data.get("data_type").asText());
            entry.put("value", data.get("value").asText());
            entries.put(entryId, entry);
            broadcastMessage(createMessage("entry_created", entry));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateEntry(WebSocket conn, JsonNode data) {
        try {
            String entryId = data.get("id").asText();
            if (entries.containsKey(entryId)) {
                entries.get(entryId).put("value", data.get("value").asText());
                broadcastMessage(createMessage("entry_updated", entries.get(entryId)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteEntry(WebSocket conn, JsonNode data) {
        try {
            String entryId = data.get("id").asText();
            if (entries.containsKey(entryId)) {
                entries.remove(entryId);
                ObjectNode message = objectMapper.createObjectNode();
                message.put("type", "entry_deleted");
                message.put("id", entryId);
                broadcastMessage(message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void subscribe(WebSocket conn, JsonNode data) {
        try {
            String key = data.get("key").asText();
            clientSubscriptions.computeIfAbsent(key, k -> new CopyOnWriteArraySet<>()).add(conn);
            for (ObjectNode entry : entries.values()) {
                if (entry.get("key").asText().equals(key)) {
                    conn.send(objectMapper.writeValueAsString(createMessage("entry_updated", entry)));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ObjectNode createMessage(String type, ObjectNode data) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", type);
        message.setAll(data);
        return message;
    }

    private void broadcastMessage(ObjectNode message) {
        connections.forEach(conn -> {
            try {
                conn.send(objectMapper.writeValueAsString(message));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void broadcastMessage(ArrayNode messages) {
        connections.forEach(conn -> {
            try {
                conn.send(objectMapper.writeValueAsString(messages));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    static byte[] serialize(final Object obj) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(obj);
            out.flush();
            return bos.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void announceTopic(String topic, Object value) {
        // Create the message object
        ObjectNode message = objectMapper.createObjectNode();
        message.put("method", "announce");

        // Create params object
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "/" + topic);
        params.put("id", entries.size() + 1); // Set a unique topic ID

        if (value instanceof Integer) {
            params.put("value", (int) value); // Value to publish
            params.put("type", "int");
        } else if (value instanceof Double) {
            params.put("value", (double) value);
        } else if (value instanceof Float) {
            params.put("value", (float) value);
        } else if (value instanceof String) {
            params.put("value", (String) value);
        } else if (value instanceof Boolean) {
            params.put("value", (boolean) value);
        } else if (value instanceof Byte[]) {
            params.put("value", serialize(value));
        }

        params.put("type", determineType(value));
        params.put("pubuid", 1); // Use the publisher ID

        ObjectNode properties = objectMapper.createObjectNode();
        // Add any properties here if needed
        params.set("properties", properties);

        // Attach params to the message
        message.set("params", params);

        // Create an array of messages if needed
        ArrayNode messagesArray = objectMapper.createArrayNode();
        messagesArray.add(message);
        entries.put(topic, message);
        // Broadcast the message to all connected clients
        broadcastMessage(messagesArray);
    }

    private static NT4Server m_server = null;
    private static boolean m_shutdownHookAdded = false;

    public static NT4Server createInstance() {
        m_server = new NT4Server(new InetSocketAddress("localhost", 5810));

        if (m_shutdownHookAdded) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    System.out.println("Shutting down server...");
                    m_server.stop(0); // Gracefully stop the server
                } catch (InterruptedException e) {
                    System.err.println("Server shutdown interrupted");
                    Thread.currentThread().interrupt(); // Restore interrupted status
                }
            }));
            m_shutdownHookAdded = true;
        }


        return m_server;
    }

}
