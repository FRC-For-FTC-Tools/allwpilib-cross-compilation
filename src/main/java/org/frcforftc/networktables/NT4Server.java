package org.frcforftc.networktables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.msgpack.core.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class NT4Server extends WebSocketServer {
    static final Map<Integer, NetworkTablesEntry> m_publisherUIDSMap = new HashMap<>();

    static final Map<String, NetworkTablesEntry> m_entries = new HashMap<>();
    private static NT4Server m_server = null;
    private static boolean m_shutdownHookAdded = false;
    private final Set<WebSocket> m_connections = new CopyOnWriteArraySet<>();
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
        System.out.println("CLIENT CONNECTED");
        //Test topic:
//        createTopic("test", 12.1);
        for (String key : m_entries.keySet()) {
            NetworkTablesEntry entry = m_entries.get(key);

            createTopic(entry.getTopic(), entry.getValue().get());
        }
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
//            System.out.println("Json message received: " + message);
            JsonNode data = m_objectMapper.readTree(message).get(0);
            processMessage(conn, data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        try {

            NetworkTablesMessage decodedMessage = decodeNT4Message(message);
            if (decodedMessage.id == -1) {
//                heartbeat(conn, (Long) decodedMessage.dataValue);
            } else {
                if (m_publisherUIDSMap.containsKey(decodedMessage.id)) {
                    NetworkTablesEntry entry = m_publisherUIDSMap.get(decodedMessage.id);
                    entry.setValue(new NetworkTablesValue(decodedMessage.dataValue, NetworkTablesValueType.getFromId(decodedMessage.dataType)));
                    for (Set<WebSocket> subscribers : m_clientSubscriptions.values()) {
                        broadcast(encodeNT4Message(System.currentTimeMillis(), entry.id, decodedMessage.id, decodedMessage.dataType, decodedMessage.dataValue), subscribers);
                    }
                }
            }
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
//                throw new IOException("Unknown data type: " + dataType);
                break;
        }

        packer.close();
        return ByteBuffer.wrap(out.toByteArray());
    }


    public NetworkTablesMessage decodeNT4Message(ByteBuffer buffer) throws IOException {
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(buffer)) {
            while (unpacker.hasNext()) {
                // Read the array header
                int arraySize = unpacker.unpackArrayHeader();
                if (arraySize != 4) {
                    throw new IOException("Invalid array size for NT4 message: " + arraySize);
                }

                // Read the topic/publisher ID
                long topicID = unpacker.unpackLong();
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

                if (m_publisherUIDSMap.containsKey((int) topicID)) {
                    NetworkTablesEntry entry = m_publisherUIDSMap.get((int) topicID);
                    entry.setValue(new NetworkTablesValue(dataValue, NetworkTablesValueType.determineType(dataValue)));
                    m_publisherUIDSMap.replace((int) topicID, entry);
                }
                // Process the decoded message
                return new NetworkTablesMessage(topicID, stamp, dataType, dataValue);
            }
        } catch (MessageInsufficientBufferException | IOException e) {
            System.err.println("Error decoding NT4 message: " + e.getMessage());
            e.printStackTrace();
        }

        return new NetworkTablesMessage(0, 0, 0, 0);
    }

    private void processMessage(WebSocket conn, JsonNode data) throws IOException {
        if (data.get("method") == null) return;
        String type = data.get("method").asText();
        if ("subscribe".equals(type)) {
            handleSubscribe(conn, data);
        } else if ("publish".equals(type)) {
            handlePublish(data);
        } else if ("unannounce".equals(type)) {
            handleUnAnnounce(data);
        }
    }

    private void handleUnAnnounce(JsonNode data) {
        //TODO
    }

    private void handleSubscribe(WebSocket conn, JsonNode data) throws IOException {
        String topic = data.get("params").get("topics").get(0).asText().replaceAll("/", "");
        if (m_entries.containsKey(topic)) {
            System.out.println("SUBSCRIBED: " + topic);
            m_clientSubscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>()).add(conn);
            conn.send(encodeNT4Message(System.currentTimeMillis(), m_entries.get(topic).id, 0, NetworkTablesValueType.getFromString(m_entries.get(topic).getValue().getType().typeString).id, m_entries.get(topic).getValue().getAs()));
        } else {
            System.out.println("FAILED TO SUBSCRIBE TO " + topic + " AVAILABLE TOPICS ARE:");
            System.out.println(m_entries.keySet());
        }
    }


    private void handlePublish(JsonNode data) {
        JsonNode params = data.get("params");
        String topic = params.get("name").asText().substring(1);
        if (m_entries.containsKey(topic)) {
            int pubUID = params.get("pubuid").asInt();
            m_entries.get(topic).id = pubUID;
            m_publisherUIDSMap.put(pubUID, m_entries.get(topic));
        }
    }

    private void heartbeat(WebSocket conn, long client_time) throws IOException {
        ObjectNode message = m_objectMapper.createObjectNode();
        message.put("method", "announce");

        // Create params object
        ObjectNode params = m_objectMapper.createObjectNode();
        params.put("name", "/stamp");
        String typeString = NetworkTablesValueType.determineType((int) client_time).typeString;

        //  NetworkTablesEntry entry = new NetworkTablesEntry("stamp", message, new NetworkTablesValue(0, typeString));

        int id = -1;

        params.put("id", id); // Set a unique topic ID

        params.put("value", client_time);
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
        conn.send(encodeNT4Message(System.currentTimeMillis(), id, 0, 2, client_time));
    }

    public void createTopic(String topic, Object value) {
        // Create the message object
        ObjectNode message = m_objectMapper.createObjectNode();
        message.put("method", "announce");

        // Create params object
        ObjectNode params = m_objectMapper.createObjectNode();
        params.put("name", "/" + topic);
        String typeString = NetworkTablesValueType.determineType(value).typeString;

        NetworkTablesEntry entry = new NetworkTablesEntry(topic, message, new NetworkTablesValue(value, typeString));

        int id;
        if (m_entries.containsKey(topic)) {
            id = m_entries.get(topic).id;
            entry = m_entries.get(topic);

            if (value != entry.getValue().get()) {
//                System.out.println("Value updated from: " + entry.getValue().get().toString() + " to: " + value.toString());
                m_entries.get(topic).update(value);
            }

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
    }

    public Map<String, NetworkTablesEntry> getEntries() {
        return m_entries;
    }
}
