/*
**LogEntry:**
```java
- String action ("UPLOAD" or "DOWNLOAD")
- String filename
- String status ("SUCCESS" or "FAILED")
- long timestamp
```

*/

package SocketProgramming;

public class LogEntry {
    private String action; // "UPLOAD" or "DOWNLOAD"
    private String filename;
    private String status; // "SUCCESS" or "FAILED"
    private long timestamp;

    public LogEntry(String action, String filename, String status, long timestamp) {
        this.action = action;
        this.filename = filename;
        this.status = status;
        this.timestamp = timestamp;
    }

    public String getAction() { return action; }
    public String getFilename() { return filename; }
    public String getStatus() { return status; }
    public long getTimestamp() { return timestamp; }
}
