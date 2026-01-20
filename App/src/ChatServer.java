import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.time.Instant;

public class ChatServer {
    private static final int PORT = 8080;
    // Store connected clients for SSE (Server-Sent Events)
    private static final List<PrintWriter> sseClients = Collections.synchronizedList(new ArrayList<>());
    // Store messages in memory
    private static final List<Map<String, String>> messages = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Serve static files
        server.createContext("/", new StaticHandler());

        // API Endpoints
        server.createContext("/api/send", new SendHandler());
        server.createContext("/api/stream", new StreamHandler());
        server.createContext("/api/status", new StatusHandler());

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Server started on http://localhost:" + PORT);
    }

    // Helper to broadcast events to all connected clients
    private static void broadcast(String eventType, String jsonData) {
        synchronized (sseClients) {
            Iterator<PrintWriter> it = sseClients.iterator();
            while (it.hasNext()) {
                PrintWriter writer = it.next();
                try {
                    synchronized (writer) {
                        writer.write("event: " + eventType + "\n");
                        writer.write("data: " + jsonData + "\n\n");
                        writer.flush();
                    }
                    if (writer.checkError()) { // Check for closed connection
                        it.remove();
                    }
                } catch (Exception e) {
                    it.remove();
                }
            }
        }
    }

    // Handler for Static Files (HTML, CSS, JS)
    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/"))
                path = "/index.html";

            // basic security check to prevent directory traversal
            if (path.contains("..")) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }

            Path filePath = Path.of("public" + path);
            if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                String mimeType = "text/plain";
                if (path.endsWith(".html"))
                    mimeType = "text/html";
                else if (path.endsWith(".css"))
                    mimeType = "text/css";
                else if (path.endsWith(".js"))
                    mimeType = "application/javascript";
                else if (path.endsWith(".png"))
                    mimeType = "image/png";

                exchange.getResponseHeaders().set("Content-Type", mimeType);
                exchange.sendResponseHeaders(200, Files.size(filePath));
                try (OutputStream os = exchange.getResponseBody()) {
                    Files.copy(filePath, os);
                }
            } else {
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }

    // Handler for Sending Messages
    static class SendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }
                String body = buf.toString();

                // Very basic manual JSON parsing
                String user = extractJsonValue(body, "user");
                String content = extractJsonValue(body, "content");
                String id = UUID.randomUUID().toString();
                String time = Instant.now().toString();

                Map<String, String> msg = new HashMap<>();
                msg.put("id", id);
                msg.put("user", user);
                msg.put("content", content);
                msg.put("time", time);
                msg.put("status", "sent"); // sent, delivered, seen

                messages.add(msg);

                String jsonResponse = String.format("{\"id\": \"%s\", \"status\": \"sent\", \"time\": \"%s\"}", id,
                        time);

                // Broadcast to all
                // Escape quotes in content for JSON safety
                String safeContent = content.replace("\"", "\\\"");
                String jsonBroadcast = String.format(
                        "{\"id\": \"%s\", \"user\": \"%s\", \"content\": \"%s\", \"time\": \"%s\", \"status\": \"sent\"}",
                        id, user, safeContent, time);
                broadcast("message", jsonBroadcast);

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(jsonResponse.getBytes());
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // Handler for Message Status Updates (Seen/Delivered)
    static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    buf.append(line);
                }
                String body = buf.toString();

                String msgId = extractJsonValue(body, "id");
                String status = extractJsonValue(body, "status");
                String byUser = extractJsonValue(body, "byUser");

                // Broadcast status update
                String jsonBroadcast = String.format("{\"id\": \"%s\", \"status\": \"%s\", \"byUser\": \"%s\"}", msgId,
                        status, byUser);
                broadcast("status", jsonBroadcast);

                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // Handler for SSE Stream
    static class StreamHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            exchange.sendResponseHeaders(200, 0);

            PrintWriter writer = new PrintWriter(exchange.getResponseBody());
            sseClients.add(writer);

            // Create a workaround to keep the connection open and detect disconnects
            // We can't easily wait ideally in the handler logic without blocking a thread
            // per client
            // But Executors.newCachedThreadPool() handles this okay for small number of
            // users (2 users)

            try {
                // Send heartbeat or keep alive
                while (!writer.checkError()) {
                    Thread.sleep(10000);
                    synchronized (writer) {
                        writer.write(": keepalive\n\n");
                        writer.flush();
                    }
                }
            } catch (InterruptedException e) {
                // ignore
            } finally {
                sseClients.remove(writer);
            }
        }
    }

    // Utility: Dumb JSON extractor (assumes key works like "key": "value")
    private static String extractJsonValue(String json, String key) {
        // Improved regex to capture content more reliably
        String pattern = "\"" + key + "\"\\s*:\\s*\"(.*?)\"";
        java.util.regex.Pattern r = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = r.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }
}
