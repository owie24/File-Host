
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {
    public AtomicBoolean visible;
    public AtomicBoolean syncOn;
    public AtomicBoolean firstSync;
    public AtomicBoolean loggedIn;
    public AtomicBoolean connected;
    public AtomicBoolean remembered;
    public AtomicBoolean directoryErr;

    private String currentEmail;
    ServerCommunication server;
    GUI gui;

    public void ClientStart() throws FileNotFoundException, InterruptedException {
        server = null;
        remembered = new AtomicBoolean();
        directoryErr = new AtomicBoolean(false);
        visible = new AtomicBoolean();
        syncOn = new AtomicBoolean();
        firstSync = new AtomicBoolean(false);
        loggedIn = new AtomicBoolean();
        connected = new AtomicBoolean();
        try {
            server = new ServerCommunication();
            gui = new GUI(server, this);
            server.AddGUI(gui, this);
            Thread.sleep(500);
        } catch (IOException | InterruptedException e) {
            System.exit(-1);
        }
        ReadStartup();
    }

    public synchronized void UpdateGUI() {
        if (directoryErr.get() && loggedIn.get()) gui.HomeDir("Error with home directory");
        if (connected.get()) {
            if (loggedIn.get()) gui.SetLoggedIn();
            else gui.SetLoggedOut();
        }
        else {
            if (loggedIn.get()) gui.SetDisconnected();
            else gui.SetLoggedOut();
        }
        if (visible.get()) gui.SetVisible();
        directoryErr.set(false);
    }

    public synchronized void Logout() {
        remembered.set(false);
        firstSync.set(false);
        loggedIn.set(false);
        gui.ForgetDevice();
        server.SendAndReceive("logout", null);
        AccessCurrentEmail(true, null);
        UpdateGUI();
    }

    public void StartupLogin() throws FileNotFoundException, InterruptedException {
        File key = new File("key.txt");
        StringBuilder builder;
        if (key.exists()) {
            builder = new StringBuilder("existing_user ");
            Scanner keyReader = new Scanner(key);
            while (keyReader.hasNext()) {
                builder.append(keyReader.nextLine()).append(" ");
            }
            keyReader.close();
            builder.append(HWID.getHWID());
            server.SendAndReceive(builder.toString(), null);
        }
        else  {
            Thread.sleep(500);
            loggedIn.set(false);
            UpdateGUI();
        }
    }


    public void ChangeDir(JTextField home, JDialog dialog, JLabel err) {
        File dir = new File(home.getText());
        if (!dir.exists()) dir.mkdir();
        if (dir.exists()) {
            server.AccessDir(true, dir.getAbsolutePath());
            String email = AccessCurrentEmail(false, null);
            File config = new File("config-" + email + ".txt");
            File newConfig = new File("temp-" + email + ".txt");
            try {
                Scanner scanner = new Scanner(new FileReader(config));
                PrintWriter out = new PrintWriter(new FileWriter(newConfig));
                String[] args;
                String text;
                while (scanner.hasNext()) {
                    text = scanner.nextLine();
                    args = text.split(" ");
                    if (args[0].equals("directory")) out.println("directory " + dir.getAbsolutePath());
                    else out.println(text);
                    out.flush();
                }
                out.close();
                scanner.close();
                System.out.println(config.delete());
                System.out.println(newConfig.renameTo(config));
                firstSync.set(false);
                if (connected.get()) server.SendAndReceive("file unsync", null);
                err.setText("Success");
                err.setForeground(Color.GREEN);
                directoryErr.set(false);
            } catch (IOException e) {
                err.setText("Error changing directory");
                err.setForeground(Color.red);
                err.repaint();
                err.revalidate();
                dialog.repaint();
                dialog.revalidate();
            }
        }
        else {
            err.setText("Directory does not exist and could no be created");
            err.setForeground(Color.red);
        }
        err.repaint();
        err.revalidate();
        dialog.repaint();
        dialog.revalidate();
    }

    private void ReadStartup() throws FileNotFoundException, InterruptedException {
        File startup = new File("startup.txt");
        if (!startup.exists()) {
            try {
                DefaultStartup();
            } catch (IOException e) {
                visible.set(true);
                return;
            }
        }
        Scanner scanner;
        try {
            scanner = new Scanner(startup);
            String text;
            String[] args;
            while (scanner.hasNext()) {
                text = scanner.nextLine();
                args = text.split(" ");
                if (args[0].equals("visible")) visible.set(args[1].equals("true"));
                System.out.println("Visible: " +visible.get());
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            visible.set(true);
        }
        StartupLogin();
    }

    public synchronized void LoadConfig(String email) {
        File config = new File("config-" + email + ".txt");
        try {
            if (!config.exists()) MakeDefaultConfig(email);
            String[] args;
            Scanner scanner = new Scanner(config);
            String text;
            while (scanner.hasNext()) {
                text = scanner.nextLine();
                args = text.split(" ");
                if (args[0].equals("directory")) {
                    server.AccessDir(true, text.substring(text.indexOf(" ") + 1));
                    File temp = new File(text.substring(text.indexOf(" ") + 1));
                    if (!temp.exists()) temp.mkdir();
                }
                else if (args[0].equals("sync")) {
                    syncOn.set(!args[1].equals("false"));
                }
            }
            scanner.close();
            UpdateGUI();
        } catch (IOException e) {
            if (loggedIn.get()) server.SendAndReceive("logout", null);
            loggedIn.set(false);
            UpdateGUI();
        }
    }

    private void DefaultStartup() throws IOException {
        File startup = new File("startup.txt");
        System.out.println(startup.createNewFile());
        PrintWriter out = new PrintWriter(new FileWriter(startup));
        out.println("visible true");
        out.flush();
        out.close();
    }

    private synchronized void MakeDefaultConfig(String email) throws IOException {
        File config = new File("config-" + email + ".txt");
        File home = new File("Home Directory");
        if (!home.exists()) System.out.println(home.mkdir());
        System.out.println(config.createNewFile());
        PrintWriter out = new PrintWriter(new FileWriter(config));
        out.println("directory " + home.getAbsolutePath());
        out.println("sync true");
        out.flush();
        out.close();
    }

    public synchronized String AccessCurrentEmail(boolean set, String email) {
        if (set) {
            currentEmail = email;
            return null;
        }
        else return currentEmail;
    }

    public synchronized void Sync() {
        try {
            String email = AccessCurrentEmail(false, null);
            File config = new File("config-" + email + ".txt");
            File newConfig = new File("temp-" + email + ".txt");
            Scanner scanner = new Scanner(new FileReader(config));
            PrintWriter out = new PrintWriter(new FileWriter(newConfig));
            String[] args;
            String text;
            while (scanner.hasNext()) {
                text = scanner.nextLine();
                args = text.split(" ");
                if (args[0].equals("sync")) out.println("sync " + !syncOn.get());
                else out.println(text);
                out.flush();
            }
            out.close();
            scanner.close();
            System.out.println(config.delete());
            System.out.println(newConfig.renameTo(config));
            gui.frame.getJMenuBar().getMenu(1).remove(1);
            if (syncOn.get()) {
                GUI.addMenuItem(gui.frame.getJMenuBar().getMenu(1), "Resume Sync", e -> Sync());
                firstSync.set(false);
                if (connected.get()) server.SendAndReceive("file unsync", null);
                server.SendAndReceive("file unsync", null);
            }
            else GUI.addMenuItem(gui.frame.getJMenuBar().getMenu(1), "Pause Sync", e -> Sync());

            syncOn.set(!syncOn.get());
            firstSync.set(false);
            UpdateGUI();
        }
        catch (IOException ignored) {}
    }
}
