package it.meucci;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {

    public static void main(String[] args) {
        
        try (ServerSocket server = new ServerSocket(3000)) {
            while (true) {
                Socket clientSocket = server.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Errore del server: " + e.getMessage());
        }
    }
}
