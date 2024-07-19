package org.frcforftc;

import networktables.Networktables;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkTablesServer {

    private static final int PORT = 5810;
    private static final Map<String, String> table = new ConcurrentHashMap<>();
    private static final Map<Socket, Thread> connections = new ConcurrentHashMap<>();
    private static final long PRINT_INTERVAL_MS = 1000; // Print connections every second

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server listening on port " + PORT);

            // Start the thread that prints and posts connections every interval
            startConnectionNotifier();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                handleClient(clientSocket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        System.out.println("New client connection: " + clientSocket.getRemoteSocketAddress());

        try {
            clientSocket.setSoTimeout(60000); // Set a timeout of 60 seconds
        } catch (SocketException e) {
            System.err.println("Error setting socket timeout: " + e.getMessage());
            return; // Exit if setting the timeout fails
        }

        Thread clientThread = new Thread(() -> {
            try (InputStream inputStream = clientSocket.getInputStream();
                 OutputStream outputStream = clientSocket.getOutputStream();
                 DataInputStream dataInputStream = new DataInputStream(inputStream);
                 DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {

                connections.put(clientSocket, Thread.currentThread());

                while (!clientSocket.isClosed()) {
                    try {
                        // Read message length
                        int length = dataInputStream.readInt();
                        if (length <= 0) {
                            System.err.println("Received invalid length: " + length);
                            continue;
                        }

                        byte[] messageBytes = new byte[length];
                        int bytesRead = 0;

                        // Read the full message into the buffer
                        while (bytesRead < length) {
                            int result = dataInputStream.read(messageBytes, bytesRead, length - bytesRead);
                            if (result == -1) {
                                // End of stream reached
                                System.out.println("Client disconnected while reading data");
                                break;
                            }
                            bytesRead += result;
                        }

                        // Check if we read the expected number of bytes
                        if (bytesRead < length) {
                            System.err.println("Incomplete data received. Expected " + length + " bytes but got " + bytesRead);
                            continue;
                        }

                        // Parse the message
                        Networktables.NetworkTableMessage message;
                        try {
                            message = Networktables.NetworkTableMessage.parseFrom(messageBytes);
                        } catch (Exception e) {
                            System.err.println("Failed to parse message: " + e.getMessage());
                            continue;
                        }
                        System.out.println("Received message: " + message);

                        // Handle message based on type
                        switch (message.getMessageCase()) {
                            case UPDATE:
                                handleUpdate(message.getUpdate());
                                break;
                            case GET:
                                handleGet(message.getGet(), dataOutputStream);
                                break;
                            default:
                                System.out.println("Unsupported message type.");
                        }
                    } catch (EOFException e) {
                        // Client disconnected
                        System.out.println("Client disconnected: " + e.getMessage());
                        break;
                    } catch (SocketTimeoutException e) {
                        // Handle timeout exceptions
                        System.out.println("Socket timeout: " + e.getMessage());
                        break;
                    } catch (SocketException e) {
                        // Handle socket exceptions
                        System.err.println("SocketException in client thread: " + e.getMessage());
                        break;
                    } catch (IOException e) {
                        // Handle other IOExceptions
                        System.err.println("IOException in client thread: " + e.getMessage());
                        break;
                    }
                }
            } catch (IOException e) {
                // Handle IOExceptions that occur during setup
                System.err.println("IOException while handling client: " + e.getMessage());
            } finally {
                System.out.println("Closing client connection: " + clientSocket.getRemoteSocketAddress());
                // Ensure the socket is closed and removed from connections
                try {
                    if (!clientSocket.isClosed()) {
                        clientSocket.close();
                    }
                } catch (IOException e) {
                    System.err.println("IOException while closing client socket: " + e.getMessage());
                }
                connections.remove(clientSocket);
            }
        });

        clientThread.start();
    }

    private static void handleUpdate(Networktables.NetworkTableMessage.Update update) {
        for (Networktables.NetworkTableMessage.Entry entry : update.getEntriesList()) {
            String key = entry.getKey();
            String value = entry.getValue();
            table.put(key, value);
            System.out.println("Updated: " + key + " = " + value);
        }

        // Create an update message to broadcast to all clients
        Networktables.NetworkTableMessage broadcastMessage = createUpdateMessage("update", "broadcasted value");
        postMessageToAllClients(broadcastMessage);
    }


    private static void handleGet(Networktables.NetworkTableMessage.Get get, DataOutputStream dataOutputStream) throws IOException {
        // Create a response for the GET request
        Networktables.NetworkTableMessage.Response.Builder responseBuilder = Networktables.NetworkTableMessage.Response.newBuilder();
        String value = table.get(get.getKey());
        if (value != null) {
            responseBuilder.addEntries(Networktables.NetworkTableMessage.Entry.newBuilder().setKey(get.getKey()).setValue(value).build());
        }

        Networktables.NetworkTableMessage response = Networktables.NetworkTableMessage.newBuilder()
                .setResponse(responseBuilder.build())
                .build();

        byte[] responseBytes = response.toByteArray();
        dataOutputStream.writeInt(responseBytes.length);
        dataOutputStream.write(responseBytes);
    }

    private static void startConnectionNotifier() {
        Thread notifierThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(PRINT_INTERVAL_MS);

                    // Create a sample update message
                    Networktables.NetworkTableMessage updateMessage = createUpdateMessage("test", "3");
                    postMessageToAllClients(updateMessage);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        notifierThread.setDaemon(true); // Make it a daemon thread to allow JVM to exit
        notifierThread.start();
    }


    private static String createConnectionUpdateMessage() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String currentTime = sdf.format(new Date());

        StringBuilder sb = new StringBuilder();
        sb.append("[").append(currentTime).append("] Active connections:\n");
        for (Socket socket : connections.keySet()) {
            sb.append(" - ").append(socket.getRemoteSocketAddress()).append("\n");
        }
        sb.append("Total connections: ").append(connections.size()).append("\n");

        return sb.toString();
    }

    private static void postMessageToAllClients(Networktables.NetworkTableMessage message) {
        byte[] messageBytes = serializeMessage(message);

        for (Socket socket : connections.keySet()) {
            if (socket.isClosed() || !socket.isConnected()) {
                connections.remove(socket);
                continue;
            }

            // Ensure the socket is still valid
            if (!socket.isConnected() || socket.isClosed()) {
                connections.remove(socket);
                continue;
            }

            try (OutputStream outputStream = socket.getOutputStream(); // the get output stream seems to be what's making the socket close, not sure why
                 DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {

                // Send the message length
                dataOutputStream.writeInt(messageBytes.length);

                // Send the actual message
                dataOutputStream.write(messageBytes);
                dataOutputStream.flush(); // Ensure data is sent immediately

                System.out.println("Message sent successfully to: " + socket.getRemoteSocketAddress());

            } catch (IOException e) {
                System.err.println("IOException while posting message to client: " + e.getMessage());
                connections.remove(socket);
                try {
                    socket.close();
                } catch (IOException closeException) {
                    System.err.println("IOException while closing client socket: " + closeException.getMessage());
                }
            }

        }
    }


    private static Networktables.NetworkTableMessage createUpdateMessage(String key, String value) {
        Networktables.NetworkTableMessage.Entry entry = Networktables.NetworkTableMessage.Entry.newBuilder()
                .setKey(key)
                .setValue(value)
                .build();

        Networktables.NetworkTableMessage.Update update = Networktables.NetworkTableMessage.Update.newBuilder()
                .addEntries(entry)
                .build();

        return Networktables.NetworkTableMessage.newBuilder()
                .setUpdate(update)
                .build();
    }


    private static byte[] serializeMessage(Networktables.NetworkTableMessage message) {
        // Adjust this to match NT4 protocol if necessary
        return message.toByteArray(); // This assumes the message is already formatted correctly
    }

    public static Map<Socket, Thread> getConnections() {
        return new ConcurrentHashMap<>(connections);
    }
}
