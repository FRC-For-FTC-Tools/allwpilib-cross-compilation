package org.frcforftc.networktables;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.protocols.IProtocol;
import org.java_websocket.protocols.Protocol;
import org.java_websocket.server.WebSocketServer;
import org.msgpack.core.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * NT4Server is a WebSocket server that handles NetworkTables communication
 * using the NT4 protocol. It supports client connections, message processing,
 * and broadcasting updates to all connected clients.
 */
public class NT4Server extends WebSocketServer {
    /**
     * Map of publisher unique IDs to NetworkTablesEntry objects
     */
    static final Map<Long, NetworkTablesEntry> m_publisherUIDSMap = new ConcurrentHashMap<>();

    /**
     * Map of topic names to NetworkTablesEntry objects
     */
    static final Map<String, NetworkTablesEntry> m_entries = new ConcurrentHashMap<>();

    /**
     * Singleton instance of NT4Server
     */
    private static NT4Server m_server = null;
    /**
     * Indicates if the shutdown hook has been added
     */
    private static boolean m_shutdownHookAdded = false;
    /**
     * Set of connected WebSocket clients
     */
    private final Set<WebSocket> m_connections = new CopyOnWriteArraySet<>();
    /**
     * Map of topics to client WebSocket subscriptions
     */
    private final Map<String, Set<WebSocket>> m_clientSubscriptions = new ConcurrentHashMap<>();
    /**
     * Jackson ObjectMapper for JSON processing
     */
    private final ObjectMapper m_objectMapper = new ObjectMapper();

    /**
     * Constructs an NT4Server instance with the specified address and protocol.
     *
     * @param address         the address to bind the server to
     * @param draft_protocols the WebSocket draft protocols to use
     */
    public NT4Server(InetSocketAddress address, Draft_6455 draft_protocols) {

        super(address, Collections.singletonList(draft_protocols));
    }

    /**
     * Creates and starts an instance of NT4Server.
     *
     * @return the created NT4Server instance
     */
    public static NT4Server createInstance() {
        ArrayList<IProtocol> protocols = new ArrayList<IProtocol>();
        protocols.add(new Protocol("v4.1.networktables.first.wpi.edu"));
        protocols.add(new Protocol("rtt.networktables.first.wpi.edu"));
        Draft_6455 draft_protocols = new Draft_6455(Collections.emptyList(), protocols);
        m_server = new NT4Server(new InetSocketAddress("localhost", 5810), draft_protocols);

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
        String subprotocol = handshake.getFieldValue("Sec-WebSocket-Protocol");
        System.out.println("CLIENT CONNECTED with " + subprotocol);

        for (String s : subprotocol.split(", ")) {
            if (s.equals("v4.1.networktables.first.wpi.edu")) {
                conn.setAttachment(s);
                conn.send("Using protocol: " + s);
                for (String key : m_entries.keySet()) {
                    NetworkTablesEntry entry = m_entries.get(key);

                    createTopic(entry.getTopic(), entry.getValue().get());
                }
            }
            if (s.equals("rtt.networktables.first.wpi.edu")) {
                conn.setAttachment(s);
                conn.send("Using protocol: " + s);
                try {
                    heartbeat(conn, System.currentTimeMillis());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        for (NetworkTablesEntry entry : m_entries.values()) {
            entry.callListenersOfEventType(NetworkTablesEvent.kConnected, entry, entry.getValue());
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
//            System.out.println("ID: "+decodedMessage.id + " ATTACH: " +conn.getAttachment() );
            if (decodedMessage.id == -1 && conn.getAttachment().equals("rtt.networktables.first.wpi.edu")) {
                heartbeat(conn, (Long) decodedMessage.dataValue);
            } else {
                if (m_publisherUIDSMap.containsKey(decodedMessage.id)) {
                    NetworkTablesEntry entry = m_publisherUIDSMap.get(decodedMessage.id);
                    entry.update(new NetworkTablesValue(decodedMessage.dataValue, NetworkTablesValueType.getFromId(decodedMessage.dataType)));
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

    /**
     * Encodes a NetworkTables message into a ByteBuffer.
     *
     * @param timestamp the message timestamp
     * @param topicId   the topic ID
     * @param pubUID    the publisher unique ID
     * @param dataType  the data type
     * @param dataValue the data value
     * @return the encoded ByteBuffer
     * @throws IOException if encoding fails
     */
    public ByteBuffer encodeNT4Message(long timestamp, long topicId, long pubUID, int dataType, Object dataValue) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessagePacker packer = MessagePack.newDefaultPacker(out);

        packer.packArrayHeader(4); // The message consists of five components
        packer.packLong(topicId);
        packer.packLong(timestamp);
        packer.packInt(dataType);

        NetworkTablesValueType dataTypeAsEnum = NetworkTablesValueType.getFromId(dataType);

        switch (dataTypeAsEnum) {
            case Boolean: // boolean
                packer.packBoolean((Boolean) dataValue);
                break;
            case Double: // double
                packer.packDouble((Double) dataValue);
                break;
            case Int: // int
                packer.packLong(((Number) dataValue).longValue());
                break;
            case Float: // float
                packer.packFloat((Float) dataValue);
                break;
            case String: // string
                packer.packString((String) dataValue);
                break;
            case Raw: // binary
                byte[] binaryData = (byte[]) dataValue;
                packer.packBinaryHeader(binaryData.length);
                packer.writePayload(binaryData);
                break;
            case BooleanArray: // boolean array
                boolean[] boolArray = (boolean[]) dataValue;
                packer.packArrayHeader(boolArray.length);
                for (boolean b : boolArray) {
                    packer.packBoolean(b);
                }
                break;
            case DoubleArray: // double array
                double[] doubleArray = (double[]) dataValue;
                packer.packArrayHeader(doubleArray.length);
                for (double d : doubleArray) {
                    packer.packDouble(d);
                }
                break;
            case IntArray: // int array
                int[] intArray = (int[]) dataValue;
                packer.packArrayHeader(intArray.length);
                for (int i : intArray) {
                    packer.packInt(i);
                }
                break;
            case FloatArray: // float array
                float[] floatArray = (float[]) dataValue;
                packer.packArrayHeader(floatArray.length);
                for (float f : floatArray) {
                    packer.packFloat(f);
                }
                break;
            case StringArray: // string array
                String[] stringArray = (String[]) dataValue;
                packer.packArrayHeader(stringArray.length);
                for (String s : stringArray) {
                    packer.packString(s);
                }
                break;
            default:
                break;
        }

        packer.close();
        return ByteBuffer.wrap(out.toByteArray());
    }

    /**
     * Decodes a NetworkTables message from a ByteBuffer.
     *
     * @param buffer the ByteBuffer to decode
     * @return the decoded NetworkTablesMessage
     * @throws IOException if decoding fails
     */
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

                NetworkTablesValueType dataTypeAsEnum = NetworkTablesValueType.getFromId(dataType);
//                System.out.println("Data Type: " + dataType);

                // Read the data value based on type
                Object dataValue = null;
                try {
                    switch (dataTypeAsEnum) {
                        case Boolean: // boolean
                            dataValue = unpacker.unpackBoolean();
                            break;
                        case Double: // double
                            dataValue = unpacker.unpackDouble();
                            break;
                        case Int: // int
                            dataValue = unpacker.unpackLong();
                            break;
                        case Float: // float
                            dataValue = unpacker.unpackFloat();
                            break;
                        case String: // string
                            dataValue = unpacker.unpackString();
                            break;
                        case BooleanArray: // boolean array
                            int boolArraySize = unpacker.unpackArrayHeader();
                            boolean[] boolArray = new boolean[boolArraySize];
                            for (int i = 0; i < boolArraySize; i++) {
                                boolArray[i] = unpacker.unpackBoolean();
                            }
                            dataValue = boolArray;
                            break;
                        case DoubleArray: // double array
                            int doubleArraySize = unpacker.unpackArrayHeader();
                            double[] doubleArray = new double[doubleArraySize];
                            for (int i = 0; i < doubleArraySize; i++) {
                                doubleArray[i] = unpacker.unpackDouble();
                            }
                            dataValue = doubleArray;
                            break;
                        case IntArray: // int array
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
                        case FloatArray: // float array
                            int floatArraySize = unpacker.unpackArrayHeader();
                            float[] floatArray = new float[floatArraySize];
                            for (int i = 0; i < floatArraySize; i++) {
                                floatArray[i] = unpacker.unpackFloat();
                            }
                            dataValue = floatArray;
                            break;
                        case StringArray: // string array
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
                    if (dataValue != entry.getValue().get()) {
                        NetworkTablesValue newValue = new NetworkTablesValue(dataValue, NetworkTablesValueType.determineType(dataValue));
                        entry.update(newValue);
                        m_publisherUIDSMap.replace(topicID, entry);
                        entry.callListenersOfEventType(NetworkTablesEvent.kTopicUpdated, entry, newValue);
                    }
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

    /**
     * Processes a JSON message and updates the relevant NetworkTables entries.
     *
     * @param conn the WebSocket connection
     * @param data the JSON data
     */
    private void processMessage(WebSocket conn, JsonNode data) throws IOException {
        if (data.get("method") == null) return;
        String type = data.get("method").asText();
        if ("subscribe".equals(type)) {
            handleSubscribe(conn, data);
        } else if ("publish".equals(type)) {
            handlePublish(data);
        } else if ("unannounce".equals(type)) {
            handleUnAnnounce(data);
        } else if ("announce".equals(type)) {
            handleAnnounce(data);
        }
    }

    private void handleAnnounce(JsonNode data) {
        JsonNode params = data.get("params");
        NetworkTablesEntry entry = m_entries.get(params.get("name").asText());
        if (entry == null) return;

        entry.callListenersOfEventType(NetworkTablesEvent.kTopicAnnounced, entry, entry.getValue());
    }

    private void handleUnAnnounce(JsonNode data) {
        JsonNode params = data.get("params");
        NetworkTablesEntry entry = m_entries.get(params.get("name").asText());
        if (entry == null) return;

        entry.callListenersOfEventType(NetworkTablesEvent.kTopicUnAnnounced, entry, entry.getValue());
    }

    private void handleSubscribe(WebSocket conn, JsonNode data) throws IOException {
        String topic = data.get("params").get("topics").get(0).asText().substring(1); // Removes the root "/" from the topic path
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
            NetworkTablesEntry entry = m_entries.get(topic);
            m_entries.get(topic).id = pubUID;
            m_publisherUIDSMap.put((long) pubUID, m_entries.get(topic));

            m_entries.get(topic).callListenersOfEventType(NetworkTablesEvent.kTopicPublished, entry, entry.getValue());
        }
    }

    /**
     * Sends a heartbeat message to a client.
     *
     * @param conn       the WebSocket connection
     * @param clientTime the current timestamp
     * @throws IOException if sending fails
     */
    private void heartbeat(WebSocket conn, long clientTime) throws IOException {
        ObjectNode message = m_objectMapper.createObjectNode();
        message.put("method", "announce");

        // Create params object
        ObjectNode params = m_objectMapper.createObjectNode();
        params.put("name", "/stamp");
        String typeString = NetworkTablesValueType.determineType((int) clientTime).typeString;

        //  NetworkTablesEntry entry = new NetworkTablesEntry("stamp", message, new NetworkTablesValue(0, typeString));

        int id = -1;

        params.put("id", id); // Set a unique topic ID

        params.put("value", clientTime);
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
        conn.send(encodeNT4Message(System.currentTimeMillis(), id, 0, 2, clientTime));
    }

    /**
     * Creates a NetworkTables topic and broadcasts its creation to all clients.
     *
     * @param topic the topic name
     * @param value the initial value of the topic
     */
    public void createTopic(String topic, Object value) {
        // Create the message object
        ObjectNode message = m_objectMapper.createObjectNode();
        message.put("method", "announce");

        // Create params object
        ObjectNode params = m_objectMapper.createObjectNode();
        params.put("name", "/" + topic);
        String typeString = NetworkTablesValueType.determineType(value).typeString;

        NetworkTablesEntry entry = new NetworkTablesEntry(topic, new NetworkTablesValue(value, typeString));

        int id;
        if (m_entries.containsKey(topic)) {
            id = m_entries.get(topic).id;
            entry = m_entries.get(topic);

            if (value != entry.getValue().get()) {
//                System.out.println("Value updated from: " + entry.getValue().get().toString() + " to: " + value.toString());
                if (NetworkTablesValueType.determineType(value) != NetworkTablesValueType.Unknown) // Prevents issue that is caused when client gets disconnected while server is running
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
