package it.meucci;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public class ClientHandler implements Runnable {
    private static final Map<String, String> MIME_TYPES = new LinkedHashMap<>();

    static {
        MIME_TYPES.put("txt", "text/plain; charset=utf-8");
        MIME_TYPES.put("md", "text/markdown; charset=utf-8");
        MIME_TYPES.put("csv", "text/csv; charset=utf-8");
    }

    private final Socket socket;
    private final String docsDir;

    public ClientHandler(Socket socket, String docsDir) {
        this.socket = socket;
        this.docsDir = docsDir;
    }

    @Override
    public void run() {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
        ) {
            String request = reader.readLine();

            if (request == null) {
                return;
            }

            if (request.trim().isEmpty()) {
                sendError(writer, 400, "Bad Request", "Richiesta vuota.\n");
                return;
            }

            String[] tokens = request.trim().split("\\s+");

            if (!tokens[0].equals("LEGGI")) {
                sendError(writer, 501, "Not Implemented", "Comando \"" + tokens[0] + "\" non supportato.\n");
                return;
            }

            if (tokens.length != 2 || !isValidBaseName(tokens[1])) {
                sendError(writer, 400, "Bad Request", "Sintassi non valida o nome file non ammesso.\n");
                return;
            }

            String baseName = tokens[1];
            File foundFile = null;
            String foundMimeType = null;

            for (Map.Entry<String, String> entry : MIME_TYPES.entrySet()) {
                File candidate = new File(docsDir, baseName + "." + entry.getKey());
                if (candidate.isFile()) {
                    foundFile = candidate;
                    foundMimeType = entry.getValue();
                    break;
                }
            }

            if (foundFile == null) {
                sendError(writer, 404, "Not Found", "Il file \"" + baseName + "\" non esiste.\n");
                return;
            }

            try {
                byte[] dati = Files.readAllBytes(foundFile.toPath());
                sendResponse(writer, 200, "OK", foundMimeType, dati);
                System.out.println("200 OK - Inviato a " + socket.getRemoteSocketAddress() + ": " + foundFile.getName());

            } catch (IOException e) {
                sendError(writer, 500, "Internal Server Error", "Errore interno durante la lettura del file.\n");
                System.err.println("Errore durante la lettura di " + foundFile.getName() + ": " + e.getMessage());
            }

        } catch (IOException e) {
            System.err.println("Errore di comunicazione con il client: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private boolean isValidBaseName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return !name.contains(".") && !name.contains("/") && !name.contains("\\");
    }

    // Metodo di supporto per inviare risposte di errore in formato text/plain
    private void sendError(PrintWriter writer, int statusCode, String statusText, String message) {
        byte[] bodyBytes = message.getBytes(StandardCharsets.UTF_8);
        sendResponse(writer, statusCode, statusText, "text/plain; charset=utf-8", bodyBytes);
    }

    // Metodo unico per inviare qualsiasi risposta (successo o errore)
    private void sendResponse(PrintWriter writer, int statusCode, String statusText, String contentType, byte[] bodyBytes) {
        String body = new String(bodyBytes, StandardCharsets.UTF_8);

        writer.println(statusCode + " " + statusText);
        writer.println("Content-Type: " + contentType);
        writer.println("Content-Length: " + bodyBytes.length);
        writer.println();
        writer.print(body);
        writer.flush();
    }
}
