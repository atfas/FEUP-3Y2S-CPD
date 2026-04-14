package common.models;

public class Message {
    private User sender;
    private Room recipient;
    private String content;
    
    public Message(User sender, Room recipient, String content) {
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
    }

    public User getSender() {
        return sender;
    }

    public Room getRecipient() {
        return recipient;
    }

    public String getContent() {
        return content;
    }
}
