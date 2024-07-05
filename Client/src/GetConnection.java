
import libs.FileClass;
import libs.Pair;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class GetConnection implements Runnable {

    private final ServerCommunication server;
    private BufferedReader in;
    private final GUI gui;
    private final Client client;


    public GetConnection(ServerCommunication server, GUI gui, Client client) throws FileNotFoundException, InterruptedException {
        this.server = server;
        in = null;
        this.gui = gui;
        this.client = client;
        File key = new File("key.txt");
        Thread.sleep(4000);
        client.UpdateGUI();
    }

    @Override
    public void run() {
        Socket socket = null;
        while (true) {
            System.out.println("Connecting");
            try {
                socket = new Socket("generic.server.com", 8888);
            } catch (IOException e) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
            if (socket != null) {
                try {
                    server.inName = new DataInputStream(socket.getInputStream());
                    server.outName = new DataOutputStream(socket.getOutputStream());
                    server.upload = socket.getOutputStream();
                    server.download = socket.getInputStream();
                    server.inName.readUTF();
                    client.connected.set(true);
                    System.out.println("HERE");
                    ping();
                    client.UpdateGUI();
                } catch (IOException e) {
                    try {
                        socket.close();
                        Thread.sleep(5000);
                    } catch (IOException | InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void ping() throws InterruptedException {
        int count = 0;
        while (client.connected.get()) {
            String home = server.AccessDir(false, null);
            server.SendAndReceive("ping", null);
            try {
                if (client.syncOn.get() && client.firstSync.get() && !CheckForChanges(home)) {
                    System.out.println("Updating");
                    server.SendAndReceive("file change", null);
                }
                else if (client.syncOn.get() && client.firstSync.get()) {
                    count++;
                    if (count >= 20) {
                        server.SendAndReceive("file sync", null);
                        count = 0;
                    }
                }
                else if (!client.firstSync.get() && client.syncOn.get()) {
                    server.SendAndReceive("file sync", null);
                    count = 0;
                }
            } catch (IOException e) {
                client.directoryErr.set(true);
                client.UpdateGUI();
                return;
            }
            Thread.sleep(15000);
        }
    }

    public boolean CheckForChanges(String home) throws IOException {
        return (boolean) server.AccessFiles(3, home);
    }

}
