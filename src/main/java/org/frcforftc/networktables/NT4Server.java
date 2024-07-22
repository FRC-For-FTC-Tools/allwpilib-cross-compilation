package org.frcforftc.networktables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.msgpack.core.*;
import org.java_websocket.WebSocket;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.handshake.ClientHandshake;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class NT4Server extends WebSocketServer {
    private static NT4Server m_server = null;
    private static boolean m_shutdownHookAdded = false;
    private final Set<WebSocket> connections = new CopyOnWriteArraySet<>();
    private final Map<String, ObjectNode> entries = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocket>> clientSubscriptions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NT4Server(InetSocketAddress address) {
        super(address);
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

    public static NT4Server createInstance() {
        m_server = new NT4Server(new InetSocketAddress("localhost", 5810));

        if (m_shutdownHookAdded) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    System.out.println("Shutting down server...");
                    m_server.stop(0);
                } catch (InterruptedException e) {
                    System.err.println("Server shutdown interrupted");
                    Thread.currentThread().interrupt();
                }
            }));
            m_shutdownHookAdded = true;
        }

        return m_server;
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
            announceTopic("test", 12);

            System.out.println("Raw message received (binary): " + Arrays.toString(message.array()));
            decodeNT4Message(message);
        } catch (Exception e) {
            e.printStackTrace();
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

    public void decodeNT4Message(ByteBuffer buffer) throws IOException {
        //TODO: i think the order its reading is wrong, should check that (value replaced by timestamp, etc)
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(buffer)) {
            while (unpacker.hasNext()) {
                // Read the array header
                int arraySize = unpacker.unpackArrayHeader();
                if (arraySize != 4) {
                    throw new IOException("Invalid array size for NT4 message: " + arraySize);
                }

                // Read the topic/publisher ID
                long topicId = unpacker.unpackLong();
                System.out.println("Topic/Publisher ID: " + topicId);

                // Read the timestamp
                long pubUID = unpacker.unpackLong();
                System.out.println("pubUID: " + pubUID);

                // Read the data type
                int dataType = unpacker.unpackInt();
                System.out.println("Data Type: " + dataType);

                // Read the data value based on type
                Object dataValue = null;
                try {
                    switch (dataType) {
                        case 0: // boolean
                            dataValue = unpacker.unpackBoolean();
                            break;
                        case 1: // double
                            dataValue = unpacker.unpackDouble();
                            break;
                        case 2: // int
                            System.out.println("INT");
                            dataValue = unpacker.unpackLong();

                            break;
                        case 3: // float
                            dataValue = unpacker.unpackFloat();
                            break;
                        case 4: // string
                            dataValue = unpacker.unpackString();
                            break;
                        case 5: // binary
                            int length = unpacker.unpackBinaryHeader();
//                            byte[] byteArray = unpacker.readByteArray(length);
//                            dataValue = byteArray;
                            break;
                        case 16: // boolean array
                            int boolArraySize = unpacker.unpackArrayHeader();
                            boolean[] boolArray = new boolean[boolArraySize];
                            for (int i = 0; i < boolArraySize; i++) {
                                boolArray[i] = unpacker.unpackBoolean();
                            }
                            dataValue = boolArray;
                            break;
                        case 17: // double array
                            int doubleArraySize = unpacker.unpackArrayHeader();
                            double[] doubleArray = new double[doubleArraySize];
                            for (int i = 0; i < doubleArraySize; i++) {
                                doubleArray[i] = unpacker.unpackDouble();
                            }
                            dataValue = doubleArray;
                            break;
                        case 18: // int array
                            int intArraySize = unpacker.unpackArrayHeader();
                            int[] intArray = new int[intArraySize];
                            for (int i = 0; i < intArraySize; i++) {
                                try {
                                    intArray[i] = unpacker.unpackInt();
                                } catch (MessageIntegerOverflowException e) {
                                    intArray[i] = (int) unpacker.unpackLong();
                                }
                            }
                            dataValue = intArray;
                            break;
                        case 19: // float array
                            int floatArraySize = unpacker.unpackArrayHeader();
                            float[] floatArray = new float[floatArraySize];
                            for (int i = 0; i < floatArraySize; i++) {
                                floatArray[i] = unpacker.unpackFloat();
                            }
                            dataValue = floatArray;
                            break;
                        case 20: // string array
                            int stringArraySize = unpacker.unpackArrayHeader();
                            String[] stringArray = new String[stringArraySize];
                            for (int i = 0; i < stringArraySize; i++) {
                                stringArray[i] = unpacker.unpackString();
                            }
                            dataValue = stringArray;
                            break;
                        default:
                            throw new IOException("Unknown data type: " + dataType);
                    }
                } catch (MessageInsufficientBufferException | IOException e) {
                    System.err.println("Error decoding data value: " + e.getMessage());
                    e.printStackTrace();
                }

                // Process the decoded message
                processMessage(topicId, pubUID, dataType, dataValue);
            }
        } catch (MessageInsufficientBufferException | IOException e) {
            System.err.println("Error decoding NT4 message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processMessage(long topicId, long timestamp, int dataType, Object dataValue) {
        // Implement message processing logic here
        System.out.println("Data Value: " + dataValue);
    }

    private void processMessage(WebSocket conn, JsonNode data) {
        if (data.get("type") == null) return;
        String type = data.get("type").asText();
        if ("subscribe".equals(type)) {
            handleSubscribe(conn, data);
        } else if ("publish".equals(type)) {
            handlePublish(data);
        }
    }

    private void handleSubscribe(WebSocket conn, JsonNode data) {
        String topic = data.get("topic").asText();
        clientSubscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>()).add(conn);
    }

    private void handlePublish(JsonNode data) {
        String topic = data.get("topic").asText();
        JsonNode valueNode = data.get("value");
        Object value = null;

        switch (valueNode.getNodeType()) {
            case NUMBER:
                if (valueNode.isDouble()) {
                    value = valueNode.asDouble();
                } else if (valueNode.isInt()) {
                    value = valueNode.asInt();
                } else if (valueNode.isFloat()) {
                    value = valueNode.asDouble();
                }
                break;
            case BOOLEAN:
                value = valueNode.asBoolean();
                break;
            case STRING:
                value = valueNode.asText();
                break;
        }

        announceTopic(topic, value);
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

    private void broadcastMessage(ArrayNode messages) {
        connections.forEach(conn -> {
            try {
                conn.send(objectMapper.writeValueAsString(messages));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
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

}

