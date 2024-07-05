import libs.Pair;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

public class UserTracking {
    private final HashMap<String, String> emailToPass;
    private final HashMap<String, String> keyToEmail;
    private final HashMap<String, String> keyToHardware;
    private HashMap<String, FileManager> fileManagers;
    private HashMap<String, List<MessageHandlerThread>> emailToHandlers;

    public UserTracking() throws IOException {
        Scanner scanner = null;
        File file = new File("users.txt");
        emailToPass = new HashMap<>();
        keyToEmail = new HashMap<>();
        keyToHardware = new HashMap<>();
        fileManagers = new HashMap<>();
        emailToHandlers = new HashMap<>();
        try {
            scanner = new Scanner(file);
        } catch (FileNotFoundException e) {
            File file1 = new File("users.txt");
            if (!file1.exists()) {
                if (!file1.createNewFile()) throw new IOException();
                else scanner = new Scanner(file1);
            }
        }
        System.out.println(file.getAbsolutePath());
        String[] args;
        while (scanner != null && scanner.hasNext()) {
            args = scanner.nextLine().split(" ");
            emailToPass.put(args[0], args[1]);
            for (int i = 3; i < args.length; i += 2) keyToHardware.put(args[i+1], args[i]);
            for (int i = 4; i < args.length; i += 2) keyToEmail.put(args[i], args[0]);
        }
        if (scanner != null) scanner.close();
    }
    //0 == CheckValidKey, args[0] = hardware, args[1] = key
    //1 == KeyToEmail, args[0] = key;
    //2 == AddNewUser, args[0] = email, args[1] = password
    //3 == DeleteUserAccount, args[0] = email
    //4 == RegisterDevice, args[0] = email, args[1] = hardware number, args[2] = key;
    //5 == RemoveDevice, args[0] = hardware number, args[1] = key;
    //6 == CheckValidPass, args[0] = email, args[1] = pass;
    //7 == ChangePass, args[0] = email, args[1] = oldpass, args[2] = newpass
    //8 == DeleteAccount
    public synchronized Pair<Boolean, String> Access(int operation, String[] args) {
        if (operation == 0) return CheckValidKey(args);
        else if (operation == 1) return KeyToEmail(args);
        else if (operation == 2) return AddNewUser(args);
        else if (operation == 3) return DeleteUserAccount(args);
        else if (operation == 4) return RegisterDevice(args);
        else if (operation == 5) return RemoveDevice(args);
        else if (operation == 6) return CheckValidPass(args);
        else if (operation == 7) return ChangePass(args);
        else if (operation == 8) return DeleteUserAccount(args);
        else return new Pair<>(false, null);
    }

    //0 == Login, args[0] = email, returns FileManager
    //1 == LogOut, args[0] = email, returns void
    //2 == NotifyThreads, args[0] = email
    //8 == DeleteAccount, args[0] = email
    public Object Log(int operation, String[] args, MessageHandlerThread thread, List<File> filesAdded, List<File> filesDeleted, List<File> foldersAdded, HashMap<File, File> filesRenamed) {
        if (operation == 0) return Login(args, thread);
        else if (operation == 1) LogOut(args, thread);
        else if (operation == 2) NotifyThreads(args);
        else if (operation == 3) UpdateChangeList(args, filesAdded, filesDeleted, foldersAdded, filesRenamed);
        else if (operation == 8) {
            if (Access(8, args).getFirst()) {
                DeleteThreads(args);
                return true;
            }
            else return false;
        }
        return null;
    }

    private void UpdateChangeList(String[] args, List<File> filesAdded, List<File> filesDeleted, List<File> foldersAdded, HashMap<File, File> filesRenamed) {
        List<MessageHandlerThread> list = emailToHandlers.get(args[0]);
        for (MessageHandlerThread m : list) {
            m.filesAdded.addAll(filesAdded);
            m.filesDeleted.addAll(filesDeleted);
            m.filesRenamed.putAll(filesRenamed);
            m.foldersAdded.addAll(foldersAdded);
        }
    }

    private void NotifyThreads(String[] args) {
        List<MessageHandlerThread> list = emailToHandlers.get(args[0]);
        for (MessageHandlerThread m : list) m.update.set(true);
    }

    private void DeleteThreads(String[] args) {
        System.out.println(args[0]);
        for (MessageHandlerThread m : emailToHandlers.get(args[0])) m.valid.set(false);
        emailToHandlers.remove(args[0]);
        fileManagers.get(args[0]).active.set(false);
        fileManagers.remove(args[0]);
    }

    private void LogOut(String[] args, MessageHandlerThread thread) {
        if (emailToHandlers.containsKey(args[0])) emailToHandlers.get(args[0]).remove(thread);
    }

    private FileManager Login(String[] args, MessageHandlerThread thread) {
        if (!fileManagers.containsKey(args[0])) fileManagers.put(args[0], new FileManager(args[0], this));
        if (!emailToHandlers.containsKey(args[0])) {
            List<MessageHandlerThread> newList = new ArrayList<>();
            newList.add(thread);
            emailToHandlers.put(args[0], newList);
        }
        else emailToHandlers.get(args[0]).add(thread);
        System.out.println(emailToHandlers);
        return fileManagers.get(args[0]);
    }

    private Pair<Boolean, String> CheckValidKey(String[] args) {
        if (!keyToHardware.containsKey(args[1]) || !keyToHardware.get(args[1]).equals(args[0])) return new Pair<>(false, null);
        else return new Pair<>(true, null);
    }

    private Pair<Boolean, String> ChangePass(String[] args) {
        if (!emailToPass.containsKey(args[0]) || !emailToPass.get(args[0]).equals(args[1])) return new Pair<>(false, "failed err");
        else {
            File users = new File("users.txt");
            File temp = new File("temp.txt");
            try {
                System.out.println(temp.createNewFile());
                emailToPass.remove(args[0]);
                emailToPass.put(args[0], args[2]);
                PrintWriter out = new PrintWriter(temp);
                Scanner in = new Scanner(users);
                String[] split;
                StringBuilder text;
                while (in.hasNext()) {
                    text = new StringBuilder(in.nextLine());
                    split = text.toString().split(" ");
                    if (split[0].equals(args[0])) {
                        text = new StringBuilder(args[0] + " " + args[2]);
                        for (int i = 2; i < split.length; i++) text.append(" ").append(split[i]);
                    }
                    out.println(text);
                    out.flush();
                }
                in.close();
                out.close();
                System.out.println(users.delete());
                System.out.println(temp.renameTo(users));
                return new Pair<>(true, "approved");
            } catch (IOException e) {
                return new Pair<>(false, "failed err");
            }
        }
    }

    private Pair<Boolean, String> KeyToEmail(String[] args) {
        if (!keyToEmail.containsKey(args[0])) return new Pair<>(false, null);
        else return new Pair<>(true, keyToEmail.get(args[0]));
    }

    private Pair<Boolean, String> AddNewUser(String[] args) {
        if (emailToPass.containsKey(args[0])) return new Pair<>(false, "user_exists");
        else {
            try {
                PrintWriter out = new PrintWriter(new FileWriter("users.txt", true));
                out.println(args[0] + " " + args[1] + " " + System.currentTimeMillis());
                out.flush();
                out.close();
                emailToPass.put(args[0], args[1]);
            } catch (IOException e) {
                return new Pair<>(false, "failed_access");
            }
            return new Pair<>(true, null);
        }
    }

    private Pair<Boolean, String> DeleteUserAccount(String[] args) {
        Scanner scanner;
        try {
            File org = new File("users.txt");
            scanner = new Scanner(org);
            File temp = new File("temp.txt");
            String line;
            String[] seperateLine;
            try {
                if (!temp.createNewFile()) return new Pair<>(false, "failed_make");
                else {
                    PrintWriter writer = new PrintWriter(new FileWriter(temp));
                    while (scanner.hasNext()) {
                        line = scanner.nextLine();
                        seperateLine = line.split(" ");
                        if (!seperateLine[0].equals(args[0])) {
                            writer.println(line);
                            writer.flush();
                        }
                        else  {
                            for (int i = 3; i < seperateLine.length; i += 2) {
                                keyToHardware.remove(seperateLine[i]);
                                keyToEmail.remove(seperateLine[i]);
                            }
                        }
                    }
                    emailToPass.remove(args[0]);
                    writer.close();
                    scanner.close();
                    System.out.println(org.delete());
                    System.out.println(temp.renameTo(org));
                    File folder = new File("User Folders/" + args[0]);
                    if (folder.exists()) {
                        DeleteFolder(folder);
                        folder.delete();
                    }
                    return new Pair<>(true, null);
                }
            } catch (IOException e) {
                return new Pair<>(false, "failed_make");
            }
        } catch (FileNotFoundException e) {
            return new Pair<>(false, "failed_read");
        }
    }

    private void DeleteFolder(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) if (f.isDirectory()) DeleteFolder(f);
            for (File f : files) f.delete();
        }
        dir.delete();
    }

    private Pair<Boolean, String> RegisterDevice(String[] args) {
        Scanner scanner;
        try {
            File org = new File("users.txt");
            scanner = new Scanner(org);
            File temp = new File("temp.txt");
            String line;
            String[] seperateLine;
            try {
                if (!temp.createNewFile()) return new Pair<>(false, "failed_make");
                else {
                    PrintWriter writer = new PrintWriter(new FileWriter(temp));
                    while (scanner.hasNext()) {
                        line = scanner.nextLine();
                        seperateLine = line.split(" ");
                        if (seperateLine[0].equals(args[0])) {
                            writer.println(line + " " + args[1] + " " + args[2]);
                            writer.flush();
                            keyToEmail.put(args[2], args[0]);
                            keyToHardware.put(args[2], args[1]);
                        }
                        else {
                            writer.println(line);
                            writer.flush();
                        }
                    }
                    writer.close();
                    scanner.close();
                    System.out.println(org.delete());
                    System.out.println(temp.renameTo(org));
                    return new Pair<>(true, null);
                }
            } catch (IOException e) {
                return new Pair<>(false, "failed_make");
            }
        } catch (FileNotFoundException e) {
            return new Pair<>(false, "failed_read");
        }
    }

    private Pair<Boolean, String> RemoveDevice(String[] args) {
        if (!keyToEmail.containsKey(args[1])) return new Pair<>(false, "not_exists");
        else {
            Scanner scanner;
            try {
                File org = new File("users.txt");
                scanner = new Scanner(org);
                File temp = new File("temp.txt");
                String line;
                String[] seperateLine;
                try {
                    if (!temp.createNewFile()) return new Pair<>(false, "failed_make");
                    else {
                        PrintWriter writer = new PrintWriter(new FileWriter(temp));
                        while (scanner.hasNext()) {
                            line = scanner.nextLine();
                            seperateLine = line.split(" ");
                            if (seperateLine[0].equals(keyToEmail.get(args[1]))) {
                                StringBuilder newLine = new StringBuilder(seperateLine[0] + " " + seperateLine[1] + " " + seperateLine[2]);
                                for (int i = 4; i < seperateLine.length; i += 2) {
                                    if (!seperateLine[i].equals(args[1])) newLine.append(" ").append(seperateLine[i]).append(" ").append(seperateLine[i + 1]);
                                }
                                writer.println(newLine);
                                writer.flush();
                            }
                            else {
                                writer.println(line);
                                writer.flush();
                            }
                        }
                        writer.close();
                        scanner.close();
                        System.out.println(org.delete());
                        System.out.println(temp.renameTo(org));
                        keyToHardware.remove(args[1]);
                        keyToEmail.remove(args[1]);
                        return new Pair<>(true, "approved");
                    }
                } catch (IOException e) {
                    return new Pair<>(false, "failed_make");
                }
            } catch (FileNotFoundException e) {
                return new Pair<>(false, "failed_read");
            }
        }
    }

    private Pair<Boolean, String> CheckValidPass(String[] args) {
        if (!emailToPass.containsKey(args[0]) || !emailToPass.get(args[0]).equals(args[1])) return new Pair<>(false, null);
        else return new Pair<>(true, null);
    }
}
