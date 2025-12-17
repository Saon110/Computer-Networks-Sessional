/* Server Setup (FileServer.java)

**Key Components:**
```
- ServerSocket on port 5000
- ConcurrentHashMap<String, ClientHandler> onlineClients
- Map<String, FileUploadSession> activeUploads
- Map<String, FileRequest> fileRequests
- Configuration constants:
  - MAX_BUFFER_SIZE = 10 MB
  - MIN_CHUNK_SIZE = 32 KB
  - MAX_CHUNK_SIZE = 512 KB
  - BASE_DIRECTORY = "./server_files/"
```

**Main Logic:**
1. Create ServerSocket on port 5000
2. Initialize data structures
3. Create BASE_DIRECTORY
4. Infinite loop:
   - Accept connection
   - Create ClientHandler thread
   - Start thread

### ClientHandler (Server-side thread)

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
7. Cleanup on exit
*/

package SocketProgramming;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Server {
    public static final int PORT = 5000;
    public static final int MAX_BUFFER_SIZE = 10 * 1024 * 1024; // 10 MB
    public static final int MIN_CHUNK_SIZE = 32 * 1024; // 32 KB
    public static final int MAX_CHUNK_SIZE = 512 * 1024; // 512 KB
    public static final String BASE_DIRECTORY = "./server_files/";

    private ConcurrentHashMap<String, ClientHandler> onlineClients;
    private ConcurrentHashMap<String, FileUploadSession> activeUploads;
    private ConcurrentHashMap<String, FileRequest> fileRequests;
    private ConcurrentHashMap<String, List<FileMetadata>> userFiles;
    private ConcurrentHashMap<String, List<Message>> userMessages;
    private ConcurrentHashMap<String, List<LogEntry>> userLogs;

    public Server() {
        onlineClients = new ConcurrentHashMap<>();
        activeUploads = new ConcurrentHashMap<>();
        fileRequests = new ConcurrentHashMap<>();
        userFiles = new ConcurrentHashMap<>();
        userMessages = new ConcurrentHashMap<>();
        userLogs = new ConcurrentHashMap<>();

        File baseDir = new File(BASE_DIRECTORY);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                clientHandler.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ConcurrentHashMap<String, ClientHandler> getOnlineClients() {
        return onlineClients;
    }

    public ConcurrentHashMap<String, FileUploadSession> getActiveUploads() {
        return activeUploads;
    }

    public ConcurrentHashMap<String, FileRequest> getFileRequests() {
        return fileRequests;
    }

    public ConcurrentHashMap<String, List<FileMetadata>> getUserFiles() {
        return userFiles;
    }

    public ConcurrentHashMap<String, List<Message>> getUserMessages() {
        return userMessages;
    }

    public ConcurrentHashMap<String, List<LogEntry>> getUserLogs() {
        return userLogs;
    }

    public synchronized long getCurrentBufferSize() {
        long total = 0;
        
        // Count chunks in active uploads (being uploaded right now)
        for (FileUploadSession session : activeUploads.values()) {
            total += session.getTotalBufferSize();
        }
        
        // Count all completed files stored on server
        for (List<FileMetadata> userFileList : userFiles.values()) {
            for (FileMetadata file : userFileList) {
                total += file.getFileSize();
            }
        }
        
        return total;
    }

    public synchronized void addLog(String username, String action, String filename, String status) {
        LogEntry entry = new LogEntry(action, filename, status, System.currentTimeMillis());
        userLogs.get(username).add(entry);
        
        // Write to log file on disk
        writeLogToDisk(username, entry);
    }
    
    private void writeLogToDisk(String username, LogEntry entry) {
        try {
            File userDir = new File(BASE_DIRECTORY + username);
            if (!userDir.exists()) {
                userDir.mkdirs();
            }
            
            File logFile = new File(userDir, "log.txt");
            FileWriter fw = new FileWriter(logFile, true); // append mode
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter pw = new PrintWriter(bw);
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String logLine = String.format("%s | %s | %s | %s",
                sdf.format(new Date(entry.getTimestamp())),
                entry.getAction(),
                entry.getFilename(),
                entry.getStatus());
            
            pw.println(logLine);
            pw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void initializeUserData(String username) {
        userFiles.putIfAbsent(username, new CopyOnWriteArrayList<>());
        userMessages.putIfAbsent(username, new CopyOnWriteArrayList<>());
        userLogs.putIfAbsent(username, new CopyOnWriteArrayList<>());
        
        // Load existing logs from disk if available
        loadLogsFromDisk(username);
        
        // Load existing files from disk if available
        loadFilesFromDisk(username);
    }
    
    private void loadFilesFromDisk(String username) {
        try {
            File userFilesDir = new File(BASE_DIRECTORY + username + "/files");
            if (!userFilesDir.exists()) {
                return;
            }
            
            File[] files = userFilesDir.listFiles();
            if (files == null) {
                return;
            }
            
            for (File file : files) {
                if (file.isFile()) {
                    // Parse filename format: fileID_originalName
                    String filename = file.getName();
                    int underscoreIndex = filename.indexOf('_');
                    
                    if (underscoreIndex > 0) {
                        String fileID = filename.substring(0, underscoreIndex);
                        String originalName = filename.substring(underscoreIndex + 1);
                        
                        // Check if this file is already in memory
                        boolean exists = false;
                        for (FileMetadata fm : userFiles.get(username)) {
                            if (fm.getFileID().equals(fileID)) {
                                exists = true;
                                break;
                            }
                        }
                        
                        if (!exists) {
                            // Create FileMetadata - check metadata file for public/private status
                            boolean isPublic = checkIfFileIsPublic(username, fileID, originalName);
                            FileMetadata metadata = new FileMetadata(
                                fileID,
                                originalName,
                                username,
                                file.length(),
                                isPublic,
                                null, // requestID unknown from disk
                                file.lastModified(),
                                file.getAbsolutePath()
                            );
                            userFiles.get(username).add(metadata);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private boolean checkIfFileIsPublic(String username, String fileID, String filename) {
        // Check if metadata file exists
        File metadataFile = new File(BASE_DIRECTORY + username + "/files/" + fileID + "_" + filename + ".meta");
        if (metadataFile.exists()) {
            try {
                BufferedReader br = new BufferedReader(new FileReader(metadataFile));
                String line = br.readLine();
                br.close();
                return "public".equals(line);
            } catch (IOException e) {
                return false;
            }
        }
        
        return false; // default to private if no metadata file
    }
    
    private void loadLogsFromDisk(String username) {
        try {
            File logFile = new File(BASE_DIRECTORY + username + "/log.txt");
            if (!logFile.exists()) {
                return;
            }
            
            BufferedReader br = new BufferedReader(new FileReader(logFile));
            String line;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(" \\| ");
                if (parts.length == 4) {
                    try {
                        long timestamp = sdf.parse(parts[0]).getTime();
                        String action = parts[1];
                        String filename = parts[2];
                        String status = parts[3];
                        
                        LogEntry entry = new LogEntry(action, filename, status, timestamp);
                        userLogs.get(username).add(entry);
                    } catch (Exception e) {
                        // Skip malformed log lines
                    }
                }
            }
            br.close();
        } catch (IOException e) {
            // Log file doesn't exist or can't be read - that's fine
        }
    }

    public synchronized void removeUserData(String username) {
        userFiles.remove(username);
        userMessages.remove(username);
        userLogs.remove(username);
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }
}
