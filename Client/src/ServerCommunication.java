
import libs.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerCommunication {

    public OutputStream upload;
    public InputStream download;
    public DataOutputStream outName;
    public DataInputStream inName;
    public AtomicBoolean connected;
    public AtomicBoolean sync;
    public AtomicBoolean firstSync;
    private String homePath;

    private GUI gui;
    private Client client;

    public List<Pair<FileClass, Object>> fileList;

    private final static char separator = File.separatorChar;

    public ServerCommunication() throws IOException {
        connected = new AtomicBoolean();
        sync = new AtomicBoolean();
        firstSync = new AtomicBoolean(false);
        homePath = null;
        connected.set(false);
    }

    public void AddGUI(GUI gui, Client client) throws FileNotFoundException {
        this.gui = gui;
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

    private synchronized String SendAndReceiveSync(String msg) throws NullPointerException {
        boolean go = true;
        String home = AccessDir(false, null);
        String response = null;
        String[] split;
        while (go) {
            go = false;
            try {
                outName.writeUTF(msg);
                response = inName.readUTF();
            } catch (IOException e) {
                return null;
            }
            split = response.split(" ");
            if (split[0].equals("logout")) {
                firstSync.set(false);
                gui.SetLoggedOut();
            }
            else if (split[0].equals("file")) {
                go = true;
            }
        }
        split = msg.split(" ");
        try {
            switch (split[0]) {
                case "file" -> {
                    switch (split[1]) {
                        case "sync" -> {
                            response = ServerSync(home);
                            LoadFiles(home);
                            return response;
                        }
                        case "change" -> {
                            return (String) AccessFiles(2, home);
                        }
                    }
                }
                default -> {
                    return response;
                }
            }
        }
        catch (IOException e) {
            return null;
        }
        return response;
    }

    //0 == Load initial files
    //1 == Check for file changes
    //2 == Update files
    //3 == Check for fundamental changes
    public synchronized Object AccessFiles(int operation, String homePath) throws IOException {
        if (!(new File(homePath).exists())) return null;
        else if (operation == 0) return RecursiveFindAll(new File(homePath));
        else if (operation == 1) return null;
        else if (operation == 2) {
            String temp = FileUploader(homePath);
            if (temp != null) fileList = RecursiveFindAll(new File(homePath));
            return temp;
        }
        else if (operation == 3) return new Pair<>(fileList, RecursiveFindAll(new File(homePath)));
        return true;
    }

    private void FileUpdater(String homePath) {

    }

    private String FileUploader(String homePath) throws IOException {
        List<Pair<FileClass, Object>> newFileList = RecursiveFindAll(new File(homePath));
        List<File> filesMoved = ServerRenameDelete(FilesToRename(fileList, newFileList), homePath);
        AddFile(FilesToAdd(filesMoved, fileList, newFileList), homePath);
        outName.writeUTF("*");
        outName.flush();
        return "good";
    }

    private void AddFile(List<File> files, String homePath) throws IOException {
        byte[] buffer;
        long bytesLeft;
        int n;
        for (File f : files) {
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

    private List<File> FilesToAdd(List<File> filesMoved, List<Pair<FileClass, Object>> oldFiles, List<Pair<FileClass, Object>> newFiles) {
        List<File> addFiles = new ArrayList<>();
        File temp;
        for (Pair<FileClass, Object> p : newFiles) {
            if (!oldFiles.contains(p) && !filesMoved.contains(p.getFirst().file())) {
                temp = p.getFirst().file();
                addFiles.add(temp);
                if (p.getFirst().isDir) addFiles.addAll(FilesToAdd(filesMoved, oldFiles, (List<Pair<FileClass, Object>>) p.getSecond()));
            }
        }
        return addFiles;
    }

    private List<File> ServerRenameDelete(HashMap<File, File> files, String home) throws IOException {
        List<File> filesMoved = new ArrayList<>();
        for (File f : files.keySet()) {
            if (files.get(f) == null) Delete(f, home);
            else {
                filesMoved.add(files.get(f));
                Rename(f, files.get(f), home);
            }
        }
        return filesMoved;
    }

    private void Rename(File old, File newFile, String home) throws IOException {
        outName.writeUTF("rename");
        outName.writeUTF(ConcatFile(old, home));
        outName.writeUTF(ConcatFile(newFile, home));
        outName.flush();
    }

    private void Delete(File file, String home) throws IOException {
        outName.writeUTF("delete");
        outName.writeUTF(ConcatFile(file, home));
        outName.flush();
    }

    private HashMap<File, File> FilesToRename(List<Pair<FileClass, Object>> oldFiles, List<Pair<FileClass, Object>> newFiles) {
        HashMap<File, File> fileFile = new HashMap<>();
        Stack<List<Pair<FileClass, Object>>> directories = new Stack<>();
        directories.push(oldFiles);
        while (!directories.isEmpty()) {
            for (Pair<FileClass, Object> p : directories.pop()) {
                if (!p.getFirst().file().exists()) fileFile.put(p.getFirst().file(), FindReplacement(newFiles, p.getFirst()));
                else if (p.getFirst().isDir) directories.push((List<Pair<FileClass, Object>>) p.getSecond());
            }
        }
        return fileFile;
    }

    private File FindReplacement(List<Pair<FileClass, Object>> newFiles, FileClass file) {
        for (Pair<FileClass, Object> p : newFiles) {
            if (file.equals(p.getFirst())) return p.getFirst().file();
            else if (!file.isDir && !p.getFirst().isDir) {
                if (file.dataSize == p.getFirst().dataSize && file.modified == p.getFirst().modified && file.type.equals(p.getFirst().type) && file.dataSize == p.getFirst().dataSize) return p.getFirst().file;
            }
            else if (p.getFirst().isDir) {
                File temp = FindReplacement((List<Pair<FileClass, Object>>) p.getSecond(), file);
                if (temp != null) return temp;
            }
        }
        return null;
    }

    public List<Pair<FileClass, Object>> RecursiveFindAll(File dir) {
        File[] files = dir.listFiles();
        List<Pair<FileClass, Object>> list = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) list.add(new Pair<>(new FileClass(f), RecursiveFindAll(f)));
                else list.add(new Pair<>(new FileClass(f), f.lastModified()));
            }
        }
        return list;
    }

    private void LoadFiles(String homePath) throws IOException {
        fileList = new ArrayList<>();
        fileList = (List<Pair<FileClass, Object>>) AccessFiles(0, homePath);
        if (fileList == null) SetHomeErr();
    }

    public synchronized String SendAndReceive(String msg) throws NullPointerException {
        return SendAndReceiveSync(msg);
    }

    public void SetHomeErr() {
        gui.SetVisible();
        gui.HomeDir(true);
        gui.Sync(sync.get());
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

    private String ServerSync(String homeDir) throws NullPointerException, IOException {
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
                int count;
                byte[] bytes = new byte[32*1024];
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
        }
        catch (IOException e) {
            firstSync.set(false);
            gui.SetLoggedOut();
            return null;
        }


        return "approved";
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
}

