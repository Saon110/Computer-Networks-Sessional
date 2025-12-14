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
        for (FileUploadSession session : activeUploads.values()) {
            total += session.getTotalBufferSize();
        }
        return total;
    }

    public synchronized void addLog(String username, String action, String filename, String status) {
        LogEntry entry = new LogEntry(action, filename, status, System.currentTimeMillis());
        userLogs.get(username).add(entry);
    }

    public synchronized void initializeUserData(String username) {
        userFiles.putIfAbsent(username, new CopyOnWriteArrayList<>());
        userMessages.putIfAbsent(username, new CopyOnWriteArrayList<>());
        userLogs.putIfAbsent(username, new CopyOnWriteArrayList<>());
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
