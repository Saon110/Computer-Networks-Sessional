/*
**FileUploadSession:**
```java
- String fileID
- String filename
- String uploader
- long expectedSize
- List<byte[]> chunks
- int chunkSize
- boolean isPublic
- String requestID
```

*/


package SocketProgramming;

import java.util.ArrayList;
import java.util.List;

public class FileUploadSession {
    private String fileID;
    private String filename;
    private String uploader;
    private long expectedSize;
    private List<byte[]> chunks;
    private int chunkSize;
    private boolean isPublic;
    private String requestID; // nullable

    public FileUploadSession(String fileID, String filename, String uploader,
                             long expectedSize, int chunkSize,
                             boolean isPublic, String requestID) {
        this.fileID = fileID;
        this.filename = filename;
        this.uploader = uploader;
        this.expectedSize = expectedSize;
        this.chunkSize = chunkSize;
        this.isPublic = isPublic;
        this.requestID = requestID;
        this.chunks = new ArrayList<>();
    }

    public void addChunk(byte[] chunk) {
        chunks.add(chunk);
    }

    public List<byte[]> getChunks() {
        return chunks;
    }

    public long getTotalBufferSize() {
        return chunks.stream().mapToLong(chunk -> chunk.length).sum();
    }

    public String getFileID() { return fileID; }
    public String getFilename() { return filename; }
    public String getUploader() { return uploader; }
    public long getExpectedSize() { return expectedSize; }
    public int getChunkSize() { return chunkSize; }
    public boolean isPublic() { return isPublic; }
    public String getRequestID() { return requestID; }
}
