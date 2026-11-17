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
                sendError(writer, "ERR SYNTAX");
                return;
            }

            String[] tokens = request.trim().split("\\s+");

            if (!tokens[0].equals("LEGGI")) {
                sendError(writer, "ERR UNKNOWNCMD");
                return;
            }

            if (tokens.length != 2 || !isValidBaseName(tokens[1])) {
                sendError(writer, "ERR SYNTAX");
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
                sendError(writer, "ERR NOTFOUND");
                return;
            }

            try {
                // Legge l'intero file in byte prima di inviare qualsiasi risposta
                byte[] dati = Files.readAllBytes(foundFile.toPath());
                String contenuto = new String(dati, StandardCharsets.UTF_8);

                writer.println("OK FILE");
                writer.println("Content-Type: " + foundMimeType);
                writer.println("Content-Length: " + dati.length); // Lunghezza esatta in byte
                writer.println();
                writer.print(contenuto);
                writer.flush(); // Obbligatorio dopo print() per svuotare il buffer prima di chiudere il socket

                System.out.println("File inviato a " + socket.getRemoteSocketAddress() + ": " + foundFile.getName() + " (" + dati.length + " byte)");

            } catch (IOException e) {
                // Se fallisce la lettura non abbiamo ancora inviato OK FILE, quindi possiamo inviare ERR READ
                sendError(writer, "ERR READ");
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

    private void sendError(PrintWriter writer, String errorCode) {
        writer.println(errorCode);
        writer.println();
    }
}
