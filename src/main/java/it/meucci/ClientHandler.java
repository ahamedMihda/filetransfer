package it.meucci;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

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
            String requestLine = reader.readLine();
            if (requestLine == null) {
                return;
            }

            // Registro del server: stampa la riga di richiesta
            System.out.println("\n--- Nuova Richiesta da " + socket.getRemoteSocketAddress() + " ---");
            System.out.println(requestLine);

            // Lettura delle intestazioni fino alla riga vuota
            Map<String, String> headers = new HashMap<>();
            boolean invalidHeaderSyntax = false;

            while (true) {
                String headerLine = reader.readLine();
                // Se la connessione si chiude prima della riga vuota, abbandona senza rispondere
                if (headerLine == null) {
                    System.out.println("[Connessione chiusa prima della riga vuota]");
                    return;
                }

                System.out.println(headerLine); // Stampa ogni riga ricevuta sul registro

                if (headerLine.isEmpty()) {
                    break; // Riga vuota raggiunta: fine delle intestazioni
                }

                int colonIndex = headerLine.indexOf(':');
                if (colonIndex <= 0) {
                    invalidHeaderSyntax = true;
                } else {
                    String name = headerLine.substring(0, colonIndex).trim().toLowerCase();
                    String value = headerLine.substring(colonIndex + 1).trim();
                    headers.put(name, value);
                }
            }

            // Controllo sintassi intestazioni (es. riga senza ':')
            if (invalidHeaderSyntax) {
                sendError(writer, 400, "Bad Request", "Intestazione non valida: manca il separatore ':'.\n");
                return;
            }

            // Stampa User-Agent se presente
            if (headers.containsKey("user-agent")) {
                System.out.println("Client (User-Agent): " + headers.get("user-agent"));
            }

            // Validazione riga di richiesta
            if (requestLine.trim().isEmpty()) {
                sendError(writer, 400, "Bad Request", "Richiesta vuota.\n");
                return;
            }

            String[] tokens = requestLine.trim().split("\\s+");
            if (tokens.length != 3) {
                sendError(writer, 400, "Bad Request", "La richiesta deve avere tre parti: COMANDO /risorsa ARCH/1.0\n");
                return;
            }

            String command = tokens[0];
            String resource = tokens[1];
            String version = tokens[2];

            if (!resource.startsWith("/")) {
                sendError(writer, 400, "Bad Request", "La risorsa deve iniziare con '/'.\n");
                return;
            }

            if (!version.startsWith(PROTOCOL_PREFIX)) {
                sendError(writer, 400, "Bad Request", "Versione del protocollo non valida.\n");
                return;
            }

            if (!version.equals(PROTOCOL_VERSION)) {
                sendError(writer, 505, "Version Not Supported", "Versione non supportata. Usare ARCH/1.0.\n");
                return;
            }

            if (!command.equals("LEGGI")) {
                sendError(writer, 501, "Not Implemented", "Comando \"" + command + "\" non supportato.\n");
                return;
            }

            String baseName = resource.substring(1);
            if (!isValidBaseName(baseName)) {
                sendError(writer, 400, "Bad Request", "Nome della risorsa non valido.\n");
                return;
            }

            // Negoziazione del contenuto tramite intestazione Accept
            String acceptHeader = headers.get("accept");
            List<String> candidateExtensions = resolveCandidateExtensions(acceptHeader);

            File foundFile = null;
            String foundMimeType = null;

            for (String ext : candidateExtensions) {
                File candidate = new File(docsDir, baseName + "." + ext);
                if (candidate.isFile()) {
                    foundFile = candidate;
                    foundMimeType = MIME_TYPES.get(ext);
                    break;
                }
            }

            if (foundFile == null) {
                // Verifica se il file esiste con un altro tipo supportato per distinguere 406 da 404
                boolean existsWithOtherType = false;
                for (String ext : MIME_TYPES.keySet()) {
                    if (new File(docsDir, baseName + "." + ext).isFile()) {
                        existsWithOtherType = true;
                        break;
                    }
                }

                if (existsWithOtherType) {
                    sendError(writer, 406, "Not Acceptable", "Nessun formato disponibile corrisponde all'intestazione Accept.\n");
                } else {
                    sendError(writer, 404, "Not Found", "La risorsa \"" + resource + "\" non esiste.\n");
                }
                return;
            }

            try {
                byte[] dati = Files.readAllBytes(foundFile.toPath());
                sendResponse(writer, 200, "OK", foundMimeType, dati);
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

    // Determina l'ordine delle estensioni da cercare in base all'header Accept
    private List<String> resolveCandidateExtensions(String acceptHeader) {
        if (acceptHeader == null || acceptHeader.trim().isEmpty()) {
            return new ArrayList<>(MIME_TYPES.keySet());
        }

        String[] acceptedTypes = acceptHeader.split(",");
        List<String> orderedExtensions = new ArrayList<>();

        for (String rawType : acceptedTypes) {
            // Considera solo la parte prima di ';' e ignora maiuscole/spazi
            String cleanType = rawType.split(";")[0].trim().toLowerCase();

            if (cleanType.equals("*/*")) {
                return new ArrayList<>(MIME_TYPES.keySet());
            }

            for (Map.Entry<String, String> entry : MIME_TYPES.entrySet()) {
                String supportedMime = entry.getValue().split(";")[0].trim().toLowerCase();
                if (supportedMime.equals(cleanType) && !orderedExtensions.contains(entry.getKey())) {
                    orderedExtensions.add(entry.getKey());
                }
            }
        }
        return orderedExtensions;
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

        System.out.println("Risposta: " + PROTOCOL_VERSION + " " + statusCode + " " + statusText);
        writer.println(PROTOCOL_VERSION + " " + statusCode + " " + statusText);
        writer.println("Content-Type: " + contentType);
        writer.println("Content-Length: " + bodyBytes.length);
        writer.println();
        writer.print(body);
        writer.flush();
    }
}
