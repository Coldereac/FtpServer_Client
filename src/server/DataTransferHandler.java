package server;

import common.Constants;
import java.io.*;
import java.net.Socket; // Приймаємо Socket
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DataTransferHandler implements Runnable {
    private Socket dataSocket; // Приймаємо вже відкритий сокет
    private Path filePath;
    private long fileSize;
    private TransferMode mode;

    public enum TransferMode {
        UPLOAD, // Сервер отримує файл
        DOWNLOAD // Сервер відправляє файл
    }

    public DataTransferHandler(Socket dataSocket, Path filePath, long fileSize, TransferMode mode) {
        this.dataSocket = dataSocket;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.mode = mode;
    }

    @Override
    public void run() {
        try (Socket currentDataSocket = this.dataSocket) { // Використовуємо прийнятий сокет
            System.out.println("Server Data Channel active for " + filePath.getFileName() + " on local port " + currentDataSocket.getLocalPort() + " (remote: " + currentDataSocket.getRemoteSocketAddress() + ")");

            if (mode == TransferMode.UPLOAD) {
                receiveFile(currentDataSocket);
            } else {
                sendFile(currentDataSocket);
            }
            System.out.println("Data transfer complete for " + filePath.getFileName() + ".");

        } catch (IOException e) {
            System.err.println("Error in DataTransferHandler for " + filePath.getFileName() + ": " + e.getMessage());
        }
    }

    private void sendFile(Socket socket) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(filePath, StandardOpenOption.READ));
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            byte[] buffer = new byte[Constants.BUFFER_SIZE];
            int bytesRead;
            long totalBytesSent = 0;

            System.out.println("Sending file: " + filePath.getFileName() + " (" + fileSize + " bytes)");

            while ((bytesRead = in.read(buffer)) != -1 && totalBytesSent < fileSize) {
                out.write(buffer, 0, bytesRead);
                totalBytesSent += bytesRead;
            }
            out.flush();
            System.out.println("File sent: " + filePath.getFileName());
        }
    }

    private void receiveFile(Socket socket) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE))) {

            byte[] buffer = new byte[Constants.BUFFER_SIZE];
            int bytesRead;
            long totalBytesRead = 0;

            System.out.println("Receiving file: " + filePath.getFileName() + " (" + fileSize + " bytes)");

            while (totalBytesRead < fileSize && (bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            System.out.println("File received: " + filePath.getFileName());
        }
    }
}