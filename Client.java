package network;


import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.UUID;

public class Client {
    private static volatile boolean running = true;
    private static String clientId;

    public static void main(String[] args) {
        final Socket[] connectionSocket = new Socket[1];
        final BufferedReader[] inputBuffer = new BufferedReader[1];
        final BufferedWriter[] outputBuffer = new BufferedWriter[1];

        // Connection retry loop
        boolean connected = false;
        while (!connected) {
            try {
                connectionSocket[0] = new Socket("localhost", 7000); // Changed to server port 7000
                inputBuffer[0] = new BufferedReader(new InputStreamReader(connectionSocket[0].getInputStream()));
                outputBuffer[0] = new BufferedWriter(new OutputStreamWriter(connectionSocket[0].getOutputStream()));
                connected = true;
                System.out.println("Connected to server!");
            } catch (IOException e) {
                System.err.println("Failed to connect: " + e.getMessage());
                System.out.println("Retrying in 5 seconds...");
                try { Thread.sleep(5000); } catch (InterruptedException ie) { return; }
            }
        }

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            try {
                if (connectionSocket[0] != null) connectionSocket[0].close();
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
            System.out.println("\nDisconnected from server.");
        }));

        try {
            // Generate and send client ID
            clientId = "Client-" + UUID.randomUUID().toString().substring(0, 5);
            outputBuffer[0].write(clientId + "\n");
            outputBuffer[0].flush();
            System.out.println("You are connected as: " + clientId);
            System.out.println("Connected to server port: 7000"); // Changed to show server port
            System.out.println("Commands:\n" +
                    "@all <message> - Broadcast to everyone\n" +
                    "@<username> <message> - Private message\n" +
                    "MEMBERS - Request member list (coordinator only)\n" +
                    "BYE - Exit");

            // Start a thread to listen for incoming messages
            new Thread(() -> {
                try {
                    String message;
                    while (running && (message = inputBuffer[0].readLine()) != null) {
                        System.out.println(message);
                    }
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Connection lost: " + e.getMessage());
                        System.exit(1);
                    }
                }
            }).start();

            // Main input loop
            Scanner consoleInput = new Scanner(System.in);
            while (running) {
                String message = consoleInput.nextLine();
                
                if (message.equalsIgnoreCase("BYE")) {
                    running = false;
                    break;
                }
                
                outputBuffer[0].write(message + "\n");
                outputBuffer[0].flush();
            }
        } catch (IOException e) {
            System.err.println("Communication error: " + e.getMessage());
        } finally {
            try {
                if (connectionSocket[0] != null) connectionSocket[0].close();
            } catch (IOException e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
            System.out.println("Client shut down.");
            System.exit(0);
        }
    }
}
