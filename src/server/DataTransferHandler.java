package server;

import common.Constants;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DataTransferHandler implements Runnable {
    private Path filePath;
    private long fileSize;
    private TransferMode mode;

    public enum TransferMode {
        UPLOAD,
        DOWNLOAD
    }

    public DataTransferHandler(Path filePath, long fileSize, TransferMode mode) {
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.mode = mode;
    }

    @Override
    public void run() {
        try (ServerSocket dataServerSocket = new ServerSocket(Constants.DATA_PORT)) {
            System.out.println("Server Data Channel listening on port " + Constants.DATA_PORT + " for " + mode + "...");
            Socket dataSocket = dataServerSocket.accept(); // Чекаємо на з'єднання від клієнта
            System.out.println("Data connection established with: " + dataSocket.getInetAddress().getHostAddress());

            if (mode == TransferMode.UPLOAD) {
                receiveFile(dataSocket);
            } else { // DOWNLOAD
                sendFile(dataSocket);
            }

            dataSocket.close(); // Закриваємо канал даних після завершення передачі
            System.out.println("Data transfer complete for " + filePath.getFileName() + ".");

        } catch (IOException e) {
            System.err.println("Error in DataTransferHandler: " + e.getMessage());
        }
    }

    private void receiveFile(Socket dataSocket) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(dataSocket.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE))) {

            byte[] buffer = new byte[Constants.BUFFER_SIZE];
            int bytesRead;
            long totalBytesRead = 0;

            System.out.println("Receiving file: " + filePath.getFileName() + " (" + fileSize + " bytes)");

            while (totalBytesRead < fileSize && (bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            System.out.println("\nFile received: " + filePath.getFileName());
        }
    }

    private void sendFile(Socket dataSocket) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(filePath, StandardOpenOption.READ));
             BufferedOutputStream out = new BufferedOutputStream(dataSocket.getOutputStream())) {

            byte[] buffer = new byte[Constants.BUFFER_SIZE];
            int bytesRead;
            long totalBytesSent = 0;

            System.out.println("Sending file: " + filePath.getFileName() + " (" + fileSize + " bytes)");

            while ((bytesRead = in.read(buffer)) != -1 && totalBytesSent < fileSize) {
                out.write(buffer, 0, bytesRead);
                totalBytesSent += bytesRead;
            }
            out.flush(); // Важливо для гарантування відправки всіх даних
            System.out.println("\nFile sent: " + filePath.getFileName());
        }
    }
}