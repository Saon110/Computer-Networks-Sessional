/*
**FileRequest:**
```java
- String requestID
- String requester
- String description
- String recipient (username or "ALL")
- long timestamp
```
*/

package SocketProgramming;

public class FileRequest {
    private String requestID;
    private String requester;
    private String description;
    private String recipient; // username or "ALL"
    private long timestamp;

    public FileRequest(String requestID, String requester, String description,
                       String recipient, long timestamp) {
        this.requestID = requestID;
        this.requester = requester;
        this.description = description;
        this.recipient = recipient;
        this.timestamp = timestamp;
    }

    public String getRequestID() { return requestID; }
    public String getRequester() { return requester; }
    public String getDescription() { return description; }
    public String getRecipient() { return recipient; }
    public long getTimestamp() { return timestamp; }
}