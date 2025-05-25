package client;

import common.Constants;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileTransferHandler implements Runnable {
    private Path filePath;
    private TransferMode mode;
    private long fileSize;

    public enum TransferMode {
        UPLOAD,
        DOWNLOAD
    }

    public FileTransferHandler(Path filePath, TransferMode mode, long fileSize) {
        this.filePath = filePath;
        this.mode = mode;
        this.fileSize = fileSize;
    }

    @Override
    public void run() {
        try (Socket dataSocket = new Socket(Constants.SERVER_ADDRESS, Constants.DATA_PORT)) {
            System.out.println("Client Data Channel connected to " + Constants.SERVER_ADDRESS + ":" + Constants.DATA_PORT);

            if (mode == TransferMode.UPLOAD) {
                sendFile(dataSocket);
            } else { // DOWNLOAD
                receiveFile(dataSocket);
            }
            System.out.println("Data transfer complete for " + filePath.getFileName() + ".");

        } catch (IOException e) {
            System.err.println("Error in FileTransferHandler for " + filePath.getFileName() + ": " + e.getMessage());
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
                System.out.print("\rUploading: " + filePath.getFileName() + " - " + (totalBytesSent * 100 / fileSize) + "%");
            }
            out.flush();
            System.out.println("\nFile sent: " + filePath.getFileName());
        }
    }

    private void receiveFile(Socket dataSocket) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(dataSocket.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE))) {

            byte[] buffer = new byte[Constants.BUFFER_SIZE];
            int bytesRead;
            long totalBytesRead = 0;

            System.out.println("Receiving file: " + filePath.getFileName() + (fileSize != -1 ? " (" + fileSize + " bytes)" : ""));

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                if (fileSize != -1 && fileSize > 0) { // Перевірка fileSize > 0, щоб уникнути ділення на нуль
                    System.out.print("\rDownloading: " + filePath.getFileName() + " - " + (totalBytesRead * 100 / fileSize) + "%");
                }
            }
            System.out.println("\nFile received: " + filePath.getFileName());
        }
    }
}