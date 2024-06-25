
import java.io.*;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {
    public AtomicBoolean visible;
    ServerCommunication server;
    GUI gui;

    public void ClientStart() {
        server = null;
        visible = new AtomicBoolean();
        ReadStartup();
        try {
            server = new ServerCommunication();
            gui = new GUI(server, this);
            server.AddGUI(gui, this);
            Thread.sleep(500);
            if (visible.get()) gui.SetVisible();
        } catch (IOException | InterruptedException e) {
            System.exit(-1);
        }
    }

    public boolean Login() throws FileNotFoundException, InterruptedException {
        File key = new File("key.txt");
        StringBuilder builder;
        String[] serverMsg;
        if (key.exists()) {
            builder = new StringBuilder("existing_user ");
            Scanner keyReader = new Scanner(key);
            while (keyReader.hasNext()) {
                builder.append(keyReader.nextLine()).append(" ");
            }
            keyReader.close();
            builder.append(HWID.getHWID());
            String response = server.SendAndReceive(builder.toString());
            if (response == null) return false;
            else if (response.equals("failed")) {
                System.out.println(key.delete());
                Login();
                return true;
            }
            else {
                gui.remembered.set(true);
                gui.LoadConfig(builder.toString().split(" ")[1]);
                gui.SetLoggedIn();
                gui.AccessCurrentEmail(true, builder.toString().split(" ")[1]);
                return true;
            }
        }
        else  {
            Thread.sleep(500);
            gui.SetLoggedOut();
            return true;
        }
    }

    private void ReadStartup() {
        File startup = new File("startup.txt");
        if (!startup.exists()) {
            try {
                DefaultStartup();
            } catch (IOException e) {
                visible.set(true);
                return;
            }
        }
        Scanner scanner = null;
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
    }

    private void DefaultStartup() throws IOException {
        File startup = new File("startup.txt");
        System.out.println(startup.createNewFile());
        PrintWriter out = new PrintWriter(new FileWriter(startup));
        out.println("visible true");
        out.flush();
        out.close();
    }
}
