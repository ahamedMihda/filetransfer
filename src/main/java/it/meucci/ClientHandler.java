package it.meucci;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler extends Thread {
    private Socket n;

    public ClientHandler(Socket s) {
        n = s;
    }

    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(n.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(
                new OutputStreamWriter(n.getOutputStream(), StandardCharsets.UTF_8), true);) {
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        ;
    }
}
