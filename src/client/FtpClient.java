package client;

import common.Constants;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class FtpClient {
    private Socket controlSocket;
    private PrintWriter out;
    private BufferedReader in;
    private Scanner scanner;

    public FtpClient() {
        try {
            controlSocket = new Socket(Constants.SERVER_ADDRESS, Constants.CONTROL_PORT);
            out = new PrintWriter(controlSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
            scanner = new Scanner(System.in);
            System.out.println("Connected to FTP server on " + Constants.SERVER_ADDRESS + ":" + Constants.CONTROL_PORT);
        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
            System.exit(1);
        }
    }

    public void authenticate(String username, String password) {
        sendCommand(String.format("AUTH %s %s", username, password));
        readServerResponse();
    }

    public void uploadFile(String localFilePathString) {
        Path localFilePath = Paths.get(localFilePathString);
        if (!Files.exists(localFilePath) || !Files.isRegularFile(localFilePath)) {
            System.err.println("Local file not found or not a regular file: " + localFilePathString);
            return;
        }

        try {
            long fileSize = Files.size(localFilePath);
            String filename = localFilePath.getFileName().toString();
            sendCommand(String.format("UPLOAD %s %d", filename, fileSize));
            System.out.println("Sent UPLOAD command for: " + filename + " (" + fileSize + " bytes)");

            String response = readServerResponse();
            if (response != null && response.startsWith("READY_FOR_UPLOAD")) {
                String[] parts = response.split(" ");
                int dataPort = -1;
                if (parts.length == 2) {
                    try {
                        dataPort = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid data port from server: " + parts[1]);
                        return;
                    }
                }

                if (dataPort != -1) {
                    new Thread(new FileTransferHandler(Constants.SERVER_ADDRESS, dataPort, localFilePath, FileTransferHandler.TransferMode.UPLOAD, fileSize)).start();
                } else {
                    System.out.println("Server did not provide a valid data port for upload.");
                }
            } else {
                System.out.println("Server denied upload request: " + (response != null ? response : "No response"));
            }

        } catch (IOException e) {
            System.err.println("Error preparing to upload file: " + e.getMessage());
        }
    }

    public void downloadFile(String remoteFileName) {
        sendCommand("DOWNLOAD " + remoteFileName);
        System.out.println("Sent DOWNLOAD command for: " + remoteFileName);

        String response = readServerResponse();
        if (response != null && response.startsWith("READY_FOR_DOWNLOAD")) {
            String[] parts = response.split(" ");
            long fileSize = -1;
            int dataPort = -1;
            if (parts.length == 3) {
                try {
                    fileSize = Long.parseLong(parts[1]);
                    dataPort = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid file size or data port from server: " + parts[1] + ", " + parts[2]);
                    return;
                }
            }

            if (fileSize != -1 && dataPort != -1) {
                Path localDownloadPath = Paths.get("downloads", remoteFileName);
                try {
                    Files.createDirectories(localDownloadPath.getParent());
                    // Передаємо null замість callback
                    new Thread(new FileTransferHandler(Constants.SERVER_ADDRESS, dataPort, localDownloadPath, FileTransferHandler.TransferMode.DOWNLOAD, fileSize)).start();
                } catch (IOException e) {
                    System.err.println("Error creating download directory: " + e.getMessage());
                }
            } else {
                System.out.println("Server did not provide valid file size or data port for download.");
            }
        } else {
            System.out.println("Server denied download request: " + (response != null ? response : "No response"));
        }
    }

    public void createDirectory(String dirName) {
        sendCommand("MKDIR " + dirName);
        readServerResponse();
    }

    public void removeDirectory(String dirName) {
        sendCommand("RMDIR " + dirName);
        readServerResponse();
    }

    public void moveDirectory(String oldPath, String newPath) {
        sendCommand(String.format("MVDIR %s %s", oldPath, newPath));
        readServerResponse();
    }

    public void listDirectory() {
        sendCommand("LIST");
        System.out.println("Content of current directory:");
        String line;
        try {
            while ((line = in.readLine()) != null) {
                if (line.equals("LIST_END")) {
                    break;
                }
                if (!line.equals("LIST_START")) {
                    System.out.println("  " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading directory list: " + e.getMessage());
        }
    }

    public void changeDirectory(String targetDir) {
        sendCommand("CD " + targetDir);
        readServerResponse();
    }

    private void sendCommand(String command) {
        out.println(command);
    }

    private String readServerResponse() {
        try {
            String response = in.readLine();
            if (response != null) {
                System.out.println("Server response: " + response);
            }
            return response;
        } catch (IOException e) {
            System.err.println("Error reading server response: " + e.getMessage());
            return null;
        }
    }

    public void close() {
        try {
            if (controlSocket != null) {
                controlSocket.close();
            }
            if (scanner != null) {
                scanner.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing client socket: " + e.getMessage());
        }
    }

    public static void main(String[] arg) {
        FtpClient client = new FtpClient();

        System.out.println("FTP Client started. Available commands:");
        System.out.println("  auth <username> <password>");
        System.out.println("  upload <localFilePath>");
        System.out.println("  download <remoteFileName>");
        System.out.println("  mkdir <dirname>");
        System.out.println("  rmdir <dirname>");
        System.out.println("  mvdir <oldPath> <newPath>");
        System.out.println("  list");
        System.out.println("  cd <targetDirectory>");
        System.out.println("  exit");

        boolean isAuthenticated = false;

        while (true) {
            System.out.print("> "); // Завжди друкуємо "> " перед введенням
            String input = client.scanner.nextLine();

            String[] parts = input.split(" ", 2);
            String command = parts[0].toLowerCase();
            String args = parts.length > 1 ? parts[1] : "";

            if (command.equals("exit")) {
                break;
            }

            if (!isAuthenticated && !command.equals("auth")) {
                System.out.println("Please authenticate first using 'auth <username> <password>'.");
                continue;
            }

            switch (command) {
                case "auth":
                    String[] creds = args.split(" ");
                    if (creds.length == 2) {
                        client.authenticate(creds[0], creds[1]);
                        isAuthenticated = true;
                    } else {
                        System.out.println("Usage: auth <username> <password>");
                    }
                    break;
                case "upload":
                    if (!args.isEmpty()) {
                        client.uploadFile(args);
                    } else {
                        System.out.println("Usage: upload <localFilePath>");
                    }
                    break;
                case "download":
                    if (!args.isEmpty()) {
                        client.downloadFile(args);
                    } else {
                        System.out.println("Usage: download <remoteFileName>");
                    }
                    break;
                case "mkdir":
                    if (!args.isEmpty()) {
                        client.createDirectory(args);
                    } else {
                        System.out.println("Usage: mkdir <dirname>");
                    }
                    break;
                case "rmdir":
                    if (!args.isEmpty()) {
                        client.removeDirectory(args);
                    } else {
                        System.out.println("Usage: rmdir <dirname>");
                    }
                    break;
                case "mvdir":
                    String[] mvArgs = args.split(" ");
                    if (mvArgs.length == 2) {
                        client.moveDirectory(mvArgs[0], mvArgs[1]);
                    } else {
                        System.out.println("Usage: mvdir <oldPath> <newPath>");
                    }
                    break;
                case "list":
                    client.listDirectory();
                    break;
                case "cd":
                    if (!args.isEmpty()) {
                        client.changeDirectory(args);
                    } else {
                        System.out.println("Usage: cd <targetDirectory>");
                    }
                    break;
                default:
                    System.out.println("Unknown command.");
            }
        }

        client.close();
        System.out.println("Client disconnected.");
    }
}