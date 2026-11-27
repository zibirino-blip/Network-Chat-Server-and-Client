package shared;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a chat message passed between server and clients.
 * Uses a simple type-tagged protocol so both sides can parse.
 *
 * Types:
 *   LOGIN    – username:password
 *   REGISTER – username:password
 *   BROADCAST – message text (sent to all)
 *   DM       – target:message  (private direct message)
 *   LIST     – request list of online users
 *   SERVER   – server → client notification
 *   QUIT     – client disconnecting
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Type {
        LOGIN, REGISTER, BROADCAST, DM, LIST, SERVER, QUIT, ERROR
    }

    private final Type   type;
    private final String sender;
    private final String payload;   
    private final String timestamp;

    public Message(Type type, String sender, String payload) {
        this.type      = type;
        this.sender    = sender;
        this.payload   = payload;
        this.timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    //  Getters     
    
    public Type   getType()      { return type; }
    public String getSender()    { return sender; }
    public String getPayload()   { return payload; }
    public String getTimestamp() { return timestamp; }

    /** Displayed in the chat window. */
    @Override
    public String toString() {
        return switch (type) {
            case SERVER    -> "[SERVER " + timestamp + "] " + payload;
            case BROADCAST -> "[" + timestamp + "] " + sender + ": " + payload;
            case DM        -> "[" + timestamp + "] (DM) " + sender + ": " + payload;
            case ERROR     -> "[ERROR] " + payload;
            default        -> payload;
        };
    }
}
