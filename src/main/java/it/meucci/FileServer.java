package it.meucci;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileServer {
    private static final int PORT = 3000;
    private static final Path DOCS_PATH = Paths.get("documenti");

    public static void main(String[] args) {
        if (!inizializzaDirectory()) {
            return;
        }

        System.out.println("=== FILE SERVER TCP AVVIATO ===");
        System.out.println("Directory file: " + DOCS_PATH.toAbsolutePath());
        System.out.println("In ascolto sulla porta " + PORT + "...");

        // try-with-resources chiude automaticamente sia il pool che il ServerSocket
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
                ServerSocket serverSocket = new ServerSocket(PORT)) {

            // Gestione elegante dello spegnimento del server (es. Ctrl+C)
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nArresto del server in corso...");
                try {
                    serverSocket.close();
                } catch (IOException ignored) {
                }
            }));

            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Nuovo client connesso: " + clientSocket.getRemoteSocketAddress());
                    pool.execute(new ClientHandler(clientSocket, DOCS_PATH.toString()));
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        System.err.println("Errore durante l'accettazione di un client: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Impossibile avviare il server sulla porta " + PORT + ": " + e.getMessage());
        }
    }

    private static boolean inizializzaDirectory() {
        try {
            if (Files.notExists(DOCS_PATH)) {
                Files.createDirectories(DOCS_PATH);
                System.out.println("Cartella creata in: " + DOCS_PATH.toAbsolutePath());
            }
            return true;
        } catch (IOException e) {
            System.err.println("Errore critico nella creazione della directory: " + e.getMessage());
            return false;
        }
    }
}
