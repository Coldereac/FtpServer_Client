package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.util.stream.Collectors;

public class ClientHandler implements Runnable {
    private Socket clientControlSocket;
    private BufferedReader in;
    private PrintWriter out;
    private Path currentDirectory;
    private Path rootDirectory;
    private boolean authenticated = false;

    public ClientHandler(Socket clientControlSocket, Path rootDirectory) {
        this.clientControlSocket = clientControlSocket;
        this.rootDirectory = rootDirectory;
        this.currentDirectory = rootDirectory;
        try {
            in = new BufferedReader(new InputStreamReader(clientControlSocket.getInputStream()));
            out = new PrintWriter(clientControlSocket.getOutputStream(), true);
        } catch (IOException e) {
            System.err.println("Error initializing ClientHandler: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        String commandLine;
        try {
            while ((commandLine = in.readLine()) != null) {
                System.out.println("Received command from " + clientControlSocket.getInetAddress().getHostAddress() + ": " + commandLine);
                processCommand(commandLine);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + clientControlSocket.getInetAddress().getHostAddress());
        } finally {
            try {
                clientControlSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }

    private void processCommand(String commandLine) {
        String[] parts = commandLine.split(" ", 2);
        String command = parts[0].toUpperCase();
        String args = parts.length > 1 ? parts[1] : "";

        if (!authenticated && !command.equals("AUTH")) {
            System.out.println("Client not authenticated. Command ignored.");
            out.println("ERROR: Not authenticated.");
            return;
        }

        switch (command) {
            case "AUTH":
                authenticate(args);
                break;
            case "UPLOAD":
                handleUploadRequest(args);
                break;
            case "DOWNLOAD":
                handleDownloadRequest(args);
                break;
            case "MKDIR":
                createDirectory(args);
                break;
            case "RMDIR":
                removeDirectory(args);
                break;
            case "MVDIR":
                moveDirectory(args);
                break;
            case "LIST":
                listDirectory();
                break;
            case "CD":
                changeDirectory(args);
                break;
            default:
                System.out.println("Unknown command: " + command);
                out.println("ERROR: Unknown command.");
                break;
        }
    }

    private void authenticate(String args) {
        String[] creds = args.split(" ");
        if (creds.length == 2 && creds[0].equals("user") && creds[1].equals("pass")) {
            authenticated = true;
            System.out.println("Client authenticated successfully.");
            out.println("OK: Authenticated successfully.");
        } else {
            System.out.println("Authentication failed for user: " + (creds.length > 0 ? creds[0] : ""));
            authenticated = false;
            out.println("ERROR: Authentication failed.");
        }
    }

    private void handleUploadRequest(String args) {
        String[] fileInfo = args.split(" ");
        if (fileInfo.length != 2) {
            System.err.println("Invalid UPLOAD command format. Expected: UPLOAD <filename> <filesize>");
            out.println("ERROR: Invalid UPLOAD command format.");
            return;
        }
        String filename = fileInfo[0];
        long filesize;
        try {
            filesize = Long.parseLong(fileInfo[1]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid filesize for UPLOAD command: " + fileInfo[1]);
            out.println("ERROR: Invalid file size.");
            return;
        }

        Path filePath = currentDirectory.resolve(filename).normalize();
        if (!filePath.startsWith(rootDirectory)) {
            System.err.println("Attempted to upload outside of root directory: " + filePath);
            out.println("ERROR: Access denied.");
            return;
        }

        try (ServerSocket dataServerSocket = new ServerSocket(0)) { // Створюємо ServerSocket на випадковому вільному порту (порт 0)
            int dataPort = dataServerSocket.getLocalPort();
            System.out.println("Preparing for file upload: " + filename + " (" + filesize + " bytes) on port " + dataPort);
            out.println("READY_FOR_UPLOAD " + dataPort); // Повідомляємо клієнта про готовність та порт для даних

            Socket dataSocket = dataServerSocket.accept(); // Чекаємо на підключення клієнта до цього порту
            System.out.println("Data channel established for upload from " + dataSocket.getInetAddress().getHostAddress() + ".");
            // Тепер передаємо прийнятий dataSocket до DataTransferHandler
            new Thread(new DataTransferHandler(dataSocket, filePath, filesize, DataTransferHandler.TransferMode.UPLOAD)).start();
        } catch (IOException e) {
            System.err.println("Error setting up data channel for upload: " + e.getMessage());
            out.println("ERROR: Could not set up data channel for upload.");
        }
    }

    private void handleDownloadRequest(String filename) {
        Path filePath = currentDirectory.resolve(filename).normalize();
        if (!filePath.startsWith(rootDirectory)) {
            System.err.println("Attempted to download outside of root directory: " + filePath);
            out.println("ERROR: Access denied.");
            return;
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            System.err.println("File not found or not a regular file: " + filePath);
            out.println("ERROR: File not found or not a regular file.");
            return;
        }

        long filesize;
        try {
            filesize = Files.size(filePath);
        } catch (IOException e) {
            System.err.println("Error getting file size: " + e.getMessage());
            out.println("ERROR: Could not get file size.");
            return;
        }

        try (ServerSocket dataServerSocket = new ServerSocket(0)) { // Створюємо ServerSocket на випадковому вільному порту
            int dataPort = dataServerSocket.getLocalPort();
            System.out.println("Preparing for file download: " + filename + " (" + filesize + " bytes) on port " + dataPort);
            out.println("READY_FOR_DOWNLOAD " + filesize + " " + dataPort); // Повідомляємо клієнта про готовність, розмір та порт

            Socket dataSocket = dataServerSocket.accept(); // Чекаємо на підключення клієнта до цього порту
            System.out.println("Data channel established for download from " + dataSocket.getInetAddress().getHostAddress() + ".");
            // Тепер передаємо прийнятий dataSocket до DataTransferHandler
            new Thread(new DataTransferHandler(dataSocket, filePath, filesize, DataTransferHandler.TransferMode.DOWNLOAD)).start();
        } catch (IOException e) {
            System.err.println("Error setting up data channel for download: " + e.getMessage());
            out.println("ERROR: Could not set up data channel for download.");
        }
    }

    private void createDirectory(String dirName) {
        Path newDirPath = currentDirectory.resolve(dirName).normalize();
        if (!newDirPath.startsWith(rootDirectory)) {
            System.err.println("Attempted to create directory outside of root: " + newDirPath);
            out.println("ERROR: Access denied.");
            return;
        }
        try {
            Files.createDirectories(newDirPath);
            System.out.println("Directory created: " + newDirPath.getFileName());
            out.println("OK: Directory created.");
        } catch (IOException e) {
            System.err.println("Error creating directory " + dirName + ": " + e.getMessage());
            out.println("ERROR: Could not create directory.");
        }
    }

    private void removeDirectory(String dirName) {
        Path targetPath = currentDirectory.resolve(dirName).normalize();
        if (!targetPath.startsWith(rootDirectory)) {
            System.err.println("Attempted to remove directory outside of root: " + targetPath);
            out.println("ERROR: Access denied.");
            return;
        }
        try {
            if (Files.exists(targetPath) && Files.isDirectory(targetPath)) {
                Files.walk(targetPath)
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
                System.out.println("Directory removed: " + targetPath.getFileName());
                out.println("OK: Directory removed.");
            } else {
                System.err.println("Directory not found or not a directory: " + dirName);
                out.println("ERROR: Directory not found or not a directory.");
            }
        } catch (IOException e) {
            System.err.println("Error removing directory " + dirName + ": " + e.getMessage());
            out.println("ERROR: Could not remove directory.");
        }
    }

    private void moveDirectory(String args) {
        String[] paths = args.split(" ");
        if (paths.length != 2) {
            System.err.println("Invalid MVDIR command format. Expected: MVDIR <oldPath> <newPath>");
            out.println("ERROR: Invalid MVDIR command format.");
            return;
        }
        String oldPathName = paths[0];
        String newPathName = paths[1];

        Path oldPath = currentDirectory.resolve(oldPathName).normalize();
        Path newPath = currentDirectory.resolve(newPathName).normalize();

        if (!oldPath.startsWith(rootDirectory) || !newPath.startsWith(rootDirectory)) {
            System.err.println("Attempted to move path outside of root: " + oldPath + " -> " + newPath);
            out.println("ERROR: Access denied.");
            return;
        }

        try {
            Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Moved: " + oldPath.getFileName() + " to " + newPath.getFileName());
            out.println("OK: Moved successfully.");
        } catch (IOException e) {
            System.err.println("Error moving " + oldPathName + " to " + newPathName + ": " + e.getMessage());
            out.println("ERROR: Could not move.");
        }
    }

    private void listDirectory() {
        try {
            String content = Files.list(currentDirectory)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.joining("\n"));
            System.out.println("Listing directory: " + currentDirectory.toAbsolutePath());
            out.println("LIST_START");
            out.println(content);
            out.println("LIST_END");
        } catch (IOException e) {
            System.err.println("Error listing directory: " + e.getMessage());
            out.println("ERROR: Could not list directory.");
        }
    }

    private void changeDirectory(String targetDirName) {
        Path targetPath;
        if (targetDirName.equals("..")) {
            targetPath = currentDirectory.getParent();
            if (targetPath == null || !targetPath.startsWith(rootDirectory)) {
                targetPath = rootDirectory;
            }
        } else if (targetDirName.equals(".")) {
            targetPath = currentDirectory;
        } else if (targetDirName.startsWith("/")) {
            targetPath = rootDirectory.resolve(targetDirName.substring(1)).normalize();
        } else {
            targetPath = currentDirectory.resolve(targetDirName).normalize();
        }

        if (!targetPath.startsWith(rootDirectory)) {
            System.err.println("Attempted to change directory outside of root: " + targetPath);
            out.println("ERROR: Access denied. Cannot go above root directory.");
            return;
        }

        try {
            if (Files.exists(targetPath) && Files.isDirectory(targetPath)) {
                currentDirectory = targetPath;
                System.out.println("Changed directory to: " + currentDirectory.toAbsolutePath());
                out.println("OK: Directory changed to " + currentDirectory.getFileName() + ".");
            } else {
                System.err.println("Directory not found or not a directory: " + targetPath);
                out.println("ERROR: Directory not found or not a directory.");
            }
        } catch (SecurityException e) {
            System.err.println("Permission denied for changing directory to " + targetPath + ": " + e.getMessage());
            out.println("ERROR: Permission denied.");
        }
    }
}