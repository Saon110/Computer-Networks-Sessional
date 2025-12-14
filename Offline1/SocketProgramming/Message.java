/*
**Message:**
```java
- String messageType ("FILE_REQUEST" or "FILE_UPLOADED")
- String from
- String content
- long timestamp
- boolean isRead
```
*/

package SocketProgramming;

public class Message {
    private String messageType; // "FILE_REQUEST" or "FILE_UPLOADED"
    private String from;
    private String content;
    private long timestamp;
    private boolean isRead;

    public Message(String messageType, String from, String content, long timestamp, boolean isRead) {
        this.messageType = messageType;
        this.from = from;
        this.content = content;
        this.timestamp = timestamp;
        this.isRead = isRead;
    }

    public String getMessageType() { return messageType; }
    public String getFrom() { return from; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { this.isRead = read; }
}

