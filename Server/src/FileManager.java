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

    private List<Pair<FileClass, Object>> activeFiles;
    private List<Pair<FileClass, Object>> deletedFiles;

    public FileManager(String email, UserTracking users) {
        this.email = email;
        active = new AtomicBoolean(true);
        this.users = users;
        HomeDirectory = new File("User Folders/" + email);
        UpdateMetaData();
        if (!HomeDirectory.exists()) {
            HomeDirectory.mkdir();
        }
    }
    //0 == sync
    //1 == unsync
    //2 == Upload
    public synchronized Object Access(int operation, DataOutputStream dialogOut, DataInputStream dialogIn, InputStream inputStream, OutputStream outputStream, Socket socket, MessageHandlerThread thread) throws IOException {
        if (!active.get() || !thread.valid.get()) return null;
        else if (!thread.sync.get() && thread.update.get()) thread.update.set(false);
        else if (thread.sync.get() && thread.update.get()) {

        }
        if (dialogOut != null) {
            dialogOut.writeUTF("good");
            dialogOut.flush();
        }
        if (operation == 0) {
            try {
                Sync(dialogOut, dialogIn, inputStream, outputStream);
                UpdateMetaData();
                thread.sync.set(true);
                users.Log(2, new String[]{email}, thread);
                thread.update.set(false);
                thread.activeFiles = new ArrayList<>(activeFiles);
                thread.deletedFiles = new ArrayList<>(deletedFiles);
                return "good";
            } catch (IOException e) {
                try {
                    socket.close();
                } catch (IOException _) {}
            }
        }
        else if (operation == 1) thread.sync.set(false);
        else if (operation == 2) {
            try {
                Upload(dialogOut, dialogIn, inputStream, thread);
                UpdateMetaData();
                users.Log(2, new String[]{email}, thread);
                thread.update.set(false);
                thread.activeFiles = new ArrayList<>(activeFiles);
                thread.deletedFiles = new ArrayList<>(deletedFiles);
                return true;
            } catch (IOException e) {
                try {
                    socket.close();
                    return null;
                } catch (IOException _) {}
            }
        }
        else if (operation == 8) return users.Log(8, new String[]{email}, null);
        return null;
    }

    private void Upload(DataOutputStream dialogOut, DataInputStream dialogIn, InputStream inputStream, MessageHandlerThread thread) throws IOException {
        String args;
        String[] split;
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
            }
            else if (split[0].equals("rename")) {
                oldName = dialogIn.readUTF();
                newName = dialogIn.readUTF();
                oldFile = GetFile(oldName, HomeDirectory.getAbsolutePath());
                newFile = GetFile(newName, HomeDirectory.getAbsolutePath());
                deleteTemp = new File(newFile.getAbsolutePath() + ".deleted");
                if (oldFile.isDirectory()) {
                    System.out.println("Renamed Directory: " + oldFile.getAbsolutePath());
                    if (newFile.exists()) {
                        Delete(newFile);
                        MoveTo(oldFile, newFile);
                    } else if (deleteTemp.exists()) MoveTo(oldFile, deleteTemp);
                    else MoveTo(oldFile, newFile);
                    Delete(oldFile);
                }
                else {
                    System.out.println("Renamed File: " + oldFile.getAbsolutePath());
                    if (deleteTemp.exists()) deleteTemp.delete();
                    else if (newFile.exists()) newFile.delete();
                    deleteTemp = new File(oldFile.getAbsolutePath() + ".deleted");
                    deleteTemp.createNewFile();
                    oldFile.renameTo(newFile);
                    oldFile = newFile;
                    oldFile.setLastModified(System.currentTimeMillis());
                }
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
                    newFile.createNewFile();
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
                }
                else {
                    if (deleteTemp.exists()) {
                        deleteTemp.renameTo(newFile);
                        deleteTemp = newFile;
                        deleteTemp.setLastModified(modified);
                    }
                    else if (newFile.exists()) newFile.setLastModified(Long.max(newFile.lastModified(), modified));
                    else {
                        newFile.mkdir();
                        newFile.setLastModified(modified);
                    }
                }
            }
        }
        UpdateMetaData();
    }

    private void MoveTo(File org, File dest) {
        if (!dest.exists()) dest.mkdir();
        if (dest.getName().contains(".") && dest.getName().substring(dest.getName().lastIndexOf(".") + 1).equals("deleted")) {
            File temp = new File(dest.getName().substring(0, dest.getName().lastIndexOf('.') - 1));
            dest.renameTo(temp);
            dest = temp;
        }
        List<String> destFiles = new ArrayList<>();
        File temp;
        File[] files = dest.listFiles();
        if (files != null) for (File f : files) destFiles.add(f.getName());
        files = org.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.getName().contains(".") || !f.getName().substring(f.getName().lastIndexOf(".") + 1).equals("deleted")) {
                    if (!f.isDirectory()) {
                        if (destFiles.contains(f.getName())) {
                            destFiles.remove(f.getName());
                            destFiles.add(f.getName() + ".deleted");
                        }
                        if (destFiles.contains(f.getName() + ".deleted")) {
                            temp = new File(dest.getAbsolutePath() + "/" + f.getName() + ".deleted");
                            temp.delete();
                        }
                        temp = new File(dest.getAbsolutePath() + "/" + f.getName());
                        f.renameTo(temp);
                        f = temp;
                    }
                    else {
                        temp = new File(dest.getAbsolutePath() + "/" + f.getName());
                        MoveTo(f, temp);
                    }
                }
            }
        }
    }

    private void Delete(File file) throws IOException {
        File deletedFile = new File(file.getAbsolutePath() + ".deleted");
        file.renameTo(deletedFile);
        file = deletedFile;
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

    private void UpdateMetaData() {
        activeFiles = RecursiveFindAllActiveFiles(HomeDirectory);
        deletedFiles = RecursiveFindAllDeletedFiles(HomeDirectory);
    }

    private List<Pair<FileClass, Object>> RecursiveFindAllDeletedFiles(File dir) {
        File[] files = dir.listFiles();
        List<Pair<FileClass, Object>> list = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.getName().contains(".") && f.getName().substring(f.getName().lastIndexOf(".") + 1).equals("deleted")) {
                    if (f.isDirectory()) list.add(new Pair<>(new FileClass(f), RecursiveFindAllActiveFiles(f)));
                    else list.add(new Pair<>(new FileClass(f), f.lastModified()));
                }
            }
        }
        return list;
    }

    private List<Pair<FileClass, Object>> RecursiveFindAllActiveFiles(File dir) {
        File[] files = dir.listFiles();
        List<Pair<FileClass, Object>> list = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (!f.getName().contains(".") || !f.getName().substring(f.getName().lastIndexOf(".") + 1).equals("deleted")) {
                    if (f.isDirectory()) list.add(new Pair<>(new FileClass(f), RecursiveFindAllActiveFiles(f)));
                    else list.add(new Pair<>(new FileClass(f), f.lastModified()));
                }
            }
        }
        return list;
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
        }
    }

    private void SetModified(long time, File file, String homePath) {
        file.getParentFile().setLastModified(time);
        if (file.getParentFile().getAbsolutePath().length() > homePath.length()) SetModified(time, file.getParentFile(), homePath);
    }

    private void GetFolders(DataInputStream dialogIn) throws IOException {
        String args;
        while (!(args = dialogIn.readUTF()).equals("*")) {
            File dir = GetFile(args, HomeDirectory.getAbsolutePath());
            File deleted = GetFile(args + ".deleted", HomeDirectory.getAbsolutePath());
            if (deleted.exists()) {
                deleted.renameTo(dir);
            }
            else if (!dir.exists()) dir.mkdir();
        }
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
