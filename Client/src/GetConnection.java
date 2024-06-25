
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


    public GetConnection(ServerCommunication server, GUI gui, Client client) throws FileNotFoundException {
        this.server = server;
        in = null;
        this.gui = gui;
        this.client = client;
        File key = new File("key.txt");
        if (key.exists()) {
            Scanner scanner = new Scanner(key);
            String email = null;
            if (scanner.hasNext()) email = scanner.nextLine();
            gui.AccessCurrentEmail(true, email);
            gui.SetDisconnected();
            scanner.close();
        }
        else gui.SetLoggedOut();
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
            if (!server.connected.get() && socket != null) {
                try {
                    String string = "";
                    server.inName = new DataInputStream(socket.getInputStream());
                    server.outName = new DataOutputStream(socket.getOutputStream());
                    server.upload = socket.getOutputStream();
                    server.download = socket.getInputStream();
                    server.inName.readUTF();
                    server.connected.set(client.Login());
                } catch (IOException | InterruptedException e) {
                    try {
                        socket.close();
                        Thread.sleep(5000);
                    } catch (IOException | InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
            else if (socket != null) {
                try {
                    ping();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void ping() throws InterruptedException {
        String response = null;
        while (true) {
            String home = server.AccessDir(false, null);
            try {
                response = server.SendAndReceive("ping");
            }
            catch (NullPointerException e) {
                server.connected.set(false);
                server.firstSync.set(false);
                File key = new File("key.txt");
                if (!key.exists()) gui.SetLoggedOut();
                else gui.SetDisconnected();
                return;
            }
            System.out.println(response);
            if (response == null) {
                server.connected.set(false);
                server.firstSync.set(false);
                File key = new File("key.txt");
                if (!key.exists()) gui.SetLoggedOut();
                else gui.SetDisconnected();
                return;
            } else {
                if (!server.firstSync.get() && server.sync.get()) {
                    try {
                        if (server.SendAndReceive("file sync") != null) server.firstSync.set(true);
                        else {
                            server.connected.set(false);
                            server.firstSync.set(false);
                            File key = new File("key.txt");
                            if (!key.exists()) gui.SetLoggedOut();
                            else gui.SetDisconnected();
                            return;
                        }
                    }
                    catch (NullPointerException e) {
                        server.connected.set(false);
                        server.firstSync.set(false);
                        File key = new File("key.txt");
                        if (!key.exists()) gui.SetLoggedOut();
                        else gui.SetDisconnected();
                        return;
                    }
                }
                else {
                    try {
                        if (server.sync.get() && CheckForChanges(home)) {
                            try {
                                response = server.SendAndReceive("file change");
                            }
                            catch (NullPointerException e) {
                                server.connected.set(false);
                                server.firstSync.set(false);
                                File key = new File("key.txt");
                                if (!key.exists()) gui.SetLoggedOut();
                                else gui.SetDisconnected();
                                return;
                            }
                            if (response == null) {
                                server.connected.set(false);
                                server.firstSync.set(false);
                                File key = new File("key.txt");
                                if (!key.exists()) gui.SetLoggedOut();
                                else gui.SetDisconnected();
                                return;
                            }
                        }
                    } catch (IOException e) {
                        server.connected.set(false);
                        server.firstSync.set(false);
                        File key = new File("key.txt");
                        if (!key.exists()) gui.SetLoggedOut();
                        else gui.SetDisconnected();
                        return;
                    }
                }
                Thread.sleep(15000);
            }
        }
    }

    public boolean CheckForChanges(String home) throws IOException {
        Pair<List<Pair<FileClass, Object>>, List<Pair<FileClass, Object>>> temp = (Pair<List<Pair<FileClass, Object>>, List<Pair<FileClass, Object>>>) server.AccessFiles(3, home);
        if (temp == null) {
            server.SetHomeErr();
            return false;
        }
        else {
            List<Pair<FileClass, Object>> oldFileList = temp.getFirst();
            List<Pair<FileClass, Object>> newFileList = temp.getSecond();
            List<Pair<File, Object>> newF = ConvertToFile(newFileList);
            List<Pair<File, Object>> oldF = ConvertToFile(oldFileList);
            System.out.println(newF.equals(oldF));
            System.out.println(newF);
            System.out.println(oldF);
            return !ConvertToFile(oldFileList).equals(ConvertToFile(newFileList));
        }
    }

    private List<Pair<File, Object>> ConvertToFile(List<Pair<FileClass, Object>> list) {
        List<Pair<File, Object>> newList = new ArrayList<>();
        for (Pair<FileClass, Object> p : list) {
            if (p.getFirst().isDir) newList.add(new Pair<>(p.getFirst().file(), ConvertToFile((List<Pair<FileClass, Object>>) p.getSecond())));
            else newList.add(new Pair<>(p.getFirst().file(), p.getSecond()));
        }
        return newList;
    }
}
