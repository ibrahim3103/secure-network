package chat;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ChatClient {

    private final String host;
    private final int port;
    private final String username;

    public ChatClient(String host, int port, String username) {
        this.host = host;
        this.port = port;
        this.username = username;
    }

    public void start() {
        System.out.println("[CLIENT] Connecting to " + host + ":" + port + " as " + username + " ...");
        try (
            Socket socket = new Socket(host, port);
            BufferedReader serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter serverOut = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader consoleIn = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
        ) {
            socket.setTcpNoDelay(true);

            // 1) Send username first
            serverOut.write(username);
            serverOut.write("\n");
            serverOut.flush();

            // 2) Thread to read from server
            Thread reader = new Thread(() -> {
                String line;
                try {
                    while ((line = serverIn.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    System.err.println("[CLIENT-READER] " + e.getMessage());
                }
            }, "server-reader");
            reader.setDaemon(true);
            reader.start();

            // 3) Main thread: read from console and send to server
            System.out.println("Type messages. Use /quit to exit.");
            String input;
            while ((input = consoleIn.readLine()) != null) {
                serverOut.write(input);
                serverOut.write("\n");
                serverOut.flush();
                if ("/quit".equalsIgnoreCase(input.trim())) break;
            }
        } catch (IOException e) {
            System.err.println("[CLIENT] " + e.getMessage());
        }
        System.out.println("[CLIENT] Disconnected.");
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java chat.ChatClient <host> <port> <username>");
            System.out.println("Example: java chat.ChatClient 127.0.0.1 12345 ibrahim");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String username = args[2];
        new ChatClient(host, port, username).start();
    }
}
