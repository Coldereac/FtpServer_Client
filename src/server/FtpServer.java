package server;

import common.Constants;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FtpServer {
    private ServerSocket controlSocket;
    private Path rootDirectory;

    public FtpServer() {
        try {
            // Створюємо кореневу директорію, якщо її немає
            rootDirectory = Paths.get(Constants.ROOT_DIRECTORY);
            if (!Files.exists(rootDirectory)) {
                Files.createDirectories(rootDirectory);
                System.out.println("Created root directory: " + rootDirectory.toAbsolutePath());
            }

            controlSocket = new ServerSocket(Constants.CONTROL_PORT);
            System.out.println("FTP Server listening on port " + Constants.CONTROL_PORT + " for control commands...");
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
            System.exit(1);
        }
    }

    public void start() {
        while (true) {
            try {
                Socket clientSocket = controlSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket, rootDirectory);
                new Thread(clientHandler).start(); // Запускаємо обробник клієнта в окремому потоці
            } catch (IOException e) {
                System.err.println("Error accepting client connection: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        FtpServer server = new FtpServer();
        server.start();
    }
}