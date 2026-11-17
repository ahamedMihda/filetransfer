package it.meucci;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public class ClientHandler implements Runnable {
    private static final String PROTOCOL_PREFIX = "ARCH/";
    private static final String PROTOCOL_VERSION = "ARCH/1.0";
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

            // 1. Controllo numero di parti (deve essere esattamente 3: COMANDO /risorsa VERSIONE)
            if (tokens.length != 3) {
                sendError(writer, 400, "Bad Request", "La richiesta deve avere tre parti: COMANDO /risorsa ARCH/1.0\n");
                return;
            }

            String command = tokens[0];
            String resource = tokens[1];
            String version = tokens[2];

            // 2. Controllo risorsa che inizia con '/'
            if (!resource.startsWith("/")) {
                sendError(writer, 400, "Bad Request", "La risorsa deve iniziare con '/'.\n");
                return;
            }

            // 3. Controllo formato versione ('ARCH/...')
            if (!version.startsWith(PROTOCOL_PREFIX)) {
                sendError(writer, 400, "Bad Request", "Versione del protocollo non valida.\n");
                return;
            }

            // 4. Controllo versione supportata ('ARCH/1.0')
            if (!version.equals(PROTOCOL_VERSION)) {
                sendError(writer, 505, "Version Not Supported", "Versione non supportata. Usare ARCH/1.0.\n");
                return;
            }

            // 5. Controllo comando supportato ('LEGGI')
            if (!command.equals("LEGGI")) {
                sendError(writer, 501, "Not Implemented", "Comando \"" + command + "\" non supportato.\n");
                return;
            }

            // Rimuove lo '/' iniziale per ottenere il nome base del file
            String baseName = resource.substring(1);

            if (!isValidBaseName(baseName)) {
                sendError(writer, 400, "Bad Request", "Nome della risorsa non valido.\n");
                return;
            }

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
                sendError(writer, 404, "Not Found", "La risorsa \"" + resource + "\" non esiste.\n");
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

    private void sendError(PrintWriter writer, int statusCode, String statusText, String message) {
        byte[] bodyBytes = message.getBytes(StandardCharsets.UTF_8);
        sendResponse(writer, statusCode, statusText, "text/plain; charset=utf-8", bodyBytes);
    }

    private void sendResponse(PrintWriter writer, int statusCode, String statusText, String contentType, byte[] bodyBytes) {
        String body = new String(bodyBytes, StandardCharsets.UTF_8);

        // Anche la riga di stato ora inizia con ARCH/1.0
        writer.println(PROTOCOL_VERSION + " " + statusCode + " " + statusText);
        writer.println("Content-Type: " + contentType);
        writer.println("Content-Length: " + bodyBytes.length);
        writer.println();
        writer.print(body);
        writer.flush();
    }
}
