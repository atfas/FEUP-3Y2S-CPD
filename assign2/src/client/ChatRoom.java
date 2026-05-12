package client;

import java.io.*;

import common.models.Room;
import common.models.User;
import common.models.Message;
import common.protocol.Request;
import common.protocol.RequestType;

public class ChatRoom {
    private Room currentRoom;
    private User currentUser;

    public ChatRoom(Room room, User user) {
        this.currentRoom = room;
        this.currentUser = user;
    }

    public void chat(ObjectInputStream in, ObjectOutputStream out) throws IOException {
        boolean chatting = true;
        while (chatting) {
            // Read user input and send messages to the server (NON-BLOCKING)
            String message = System.console().readLine();
            if (message.equalsIgnoreCase("/leave")) {
                new Request(RequestType.LEAVE_ROOM, null).sendRequest(out);
                chatting = false;
                continue;
            }
            Message chatMessage = new Message(currentUser, currentRoom, message);
            new Request(RequestType.CHAT_MESSAGE, chatMessage).sendRequest(out);

            // Listen for incoming messages from the server and display them (NON-BLOCKING)
            listenForMessages(in);
        }
    }

    private void listenForMessages(ObjectInputStream in) {
        new Thread(() -> {
            try {
                while (true) {
                    Request request = Request.getRequest(in);
                    if (request.getType() == RequestType.CHAT_MESSAGE) {
                        Message chatMessage = (Message) request.getPayload();
                        System.out.println(chatMessage.getSender() + ": " + chatMessage.getContent());
                    }
                }
            } catch (IOException e) {
                System.out.println("Connection lost.");
            }
        }).start();
    }
}
