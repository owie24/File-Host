import java.awt.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Locale;


public class Main {
    public static void main(String[] args) throws InterruptedException, IOException {
        Client client = new Client();
        client.ClientStart();
    }
}