import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

public class Client implements Runnable {
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private Server server;
    private int port;

    public Client(Socket clientSocket, Server server) {
        this.clientSocket = clientSocket;
        this.server = server;
        this.port = clientSocket.getPort();

        try {
            this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            this.out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true);

            while (true) {
                if (username == null) {
                    out.println("Enter your username: ");
                }
                this.username = in.readLine();

                if (username != null) {
                    username = username.trim();
                }

                if (username == null || username.isEmpty()) {
                    out.println("ERROR: Username cannot be empty.");
                    continue;
                }

                synchronized (server) {
                    if (server.getActiveClients().containsKey(username)) {
                        out.println("ERROR: Username already taken.");
                        continue;
                    } else {
                        server.registerClient(username, this);
                        System.out.println("Client registered: " + username);
                        out.println("SUCCESS: Client registered: " + username);
                        out.println("REGISTRATION_COMPLETE");
                        server.handleMessage(username, username + " has joined the chat!", "BROADCAST", null);
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Error initializing client connection: " + e.getMessage());
            disconnect();
        }
    }

    public int getPort() {
        return port;
    }

    @Override
    public void run() {
        try {
            String message;

            while (true) {
                displayMenu();
                String choice = in.readLine();

                switch (choice) {
                    case "1":
                        out.println("Enter your message:");
                        message = in.readLine();
                        if (message != null && !message.trim().isEmpty()) {
                            server.handleMessage(username, message, "BROADCAST", null);
                        } else {
                            out.println("Message cannot be empty.");
                        }
                        break;

                    case "2":
                        out.println("Enter the username of the recipient:");
                        String recipient = in.readLine();
                        out.println("Enter your message:");
                        message = in.readLine();
                        if (message != null && !message.trim().isEmpty()) {
                            server.handleMessage(username, message, "SPECIFIC_USERS", List.of(recipient.trim()));
                        } else {
                            out.println("Message cannot be empty.");
                        }
                        break;

                    case "3":
                        out.println("Enter usernames of recipients separated by commas:");
                        String recipients = in.readLine();
                        out.println("Enter your message:");
                        message = in.readLine();
                        if (message != null && !message.trim().isEmpty()) {
                            List<String> recipientList = Arrays.asList(recipients.split(","));
                            server.handleMessage(username, message, "SPECIFIC_USERS", recipientList);
                        } else {
                            out.println("Message cannot be empty.");
                        }
                        break;

                    case "4":
                        out.println("Enter usernames of people to exclude, separated by commas:");
                        String excludedUsers = in.readLine();
                        out.println("Enter your message:");
                        message = in.readLine();
                        if (message != null && !message.trim().isEmpty()) {
                            List<String> exclusionList = Arrays.asList(excludedUsers.split(","));
                            server.handleMessage(username, message, "BROADCAST_WITH_EXCLUSIONS", exclusionList);
                        } else {
                            out.println("Message cannot be empty.");
                        }
                        break;

                    case "5":
                        out.println("Banned phrases: " + String.join(", ", server.getBannedPhrases()));
                        break;

                    case "6":
                        StringBuilder clientsList = new StringBuilder("All connected clients:\n");
                        for (String clientUsername : server.getActiveClients().keySet()) {
                            clientsList.append(clientUsername).append("\n");
                        }
                        out.println(clientsList.toString());
                        break;

                    case "exit":
                        return;

                    default:
                        out.println("Invalid option. Please select a valid menu option.");
                }
            }

        } catch (IOException e) {
            System.out.println("Client " + username + " disconnected.");
        } finally {
            disconnect();
        }
    }

    private void displayMenu() {
        out.println("\n=== MENU ===");
        out.println("1. Send a message to everyone");
        out.println("2. Send a message to a specific person");
        out.println("3. Send a message to multiple specific people");
        out.println("4. Send a message to everyone except specific people");
        out.println("5. Query the server for the list of banned phrases");
        out.println("6. Show all connected clients");
        out.println("Type 'exit' to disconnect");
        out.println("Please select an option (1-6 or 'exit'):");
    }

    public String getUsername() {
        return username;
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public void disconnect() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }

            synchronized (server) {
                server.unregisterClient(username);
            }

            if (username != null) {
                server.handleMessage(username, username + " has left the chat!", "BROADCAST", null);
                System.out.println("Client " + username + " has disconnected.");
            }
        } catch (IOException e) {
            System.out.println("Error while disconnecting client: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 1818);
             BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
             PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader serverInput = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            String serverMessage;

            while ((serverMessage = serverInput.readLine()) != null) {
                System.out.println(serverMessage);

                if (serverMessage.startsWith("Enter your username:") || serverMessage.startsWith("ERROR:")) {
                    if (serverMessage.startsWith("ERROR:")) {
                        System.out.println("Enter your username:");
                    }

                    String username = input.readLine();
                    output.println(username);
                    output.flush();
                }
                else if (serverMessage.equals("REGISTRATION_COMPLETE")) {
                    System.out.println("Registration successful! Proceeding to menu...");
                    break;
                }
            }

            new Thread(() -> {
                String serverMsg;
                try {
                    while ((serverMsg = serverInput.readLine()) != null) {
                        System.out.println(serverMsg);
                    }
                } catch (IOException e) {
                    System.out.println("Connection to server lost.");
                }
            }).start();

            String userMessage;
            while ((userMessage = input.readLine()) != null) {
                if (userMessage.equalsIgnoreCase("exit")) {
                    output.println("exit");
                    break;
                }
                output.println(userMessage);
            }

            socket.close();
            System.out.println("You have disconnected from the server.");

            System.exit(0);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}