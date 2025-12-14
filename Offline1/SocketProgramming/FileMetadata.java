/*
**FileMetadata:**
```java
- String fileID
- String filename
- String owner
- long fileSize
- boolean isPublic
- String requestID (nullable)
- long uploadTimestamp
- String filePath
```
*/

package SocketProgramming;

public class FileMetadata {
    private String fileID;
    private String filename;
    private String owner;
    private long fileSize;
    private boolean isPublic;
    private String requestID; // nullable
    private long uploadTimestamp;
    private String filePath;

    public FileMetadata(String fileID, String filename, String owner, long fileSize,
                        boolean isPublic, String requestID, long uploadTimestamp, String filePath) {
        this.fileID = fileID;
        this.filename = filename;
        this.owner = owner;
        this.fileSize = fileSize;
        this.isPublic = isPublic;
        this.requestID = requestID;
        this.uploadTimestamp = uploadTimestamp;
        this.filePath = filePath;
    }

    public String getFileID() { return fileID; }
    public String getFilename() { return filename; }
    public String getOwner() { return owner; }
    public long getFileSize() { return fileSize; }
    public boolean isPublic() { return isPublic; }
    public String getRequestID() { return requestID; }
    public long getUploadTimestamp() { return uploadTimestamp; }
    public String getFilePath() { return filePath; }
}