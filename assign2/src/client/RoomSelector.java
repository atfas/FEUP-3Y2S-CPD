package client;

import java.io.*;
import java.util.Scanner;

import common.models.Room;
import common.models.User;
import common.protocol.Request;
import common.protocol.RequestType;

public class RoomSelector {
    private User currentUser;

    public RoomSelector(User user) {
        this.currentUser = user;
    }

    public void selectRoomPage(ObjectInputStream in, ObjectOutputStream out) throws IOException {
        @SuppressWarnings("resource")
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("Select a room to join(/logout to Logout):");
            System.out.println("Available rooms:");

            new Request(RequestType.ROOMS_LIST, null).sendRequest(out);
            Room[] rooms;

            Request response = Request.getRequest(in);
            if (response.getType() == RequestType.ROOMS_LIST) {
                rooms = (Room[]) response.getPayload();
                for (int i = 0; i < rooms.length; i++) {
                    System.out.println(rooms[i].getName());
                }
            } else {
                System.out.println("Failed to retrieve rooms list.");
            }

            System.out.println("Enter room name to join (New name to create a new room): ");
            String choice = scanner.nextLine();
            if (choice.equalsIgnoreCase("/logout")) {
                new Request(RequestType.LOGOUT, null).sendRequest(out);
                return;
            }

            ChatRoom chatRoom = new ChatRoom(new Room(choice), currentUser);
            chatRoom.chat(in, out);
        }
    }
}
