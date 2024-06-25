
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class GUI{

    private JMenuBar topMenu;
    private JFrame frame;
    private ServerCommunication server;
    public AtomicBoolean remembered;
    private String currentEmail;
    private final Client client;

    public GUI(ServerCommunication server, Client client) {
        this.client = client;
        currentEmail = null;
        this.server = server;
        remembered = new AtomicBoolean(false);
        frame = new JFrame("Remote File Host");
        frame.setSize(512, 512);
        frame.setLocation(704, 284);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setLayout(null);
        frame.setBackground(Color.LIGHT_GRAY);

        topMenu = new JMenuBar();
        frame.setJMenuBar(topMenu);
        SetDisconnected();

        if (SystemTray.isSupported()) {
            SystemTray systemTray = SystemTray.getSystemTray();

            //get default toolkit
            //Toolkit toolkit = Toolkit.getDefaultToolkit();
            //get image
            //Toolkit.getDefaultToolkit().getImage("src/resources/busylogo.jpg");
            Image image = Toolkit.getDefaultToolkit().getImage("icon.png");

            //popupmenu
            PopupMenu trayPopupMenu = new PopupMenu();

            //1t menuitem for popupmenu
            MenuItem action = new MenuItem("Open");
            action.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    SetVisible();
                }
            });
            trayPopupMenu.add(action);
            TrayIcon trayIcon = new TrayIcon(image, "Remote File Client", trayPopupMenu);
            trayIcon.setImageAutoSize(true);

            try{
                systemTray.add(trayIcon);
            }catch(AWTException awtException){
                awtException.printStackTrace();
            }
        }
    }

    private synchronized static void addMenuItem(JMenu menu, String title, ActionListener actionListener) {
        JMenuItem menuItem = new JMenuItem(title);
        menuItem.addActionListener(actionListener);
        menu.add(menuItem);
    }

    public synchronized String AccessCurrentEmail(boolean set, String email) {
        if (set) {
            currentEmail = email;
            return null;
        }
        else return currentEmail;
    }

    public synchronized void SetLoggedIn() {
        SetMenu();
        frame.getContentPane().removeAll();

        JLabel connection = new JLabel("Connected");
        connection.setFont(new Font("Aptos", Font.BOLD, 40));
        connection.setForeground(Color.GREEN);
        connection.setBounds(135, 60, 300, 50);
        frame.add(connection);

        frame.revalidate();
        frame.repaint();
    }

    private synchronized void SetMenu() {
        topMenu = new JMenuBar();
        frame.setJMenuBar(topMenu);
        JMenu account = new JMenu("Account");
        JMenu config = new JMenu("Config");
        topMenu.add(account);
        topMenu.add(config);

        // Add menu items
        addMenuItem(account, "Change Password", e -> ChangePass());
        if (remembered.get()) addMenuItem(account, "Forget Device", e -> ForgetDevice());
        addMenuItem(account, "Delete Account", e -> DeleteConfirmation());
        addMenuItem(account, "Logout", e -> Logout());

        addMenuItem(config, "Home Directory", e -> HomeDir(false));
        if (server.sync.get()) addMenuItem(config, "Pause Sync", e -> Sync(true));
        else addMenuItem(config, "Resume Sync", e -> Sync(false));

        SetStartupBar();

        frame.revalidate();
        frame.repaint();
    }

    private synchronized void DeleteConfirmation() {
        JDialog confirm = new JDialog(frame, "Confirm Delete", true);
        confirm.setLayout(null);
        confirm.setSize(400, 200);

        JButton yes = new JButton("Delete");
        yes.setBounds(5, 30, 175, 80);
        yes.addActionListener(e -> DeleteAccount(confirm));
        confirm.add(yes);

        JButton no = new JButton("Cancel");
        no.setBounds(200, 30, 175, 80);
        no.addActionListener(e -> CloseWindow(confirm));
        confirm.add(no);

        confirm.setLocationRelativeTo(frame);
        confirm.setVisible(true);
    }

    private synchronized void DeleteAccount(JDialog dialog) {
        String response = server.SendAndReceive("delete_account"), email = AccessCurrentEmail(false, null);
        if (!response.equals("failed")) {
            dialog.setVisible(false);
            File config = new File("config-" + email + ".txt");
            if (config.exists()) config.delete();
            File key = new File("key.txt");
            if (key.exists()) key.delete();
        }
    }

    private synchronized void CloseWindow(JDialog dialog) {
        dialog.setVisible(false);
    }

    public synchronized void SetLoggedOut() {
        System.out.println("Logged Out");
        topMenu = new JMenuBar();
        frame.getContentPane().removeAll();
        frame.setJMenuBar(topMenu);

        JLabel connection = new JLabel("Disconnected");
        connection.setFont(new Font("Aptos", Font.BOLD, 40));
        connection.setForeground(Color.RED);
        connection.setBounds(115, 60, 300, 50);
        frame.add(connection);
        JLabel email = new JLabel("Email Address:");
        email.setFont(new Font("Aptos", Font.PLAIN, 18));
        email.setForeground(Color.BLACK);
        email.setBounds(20, 160, 130, 30);
        JTextField emailText = new JTextField();
        emailText.setFont(new Font("Aptos", Font.PLAIN, 18));
        emailText.setForeground(Color.BLACK);
        emailText.setBounds(160, 160, 300, 30);

        JLabel password = new JLabel("Password:");
        password.setFont(new Font("Aptos", Font.PLAIN, 18));
        password.setForeground(Color.BLACK);
        password.setBounds(20, 220, 130, 30);
        JTextField passText = new JTextField();
        passText.setFont(new Font("Aptos", Font.PLAIN, 18));
        passText.setForeground(Color.BLACK);
        passText.setBounds(160, 220, 300, 30);

        JButton login = new JButton("Login");
        login.setBounds(196, 310, 90, 50);

        JButton register = new JButton("Register New Account");
        register.setBounds(162, 370, 160, 30);
        register.addActionListener(e -> RegisterNewAccount());

        JLabel err = new JLabel("");
        err.setBounds(162, 260, 180, 20);
        err.setForeground(Color.red);

        JLabel serverStatus = new JLabel("Server Status:");
        serverStatus.setFont(new Font("Aptos", Font.PLAIN, 12));
        serverStatus.setForeground(Color.BLACK);
        serverStatus.setBounds(150, 110, 90, 30);

        JLabel serverStatusText;

        if (server.connected.get()) {
            serverStatusText = new JLabel("Online");
            serverStatusText.setForeground(Color.GREEN);
            serverStatusText.setFont(new Font("Aptos", Font.PLAIN, 12));
            serverStatusText.setBounds(240, 110, 90, 30);
        }
        else {
            serverStatusText = new JLabel("Offline");
            serverStatusText.setForeground(Color.RED);
            serverStatusText.setFont(new Font("Aptos", Font.PLAIN, 12));
            serverStatusText.setBounds(240, 110, 90, 30);
        }

        JCheckBox remember = new JCheckBox("Remember Me");
        remember.setFont(new Font("Aptos", Font.PLAIN, 12));
        remember.setBounds(350, 255, 140, 15);
        login.addActionListener(e -> {
            try {
                Login(emailText, passText, remember, err);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        if (AccessCurrentEmail(false, null) != null) {
            JMenu config = new JMenu("Config");
            topMenu.add(config);
            addMenuItem(config, "Home Directory", e -> HomeDir(false));
            if (server.sync.get()) addMenuItem(config, "Pause Sync", e -> Sync(true));
            else addMenuItem(config, "Resume Sync", e -> Sync(false));
        }

        SetStartupBar();

        frame.add(err);
        frame.add(register);
        frame.add(remember);
        frame.add(serverStatusText);
        frame.add(login);
        frame.add(email);
        frame.add(emailText);
        frame.add(password);
        frame.add(passText);
        frame.add(serverStatus);

        frame.revalidate();
        frame.repaint();
    }

    private synchronized void RegisterNewAccount() {
        JDialog register = new JDialog(frame, "Register", true);
        register.setLayout(null);
        register.setSize(400, 250);

        JLabel wrongPass = new JLabel("");
        wrongPass.setBounds(110, 10, 220, 30);
        register.add(wrongPass);

        JTextField oldPass = new JTextField();
        JLabel oldText = new JLabel("Email");
        oldText.setBounds(10, 50, 90, 20);
        oldPass.setBounds(120, 50, 240, 20);
        register.add(oldText);
        register.add(oldPass);

        JTextField newPass = new JTextField();
        JLabel newText = new JLabel("Password");
        newText.setBounds(10, 80, 90, 20);
        newPass.setBounds(120, 80, 240, 20);
        register.add(newText);
        register.add(newPass);

        JTextField comfText = new JTextField();
        JLabel comf = new JLabel("Confirm Password");
        comf.setBounds(10, 110, 110, 20);
        comfText.setBounds(120, 110, 240, 20);
        register.add(comf);
        register.add(comfText);

        JButton apply = new JButton("Register");
        apply.setBounds(register.getWidth()/2 - 50, 140, 100, 50);
        apply.addActionListener(e -> Register(wrongPass, oldPass, newPass, comfText, register));
        register.add(apply);

        register.setLocationRelativeTo(frame);
        register.setVisible(true);
    }

    private synchronized void Register(JLabel err, JTextField emailText, JTextField password, JTextField comPassword, JDialog dialog) {
        String email = emailText.getText(), pass = password.getText(), compass = comPassword.getText();
        String msg;
        if (email.isEmpty() || pass.isEmpty() || compass.isEmpty()) {
            msg = "Invalid Field";
            err.setForeground(Color.red);
        }
        else if (!ValidEmail(email)) {
            msg = "Invalid Email";
            err.setForeground(Color.red);
        }
        else if (!pass.equals(compass)) {
            msg = "Passwords do not Match";
            err.setForeground(Color.red);
        }
        else if (pass.length() < 6 || !CheckAlpha(pass)) {
            msg = "At least 6 characters & alphanumeric";
            err.setForeground(Color.red);
        }
        else  {
            msg = "new_user " + email + " " + pass;
            msg = server.SendAndReceive(msg);
            String[] split = null;
            if (msg != null)  split = msg.split(" ");
            if (msg != null && split[0].equals("approved")) err.setForeground(Color.green);
            else err.setForeground(Color.red);

            if (msg == null || split[1].equals("failed_access")) msg = "Error Registering";
            else if (split[0].equals("approved")) msg = "Success";
            else msg = "User Already Exists";
        }
        err.setText(msg);
        err.repaint();
        err.revalidate();
        dialog.repaint();
        dialog.revalidate();
    }

    public synchronized void SetDisconnected() {
        topMenu = new JMenuBar();
        frame.getContentPane().removeAll();
        frame.setJMenuBar(topMenu);
        if (AccessCurrentEmail(false, null) != null) {
            JMenu account = new JMenu("Account");
            topMenu.add(account);
            addMenuItem(account, "Logout", e -> Logout());
        }
        JLabel connection = new JLabel("Server Offline");
        connection.setFont(new Font("Aptos", Font.BOLD, 40));
        connection.setForeground(Color.RED);
        connection.setBounds(115, 120, 300, 50);
        SetStartupBar();

        frame.add(connection);
        frame.revalidate();
        frame.repaint();
    }

    public synchronized void SetVisible() {
        topMenu.setVisible(true);
        frame.setVisible(true);
    }

    private synchronized void ChangePass() {
        JDialog resetDialog;
        resetDialog = new JDialog(frame, "Change Password", true);
        resetDialog.setLayout(null);
        resetDialog.setSize(400, 250);

        JLabel wrongPass = new JLabel("");
        wrongPass.setBounds(110, 10, 220, 30);
        resetDialog.add(wrongPass);

        JTextField oldPass = new JTextField();
        JLabel oldText = new JLabel("Old Password");
        oldText.setBounds(10, 50, 90, 20);
        oldPass.setBounds(120, 50, 240, 20);
        resetDialog.add(oldText);
        resetDialog.add(oldPass);

        JTextField newPass = new JTextField();
        JLabel newText = new JLabel("New Password");
        newText.setBounds(10, 80, 90, 20);
        newPass.setBounds(120, 80, 240, 20);
        resetDialog.add(newText);
        resetDialog.add(newPass);

        JTextField comfText = new JTextField();
        JLabel comf = new JLabel("Confirm Password");
        comf.setBounds(10, 110, 110, 20);
        comfText.setBounds(120, 110, 240, 20);
        resetDialog.add(comf);
        resetDialog.add(comfText);

        JButton apply = new JButton("Apply");
        apply.setBounds(resetDialog.getWidth()/2 - 40, 140, 80, 50);
        apply.addActionListener(e -> ApplyNewPass(oldPass, newPass, comfText, resetDialog, wrongPass));
        resetDialog.add(apply);

        resetDialog.setLocationRelativeTo(frame);
        resetDialog.revalidate();
        resetDialog.setVisible(true);
    }

    public synchronized void HomeDir(boolean err) {
        JDialog home = new JDialog(frame, "Home Directory", true);
        home.setLayout(null);
        home.setSize(450, 120);

        JLabel homeDir = new JLabel("Directory");
        homeDir.setBounds(5, 5, 70, 20);
        home.add(homeDir);

        JTextField homeText = new JTextField(server.AccessDir(false, null));
        homeText.setBounds(70, 5, 360, 20);
        home.add(homeText);

        if (err) {
            JLabel notExist = new JLabel("Error with Directory");
            notExist.setFont(new Font(null, Font.BOLD, 10));
            notExist.setBounds(165, 30, 150, 20);
            home.add(notExist);
        }

        JButton apply = new JButton("Apply");
        apply.addActionListener(e -> ChangeDir(homeText, home));
        apply.setBounds(180, 48, 80, 30);
        home.add(apply);

        home.setLocationRelativeTo(frame);
        home.setVisible(true);
    }

    private void ChangeDir(JTextField home, JDialog dialog) {
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
                LoadConfig(email);
                dialog.setVisible(false);
                HomeDir(false);
            } catch (IOException e) {
                dialog.setVisible(false);
                HomeDir(true);
            }
        }
        else {
            dialog.setVisible(false);
            HomeDir(true);
        }
    }

    public synchronized void Sync(boolean state) {
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
                if (args[0].equals("sync")) out.println("sync " + !state);
                else out.println(text);
                out.flush();
            }
            out.close();
            scanner.close();
            System.out.println(config.delete());
            System.out.println(newConfig.renameTo(config));
            frame.getJMenuBar().getMenu(1).remove(1);
            if (state) {
                addMenuItem(frame.getJMenuBar().getMenu(1), "Resume Sync", e -> Sync(!state));
                server.firstSync.set(false);
            }
            else addMenuItem(frame.getJMenuBar().getMenu(1), "Pause Sync", e -> Sync(!state));

            server.sync.set(!state);
            frame.revalidate();
            LoadConfig(email);
        }
        catch (IOException _) {}
    }

    public synchronized void ForgetDevice() {
        File key = new File("key.txt");
        if (server.connected.get() && key.exists()) {
            StringBuilder msg = new StringBuilder("forget_device ");
            int i = 0;
            try {
                Scanner scanner = new Scanner(key);
                while (scanner.hasNext()) {
                    if (i == 1) msg.append(scanner.nextLine()).append(" ");
                    else scanner.nextLine();
                    i++;
                }
                msg.append(HWID.getHWID());
                server.SendAndReceive(msg.toString()).split(" ");
                scanner.close();
                topMenu.getMenu(0).remove(1);
                frame.revalidate();
            } catch (FileNotFoundException _) {}
        }
        if (key.exists()) System.out.println(key.delete());
    }

    private synchronized void ApplyNewPass(JTextField oldPass, JTextField newPass, JTextField comPassword, JDialog dialog, JLabel err) {
        String oldPassword = oldPass.getText(), newPassword = newPass.getText(), comPass = comPassword.getText();
        String msg = "change_pass " + AccessCurrentEmail(false, null) + " " + oldPassword + " " + newPassword;
        if (oldPassword.isEmpty() || newPassword.isEmpty()) {
            err.setText("Invalid Field");
            err.setForeground(Color.RED);
            err.repaint();
            err.revalidate();
            dialog.repaint();
            dialog.revalidate();
        }
        else if (!newPassword.equals(comPass)) {
            msg = "Passwords do not Match";
            err.setForeground(Color.red);
            err.setText(msg);
            err.repaint();
            err.revalidate();
            dialog.repaint();
            dialog.revalidate();
        }
        else if (comPass.length() < 6 || !CheckAlpha(comPass)) {
            msg = "At least 6 characters & alphanumeric";
            err.setForeground(Color.red);
            err.setText(msg);
            err.repaint();
            err.revalidate();
            dialog.repaint();
            dialog.revalidate();
        }
        else {
            msg = server.SendAndReceive(msg);
            if (msg != null && msg.equals("approved")) err.setForeground(Color.green);
            else err.setForeground(Color.red);

            if (msg == null) msg = "Error Changing Password";
            else if (msg.equals("approved")) msg = "Success";
            else msg = "Incorrect Old Password";

            err.setText(msg);
            err.repaint();
            err.revalidate();
            dialog.repaint();
            dialog.revalidate();

            System.out.println("Failed Pass");

        }
    }

    private synchronized void Login(JTextField email, JTextField pass, JCheckBox remember, JLabel err) throws IOException {
        String serverMsg, emailText = email.getText(), password = pass.getText();
        if (!email.getText().isEmpty() && !pass.getText().isEmpty()) {
            if (!remember.isSelected()) serverMsg = "existing_user " + emailText + " " + password;
            else serverMsg = "existing_user_remember " + email.getText() + " " + pass.getText() + " " + HWID.getHWID();
            serverMsg = server.SendAndReceive(serverMsg);
            if (serverMsg == null) {
                server.connected.set(false);
                if (!remembered.get()) SetLoggedOut();
                else SetDisconnected();
            }
            else {
                String[] args = serverMsg.split(" ");
                if (args[0].equals("approved")) {
                    if (args.length == 2) {
                        File key = new File("key.txt");
                        if (key.exists()) key.delete();
                        key.createNewFile();
                        PrintWriter out = new PrintWriter(new FileWriter(key));
                        out.println(emailText);
                        out.println(args[1]);
                        out.flush();
                        out.close();
                        remembered.set(true);
                    }
                    AccessCurrentEmail(true, emailText);
                    LoadConfig(emailText);
                    SetLoggedIn();
                }
                else {
                    err.setText("Incorrect Email or Password");
                    err.revalidate();
                    frame.repaint();
                    frame.revalidate();
                }
            }
        }
        else {
            err.setText("Invalid Field");
            err.revalidate();
            frame.repaint();
            frame.revalidate();
        }
    }
    public synchronized void Logout() {
        remembered.set(false);
        ForgetDevice();
        server.SendAndReceive("logout");
        AccessCurrentEmail(true, null);
        server.firstSync.set(false);
        SetLoggedOut();
    }

    public synchronized void LoadConfig(String email) {
        System.out.println("HERE1");
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
                    server.sync.set(!args[1].equals("false"));
                    System.out.println(server.sync.get());
                }
            }
            scanner.close();
            System.out.println("HERE2");
            if (server.sync.get() && server.connected.get()) {
                server.firstSync.set(false);
            }
            else if (!server.sync.get() && server.connected.get()) {
                server.SendAndReceive("file unsync");
            }
        } catch (IOException e) {
            Logout();
        }
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

    private synchronized void SetStartupBar() {
        JMenu startup = new JMenu("Startup");
        topMenu.add(startup);

        if (client.visible.get()) addMenuItem(startup, "Set Invisible on Startup", e -> ToggleStartup(startup));
        else addMenuItem(startup, "Set Visible on Startup", e -> ToggleStartup(startup));
    }

    private synchronized void ToggleStartup(JMenu toggle) {
        File newStartup = new File("temp-startup.txt");
        File startup = new File("startup.txt");
        if (newStartup.exists()) newStartup.delete();
        try {
            newStartup.createNewFile();
            Scanner in = new Scanner(startup);
            PrintWriter out = new PrintWriter(newStartup);
            String[] split;
            String text;
            while (in.hasNext()) {
                text = in.nextLine();
                split = text.split(" ");
                if (split[0].equals("visible")) out.println("visible " + !client.visible.get());
                else out.println(text);
                out.flush();
            }
            in.close();
            out.close();
            System.out.println(startup.delete());
            System.out.println(newStartup.renameTo(startup));


            if (client.visible.get()) toggle.getItem(0).setText("Set Visible on Startup");
            else toggle.getItem(0).setText("Set Invisible on Startup");
            client.visible.set(!client.visible.get());

            toggle.getItem(0).revalidate();
            topMenu.revalidate();
            frame.revalidate();
        } catch (IOException _) {}
    }

    private boolean CheckAlpha(String msg) {
        for (int i = 0; i < msg.length(); i++) {
            System.out.println((int) msg.charAt(i));
            if ((int) msg.charAt(i) < 33 || (int) msg.charAt(i) > 126) return false;
        }
        return true;
    }

    private boolean ValidEmail(String email) {
        return CheckAlpha(email) && email.indexOf('@') != -1 && email.indexOf('@') == email.lastIndexOf('@') && email.lastIndexOf('.') != -1 && email.length() - email.lastIndexOf('.') - 1 <= 3;
    }
}

