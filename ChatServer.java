package chat;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ChatServer {

    private final int port;
    private final Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() {
        System.out.println("[SERVER] Starting on port " + port + " ...");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            while (true) {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                ClientHandler handler = new ClientHandler(socket);
                Thread t = new Thread(handler, "client-" + socket.getRemoteSocketAddress());
                t.start();
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Fatal: " + e.getMessage());
        }
    }

    private void broadcast(String message, ClientHandler from) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                if (c != from) {
                    c.send(message);
                }
            }
        }
        System.out.println("[BROADCAST] " + message);
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private BufferedWriter out;
        private String username = null;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

                // 1) First line from client must be the username
                username = in.readLine();
                if (username == null || username.isBlank()) {
                    send("SYSTEM: Invalid username. Closing connection.");
                    close();
                    return;
                }

                clients.add(this);
                send("SYSTEM: Welcome, " + username + "!");
                broadcast("SYSTEM: " + username + " joined the chat.", this);

                // 2) Read messages until client quits or disconnects
                String line;
                while ((line = in.readLine()) != null) {
                    if ("/quit".equalsIgnoreCase(line.trim())) {
                        break;
                    }
                    String formatted = username + ": " + line;
                    broadcast(formatted, this);
                }
            } catch (IOException e) {
                System.err.println("[CLIENT ERR] " + e.getMessage());
            } finally {
                try {
                    clients.remove(this);
                    if (username != null) {
                        broadcast("SYSTEM: " + username + " left the chat.", this);
                    }
                    close();
                } catch (Exception ignored) {}
            }
        }

        void send(String msg) {
            try {
                out.write(msg);
                out.write("\n");
                out.flush();
            } catch (IOException e) {
                // If we can't write to client, drop them
                System.err.println("[SEND FAIL] " + username + ": " + e.getMessage());
                try { close(); } catch (IOException ignored) {}
            }
        }

        void close() throws IOException {
            try { if (in != null) in.close(); } catch (IOException ignored) {}
            try { if (out != null) out.close(); } catch (IOException ignored) {}
            if (socket != null && !socket.isClosed()) socket.close();
        }
    }

    public static void main(String[] args) {
        int port = 12345; // default port
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }
        new ChatServer(port).start();
    }
}
