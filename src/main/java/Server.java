import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Server {
    private ServerSocket serverSocket;
    private int port;
    private String serverName;
    private List<String> bannedPhrases;
    private ExecutorService threadPool;
    private HashMap<String, Client> activeClients;
    private boolean isRunning;

    public Server() {
        this.activeClients = new HashMap<>();
        loadConfig();
        this.threadPool = Executors.newCachedThreadPool(); // to allow arbitrary number of clients
        this.isRunning = true;

        // shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown Hook: Server is shutting down...");
            stopServer();
        }));
    }

    public void registerClient(String username, Client clientHandler) {
        synchronized (activeClients) {
            activeClients.put(username, clientHandler);
            System.out.println("Client registered: " + username + " on port: " + clientHandler.getPort());
            // list of all currently connected clients to the newly registered client
            StringBuilder clientsList = new StringBuilder("Connected clients:\n");
            for (String clientUsername : activeClients.keySet()) {
                if (!clientUsername.equals(username)) {
                    clientsList.append(clientUsername).append("\n");
                }
            }
            clientHandler.sendMessage(clientsList.toString());
            clientHandler.sendMessage("REGISTRATION_COMPLETE");
        }
    }

    public void unregisterClient(String username) {
        synchronized (activeClients) {
            activeClients.remove(username);
        }
    }

    public HashMap<String, Client> getActiveClients() {
        return activeClients;
    }

    public void startServer()  {
        loadConfig();
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("The server is listening on port " + port);

            // thread to handle server shutdown command
            new Thread(this::listenForShutdown).start();

            while (isRunning) {
                try {
                    // new clients
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected");

                    // client handler
                    Client clientHandler = new Client(clientSocket, this);
                    threadPool.execute(clientHandler);
                } catch (IOException e) {
                    if (isRunning) {
                        System.out.println("Error accepting new client connection: " + e.getMessage());
                    } else {
                        System.out.println("Server stopped accepting new clients.");
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void listenForShutdown() {
        try (BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in))) {
            String command;
            while (isRunning && (command = consoleInput.readLine()) != null) {
                if ("shutdown".equalsIgnoreCase(command.trim())) {
                    System.out.println("Shutdown command received. Shutting down the server...");
                    stopServer();
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("Error while reading shutdown command: " + e.getMessage());
        }
    }

    public void stopServer() {
        isRunning = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.out.println("Error closing the server socket: " + e.getMessage());
        }

        HashMap<String, Client> clientsCopy;
        synchronized (activeClients) {
            clientsCopy = new HashMap<>(activeClients);
        }

        for (Client client : clientsCopy.values()) {
            client.sendMessage("Server is shutting down. You will be disconnected.");
            client.disconnect();
        }

        synchronized (activeClients) {
            activeClients.clear();
        }

        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
        }

        System.out.println("Server has shut down.");
}

    public void handleMessage(String sender, String message, String type, List<String> recipients) {
        synchronized (activeClients) {
            if (containsBannedPhrase(message)) {
                System.out.println("Message from " + sender + " contains banned phrases and will not be delivered.");
                Client senderClient = activeClients.get(sender);
                if (senderClient != null) {
                    senderClient.sendMessage("Your message contains a banned phrase and was not delivered.");
                }
                return;
            }

            switch (type) {
                case "BROADCAST":
                    for (Client client : activeClients.values()) {
                        if (!client.getUsername().equals(sender)) {
                            client.sendMessage(sender + ": " + message);
                        }
                    }
                    break;

                case "SPECIFIC_USERS":
                    for (String recipient : recipients) {
                        Client client = activeClients.get(recipient.trim());
                        if (client != null) {
                            client.sendMessage(sender + " (private): " + message);
                        } else {
                            Client senderClient = activeClients.get(sender);
                            if (senderClient != null) {
                                senderClient.sendMessage("User " + recipient + " not found.");
                            }
                        }
                    }
                    break;

                case "BROADCAST_WITH_EXCLUSIONS":
                    for (Client client : activeClients.values()) {
                        if (!client.getUsername().equals(sender) && !recipients.contains(client.getUsername())) {
                            client.sendMessage(sender + ": " + message);
                        }
                    }
                    break;

                default:
                    System.out.println("Unknown message type received.");
            }
        }
    }

    private void loadConfig() {
        String filePath = "/Users/danylooliinyk/programming/UTP/Client_Server_Project/src/main/java/configuration.txt";

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0];
                    String value = parts[1];
                    switch (key) {
                        case "port":
                            setPort(Integer.parseInt(value));
                            break;
                        case "name":
                            setServerName(value);
                            break;
                        case "bannedPhrases":
                            setBannedPhrases(List.of(value.split(",")));
                            break;
                        default:
                            System.out.println("Unknown key: " + key);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean containsBannedPhrase(String message) {
        for (String bannedPhrase : bannedPhrases) {
            if (message.toLowerCase().contains(bannedPhrase.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public int getPort() {
        return port;
    }

    public String getServerName() {
        return serverName;
    }

    public List<String> getBannedPhrases() {
        return bannedPhrases;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public void setBannedPhrases(List<String> bannedPhrases) {
        this.bannedPhrases = bannedPhrases;
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.startServer();
    }
}