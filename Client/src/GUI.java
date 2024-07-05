
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

public class GUI{

    private JMenuBar topMenu;
    public JFrame frame;
    private ServerCommunication server;
    private final Client client;

    public GUI(ServerCommunication server, Client client) {
        this.client = client;
        this.server = server;
        frame = new JFrame("Remote File Host");
        frame.setSize(512, 512);
        frame.setLocation(704, 284);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                JDialog confirm = new JDialog(frame, "Close Option", true);
                confirm.setLayout(null);
                confirm.setSize(400, 200);

                JButton yes = new JButton("Exit");
                yes.setBounds(5, 30, 175, 80);
                yes.addActionListener(e -> Exit());
                confirm.add(yes);

                JButton no = new JButton("Minimize");
                no.setBounds(200, 30, 175, 80);
                no.addActionListener(e -> Minimize(confirm));
                confirm.add(no);

                confirm.setLocationRelativeTo(frame);
                confirm.setVisible(true);
            }
        });
        frame.setLayout(null);
        frame.setBackground(Color.LIGHT_GRAY);

        topMenu = new JMenuBar();
        frame.setJMenuBar(topMenu);

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
                    client.visible.set(true);
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

    private synchronized void Exit() {
        System.exit(0);
    }

    private synchronized void Minimize(JDialog dialog) {
        dialog.dispose();
        frame.setVisible(false); //********** Remove
        client.visible.set(false);
    }

    public synchronized static void addMenuItem(JMenu menu, String title, ActionListener actionListener) {
        JMenuItem menuItem = new JMenuItem(title);
        menuItem.addActionListener(actionListener);
        menu.add(menuItem);
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
        if (client.remembered.get()) addMenuItem(account, "Forget Device", e -> ForgetDevice());
        addMenuItem(account, "Delete Account", e -> DeleteConfirmation());
        addMenuItem(account, "Logout", e -> client.Logout());

        addMenuItem(config, "Home Directory", e -> HomeDir(""));
        if (client.syncOn.get()) addMenuItem(config, "Pause Sync", e -> client.Sync());
        else addMenuItem(config, "Resume Sync", e -> client.Sync());

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
        server.SendAndReceive("delete_account", null);
        dialog.setVisible(false);
        /*if (!response.equals("failed")) {
            File config = new File("config-" + email + ".txt");
            if (config.exists()) config.delete();
            File key = new File("key.txt");
            if (key.exists()) key.delete();
        }*/
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

        if (client.connected.get()) {
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
            err.setText(msg);
            err.repaint();
            err.revalidate();
            dialog.repaint();
            dialog.revalidate();
        }
        else if (!ValidEmail(email)) {
            msg = "Invalid Email";
            err.setForeground(Color.red);
            err.setText(msg);
            err.repaint();
            err.revalidate();
            dialog.repaint();
            dialog.revalidate();
        }
        else if (!pass.equals(compass)) {
            msg = "Passwords do not Match";
            err.setForeground(Color.red);
            err.setText(msg);
            err.repaint();
            err.revalidate();
            dialog.repaint();
            dialog.revalidate();
        }
        else if (pass.length() < 6 || !CheckAlpha(pass)) {
            msg = "At least 6 characters & alphanumeric";
            err.setForeground(Color.red);
            err.setText(msg);
            err.repaint();
            err.revalidate();
            dialog.repaint();
            dialog.revalidate();
        }
        else  {
            msg = "new_user " + email + " " + pass;
            server.SendAndReceive(msg, err);
        }
    }

    public synchronized void SetDisconnected() {
        topMenu = new JMenuBar();
        frame.getContentPane().removeAll();
        frame.setJMenuBar(topMenu);
        JMenu account = new JMenu("Account");
        topMenu.add(account);
        addMenuItem(account, "Logout", e -> client.Logout());
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

    public synchronized void HomeDir(String err) {
        JDialog home = new JDialog(frame, "Home Directory", true);
        home.setLayout(null);
        home.setSize(450, 120);

        JLabel homeDir = new JLabel("Directory");
        homeDir.setBounds(5, 5, 70, 20);
        home.add(homeDir);

        JTextField homeText = new JTextField(server.AccessDir(false, null));
        homeText.setBounds(70, 5, 360, 20);
        home.add(homeText);

        JLabel notExist = new JLabel(err);
        notExist.setFont(new Font(null, Font.BOLD, 10));
        notExist.setBounds(165, 30, 150, 20);
        notExist.setForeground(Color.RED);
        home.add(notExist);


        JButton apply = new JButton("Apply");
        apply.addActionListener(e -> client.ChangeDir(homeText, home, notExist));
        apply.setBounds(180, 48, 80, 30);
        home.add(apply);

        home.setLocationRelativeTo(frame);
        home.setVisible(true);
    }


    public synchronized void ForgetDevice() {
        File key = new File("key.txt");
        if (client.connected.get() && key.exists()) {
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
                server.SendAndReceive(msg.toString(), null);
                scanner.close();
                topMenu.getMenu(0).remove(1);
                frame.revalidate();
            } catch (FileNotFoundException ignored) {}
        }
        if (key.exists()) System.out.println(key.delete());
    }

    private synchronized void ApplyNewPass(JTextField oldPass, JTextField newPass, JTextField comPassword, JDialog dialog, JLabel err) {
        String oldPassword = oldPass.getText(), newPassword = newPass.getText(), comPass = comPassword.getText();
        String msg = "change_pass " + oldPassword + " " + newPassword;
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
            server.SendAndReceive(msg, err);
        }
    }

    private synchronized void Login(JTextField email, JTextField pass, JCheckBox remember, JLabel err) throws IOException {
        String serverMsg, emailText = email.getText(), password = pass.getText();
        if (!email.getText().isEmpty() && !pass.getText().isEmpty()) {
            if (!remember.isSelected()) serverMsg = "existing_user " + emailText + " " + password;
            else serverMsg = "existing_user_remember " + email.getText() + " " + pass.getText() + " " + HWID.getHWID();
            server.SendAndReceive(serverMsg, err);
        }
        else {
            err.setText("Invalid Field");
            err.revalidate();
            frame.repaint();
            frame.revalidate();
        }
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
        } catch (IOException ignored) {}
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

