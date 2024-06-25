import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class Main {
    public static void main(String[] args) throws IOException {
        File users = new File("users.txt");
        File folders = new File("User Folders");
        if (!folders.exists()) System.out.println(folders.mkdir());
        if (!users.exists())  System.out.println(users.createNewFile());
        Thread handler = new Thread(new MessageHandler());
        handler.start();
    }
}