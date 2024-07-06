import libs.FileClass;
import libs.Pair;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileManager {

    private final File HomeDirectory;
    private final UserTracking users;
    public AtomicBoolean active;
    private final String email;

    public FileManager(String email, UserTracking users) {
        this.email = email;
        active = new AtomicBoolean(true);
        this.users = users;
        HomeDirectory = new File("User Folders/" + email);
        if (!HomeDirectory.exists()) {
            HomeDirectory.mkdir();
        }
    }
    //0 == sync
    //1 == unsync
    //2 == Upload
    public synchronized Object Access(int operation, DataOutputStream dialogOut, DataInputStream dialogIn, InputStream inputStream, OutputStream outputStream, Socket socket, MessageHandlerThread thread) throws IOException {
        if (!active.get() || !thread.valid.get()) return null;
        else if (!thread.sync.get() && thread.update.get()) {
            thread.update.set(false);
            thread.filesDeleted.clear();
            thread.filesAdded.clear();
            thread.filesRenamed.clear();
        }
        else if (thread.sync.get() && thread.update.get()) {
            FileUploader(dialogOut, dialogIn, inputStream, outputStream, thread);
            thread.foldersAdded.clear();
            thread.filesAdded.clear();
            thread.filesRenamed.clear();
            thread.filesDeleted.clear();
            thread.update.set(false);
        }
        if (dialogOut != null) {
            dialogOut.writeUTF("good");
            dialogOut.flush();
        }
        if (operation == 0) {
            try {
                Sync(dialogOut, dialogIn, inputStream, outputStream);
                thread.filesRenamed.clear();
                thread.filesAdded.clear();
                thread.filesDeleted.clear();
                thread.sync.set(true);
                users.Log(2, new String[]{email}, thread, null, null, null, null);
                thread.update.set(false);
                return "good";
            } catch (IOException e) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
        else if (operation == 2) {
            try {
                Upload(dialogOut, dialogIn, inputStream, thread);
                users.Log(2, new String[]{email}, thread, null, null, null, null);
                thread.update.set(false);
                thread.filesRenamed.clear();
                thread.filesAdded.clear();
                thread.filesDeleted.clear();
                return true;
            } catch (IOException e) {
                try {
                    socket.close();
                    return null;
                } catch (IOException ignored) {}
            }
        }
        else if (operation == 8) return users.Log(8, new String[]{email}, null, null, null, null, null);
        return false;
    }

    private void FileUploader(DataOutputStream dialogOut, DataInputStream dialogIn, InputStream inputStream, OutputStream outputStream, MessageHandlerThread thread) throws IOException {
        AddFile(thread.foldersAdded, dialogOut, dialogIn, inputStream, outputStream);
        RenameFile(thread.filesRenamed, dialogOut);
        DeleteFile(thread.filesDeleted, dialogOut);
        AddFile(thread.filesAdded, dialogOut, dialogIn, inputStream, outputStream);
        dialogOut.writeUTF("*");
        dialogOut.flush();
    }

    private void DeleteFile(List<File> files, DataOutputStream dialogOut) throws IOException {
        for (File f : files) {
            dialogOut.writeUTF("delete");
            dialogOut.writeUTF(ConcatFile(f));
            dialogOut.flush();
        }
    }

    private void RenameFile(HashMap<File, File> fileToRename, DataOutputStream dialogOut) throws IOException {
        for (Map.Entry<File, File> e : fileToRename.entrySet()) {
            dialogOut.writeUTF("rename");
            dialogOut.writeUTF(ConcatFile(e.getKey()));
            dialogOut.writeUTF(ConcatFile(e.getValue()));
            dialogOut.flush();
        }
    }

    private void AddFile(List<File> files, DataOutputStream dialogOut, DataInputStream dialogIn, InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer;
        long bytesLeft;
        int n;
        for (File f : files) {
            dialogOut.writeUTF("add");
            if (f.isDirectory()) dialogOut.writeUTF("folder");
            else dialogOut.writeUTF("file");
            dialogOut.writeUTF(ConcatFile(f));
            dialogOut.writeLong(f.lastModified());
            dialogOut.flush();
            if (!f.isDirectory()) {
                dialogOut.writeLong(f.length());
                dialogOut.flush();
                if (!dialogIn.readUTF().equals("skip")) {
                    InputStream fileStream = new FileInputStream(f);
                    bytesLeft = f.length();
                    buffer = new byte[8192];
                    while (bytesLeft > 0) {
                        n = fileStream.read(buffer, 0, (int) Math.min(buffer.length, bytesLeft));
                        if (n < 0) {
                            throw new EOFException("Expected " + bytesLeft + " more bytes to read");
                        }
                        outputStream.write(buffer, 0, n);
                        bytesLeft -= n;
                    }
                    fileStream.close();
                }
            }
        }
    }

    private void Upload(DataOutputStream dialogOut, DataInputStream dialogIn, InputStream inputStream, MessageHandlerThread thread) throws IOException {
        String args;
        String[] split;
        List<File> filesAdded = new ArrayList<>(), filesDeleted = new ArrayList<>(), foldersAdded = new ArrayList<>();
        HashMap<File, File> filesRenamed = new HashMap<>();
        while (!(args = dialogIn.readUTF()).equals("*")) {
            System.out.println(args);
            String oldName, newName;
            File oldFile, newFile, deleteTemp;
            split = args.split(" ");
            if (split[0].equals("delete")) {
                oldName = dialogIn.readUTF();
                oldFile = GetFile(oldName, HomeDirectory.getAbsolutePath());
                if (oldFile.isDirectory()) {
                    System.out.println("Deleted Directory: " + oldFile.getAbsolutePath());
                    Delete(oldFile);
                }
                else {
                    System.out.println("Deleted File: " + oldFile.getAbsolutePath());
                    newFile = new File(oldFile.getAbsolutePath() + ".deleted");
                    if (newFile.exists()) {
                        newFile.setLastModified(System.currentTimeMillis());
                        oldFile.delete();
                    }
                    else {
                        oldFile.delete();
                        newFile.createNewFile();
                    }
                }
                filesDeleted.add(oldFile);
            }
            else if (split[0].equals("rename")) {
                oldName = dialogIn.readUTF();
                newName = dialogIn.readUTF();
                oldFile = GetFile(oldName, HomeDirectory.getAbsolutePath());
                newFile = GetFile(newName, HomeDirectory.getAbsolutePath());
                deleteTemp = new File(newFile.getAbsolutePath() + ".deleted");
                System.out.println("Renamed File: " + oldFile.getAbsolutePath());
                if (deleteTemp.exists()) deleteTemp.delete();
                else if (newFile.exists()) newFile.delete();
                deleteTemp = new File(oldFile.getAbsolutePath() + ".deleted");
                deleteTemp.createNewFile();
                oldFile.renameTo(newFile);
                oldFile = newFile;
                oldFile.setLastModified(System.currentTimeMillis());
                filesRenamed.put(oldFile, newFile);
            }
            else {
                String type = dialogIn.readUTF();
                String name = dialogIn.readUTF();
                newFile = GetFile(name, HomeDirectory.getAbsolutePath());
                deleteTemp = new File(newFile.getAbsolutePath() + ".deleted");
                long modified = dialogIn.readLong();
                if (type.equals("file")) {
                    long bytesLeft = dialogIn.readLong();
                    if (deleteTemp.exists()) deleteTemp.delete();
                    if (newFile.exists()) newFile.delete();
                    MakeFile(newFile);
                    OutputStream fileWrite = new FileOutputStream(newFile);
                    dialogOut.writeUTF(">");
                    dialogOut.flush();

                    int n;
                    byte[] buffer = new byte[8192];
                    while (bytesLeft > 0) {
                        n = inputStream.read(buffer, 0, (int) Math.min(buffer.length, bytesLeft));
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
                    if (deleteTemp.exists()) {
                        deleteTemp.renameTo(newFile);
                        deleteTemp = newFile;
                        deleteTemp.setLastModified(modified);
                    }
                    else if (newFile.exists()) newFile.setLastModified(Long.max(newFile.lastModified(), modified));
                    else {
                        MakeFolder(newFile);
                        newFile.setLastModified(modified);
                    }
                    foldersAdded.add(newFile);
                }
            }
        }
        users.Log(3, new String[]{email}, thread, filesAdded, filesDeleted, foldersAdded, filesRenamed);
    }

    private void MakeFile(File newFile) throws IOException {
        String name = ConcatFile(newFile);
        StringBuilder homepath = new StringBuilder(HomeDirectory.getAbsolutePath());
        while (name.contains("\\")) {
            homepath.append("\\").append(name.substring(0, name.indexOf("\\")));
            name = name.substring(name.indexOf("\\") + 1);
            File dir = new File(homepath.toString());
            if (!dir.exists()) dir.mkdir();
        }
        homepath.append("\\").append(name);
        File file = new File(homepath.toString());
        file.createNewFile();
    }

    private void MakeFolder(File newFile) throws IOException {
        String name = ConcatFile(newFile);
        StringBuilder homepath = new StringBuilder(HomeDirectory.getAbsolutePath());
        while (name.contains("\\")) {
            homepath.append("\\").append(name.substring(0, name.indexOf("\\")));
            name = name.substring(name.indexOf("\\") + 1);
            File dir = new File(homepath.toString());
            if (!dir.exists()) dir.mkdir();
        }
        homepath.append("\\").append(name);
        File file = new File(homepath.toString());
        file.mkdir();
    }

    private void Delete(File file) throws IOException {
        File deletedFile = new File(file.getAbsolutePath() + ".deleted");
        file.renameTo(deletedFile);
        deletedFile.setLastModified(System.currentTimeMillis());
        File[] files = deletedFile.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.getName().contains(".") || !f.getName().substring(f.getName().lastIndexOf(".") + 1).equals("deleted")) {
                    deletedFile = new File(f.getAbsolutePath() + ".deleted");
                    System.out.println(deletedFile.getAbsolutePath());
                    if (!deletedFile.exists()) {
                        if (f.isDirectory()) Delete(f);
                        else {
                            deletedFile.createNewFile();
                            f.delete();
                        }
                    }
                    else {
                        f.delete();
                        deletedFile.setLastModified(System.currentTimeMillis());
                    }
                }
            }
        }
    }


    private long GetLastSync() {
        try {
            Scanner scanner = new Scanner(new File("users.txt"));
            String[] split;
            while (scanner.hasNext()) {
                split = scanner.nextLine().split(" ");
                if (split[0].equals(email)) {
                    scanner.close();
                    return Long.parseLong(split[2]);
                }
            }
        } catch (FileNotFoundException e) {
            File file = new File("users.txt");
            try {
                file.createNewFile();
                return GetLastSync();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        return -1;
    }

    private void Sync(DataOutputStream dialogOut, DataInputStream dialogIn, InputStream inputStream, OutputStream outputStream) throws IOException {
        CleanUpDeleted(HomeDirectory);
        RecursiveFindAllFiles(dialogOut, HomeDirectory);
        dialogOut.writeUTF("*");
        dialogOut.flush();

        SendFiles(dialogOut, dialogIn, outputStream);
        GetFolders(dialogIn);
        ReceiveFiles(dialogOut, dialogIn, inputStream);
    }

    private void ReceiveFiles(DataOutputStream dialogOut, DataInputStream dialogIn, InputStream inputStream) throws IOException {
        String name;
        long modified, size;
        List<File> filesAdded = new ArrayList<>();
        while (!(name = dialogIn.readUTF()).equals("*")) {
            System.out.println("Downloading: " + name);
            File file = GetFile(name, HomeDirectory.getAbsolutePath());
            File temp = GetFile(name + ".deleted", HomeDirectory.getAbsolutePath());
            if (temp.exists()) temp.delete();
            if (file.exists()) file.delete();
            modified = dialogIn.readLong();
            size = dialogIn.readLong();
            file.createNewFile();
            OutputStream fileWrite = new FileOutputStream(file);
            dialogOut.writeUTF(">");
            dialogOut.flush();
            int n;
            long bytesLeft = size;
            byte[] buffer = new byte[8192];
            while (bytesLeft > 0) {
                n = inputStream.read(buffer, 0, (int) Math.min(buffer.length, bytesLeft));
                if (n < 0) {
                    throw new EOFException("Expected " + bytesLeft + " more bytes to read");
                }
                fileWrite.write(buffer, 0, n);
                bytesLeft -= n;
            }
            fileWrite.close();
            file.setLastModified(modified);
            filesAdded.add(file);
        }
        users.Log(3, new String[]{email}, null, filesAdded, new ArrayList<>(), new ArrayList<>(), new HashMap<>());
    }

    private void SetModified(long time, File file, String homePath) {
        file.getParentFile().setLastModified(time);
        if (file.getParentFile().getAbsolutePath().length() > homePath.length()) SetModified(time, file.getParentFile(), homePath);
    }

    private void GetFolders(DataInputStream dialogIn) throws IOException {
        String args;
        List<File> foldersAdded = new ArrayList<>();
        while (!(args = dialogIn.readUTF()).equals("*")) {
            File dir = GetFile(args, HomeDirectory.getAbsolutePath());
            File deleted = GetFile(args + ".deleted", HomeDirectory.getAbsolutePath());
            if (deleted.exists()) {
                deleted.renameTo(dir);
                foldersAdded.add(dir);
            }
            else if (!dir.exists()) {
                dir.mkdir();
                foldersAdded.add(dir);
            }
        }
        users.Log(3, new String[]{email}, null, new ArrayList<>(), new ArrayList<>(), foldersAdded, new HashMap<>());
    }

    private void SendFiles(DataOutputStream dialogOut, DataInputStream dialogIn, OutputStream outputStream) throws IOException {
        String name;
        int n;
        while (!(name = dialogIn.readUTF()).equals("*")) {
            File file = new File(HomeDirectory.getAbsolutePath() + "/" + name);
            InputStream inputStream = new FileInputStream(file);
            long bytesLeft = file.length();
            dialogOut.writeLong(bytesLeft);
            dialogOut.writeLong(file.lastModified());
            dialogOut.flush();
            dialogIn.readUTF();
            byte[] buffer = new byte[8192];
            while (bytesLeft > 0) {
                n = inputStream.read(buffer, 0, (int) Math.min(buffer.length, bytesLeft));
                if (n < 0) {
                    throw new EOFException("Expected " + bytesLeft + " more bytes to read");
                }
                outputStream.write(buffer, 0, n);
                bytesLeft -= n;
            }
            inputStream.close();
        }
    }

    private boolean CleanUpDeleted(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return true;
        else {
            for (File f : files) {
                if (f.getName().contains(".") && f.getName().substring(f.getName().lastIndexOf(".") + 1).equals("deleted")) {
                    if (f.isDirectory()) {
                        if (CleanUpDeleted(f) && System.currentTimeMillis() - f.lastModified() > 2.592e+9) f.delete();
                    }
                    else if (System.currentTimeMillis() - f.lastModified() > 2.592e+9) f.delete();
                }
            }
        }
        return dir.listFiles() == null;
    }

    private void RecursiveFindAllFiles(DataOutputStream dialogOut, File dir) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.getName().contains(".") || !f.getName().substring(f.getName().lastIndexOf(".") + 1).equals("deleted")) {
                dialogOut.writeUTF(ConcatFile(f));
                dialogOut.writeUTF(String.valueOf(f.lastModified()));
                if (f.isDirectory()) dialogOut.writeUTF("folder");
                else dialogOut.writeUTF("file");
                dialogOut.flush();
                if (f.isDirectory()) RecursiveFindAllFiles(dialogOut, f);
            }
        }
    }

    private String ConcatFile(File file) {
        String path = file.getAbsolutePath(), home = HomeDirectory.getAbsolutePath();
        return path.substring(home.length() + 1);
    }

    private File GetFile(String filePath, String homePath) {
        return new File(homePath + "/" + filePath);
    }
}
