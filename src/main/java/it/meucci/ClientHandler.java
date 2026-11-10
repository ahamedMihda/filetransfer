package it.meucci;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final String docsDir;

    public ClientHandler(Socket socket, String docsDir) {
        this.socket = socket;
        this.docsDir = docsDir;
    }

    @Override
    public void run() {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true)
        ) {
            String request = reader.readLine();
            if (request == null || request.trim().isEmpty()) {
                sendError(writer, "ERR BAD_REQUEST Richiesta vuota");
                return;
            }

            // Parsing della richiesta (es. "LEGGI avvisi.txt", gestendo spazi multipli)
            String[] tokens = request.trim().split("\\s+");
            if (tokens.length < 2 || !tokens[0].equalsIgnoreCase("LEGGI")) {
                sendError(writer, "ERR BAD_REQUEST Comando non valido o formato errato");
                return;
            }

            String filename = tokens[1];

            // Validazione di sicurezza del nome file (prevenzione path traversal e caratteri non ammessi)
            if (!isValidFilename(filename)) {
                sendError(writer, "ERR BAD_REQUEST Nome file non valido");
                return;
            }

            File file = new File(docsDir, filename);
            if (!file.exists() || !file.isFile()) {
                sendError(writer, "ERR NOT_FOUND File non trovato");
                return;
            }

            // Invio della risposta di successo secondo il protocollo
            writer.println("OK FILE");
            writer.println(); // Riga vuota di separazione

            // Lettura e invio riga per riga del file
            try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line;
                while ((line = fileReader.readLine()) != null) {
                    writer.println(line);
                }
            }
            System.out.println("File inviato con successo a " + socket.getRemoteSocketAddress() + ": " + filename);

        } catch (IOException e) {
            System.err.println("Errore durante la gestione del client: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Chiusura silenziosa del socket
            }
        }
    }

    private boolean isValidFilename(String filename) {
        if (filename == null || !filename.endsWith(".txt")) {
            return false;
        }
        // Vieta barre, backslash, sequenze di risalita (..) e spazi
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..") || filename.contains(" ")) {
            return false;
        }
        return true;
    }

    private void sendError(PrintWriter writer, String errorMsg) {
        writer.println(errorMsg);
        writer.println(); // Riga vuota per rispettare la struttura del protocollo
    }
}