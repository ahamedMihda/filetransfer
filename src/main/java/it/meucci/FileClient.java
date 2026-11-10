package it.meucci;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class FileClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 3000;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== CLIENT ARCHIVIO FILE TCP ===");
        while (true) {
            System.out.print("\nInserisci il nome del file da richiedere (es. avvisi.txt) o digita 'esci': ");
            String filename = scanner.nextLine().trim();

            if (filename.equalsIgnoreCase("esci")) {
                break;
            }

            if (filename.isEmpty()) {
                System.out.println("Il nome del file non può essere vuoto.");
                continue;
            }

            try (
                Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"))
            ) {
                // Invio della richiesta al server
                writer.println("LEGGI " + filename);

                String statusLine = reader.readLine();
                if (statusLine == null) {
                    System.out.println("Errore: Nessuna risposta ricevuta dal server.");
                    continue;
                }


                if (statusLine.startsWith("OK")) {
                    System.out.println("\n--- CONTENUTO DEL FILE ---");
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                    System.out.println("--------------------------");
                } else {
                    System.out.println("Errore dal server: " + statusLine);
                }

            } catch (IOException e) {
                System.err.println("Impossibile connettersi al server: " + e.getMessage());
            }
        }

        scanner.close();
        System.out.println("Client chiuso.");
    }
}