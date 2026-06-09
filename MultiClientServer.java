package network;

import java.io.*;
import java.net.*;
import java.util.*;

public class MultiClientServer {
    private static final int PORT = 7000;
    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private static ClientHandler coordinator = null;
    private static ClientHandler nextInLine = null;
    
    private static final Timer timer = new Timer(true);

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    sendMembersListToCoordinator();
                }
            }, 0, 20000);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected from: " + clientSocket.getInetAddress().getHostAddress());
                System.out.println("Connected to Server on Port: " + clientSocket.getPort());

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                synchronized (clients) {
                    clients.add(clientHandler);
                    
                    if (coordinator == null) {
                        coordinator = clientHandler;
                        coordinator.setCoordinator(true);
                        System.out.println(clientHandler.clientId + " is now the coordinator.");
                        coordinator.sendMessage("You are now the coordinator");
                    } else {
                        clientHandler.sendMessage("Current coordinator is: " + coordinator.clientId);
                        clientHandler.sendMessage("Available commands:");
                        clientHandler.sendMessage("@all <message> - Broadcast to everyone");
                        clientHandler.sendMessage("@<username> <message> - Private message");
                        clientHandler.sendMessage("MEMBERS - List online users (coordinator only)");
                        clientHandler.sendMessage("BYE - Exit");
                    }
                    
                    if (nextInLine == null && clientHandler != coordinator) {
                        nextInLine = clientHandler;
                    }
                }
                new Thread(clientHandler).start();
            }
            
            
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendMembersListToCoordinator() {
        synchronized (clients) {
            if (coordinator == null) return;
            
            StringBuilder membersList = new StringBuilder("Active Members (Periodic Update):\n");
            for (ClientHandler client : clients) {
                membersList.append("ID: ").append(client.clientId);
                if (client == coordinator) {
                    membersList.append(" (Coordinator)");
                }
                membersList.append(", IP: ").append(client.socket.getInetAddress().getHostAddress());
                membersList.append(", Port: ").append(client.socket.getLocalPort()).append("\n");
            }
            membersList.append("END_MEMBERS");

            try {
                coordinator.output.write(membersList.toString());
                coordinator.output.newLine();
                coordinator.output.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader input;
        private BufferedWriter output;
        private String clientId;
        private boolean isCoordinator = false;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                this.clientId = input.readLine();
                System.out.println("Client registered: " + clientId);
                broadcastMessage(clientId + " has joined the chat!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                String message;
                while ((message = input.readLine()) != null) {
                    System.out.println(clientId + ": " + message);

                    if (message.equalsIgnoreCase("BYE")) {
                        break;
                    } else if (message.equalsIgnoreCase("MEMBERS")) {
                        if (this.isCoordinator) {
                            sendMembersList();
                        } else {
                            sendMessage("ERROR: You must be the coordinator to view member list");
                        }
                    } else if (message.startsWith("@")) {
                        handleDirectedMessage(message);
                    } else {
                        broadcastMessage(clientId + ": " + message);
                    }
                }
            } catch (IOException e) {
                System.out.println("Client disconnected: " + clientId);
            } finally {
                closeConnection();
            }
        }
        
        

        private void handleDirectedMessage(String message) throws IOException {
            int spaceIndex = message.indexOf(' ');
            if (spaceIndex == -1) {
                sendMessage("Invalid format. Use @username message or @all message");
                return;
            }
            
            String recipient = message.substring(1, spaceIndex);
            String content = message.substring(spaceIndex + 1);
            
            if (recipient.equalsIgnoreCase("all")) {
                broadcastMessage(clientId + " (to all): " + content);
            } else {
                sendPrivateMessage(recipient, clientId + " (private): " + content);
            }
        }

        public void setCoordinator(boolean isCoordinator) {
            this.isCoordinator = isCoordinator;
            if (isCoordinator) {
                sendMessage("You are now the coordinator");
            }
        }

        public void sendMessage(String message) {
            try {
                output.write(message);
                output.newLine();
                output.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void sendMembersList() {
            try {
                StringBuilder membersList = new StringBuilder("Active Members (Requested):\n");
                synchronized (clients) {
                    for (ClientHandler client : clients) {
                        membersList.append("ID: ").append(client.clientId);
                        if (client == coordinator) {
                            membersList.append(" (Coordinator)");
                        }
                        membersList.append(", IP: ").append(client.socket.getInetAddress().getHostAddress());
                        membersList.append(", Port: ").append(client.socket.getPort()).append("\n");
                    }
                }
                membersList.append("END_MEMBERS");
                sendMessage(membersList.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void sendPrivateMessage(String recipientId, String message) {
            boolean recipientFound = false;
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    if (client.clientId.equalsIgnoreCase(recipientId)) {
                        try {
                            client.output.write(message + "\n");
                            client.output.flush();
                            this.output.write("[Sent to " + recipientId + "]: " + 
                                           message.substring(message.indexOf(':') + 1) + "\n");
                            this.output.flush();
                            recipientFound = true;
                            break;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            
            if (!recipientFound) {
                try {
                    this.output.write("User " + recipientId + " not found.\n");
                    this.output.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void broadcastMessage(String message) {
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    try {
                        if (!client.clientId.equals(this.clientId)) {
                            client.output.write(message + "\n");
                            client.output.flush();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void closeConnection() {
            try {
                synchronized (clients) {
                    clients.remove(this);
                    broadcastMessage(clientId + " has left the chat.");
                    
                    if (this == coordinator) {
                        coordinator = nextInLine;
                        if (coordinator != null) {
                            coordinator.setCoordinator(true);
                            System.out.println(coordinator.clientId + " is now the coordinator.");
                            broadcastMessage("New coordinator is: " + coordinator.clientId);
                        }
                        updateNextInLine();
                    } else if (this == nextInLine) {
                        updateNextInLine();
                    }
                }
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void updateNextInLine() {
            nextInLine = clients.stream()
                             .filter(c -> c != coordinator)
                             .findFirst()
                             .orElse(null);
        }
    }
}

