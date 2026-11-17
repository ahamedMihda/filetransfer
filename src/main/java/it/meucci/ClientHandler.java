package it.meucci;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

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
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            String request = reader.readLine();

            // 1. Se il client si disconnette senza inviare nulla, termina silenziosamente
            if (request == null) {
                return;
            }

            // 2. Controllo richiesta vuota (solo spazi o Invio)
            if (request.trim().isEmpty()) {
                sendError(writer, "ERR SYNTAX");
                return;
            }

            // Separa usando uno o più spazi
            String[] tokens = request.trim().split("\\s+");

            // 3. Controllo comando (deve essere esattamente "LEGGI" in maiuscolo)
            if (!tokens[0].equals("LEGGI")) {
                sendError(writer, "ERR UNKNOWNCMD");
                return;
            }

            // 4. Controllo numero di argomenti e validità del nome file
            if (tokens.length != 2 || !isValidFilename(tokens[1])) {
                sendError(writer, "ERR SYNTAX");
                return;
            }

            String filename = tokens[1];
            File file = new File(docsDir, filename);

            // 5. Controllo presenza del file
            if (!file.isFile()) {
                sendError(writer, "ERR NOTFOUND");
                return;
            }

            // 6. Apertura in lettura e invio del contenuto
            boolean headerSent = false;
            try (BufferedReader fileReader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

                writer.println("OK FILE");
                writer.println(); // Riga vuota di separazione
                headerSent = true;

                String line;
                while ((line = fileReader.readLine()) != null) {
                    writer.println(line);
                }
                System.out.println("File inviato a " + socket.getRemoteSocketAddress() + ": " + filename);

            } catch (IOException e) {
                if (!headerSent) {
                    sendError(writer, "ERR READ");
                } else {
                    System.err.println("Errore durante la lettura di " + filename + ": " + e.getMessage());
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

    private boolean isValidFilename(String filename) {
        if (filename == null || !filename.endsWith(".txt") || filename.equals(".txt")) {
            return false;
        }
        // Vieta barre, backslash e sequenze di risalita (..)
        return !filename.contains("/") && !filename.contains("\\") && !filename.contains("..");
    }

    private void sendError(PrintWriter writer, String errorCode) {
        writer.println(errorCode);
        writer.println(); // Riga vuota richiesta dal protocollo anche in caso di errore
    }
}
