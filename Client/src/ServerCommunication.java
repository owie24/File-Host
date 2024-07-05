
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import libs.*;

public class ServerCommunication {

    public OutputStream upload;
    public InputStream download;
    public DataOutputStream outName;
    public DataInputStream inName;
    private String homePath;
    private Client client;

    public FileClass files;

    private List<File> filesAdded;
    private List<File> filesDeleted;
    private HashMap<File, File> filesRenamed;

    private final static char separator = File.separatorChar;

    public ServerCommunication() throws IOException {
        filesAdded = new ArrayList<>();
        filesDeleted = new ArrayList<>();
        filesRenamed =  new HashMap<>();
        homePath = null;
    }

    public void AddGUI(GUI gui, Client client) throws FileNotFoundException, InterruptedException {
        this.client = client;
        Thread connections = new Thread(new GetConnection(this, gui, client));
        connections.start();
    }

    public synchronized String AccessDir(boolean set, String path) {
        if (set) {
            homePath = path;
            return null;
        }
        else return homePath;
    }

    private synchronized void SendAndReceiveSync(String msg, JLabel errorMsg) throws NullPointerException {
        boolean go = true;
        String home = AccessDir(false, null);
        String response = null;
        String[] split, responseSplit;
        while (go) {
            go = false;
            try {
                outName.writeUTF(msg);
                response = inName.readUTF();
            } catch (IOException ignored) {}
            if (response == null) {
                return;
            }
            split = response.split(" ");
            if (split[0].equals("logout")) {
                client.firstSync.set(false);
                client.loggedIn.set(false);
                client.UpdateGUI();
            }
            else if (split[0].equals("file")) {
                go = true;
                try {
                    AccessFiles(1, home);
                } catch (IOException ignored) {}
            }
        }
        split = msg.split(" ");
        responseSplit = response.split(" ");
        System.out.println(response);
        try {
            switch (split[0]) {
                case "file" -> {
                    switch (split[1]) {
                        case "sync" -> {
                            ServerSync(home);
                            filesRenamed.clear();
                            filesAdded.clear();
                            filesDeleted.clear();
                            AccessFiles(0, home);
                        }
                        case "change" -> {
                            AccessFiles(2, home);
                        }
                    }
                }
                case "existing_user_remember" -> {
                    if (responseSplit[0].equals("failed")) {
                        if (responseSplit[1].equals("err_register")) errorMsg.setText("Account does not exit");
                        else errorMsg.setText("Password is incorrect");
                        errorMsg.revalidate();
                    }
                    else {
                        MakeKey(split[1], responseSplit);
                        client.remembered.set(true);
                        client.loggedIn.set(true);
                        client.directoryErr.set(false);
                        client.AccessCurrentEmail(true, split[1]);
                        client.LoadConfig(split[1]);
                        client.UpdateGUI();
                    }
                }
                case "existing_user" -> {
                    if (responseSplit[0].equals("failed")) {
                        if (responseSplit[1].equals("err_register")) errorMsg.setText("Account does not exit");
                        else errorMsg.setText("Password is incorrect");
                        errorMsg.revalidate();
                    }
                    else {
                        client.loggedIn.set(true);
                        client.directoryErr.set(false);
                        client.AccessCurrentEmail(true, split[1]);
                        if (split.length == 4) client.remembered.set(true);
                        client.LoadConfig(split[1]);
                        client.UpdateGUI();
                    }
                }
                case "new_user" -> {
                    if (responseSplit[0].equals("failed")) {
                        if (responseSplit[1].equals("user_exists")) errorMsg.setText("Account already exists");
                        else errorMsg.setText("An error occurred");
                        errorMsg.setForeground(Color.red);
                        errorMsg.repaint();
                        errorMsg.revalidate();
                    }
                    else {
                        errorMsg.setText("Success");
                        errorMsg.setForeground(Color.green);
                        errorMsg.repaint();
                        errorMsg.revalidate();
                    }
                }
                default -> {
                }
            }
        }
        catch (IOException ignored) {
        }
    }


    //0 == Load initial files
    //1 == Download server changes
    //2 == Update files
    //3 == Check for fundamental changes
    public synchronized Object AccessFiles(int operation, String homePath) throws IOException {
        if (!(new File(homePath).exists())) {
            client.directoryErr.set(true);
            client.UpdateGUI();
        }
        else if (operation == 0) files = new FileClass(new File(homePath), null);
        else if (operation == 1) FileUpdater(homePath);
        else if (operation == 2) FileUploader(homePath);
        else if (operation == 3) return files.Difference(new FileClass(new File(homePath), null));
        return true;
    }

    private void FileUpdater(String homePath) throws IOException {
        String args;
        String[] split;
        while (!(args = inName.readUTF()).equals("*")) {
            System.out.println(args);
            String oldName, newName;
            File oldFile, newFile;
            split = args.split(" ");
            if (split[0].equals("delete")) {
                oldName = inName.readUTF();
                oldFile = GetFile(oldName, homePath);
                if (oldFile.isDirectory()) {
                    System.out.println("Deleted Directory: " + oldFile.getAbsolutePath());
                    Delete(oldFile);
                }
                else {
                    System.out.println("Deleted File: " + oldFile.getAbsolutePath());
                    if (oldFile.exists() && oldFile.delete()) {
                        filesDeleted.add(oldFile);
                    }
                }
            }
            else if (split[0].equals("rename")) {
                oldName = inName.readUTF();
                newName = inName.readUTF();
                oldFile = GetFile(oldName, homePath);
                newFile = GetFile(newName, homePath);
                System.out.println("Renamed File: " + oldFile.getAbsolutePath());
                if (!newFile.exists() || newFile.delete()) {
                    oldFile.renameTo(newFile);
                    oldFile = newFile;
                    oldFile.setLastModified(System.currentTimeMillis());
                    filesRenamed.put(oldFile, newFile);
                }
            }
            else {
                String type = inName.readUTF();
                String name = inName.readUTF();
                newFile = GetFile(name, homePath);
                long modified = inName.readLong();
                if (type.equals("file")) {
                    long bytesLeft = inName.readLong();
                    if (!newFile.exists() || newFile.delete()) {
                        newFile.createNewFile();
                        OutputStream fileWrite = new FileOutputStream(newFile);
                        outName.writeUTF(">");
                        outName.flush();

                        int n;
                        byte[] buffer = new byte[8192];
                        while (bytesLeft > 0) {
                            n = download.read(buffer, 0, (int) Math.min(buffer.length, bytesLeft));
                            if (n < 0) {
                                throw new EOFException("Expected " + bytesLeft + " more bytes to read");
                            }
                            fileWrite.write(buffer, 0, n);
                            bytesLeft -= n;
                        }
                        fileWrite.close();
                        newFile.setLastModified(modified);
                        filesAdded.add(newFile);
                    }
                    else {
                        outName.writeUTF("skip");
                        outName.flush();
                    }
                }
                else {
                    if (newFile.exists()) newFile.setLastModified(Long.max(newFile.lastModified(), modified));
                    else {
                        newFile.mkdir();
                        newFile.setLastModified(modified);
                        filesAdded.add(newFile);
                    }
                }
            }
        }
    }

    private boolean Delete(File file) {
        if (!file.isDirectory()) {
            if (file.delete()) {
                filesDeleted.add(file);
                return true;
            }
            else return false;
        }
        else {
            File[] files = file.listFiles();
            boolean status = true;
            if (files != null) {
                for (File f : files) {
                    if (!Delete(f)) status = false;
                }
            }
            if (status) {
                if (file.delete()) {
                    filesDeleted.add(file);
                }
                else status = false;
            }
            return status;
        }
    }

    private void FileUploader(String homePath) throws IOException {
        FileClass newFileList = new FileClass(new File(homePath), null);
        HashMap<File, File> filesToRename = files.getRenamed(newFileList);
        List<File> fileToDelete = files.getDeleted();
        Pair<List<File>, List<File>> temp = files.getAdded();
        AddFile(temp.getFirst(), homePath);
        RenameFile(filesToRename, homePath);
        DeleteFile(fileToDelete, homePath);
        AddFile(temp.getSecond(), homePath);
        System.out.println(temp.getFirst() + "\n" + temp.getSecond());
        outName.writeUTF("*");
        outName.flush();
        files = new FileClass(new File(homePath), null);
        filesRenamed.clear();
        filesAdded.clear();
        filesDeleted.clear();
    }

    private void DeleteFile(List<File> files, String homePath) throws IOException {
        for (File f : files) {
            if (!filesDeleted.contains(f)) {
                outName.writeUTF("delete");
                outName.writeUTF(ConcatFile(f, homePath));
                outName.flush();
            }
        }
    }

    private void RenameFile(HashMap<File, File> fileToRename, String homePath) throws IOException {
        for (Map.Entry<File, File> e : fileToRename.entrySet()) {
            if (!fileToRename.containsKey(e.getKey()) || !fileToRename.get(e.getKey()).equals(e.getValue())) {
                outName.writeUTF("rename");
                outName.writeUTF(ConcatFile(e.getKey(), homePath));
                outName.writeUTF(ConcatFile(e.getValue(), homePath));
                outName.flush();
            }
        }
    }

    private void AddFile(List<File> files, String homePath) throws IOException {
        byte[] buffer;
        long bytesLeft;
        int n;
        for (File f : files) {
            if (!filesAdded.contains(f)) {
                outName.writeUTF("add");
                if (f.isDirectory()) outName.writeUTF("folder");
                else outName.writeUTF("file");
                outName.writeUTF(ConcatFile(f, homePath));
                outName.writeLong(f.lastModified());
                outName.flush();
                if (!f.isDirectory()) {
                    outName.writeLong(f.length());
                    outName.flush();
                    inName.readUTF();

                    InputStream inputStream = new FileInputStream(f);
                    bytesLeft = f.length();
                    buffer = new byte[8192];
                    while (bytesLeft > 0) {
                        n = inputStream.read(buffer, 0, (int) Math.min(buffer.length, bytesLeft));
                        if (n < 0) {
                            throw new EOFException("Expected " + bytesLeft + " more bytes to read");
                        }
                        upload.write(buffer, 0, n);
                        bytesLeft -= n;
                    }
                    inputStream.close();
                }
            }
        }
    }

    public synchronized void SendAndReceive(String msg, JLabel errorMsg) throws NullPointerException {
        SendAndReceiveSync(msg, errorMsg);
    }

    private Pair<List<String>,List<File> > GetFilesToDownload(String home) throws IOException {
        List<String> filesToADownload = new ArrayList<>();
        File file;
        String name, folderFile;
        long modified;
        List<File> serverFiles = new ArrayList<>();
        while (!(name = inName.readUTF()).equals("*")) {
            modified = Long.parseLong(inName.readUTF());
            folderFile = inName.readUTF();
            file = GetFile(name, home);
            if (file.exists() && name.contains(".") && name.substring(name.lastIndexOf(".") + 1).equals("deleted") && modified > file.lastModified()) {
                if (file.isDirectory()) RecursiveDeleteFolder(file);
                else file.delete();
            } else if (!(name.contains(".") && name.substring(name.lastIndexOf(".") + 1).equals("deleted"))) {
                if (!file.exists()) {
                    if (folderFile.equals("folder")) {
                        file.mkdir();
                    }
                    else {
                        file.createNewFile();
                        filesToADownload.add(name);
                        serverFiles.add(file);
                    }
                } else if (!file.isDirectory() && modified >= file.lastModified()) {
                    file.delete();
                    file.createNewFile();
                    filesToADownload.add(name);
                    serverFiles.add(file);
                }
            }
        }
        return new Pair<>(filesToADownload, serverFiles);
    }

    private void ServerSync(String homeDir) throws NullPointerException, IOException {
        long total, modified, bytesLeft;
        byte[] buffer;
        File file;
        int n;
        Pair<List<String>, List<File>> pair = GetFilesToDownload(homeDir);
        List<String> filesToADownload = pair.getFirst();
        List<File> serversFiles = pair.getSecond();
        try {
            for (String down : filesToADownload) {
                outName.writeUTF(down);
                outName.flush();

                file = GetFile(down, homeDir);
                OutputStream fileWrite = new FileOutputStream(file);
                total = inName.readLong();
                modified = inName.readLong();
                outName.writeUTF(">");
                outName.flush();

                bytesLeft = total;
                buffer = new byte[8192];
                while (bytesLeft > 0) {
                    n = download.read(buffer, 0, (int) Math.min(buffer.length, bytesLeft));
                    if (n < 0) {
                        throw new EOFException("Expected " + bytesLeft + " more bytes to read");
                    }
                    fileWrite.write(buffer, 0, n);
                    bytesLeft -= n;
                }
                fileWrite.close();
                file.setLastModified(modified);
                SetModified(Long.max(modified, file.getParentFile().lastModified()), file, homeDir);
            }
            outName.writeUTF("*");
            outName.flush();

            List<String> folders = GetAllFolders(new File(homeDir), homeDir);
            for (String s : folders) {
                outName.writeUTF(s);
                outName.flush();
            }

            outName.writeUTF("*");
            outName.flush();

            folders = GetAllFiles(new File(homeDir), homeDir, serversFiles);

            for (String s : folders) {
                file = new File(homeDir + "/" + s);
                InputStream inputStream = new FileInputStream(file);
                outName.writeUTF(s);
                outName.writeLong(file.lastModified());
                outName.writeLong(file.length());
                outName.flush();
                inName.readUTF();
                bytesLeft = file.length();
                buffer = new byte[8192];
                while (bytesLeft > 0) {
                    n = inputStream.read(buffer, 0, (int) Math.min(buffer.length, bytesLeft));
                    if (n < 0) {
                        throw new EOFException("Expected " + bytesLeft + " more bytes to read");
                    }
                    upload.write(buffer, 0, n);
                    bytesLeft -= n;
                }
                inputStream.close();
            }

            outName.writeUTF("*");
            outName.flush();
            client.firstSync.set(true);
        }
        catch (IOException e) {
            outName.writeUTF("*");
            outName.writeUTF("file unsync");
            outName.flush();
            client.firstSync.set(false);
            client.syncOn.set(false);
            client.UpdateGUI();
        }
    }

    private void RecursiveDeleteFolder(File dir) {

    }

    private List<String> GetAllFiles(File dir, String home, List<File> serversFiles) {
        File[] files = dir.listFiles();
        List<String> filesToUpload = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (!f.isDirectory() && !serversFiles.contains(f)) filesToUpload.add(ConcatFile(f, home));
                else if (f.isDirectory()) filesToUpload.addAll(GetAllFiles(f, home, serversFiles));
            }
        }
        return filesToUpload;
    }

    private void SetModified(long time, File file, String homePath) {
        file.getParentFile().setLastModified(time);
        if (file.getParentFile().getAbsolutePath().length() > homePath.length()) SetModified(time, file.getParentFile(), homePath);
    }

    private List<String> GetAllFolders(File dir, String home) {
        File[] files = dir.listFiles();
        List<String> foldersToUpload = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    foldersToUpload.add(ConcatFile(f, home));
                    foldersToUpload.addAll(GetAllFolders(f, home));
                }
            }
        }
        return foldersToUpload;
    }

    private String ConcatFile(File file, String homePath) {
        String path = file.getAbsolutePath();
        return path.substring(homePath.length() + 1);
    }

    private File GetFile(String filePath, String homePath) {
        String file = filePath;
        if (file.contains(".") && file.substring(file.lastIndexOf(".") + 1).equals("deleted")) file = file.substring(0, file.lastIndexOf(".") - 1);
        if (separator != '\\') file = file.replaceAll("\\\\", String.valueOf(separator));
        return new File(homePath + separator + file);
    }

    private void MakeKey(String email, String[] args) throws IOException {
        File key = new File("key.txt");
        if (key.exists()) key.delete();
        key.createNewFile();
        PrintWriter out = new PrintWriter(new FileWriter(key));
        out.println(email);
        out.println(args[1]);
        out.flush();
        out.close();
    }
}

