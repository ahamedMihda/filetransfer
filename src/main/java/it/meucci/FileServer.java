package it.meucci;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileServer {
    private static final int PORT = 3000;
    private static final String DOCS_DIR = "src/main/java/it/meucci/documenti";

    public static void main(String[] args) {
        // Creazione automatica della cartella documenti se non esiste
        File dir = new File(DOCS_DIR);
        if (!dir.exists()) {
            dir.mkdir();
            System.out.println("Cartella '" + DOCS_DIR + "' creata. Inserire qui i file di testo.");
        }

        System.out.println("=== FILE SERVER TCP AVVIATO ===");
        System.out.println("In ascolto sulla porta " + PORT + "...");

        // Thread pool per gestire più client concorrenti (Multiclient)
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connessione stabilita con il client: " + clientSocket.getRemoteSocketAddress());
                
                // Delega la gestione della connessione a un thread nel pool
                pool.execute(new ClientHandler(clientSocket, DOCS_DIR));
            }
        } catch (IOException e) {
            System.err.println("Errore nel server socket: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }
}