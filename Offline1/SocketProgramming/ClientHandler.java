/*ClientHandler (Server-side thread)

**Fields:**
```
- Socket socket
- FileServer server
- String username (null until logged in)
- DataInputStream dis
- DataOutputStream dos
- PrintWriter out
- BufferedReader in
- boolean isRunning
```

**Lifecycle:**
1. Initialize streams from socket
2. Wait for LOGIN message
3. Validate username (check if already online)
4. If valid:
   - Create user directory
   - Add to online clients
   - Send SUCCESS
5. If invalid:
   - Send ERROR
   - Close connection
6. Enter message loop
7. Cleanup on exit*/

package SocketProgramming;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.text.SimpleDateFormat;

public class ClientHandler extends Thread {
    private Socket socket;
    private Server server;
    private String username = null;
    private DataInputStream dis;
    private DataOutputStream dos;
    private boolean isRunning = true;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        try {
            this.dis = new DataInputStream(socket.getInputStream());
            this.dos = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
            isRunning = false;
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
    @Override
    public void run() {
        try {
            // Step 2: Wait for LOGIN message
            String loginMessage = readMessage();
            if (loginMessage != null && loginMessage.startsWith("LOGIN ")) {
                String requestedUsername = loginMessage.substring(6).trim();
                // Step 3: Validate username
                if (isUsernameValid(requestedUsername)) {
                    this.username = requestedUsername;
                    // Step 4: Create user directory and add to online clients
                    File userDir = new File(Server.BASE_DIRECTORY + username);
                    if (!userDir.exists()) {
                        userDir.mkdirs();
                    }
                    server.getOnlineClients().put(username, this);
                    server.initializeUserData(username);
                    sendMessage("SUCCESS");
                    server.addLog(username, "LOGIN", "", "SUCCESS");
                } else {
                    // Step 5: Send ERROR and close connection
                    sendMessage("ERROR Username already online");
                    closeConnection();
                    return;
                }       
            } else {
                sendMessage("ERROR Invalid login message");
                closeConnection();
                return;
            }   
            // Step 6: Enter message loop
            while (isRunning) {
                String message = readMessage();
                if (message == null) {
                    break;
                }
                handleMessage(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Step 7: Cleanup on exit
            cleanup();
        }
    }   
    private boolean isUsernameValid(String username) {
        ConcurrentHashMap<String, ClientHandler> onlineClients = server.getOnlineClients();
        return !onlineClients.containsKey(username);
    }
    private void handleMessage(String message) {
        try {
            String[] parts = message.split("\\|");
            String messageType = parts[0];

            switch (messageType) {
                case "LIST_CLIENTS":
                    handleListClients();
                    break;
                case "LIST_MY_FILES":
                    handleListMyFiles();
                    break;
                case "LIST_PUBLIC_FILES":
                    handleListPublicFiles(parts.length > 1 ? parts[1] : "");
                    break;
                case "UPLOAD_REQUEST":
                    handleUploadRequest(parts);
                    break;
                case "UPLOAD_CHUNK":
                    handleUploadChunk(parts);
                    break;
                case "UPLOAD_CANCEL":
                    handleUploadCancel(parts[1]);
                    break;
                case "UPLOAD_COMPLETE":
                    handleUploadComplete(parts[1]);
                    break;
                case "DOWNLOAD_REQUEST":
                    handleDownloadRequest(parts[1]);
                    break;
                case "FILE_REQUEST":
                    handleFileRequest(parts[1], parts[2]);
                    break;
                case "VIEW_MESSAGES":
                    handleViewMessages();
                    break;
                case "VIEW_LOGS":
                    handleViewLogs();
                    break;
                case "LOGOUT":
                    isRunning = false;
                    break;
                default:
                    try { sendMessage("ERROR|Unknown command"); } catch (IOException e) { e.printStackTrace(); }
            }
        } catch (Exception e) {
            e.printStackTrace();
            try { sendMessage("ERROR|" + e.getMessage()); } catch (IOException ioe) { ioe.printStackTrace(); }
        }
    }

    private void handleListClients() {
        StringBuilder response = new StringBuilder("CLIENTS_RESPONSE");
        
        // Get all users who have ever logged in (have files/logs data)
        Set<String> allUsers = new HashSet<>();
        allUsers.addAll(server.getUserFiles().keySet());
        allUsers.addAll(server.getUserLogs().keySet());
        
        // Get online users
        Set<String> onlineUsers = server.getOnlineClients().keySet();
        
        response.append("|").append(allUsers.size());
        
        for (String user : allUsers) {
            String status = onlineUsers.contains(user) ? "ONLINE" : "OFFLINE";
            response.append("|").append(user).append("|").append(status);
        }
        
        try { sendMessage(response.toString()); } catch (IOException e) { e.printStackTrace(); }
    }

    private void handleListMyFiles() {
        List<FileMetadata> files = server.getUserFiles().get(username);
        StringBuilder response = new StringBuilder("MY_FILES_RESPONSE");
        response.append("|").append(files.size());
        
        for (FileMetadata fm : files) {
            response.append("|").append(fm.getFileID())
                   .append("|").append(fm.getFilename())
                   .append("|").append(fm.getFileSize())
                   .append("|").append(fm.isPublic() ? "PUBLIC" : "PRIVATE")
                   .append("|").append(fm.getUploadTimestamp());
        }
        
        try { sendMessage(response.toString()); } catch (IOException e) { e.printStackTrace(); }
    }

    private void handleListPublicFiles(String ownerFilter) {
        StringBuilder response = new StringBuilder("PUBLIC_FILES_RESPONSE");
        List<String> fileInfoList = new ArrayList<>();
        
        for (String user : server.getUserFiles().keySet()) {
            if (user.equals(username)) continue;
            if (!ownerFilter.isEmpty() && !user.equals(ownerFilter)) continue;
            
            List<FileMetadata> files = server.getUserFiles().get(user);
            for (FileMetadata fm : files) {
                if (fm.isPublic()) {
                    String info = fm.getOwner() + "|" + fm.getFileID() + "|" + 
                                 fm.getFilename() + "|" + fm.getFileSize() + "|" + 
                                 fm.getUploadTimestamp();
                    fileInfoList.add(info);
                }
            }
        }
        
        response.append("|").append(fileInfoList.size());
        for (String info : fileInfoList) {
            response.append("|").append(info);
        }
        
        try { sendMessage(response.toString()); } catch (IOException e) { e.printStackTrace(); }
    }

    private void handleUploadRequest(String[] parts) {
        try {
            String filename = parts[1];
            long fileSize = Long.parseLong(parts[2]);
            boolean isPublic = parts[3].equals("PUBLIC");
            String requestID = parts.length > 4 && !parts[4].isEmpty() ? parts[4] : null;
            
            // Validate request ID if provided
            if (requestID != null && !server.getFileRequests().containsKey(requestID)) {
                try { sendMessage("UPLOAD_DENIED|Invalid request ID"); } catch (IOException e) { e.printStackTrace(); }
                return;
            }
            
            // Force public if uploaded for request
            if (requestID != null) {
                isPublic = true;
            }
            
            // Check buffer capacity
            long currentBuffer = server.getCurrentBufferSize();
            if (currentBuffer + fileSize > Server.MAX_BUFFER_SIZE) {
                try { sendMessage("UPLOAD_DENIED|Server buffer full. Try again later."); } catch (IOException e) { e.printStackTrace(); }
                return;
            }
            
            // Generate fileID and chunk size
            String fileID = UUID.randomUUID().toString();
            Random random = new Random();
            int chunkSize = Server.MIN_CHUNK_SIZE + 
                random.nextInt(Server.MAX_CHUNK_SIZE - Server.MIN_CHUNK_SIZE + 1);
            
            // Create upload session
            FileUploadSession session = new FileUploadSession(
                fileID, filename, username, fileSize, chunkSize, isPublic, requestID
            );
            
            server.getActiveUploads().put(fileID, session);
            
            // Send approval
            try { sendMessage("UPLOAD_APPROVED|" + fileID + "|" + chunkSize); } catch (IOException e) { e.printStackTrace(); }
        } catch (Exception e) {
            try { sendMessage("UPLOAD_DENIED|" + e.getMessage()); } catch (IOException ioe) { ioe.printStackTrace(); }
        }
    }

    private void handleUploadChunk(String[] parts) throws IOException {
        String fileID = parts[1];
        int chunkNumber = Integer.parseInt(parts[2]);
        int chunkSize = Integer.parseInt(parts[3]);
        
        // Read chunk data (binary)
        byte[] chunk = new byte[chunkSize];
        dis.readFully(chunk);
        
        // Get session
        FileUploadSession session = server.getActiveUploads().get(fileID);
        if (session == null) {
            try { sendMessage("CHUNK_ERROR|Invalid file ID"); } catch (IOException e) { e.printStackTrace(); }
            return;
        }
        
        // Store chunk
        session.addChunk(chunk);
        
        // Send ACK
        try { sendMessage("CHUNK_ACK|" + chunkNumber); } catch (IOException e) { e.printStackTrace(); }
    }
    
    private void handleUploadCancel(String fileID) {
        FileUploadSession session = server.getActiveUploads().get(fileID);
        if (session != null) {
            server.getActiveUploads().remove(fileID);
            server.addLog(username, "UPLOAD", session.getFilename(), "CANCELLED");
            System.out.println("Upload cancelled by client: " + fileID);
        }
    }

    private void handleUploadComplete(String fileID) throws IOException {
        FileUploadSession session = server.getActiveUploads().get(fileID);
        if (session == null) {
            try { sendMessage("UPLOAD_ERROR|Invalid file ID"); } catch (IOException e) { e.printStackTrace(); }
            return;
        }
        
        // Validate size
        long receivedSize = session.getTotalBufferSize();
        
        if (receivedSize != session.getExpectedSize()) {
            server.getActiveUploads().remove(fileID);
            try { sendMessage("UPLOAD_ERROR|Size mismatch"); } catch (IOException e) { e.printStackTrace(); }
            server.addLog(username, "UPLOAD", session.getFilename(), "FAILED");
            return;
        }
        
        // Write file to disk
        File userFilesDir = new File(Server.BASE_DIRECTORY + username + "/files/");
        if (!userFilesDir.exists()) {
            userFilesDir.mkdirs();
        }
        
        String filePath = userFilesDir.getPath() + "/" + fileID + "_" + session.getFilename();
        FileOutputStream fos = new FileOutputStream(filePath);
        
        for (byte[] chunk : session.getChunks()) {
            fos.write(chunk);
        }
        fos.close();
        
        // Create metadata
        FileMetadata metadata = new FileMetadata(
            fileID, session.getFilename(), username, receivedSize, 
            session.isPublic(), session.getRequestID(), 
            System.currentTimeMillis(), filePath
        );
        
        server.getUserFiles().get(username).add(metadata);
        
        // Save metadata to disk for persistence
        saveFileMetadata(fileID, session.getFilename(), session.isPublic());
        
        // If uploaded for request, notify requester
        if (session.getRequestID() != null) {
            FileRequest request = server.getFileRequests().get(session.getRequestID());
            if (request != null) {
                Message msg = new Message(
                    "FILE_UPLOADED",
                    username,
                    "File '" + session.getFilename() + "' uploaded for request: " + request.getDescription(),
                    System.currentTimeMillis(),
                    false
                );
                
                server.getUserMessages().get(request.getRequester()).add(msg);
            }
        }
        
        // Remove from active uploads
        server.getActiveUploads().remove(fileID);
        
        // Log success
        server.addLog(username, "UPLOAD", session.getFilename(), "SUCCESS");
        
        try { sendMessage("UPLOAD_SUCCESS|" + fileID); } catch (IOException e) { e.printStackTrace(); }
    }

    private void handleDownloadRequest(String fileID) throws IOException {
        // Find file
        FileMetadata metadata = findFile(fileID);
        
        if (metadata == null) {
            try { sendMessage("DOWNLOAD_ERROR|File not found"); } catch (IOException e) { e.printStackTrace(); }
            return;
        }
        
        // Check permission
        if (!metadata.getOwner().equals(username) && !metadata.isPublic()) {
            try { sendMessage("DOWNLOAD_ERROR|Permission denied"); } catch (IOException e) { e.printStackTrace(); }
            return;
        }
        
        // Get file
        File file = new File(metadata.getFilePath());
        if (!file.exists()) {
            try { sendMessage("DOWNLOAD_ERROR|File not found on disk"); } catch (IOException e) { e.printStackTrace(); }
            return;
        }
        
        long fileSize = file.length();
        int numChunks = (int) ((fileSize + Server.MAX_CHUNK_SIZE - 1) / Server.MAX_CHUNK_SIZE);
        
        // Send download start using writeUTF for proper synchronization
        sendMessage(String.format("DOWNLOAD_START|%s|%d|%d",
            metadata.getFilename(), fileSize, numChunks));
        
        // Send chunks
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[Server.MAX_CHUNK_SIZE];
        int chunkNumber = 0;
        int bytesRead;
        
        while ((bytesRead = fis.read(buffer)) > 0) {
            // Send chunk header using writeUTF for proper synchronization
            sendMessage(String.format("DOWNLOAD_CHUNK|%d|%d", chunkNumber, bytesRead));
            
            // Send chunk data (raw bytes)
            dos.write(buffer, 0, bytesRead);
            dos.flush();
            
            chunkNumber++;
        }
        
        fis.close();
        
        // Send completion using writeUTF for proper synchronization
        sendMessage("DOWNLOAD_COMPLETE");
        
        // Log download
        server.addLog(username, "DOWNLOAD", metadata.getFilename(), "SUCCESS");
        if (!metadata.getOwner().equals(username)) {
            server.addLog(metadata.getOwner(), "DOWNLOAD", metadata.getFilename(), "SUCCESS");
        }
    }

    private FileMetadata findFile(String fileID) {
        // Search in own files
        List<FileMetadata> myFiles = server.getUserFiles().get(username);
        if (myFiles != null) {
            for (FileMetadata fm : myFiles) {
                if (fm.getFileID().equals(fileID)) {
                    return fm;
                }
            }
        }
        
        // Search in other users' public files
        for (String user : server.getUserFiles().keySet()) {
            if (user.equals(username)) continue;
            
            List<FileMetadata> files = server.getUserFiles().get(user);
            for (FileMetadata fm : files) {
                if (fm.getFileID().equals(fileID) && fm.isPublic()) {
                    return fm;
                }
            }
        }
        
        return null;
    }

    private void handleFileRequest(String description, String recipient) {
        // Generate request ID
        String requestID = "REQ_" + System.currentTimeMillis();
        
        // Create request object
        FileRequest request = new FileRequest(
            requestID, username, description, recipient, System.currentTimeMillis()
        );
        
        server.getFileRequests().put(requestID, request);
        
        // Create message
        Message msg = new Message(
            "FILE_REQUEST",
            username,
            "Request ID: " + requestID + " | " + description,
            System.currentTimeMillis(),
            false
        );
        
        // Deliver message
        if (recipient.equals("ALL")) {
            // Broadcast to all users
            for (String user : server.getUserMessages().keySet()) {
                if (!user.equals(username)) {
                    server.getUserMessages().get(user).add(msg);
                }
            }
        } else {
            // Send to specific user
            if (server.getUserMessages().containsKey(recipient)) {
                server.getUserMessages().get(recipient).add(msg);
            }
        }
        
        try { sendMessage("FILE_REQUEST_SENT|" + requestID); } catch (IOException e) { e.printStackTrace(); }
    }

    private void handleViewMessages() {
        List<Message> messages = server.getUserMessages().get(username);
        List<Message> unread = new ArrayList<>();
        
        for (Message msg : messages) {
            if (!msg.isRead()) {
                unread.add(msg);
            }
        }
        
        StringBuilder response = new StringBuilder("MESSAGES_RESPONSE");
        response.append("|").append(unread.size());
        
        for (Message msg : unread) {
            // Extract description from content for better display
            String displayContent = msg.getContent();
            String formatted = String.format("%s from %s: %s",
                msg.getMessageType(), msg.getFrom(), displayContent);
            response.append("|").append(formatted);
            msg.setRead(true);  // Mark as read
        }
        
        try { sendMessage(response.toString()); } catch (IOException e) { e.printStackTrace(); }
    }

    private void handleViewLogs() {
        List<LogEntry> logs = server.getUserLogs().get(username);
        
        // Filter out LOGIN logs, show only UPLOAD and DOWNLOAD
        List<LogEntry> filteredLogs = new ArrayList<>();
        for (LogEntry log : logs) {
            if (log.getAction().equals("UPLOAD") || log.getAction().equals("DOWNLOAD")) {
                filteredLogs.add(log);
            }
        }
        
        StringBuilder response = new StringBuilder("LOGS_RESPONSE");
        response.append("|").append(filteredLogs.size());
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        
        for (LogEntry log : filteredLogs) {
            // Format without pipes to avoid splitting issues - use semicolon as internal separator
            String formatted = String.format("%s ; %s ; %s ; %s",
                sdf.format(new Date(log.getTimestamp())),
                log.getAction(),
                log.getFilename(),
                log.getStatus());
            response.append("|").append(formatted);
        }
        
        try { sendMessage(response.toString()); } catch (IOException e) { e.printStackTrace(); }
    }
    
    private void saveFileMetadata(String fileID, String filename, boolean isPublic) {
        try {
            File metadataFile = new File(Server.BASE_DIRECTORY + username + "/files/" + fileID + "_" + filename + ".meta");
            FileWriter fw = new FileWriter(metadataFile);
            PrintWriter pw = new PrintWriter(fw);
            pw.println(isPublic ? "public" : "private");
            pw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void closeConnection() {
        isRunning = false;
        try {
            socket.close();         
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void cleanup() {
        if (username != null) {
            // Remove incomplete uploads
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, FileUploadSession> entry : server.getActiveUploads().entrySet()) {
                if (entry.getValue().getUploader().equals(username)) {
                    toRemove.add(entry.getKey());
                    server.addLog(username, "UPLOAD", entry.getValue().getFilename(), "FAILED - Disconnected");
                }
            }
            toRemove.forEach(server.getActiveUploads()::remove);
            
            // Remove from online clients
            server.getOnlineClients().remove(username);
            
            // Don't delete user directory or data - keep files and logs while server runs
            // User data persists for the lifetime of the server
        }
        
        try {
            if (dis != null) dis.close();
            if (dos != null) dos.close();
            if (dis != null) dis.close();
            if (dos != null) dos.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void deleteDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            dir.delete();
        }
    }
}
