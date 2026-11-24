package it.meucci;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class ClientHandler implements Runnable {
    private static final String PROTOCOL_PREFIX = "HTTP/";
    private static final String PROTOCOL_VERSION = "HTTP/1.0";
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
            boolean richiestaHead = tokens[0].equals("HEAD");
            if (tokens.length != 3) {
                sendError(writer, 400, "Bad Request", "La richiesta deve avere tre parti: COMANDO /risorsa HTTP/1.0\n", richiestaHead);
                return;
            }

            String command = tokens[0];
            String resource = tokens[1];
            String version = tokens[2];

            if (!resource.startsWith("/")) {
                sendError(writer, 400, "Bad Request", "La risorsa deve iniziare con '/'.\n", richiestaHead);
                return;
            }

            if (!version.startsWith(PROTOCOL_PREFIX)) {
                sendError(writer, 400, "Bad Request", "Versione del protocollo non valida.\n", richiestaHead);
                return;
            }

            // Browser e curl usano 1.0 o 1.1: rispondiamo sempre con 1.0
            if (!version.equals("HTTP/1.0") && !version.equals("HTTP/1.1")) {
                sendError(writer, 505, "Version Not Supported", "Versione non supportata. Usare HTTP/1.0 o HTTP/1.1.\n", richiestaHead);
                return;
            }

            if (!command.equals("GET") && !command.equals("HEAD") && !command.equals("PUT")) {
                sendError(writer, 501, "Not Implemented", "Comando \"" + command + "\" non supportato.\n", richiestaHead);
                return;
            }

            if (!isValidResource(resource)) {
                sendError(writer, 400, "Bad Request", "Percorso della risorsa non valido.\n", richiestaHead);
                return;
            }

            File percorso = percorsoRisorsa(resource);

            // PUT riceve il testo nel corpo della richiesta e lo salva sul disco
            if (command.equals("PUT")) {
                gestisciPut(reader, writer, headers, percorso, resource);
                return;
            }

            if (percorso.isDirectory()) {
                sendError(writer, 404, "Not Found", "La risorsa \"" + resource + "\" indica una cartella.\n", richiestaHead);
                return;
            }

            // Negoziazione del contenuto tramite intestazione Accept
            String acceptHeader = headers.get("accept");
            List<String> candidateExtensions = resolveCandidateExtensions(acceptHeader);

            File foundFile = null;
            String foundMimeType = null;

            for (String ext : candidateExtensions) {
                File candidate = fileConEstensione(percorso, ext);
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
                    if (fileConEstensione(percorso, ext).isFile()) {
                        existsWithOtherType = true;
                        break;
                    }
                }

                if (existsWithOtherType) {
                    sendError(writer, 406, "Not Acceptable", "Nessun formato disponibile corrisponde all'intestazione Accept.\n", richiestaHead);
                } else {
                    sendError(writer, 404, "Not Found", "La risorsa \"" + resource + "\" non esiste.\n", richiestaHead);
                }
                return;
            }

            try {
                byte[] dati = Files.readAllBytes(foundFile.toPath());
                // HEAD invia solo le intestazioni: Content-Length dice quanto sarebbe lungo il contenuto
                sendResponse(writer, 200, "OK", foundMimeType, dati, !richiestaHead);
            } catch (IOException e) {
                sendError(writer, 500, "Internal Server Error", "Errore interno durante la lettura del file.\n", richiestaHead);
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

    // Ogni segmento dopo la barra iniziale deve essere non vuoto e senza '.' o '\'
    private boolean isValidResource(String resource) {
        if (!resource.startsWith("/")) {
            return false;
        }
        String[] segmenti = resource.split("/", -1);
        if (segmenti.length < 2) {
            return false;
        }
        for (int i = 1; i < segmenti.length; i++) {
            String segmento = segmenti[i];
            if (segmento.isEmpty() || segmento.contains(".") || segmento.contains("\\")) {
                return false;
            }
        }
        return true;
    }

    // Costruisce il percorso della risorsa dentro la cartella documenti
    private File percorsoRisorsa(String resource) {
        File percorso = new File(docsDir);
        for (String segmento : resource.split("/", -1)) {
            if (!segmento.isEmpty()) {
                percorso = new File(percorso, segmento);
            }
        }
        return percorso;
    }

    private File fileConEstensione(File percorso, String estensione) {
        return new File(percorso.getParentFile(), percorso.getName() + "." + estensione);
    }

    // L'estensione del file salvato si ricava dal Content-Type, non dal nome richiesto
    private String estensioneDaContentType(String contentType) {
        String tipo = contentType.split(";")[0].trim().toLowerCase();
        for (Map.Entry<String, String> entry : MIME_TYPES.entrySet()) {
            if (entry.getValue().split(";")[0].trim().toLowerCase().equals(tipo)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void gestisciPut(BufferedReader reader, PrintWriter writer, Map<String, String> headers,
                             File percorso, String resource) throws IOException {
        String lunghezzaHeader = headers.get("content-length");
        if (lunghezzaHeader == null) {
            sendError(writer, 411, "Length Required", "Manca l'intestazione Content-Length.\n");
            return;
        }

        int lunghezza;
        try {
            lunghezza = Integer.parseInt(lunghezzaHeader.trim());
            if (lunghezza < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            sendError(writer, 400, "Bad Request", "Content-Length non numerico o negativo.\n");
            return;
        }

        String contentType = headers.get("content-type");
        if (contentType == null) {
            sendError(writer, 400, "Bad Request", "Manca l'intestazione Content-Type.\n");
            return;
        }

        String estensione = estensioneDaContentType(contentType);
        if (estensione == null) {
            sendError(writer, 415, "Unsupported Media Type", "Tipo di contenuto non supportato.\n");
            return;
        }

        File destinazione = fileConEstensione(percorso, estensione);
        if (!destinazione.getParentFile().isDirectory()) {
            sendError(writer, 404, "Not Found", "La cartella di destinazione non esiste.\n");
            return;
        }

        // curl può attendere il via libera prima di inviare il corpo
        String expect = headers.get("expect");
        if (expect != null && expect.toLowerCase().contains("100-continue")) {
            inviaRiga(writer, PROTOCOL_VERSION + " 100 Continue");
            inviaRiga(writer, "");
            writer.flush();
        }

        // Il corpo non è delimitato dalla chiusura del socket: leggiamo esattamente tanti byte
        StringBuilder corpo = new StringBuilder();
        int byteLetti = 0;
        while (byteLetti < lunghezza) {
            int c = reader.read();
            if (c == -1) {
                break; // contenuto incompleto
            }
            corpo.append((char) c);
            byteLetti += String.valueOf((char) c).getBytes(StandardCharsets.UTF_8).length;
        }
        if (byteLetti != lunghezza) {
            sendError(writer, 400, "Bad Request", "Contenuto più corto del previsto.\n");
            return;
        }

        boolean esisteva = destinazione.isFile();
        try {
            Files.write(destinazione.toPath(), corpo.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            sendError(writer, 500, "Internal Server Error", "Errore interno durante la scrittura del file.\n");
            System.err.println("Errore durante la scrittura di " + destinazione.getName() + ": " + e.getMessage());
            return;
        }

        String messaggio = esisteva
                ? "File \"" + resource + "\" sostituito.\n"
                : "File \"" + resource + "\" creato.\n";
        sendResponse(writer, esisteva ? 200 : 201, esisteva ? "OK" : "Created",
                "text/plain; charset=utf-8", messaggio.getBytes(StandardCharsets.UTF_8));
    }

    private void sendError(PrintWriter writer, int statusCode, String statusText, String message) {
        sendError(writer, statusCode, statusText, message, true);
    }

    private void sendError(PrintWriter writer, int statusCode, String statusText, String message, boolean inviaCorpo) {
        byte[] bodyBytes = message.getBytes(StandardCharsets.UTF_8);
        sendResponse(writer, statusCode, statusText, "text/plain; charset=utf-8", bodyBytes, inviaCorpo);
    }

    private void sendResponse(PrintWriter writer, int statusCode, String statusText, String contentType, byte[] bodyBytes) {
        sendResponse(writer, statusCode, statusText, contentType, bodyBytes, true);
    }

    private void sendResponse(PrintWriter writer, int statusCode, String statusText, String contentType,
                              byte[] bodyBytes, boolean inviaCorpo) {
        String body = new String(bodyBytes, StandardCharsets.UTF_8);

        System.out.println("Risposta: " + PROTOCOL_VERSION + " " + statusCode + " " + statusText);
        inviaRiga(writer, PROTOCOL_VERSION + " " + statusCode + " " + statusText);
        inviaRiga(writer, "Content-Type: " + contentType);
        inviaRiga(writer, "Content-Length: " + bodyBytes.length);
        inviaRiga(writer, "Connection: close");
        inviaRiga(writer, "");
        if (inviaCorpo) {
            writer.print(body);
        }
        writer.flush();
    }

    // Righe di stato e intestazioni terminano con CRLF, come nel protocollo del browser
    private void inviaRiga(PrintWriter writer, String testo) {
        writer.print(testo + "\r\n");
    }
}
