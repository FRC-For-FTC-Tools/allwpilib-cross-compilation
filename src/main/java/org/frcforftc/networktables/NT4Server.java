package org.frcforftc.networktables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.core.net.ssl.StoreConfiguration;
import org.apache.logging.log4j.message.Message;
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

class NT4Server extends WebSocketServer {
    static final Map<Integer, NetworkTablesEntry> m_publisherUIDSMap = new HashMap<>();
    private static NT4Server m_server = null;
    private static boolean m_shutdownHookAdded = false;
    private final Set<WebSocket> m_connections = new CopyOnWriteArraySet<>();
    private final Map<String, NetworkTablesEntry> m_entries = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocket>> m_clientSubscriptions = new ConcurrentHashMap<>();
    private final ObjectMapper m_objectMapper = new ObjectMapper();

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
        m_connections.add(conn);
        System.out.println("new connection:" + conn.getResourceDescriptor());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        m_connections.remove(conn);
        for (Set<WebSocket> subscribers : m_clientSubscriptions.values()) {
            subscribers.remove(conn);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JsonNode data = m_objectMapper.readTree(message);
            processMessage(conn, data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        try {
            announceTopic("test", 12.1);
            announceTopic("test2", 1.1);

//            System.out.println("Raw message received (binary): " + Arrays.toString(message.array()));
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

    public ByteBuffer encodeNT4Message(long timestamp, long topicId, long pubUID, int dataType, Object dataValue) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessagePacker packer = MessagePack.newDefaultPacker(out);

        packer.packArrayHeader(4); // The message consists of five components
        packer.packLong(topicId);
        packer.packLong(timestamp);
        packer.packInt(dataType);

        switch (dataType) {
            case 0: // boolean
                packer.packBoolean((Boolean) dataValue);
                break;
            case 1: // double
                packer.packDouble((Double) dataValue);
                break;
            case 2: // int
                packer.packLong((Integer) dataValue);
                break;
            case 3: // float
                packer.packFloat((Float) dataValue);
                break;
            case 4: // string
                packer.packString((String) dataValue);
                break;
            case 5: // binary
                byte[] binaryData = (byte[]) dataValue;
                packer.packBinaryHeader(binaryData.length);
                packer.writePayload(binaryData);
                break;
            case 16: // boolean array
                boolean[] boolArray = (boolean[]) dataValue;
                packer.packArrayHeader(boolArray.length);
                for (boolean b : boolArray) {
                    packer.packBoolean(b);
                }
                break;
            case 17: // double array
                double[] doubleArray = (double[]) dataValue;
                packer.packArrayHeader(doubleArray.length);
                for (double d : doubleArray) {
                    packer.packDouble(d);
                }
                break;
            case 18: // int array
                int[] intArray = (int[]) dataValue;
                packer.packArrayHeader(intArray.length);
                for (int i : intArray) {
                    packer.packInt(i);
                }
                break;
            case 19: // float array
                float[] floatArray = (float[]) dataValue;
                packer.packArrayHeader(floatArray.length);
                for (float f : floatArray) {
                    packer.packFloat(f);
                }
                break;
            case 20: // string array
                String[] stringArray = (String[]) dataValue;
                packer.packArrayHeader(stringArray.length);
                for (String s : stringArray) {
                    packer.packString(s);
                }
                break;
            default:
                throw new IOException("Unknown data type: " + dataType);
        }

        packer.close();
        return ByteBuffer.wrap(out.toByteArray());
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
                long topicID = unpacker.unpackLong();
                if (topicID < 0) return;
//                System.out.println("id: " + topicID);
                // Read the timestamp
                long stamp = unpacker.unpackLong();
//                System.out.println("stamp: " + stamp);

                // Read the data type
                int dataType = unpacker.unpackInt();
//                System.out.println("Data Type: " + dataType);

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
                            dataValue = unpacker.unpackLong();
                            break;
                        case 3: // float
                            dataValue = unpacker.unpackFloat();
                            break;
                        case 4: // string
                            dataValue = unpacker.unpackString();
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
                processMessage(topicID, stamp, dataType, dataValue);
            }
        } catch (MessageInsufficientBufferException | IOException e) {
            System.err.println("Error decoding NT4 message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processMessage(long topicId, long timestamp, int dataType, Object dataValue) {
        // Implement message processing logic here
//        System.out.println("Data Value: " + dataValue);
    }

    private void processMessage(WebSocket conn, JsonNode data) {
        if (data.get("type") == null) return;
        String type = data.get("type").asText();
        if ("subscribe".equals(type)) {
            handleSubscribe(conn, data);
        } else if ("publish".equals(type)) {
            handlePublish(data);
        } else if ("announce".equals(type)) {
            JsonNode params = data.get("params");
//            onTopicAnnounced(params.get("name").asText(), params.get("id").asLong(), params.get("pubuid").asLong());
        }
    }

    private void handleSubscribe(WebSocket conn, JsonNode data) {
        String topic = data.get("topic").asText();
        m_clientSubscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>()).add(conn);
    }

    private void handlePublish(JsonNode data) {
        String topic = data.get("topic").asText();
        JsonNode typeNode = data.get("type");
        Object value = null;
    }

    public void announceTopic(String topic, Object value) {
        topic = "/" + topic;
        // Create the message object
        ObjectNode message = m_objectMapper.createObjectNode();
        message.put("method", "announce");

        // Create params object
        ObjectNode params = m_objectMapper.createObjectNode();
        params.put("name", topic);
        String typeString = NetworkTablesValueType.determineType(value).typeString;

        NetworkTablesEntry entry = new NetworkTablesEntry(topic, message, new NetworkTablesValue(value, typeString));

        int id = 0;
        if (m_entries.containsKey(topic)) {
            entry = m_entries.get(topic);
            id = entry.id;
        } else {
            id = m_entries.size() + 1;
            entry.id = m_entries.size() + 1;
            m_entries.put(topic, entry);
        }
        params.put("id", id); // Set a unique topic ID

        params.put("type", typeString);
        params.put("pubuid", id); // Use the publisher ID

        ObjectNode properties = m_objectMapper.createObjectNode();
        // Add any properties here if needed
        params.set("properties", properties);

        // Attach params to the message
        message.set("params", params);

        // Create an array of messages if needed
        ArrayNode messagesArray = m_objectMapper.createArrayNode();
        messagesArray.add(message);
        // Broadcast the message to all connected clients

        broadcast(messagesArray.toString());
        publishEntry(entry);
        onTopicAnnounced(topic, id, id, NetworkTablesValueType.getFromString(typeString).id, value);
    }

    private void publishEntry(NetworkTablesEntry entry) {
        ObjectNode message = m_objectMapper.createObjectNode();
        message.put("method", "publish");
        ObjectNode params = m_objectMapper.createObjectNode();
        params.put("name", entry.getTopic());
        params.put("pubuid", entry.id);
        params.put("type", entry.getLocalValue().getType().typeString);
        ObjectNode properties = m_objectMapper.createObjectNode();
        // Add any properties here if needed
        params.set("properties", properties);
        message.set("params", params);
        ArrayNode messageArray = m_objectMapper.createArrayNode();
        messageArray.add(message);
        broadcast(messageArray.toString());
    }

    public void onTopicAnnounced(String topic, int topicId, int pubUID, int dataType, Object dataValue) {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        m_publisherUIDSMap.put(pubUID, m_entries.get(topic));
        try {
            decodeNT4Message(encodeNT4Message(System.currentTimeMillis(), topicId, pubUID, dataType, dataValue));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
