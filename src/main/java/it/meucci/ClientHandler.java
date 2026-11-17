package it.meucci;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class ClientHandler implements Runnable {
    // Mappa ordinata: associa ogni estensione ammessa al suo tipo MIME standard
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

            // Cerca il file seguendo l'ordine di inserimento nella LinkedHashMap
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

            boolean headerSent = false;
            try (BufferedReader fileReader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(foundFile), StandardCharsets.UTF_8))) {

                writer.println("OK FILE");
                // Nuovo formato standard della Tappa 3: "Nome: valore"
                writer.println("Content-Type: " + foundMimeType);
                writer.println();
                headerSent = true;

                String line;
                while ((line = fileReader.readLine()) != null) {
                    writer.println(line);
                }
                System.out.println("File inviato a " + socket.getRemoteSocketAddress() + ": " + foundFile.getName());

            } catch (IOException e) {
                if (!headerSent) {
                    sendError(writer, "ERR READ");
                } else {
                    System.err.println("Errore durante la lettura di " + foundFile.getName() + ": " + e.getMessage());
                }
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
