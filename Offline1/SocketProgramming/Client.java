/*

### Client Application (Client.java)

**Main Flow:**
1. Connect to server (host, port)
2. Prompt for username
3. Send LOGIN message
4. Wait for response
5. If SUCCESS: show menu
6. If ERROR: exit

**Main Menu:**
```
1. List all clients
2. List my files
3. List public files
4. Upload file
5. Download file
6. Send file request
7. View messages
8. View logs
9. Logout
```

---

## Phase 2: Data Structures (In-Memory)

### Server Data Structures

```java
// All connected users
ConcurrentHashMap<String, ClientHandler> onlineClients;

// File metadata per user
ConcurrentHashMap<String, List<FileMetadata>> userFiles;

// Active upload sessions
ConcurrentHashMap<String, FileUploadSession> activeUploads;

// File requests
ConcurrentHashMap<String, FileRequest> fileRequests;

// Messages per user
ConcurrentHashMap<String, List<Message>> userMessages;

// Logs per user
ConcurrentHashMap<String, List<LogEntry>> userLogs;
```

*/

package SocketProgramming;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private Scanner scanner;
    private String username;

    public Client(String host, int port) {
        try {
            this.socket = new Socket(host, port);
            this.dis = new DataInputStream(socket.getInputStream());
            this.dos = new DataOutputStream(socket.getOutputStream());
            this.scanner = new Scanner(System.in);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // Helper method to send text message
    private void sendMessage(String message) throws IOException {
        dos.writeUTF(message);
        dos.flush();
    }
    
    // Helper method to read text message
    private String readMessage() throws IOException {
        return dis.readUTF();
    }

    public void start() {
        try {
            // Step 2: Prompt for username
            System.out.print("Enter username: ");
            String inputUsername = scanner.nextLine().trim();

            // Step 3: Send LOGIN message
            sendMessage("LOGIN " + inputUsername);

            // Step 4: Wait for response
            String response = readMessage();
            if ("SUCCESS".equals(response)) {
                this.username = inputUsername;
                System.out.println("Login successful!");
                showMenu();
            } else {
                System.out.println("Login failed: " + response);
                closeConnection();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showMenu() {
        while (true) {
            try {
                System.out.println("\n=== Main Menu ===");
                System.out.println("1. List all clients");
                System.out.println("2. List my files");
                System.out.println("3. List public files");
                System.out.println("4. Upload file");
                System.out.println("5. Download file");
                System.out.println("6. Send file request");
                System.out.println("7. View messages");
                System.out.println("8. View logs");
                System.out.println("9. Logout");
                System.out.print("Choose an option: ");

                String choice = scanner.nextLine().trim();
                
                switch (choice) {
                    case "1":
                        listClients();
                        break;
                    case "2":
                        listMyFiles();
                        break;
                    case "3":
                        listPublicFiles();
                        break;
                    case "4":
                        uploadFile();
                        break;
                    case "5":
                        downloadFile();
                        break;
                    case "6":
                        sendFileRequest();
                        break;
                    case "7":
                        viewMessages();
                        break;
                    case "8":
                        viewLogs();
                        break;
                    case "9":
                        System.out.println("Logging out...");
            sendMessage("LOGOUT");
                        closeConnection();
                        return;
                    default:
                        System.out.println("Invalid choice. Please try again.");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void listClients() throws IOException {
            sendMessage("LIST_CLIENTS");
        
        String response = readMessage();
        String[] parts = response.split("\\|");
        
        if (parts[0].equals("CLIENTS_RESPONSE")) {
            int count = Integer.parseInt(parts[1]);
            System.out.println("\n=== Client List ===");
            System.out.println("Total clients: " + count);
            
            for (int i = 0; i < count; i++) {
                String clientUsername = parts[2 + i*2];
                String status = parts[3 + i*2];
                System.out.println("  - " + clientUsername + " (" + status + ")");
            }
        }
    }

    private void listMyFiles() throws IOException {
            sendMessage("LIST_MY_FILES");
        
        String response = readMessage();
        String[] parts = response.split("\\|");
        
        if (parts[0].equals("MY_FILES_RESPONSE")) {
            int count = Integer.parseInt(parts[1]);
            System.out.println("\n=== My Files ===");
            System.out.println("Total files: " + count);
            
            if (count > 0) {
                int idx = 2;
                for (int i = 0; i < count; i++) {
                    String fileID = parts[idx++];
                    String filename = parts[idx++];
                    String size = parts[idx++];
                    String access = parts[idx++];
                    String timestamp = parts[idx++];
                    System.out.println("  " + (i+1) + ". " + filename);
                    System.out.println("     ID: " + fileID);
                    System.out.println("     Size: " + size + " bytes");
                    System.out.println("     Access: " + access);
                }
            }
        }
    }

    private void listPublicFiles() throws IOException {
        System.out.print("Enter owner filter (or press Enter for all): ");
        String ownerFilter = scanner.nextLine().trim();
        
            sendMessage("LIST_PUBLIC_FILES|" + ownerFilter);
        
        String response = readMessage();
        String[] parts = response.split("\\|");
        
        if (parts[0].equals("PUBLIC_FILES_RESPONSE")) {
            int count = Integer.parseInt(parts[1]);
            System.out.println("\n=== Public Files ===");
            System.out.println("Total files: " + count);
            
            if (count > 0) {
                int idx = 2;
                for (int i = 0; i < count; i++) {
                    String owner = parts[idx++];
                    String fileID = parts[idx++];
                    String filename = parts[idx++];
                    String size = parts[idx++];
                    String timestamp = parts[idx++];
                    System.out.println("  " + (i+1) + ". " + filename + " (by " + owner + ")");
                    System.out.println("     ID: " + fileID);
                    System.out.println("     Size: " + size + " bytes");
                }
            }
        }
    }

    private void uploadFile() throws IOException {
        System.out.print("Enter file path: ");
        String filePath = scanner.nextLine().trim();
        
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            System.out.println("File not found!");
            return;
        }
        
        System.out.print("Make file public? (yes/no): ");
        String publicChoice = scanner.nextLine().trim();
        boolean isPublic = publicChoice.equalsIgnoreCase("yes");
        
        System.out.print("Enter request ID (or press Enter to skip): ");
        String requestID = scanner.nextLine().trim();
        
        // Send upload request
        String message;
        if (requestID.isEmpty()) {
            message = String.format("UPLOAD_REQUEST|%s|%d|%s",
                file.getName(),
                file.length(),
                isPublic ? "PUBLIC" : "PRIVATE");
        } else {
            message = String.format("UPLOAD_REQUEST|%s|%d|%s|%s",
                file.getName(),
                file.length(),
                isPublic ? "PUBLIC" : "PRIVATE",
                requestID);
        }
            sendMessage(message);
        
        // Wait for response
        String response = readMessage();
        String[] parts = response.split("\\|");
        
        if (parts[0].equals("UPLOAD_DENIED")) {
            System.out.println("Upload denied: " + parts[1]);
            return;
        }
        
        if (parts[0].equals("UPLOAD_APPROVED")) {
            String fileID = parts[1];
            int chunkSize = Integer.parseInt(parts[2]);
            System.out.println("\nServer approved upload:");
            System.out.println("  FileID: " + fileID);
            System.out.println("  Chunk size: " + chunkSize + " bytes");
            
            // Ask for confirmation
            System.out.print("Confirm upload? (yes/no): ");
            String confirm = scanner.nextLine().trim();
            
            if (!confirm.equalsIgnoreCase("yes")) {
                System.out.println("Upload cancelled.");
                    sendMessage("UPLOAD_CANCEL|" + fileID);
                return;
            }
            
            // Start chunk upload
            System.out.println("Starting upload...");
            uploadChunks(file, fileID, chunkSize);
        }
    }

    private void uploadChunks(File file, String fileID, int chunkSize) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[chunkSize];
        int chunkNumber = 0;
        int bytesRead;
        
        System.out.println("Starting upload...");
        
        while ((bytesRead = fis.read(buffer)) > 0) {
            // Send chunk header (text)
            String header = String.format("UPLOAD_CHUNK|%s|%d|%d",
                fileID, chunkNumber, bytesRead);
            sendMessage(header);
            
            // Send chunk data (binary)
            dos.write(buffer, 0, bytesRead);
            dos.flush();
            
            // Wait for ACK
            String ack = readMessage();
            String[] parts = ack.split("\\|");
            
            if (!parts[0].equals("CHUNK_ACK")) {
                System.out.println("Upload failed at chunk " + chunkNumber);
                fis.close();
                return;
            }
            
            System.out.println("Chunk " + chunkNumber + " uploaded (" + bytesRead + " bytes)");
            chunkNumber++;
        }
        
        fis.close();
        
        // Send completion message
            sendMessage("UPLOAD_COMPLETE|" + fileID);
        
        // Wait for final response
        String response = readMessage();
        String[] parts = response.split("\\|");
        
        if (parts[0].equals("UPLOAD_SUCCESS")) {
            System.out.println("File uploaded successfully!");
        } else {
            System.out.println("Upload failed: " + parts[1]);
        }
    }

    private void downloadFile() throws IOException {
        System.out.print("Enter file ID: ");
        String fileID = scanner.nextLine().trim();
        
        System.out.print("Save as (filename): ");
        String saveAs = scanner.nextLine().trim();
        
        // Send download request
            sendMessage("DOWNLOAD_REQUEST|" + fileID);
        
        // Wait for response
        String response = readMessage();
        String[] parts = response.split("\\|");
        
        if (parts[0].equals("DOWNLOAD_ERROR")) {
            System.out.println("Download failed: " + parts[1]);
            return;
        }
        
        if (parts[0].equals("DOWNLOAD_START")) {
            String filename = parts[1];
            long fileSize = Long.parseLong(parts[2]);
            int numChunks = Integer.parseInt(parts[3]);
            
            System.out.println("Downloading: " + filename);
            System.out.println("Size: " + fileSize + " bytes");
            System.out.println("Chunks: " + numChunks);
            
            receiveFile(saveAs, numChunks);
        }
    }

    private void receiveFile(String saveAs, int numChunks) throws IOException {
        FileOutputStream fos = new FileOutputStream(saveAs);
        
        for (int i = 0; i < numChunks; i++) {
            // Read chunk header
            String header = readMessage();
            String[] parts = header.split("\\|");
            
            int chunkNumber = Integer.parseInt(parts[1]);
            int chunkSize = Integer.parseInt(parts[2]);
            
            // Read chunk data
            byte[] buffer = new byte[chunkSize];
            dis.readFully(buffer);
            
            fos.write(buffer);
            
            System.out.println("Received chunk " + chunkNumber + " (" + chunkSize + " bytes)");
        }
        
        fos.close();
        
        // Wait for completion message
        String completion = readMessage();
        if (completion.startsWith("DOWNLOAD_COMPLETE")) {
            System.out.println("Download complete! File saved as: " + saveAs);
        }
    }

    private void sendFileRequest() throws IOException {
        System.out.print("Enter file description: ");
        String description = scanner.nextLine().trim();
        
        System.out.print("Enter recipient (username or ALL): ");
        String recipient = scanner.nextLine().trim();
        
            sendMessage("FILE_REQUEST|" + description + "|" + recipient);
        
        String response = readMessage();
        String[] parts = response.split("\\|");
        
        if (parts[0].equals("FILE_REQUEST_SENT")) {
            System.out.println("Request sent! Request ID: " + parts[1]);
            System.out.println("Users can upload files with this request ID.");
        }
    }

    private void viewMessages() throws IOException {
            sendMessage("VIEW_MESSAGES");
        
        String response = readMessage();
        String[] parts = response.split("\\|");
        
        if (parts[0].equals("MESSAGES_RESPONSE")) {
            int count = Integer.parseInt(parts[1]);
            System.out.println("\n=== Messages (" + count + " unread) ===");
            
            if (count > 0) {
                for (int i = 0; i < count; i++) {
                    System.out.println((i+1) + ". " + parts[2+i]);
                }
            } else {
                System.out.println("No unread messages.");
            }
        }
    }

    private void viewLogs() throws IOException {
            sendMessage("VIEW_LOGS");
        
        String response = readMessage();
        String[] parts = response.split("\\|");
        
        if (parts[0].equals("LOGS_RESPONSE")) {
            int count = Integer.parseInt(parts[1]);
            System.out.println("\n=== Activity Logs ===");
            
            if (count > 0) {
                for (int i = 0; i < count; i++) {
                    System.out.println(parts[2+i]);
                }
            } else {
                System.out.println("No activity logs.");
            }
        }
    }

    private void closeConnection() {
        try {
            if (scanner != null) scanner.close();
            if (dis != null) dis.close();
            if (dos != null) dos.close();
            if (dis != null) dis.close();
            if (dos != null) dos.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Client client = new Client("localhost", 5000);
        client.start();
    }
}
