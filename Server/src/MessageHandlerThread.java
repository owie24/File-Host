import libs.FileClass;
import libs.Pair;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class MessageHandlerThread implements Runnable{
    private final InputStream download;
    private final OutputStream upload;
    private final UserTracking users;
    private final Socket socket;
    public final DataOutputStream outputStream;
    public final DataInputStream inputStream;


    private FileManager manager;
    Socket client;
    public AtomicBoolean valid;
    public AtomicBoolean update;
    public AtomicBoolean sync;

    String email;

    public List<Pair<FileClass, Object>> activeFiles;
    public List<Pair<FileClass, Object>> deletedFiles;

    public MessageHandlerThread(Socket socket, UserTracking users) throws IOException {
        activeFiles = new ArrayList<>();
        deletedFiles = new ArrayList<>();
        sync = new AtomicBoolean(false);
        this.socket = socket;
        valid = new AtomicBoolean();
        update = new AtomicBoolean(false);
        download = socket.getInputStream();
        upload = socket.getOutputStream();
        outputStream = new DataOutputStream(upload);
        inputStream = new DataInputStream(download);
        outputStream.writeUTF("Here");
        outputStream.flush();
        this.users = users;
        this.client = socket;
        manager = null;
    }

    //new_user or existing_user, email, either pass or, key and HWID
    @Override
    public void run() {
        while (true) {
            try {
                email = null;
                valid.set(true);
                String in = inputStream.readUTF();
                String[] args = in.split(" ");
                switch (args[0]) {
                    case "existing_user" -> {
                        if (args.length == 3) {
                            String[] check = {args[1], args[2]};
                            if (users.Access(6, check).getFirst()) {
                                outputStream.writeUTF("approved");
                                outputStream.flush();
                                email = args[1];
                                Standby();
                            } else {
                                outputStream.writeUTF("failed pass_incorrect");
                                outputStream.flush();
                            }
                        } else {
                            String[] check1 = {args[3], args[2]}, check2 = {args[2]};
                            if (!users.Access(0, check1).getFirst() || !users.Access(1, check2).getSecond().equals(args[1])) {
                                System.out.println("Incorrect Key");
                                Thread.sleep(3000);
                                outputStream.writeUTF("failed invalid_key");
                                outputStream.flush();
                            } else {
                                System.out.println("Correct Key");
                                outputStream.writeUTF("approved");
                                outputStream.flush();
                                email = args[1];
                                Standby();
                            }
                        }
                    }
                    case "existing_user_remember" -> {
                        String[] check = {args[1], args[2]};
                        if (users.Access(6, check).getFirst()) {
                            String key = GenerateKey();
                            String[] access = {args[1], args[3], key};
                            Pair<Boolean, String> pair = users.Access(4, access);
                            if (pair.getFirst()) outputStream.writeUTF("approved " + key);
                            else outputStream.writeUTF("failed err_register");
                            outputStream.flush();
                            email = args[1];
                            Standby();
                        } else {
                            outputStream.writeUTF("failed pass_incorrect");
                            outputStream.flush();
                        }
                    }
                    case "new_user" -> {
                        String[] check = {args[1], args[2]};
                        Pair<Boolean, String> status = users.Access(2, check);
                        if (!status.getFirst()) {
                            outputStream.writeUTF("failed " + status.getSecond());
                            outputStream.flush();
                        } else {
                            File file = new File("user Folders/" + args[1]);
                            System.out.println(file.mkdir());
                            outputStream.writeUTF("approved add_user");
                            outputStream.flush();
                        }
                    }
                    case "ping" -> {
                        outputStream.writeUTF("ping");
                        outputStream.flush();
                    }
                    default -> {
                        outputStream.writeUTF("failed err");
                        outputStream.flush();
                    }
                }

            } catch(IOException e){
                return;
            } catch(InterruptedException e){
                throw new RuntimeException(e);
            }
        }
    }

    private void Standby() throws IOException {
        String[] args;
        args = new String[]{email};
        if ((manager = (FileManager) users.Log(0, args, this)) == null) {
            LogOut();
            return;
        }
        sync.set(true);
        while (true) {
            try {
                String text;
                text = inputStream.readUTF();
                args = text.split(" ");
                if (!valid.get()) {
                    LogOut();
                    return;
                }
                switch (args[0]) {
                    case "ping" -> {
                        outputStream.writeUTF("ping");
                        outputStream.flush();
                        System.out.println("ping");
                    }
                    case "logout" -> {
                        LogOut();
                    }
                    case "delete_account" -> {
                        System.out.println("HERE");
                        String[] check = {email};
                        boolean result = (boolean) manager.Access(8, null, null, null, null, socket, this);
                        if (result) outputStream.writeUTF("logout");
                        else outputStream.writeUTF("failed");
                        outputStream.flush();
                        if (result){
                            users.Log(1, check, this);
                            manager = null;
                            email = null;
                            return;
                        }
                    }
                    case "forget_device" -> {
                        String[] check = {args[2], args[1]};
                        Pair<Boolean, String> response = users.Access(5, check);
                        outputStream.writeUTF(response.getSecond());
                        outputStream.flush();
                    }
                    case "change_pass" -> {
                        String[] check = {args[1], args[2], args[3]};
                        outputStream.writeUTF(users.Access(7, check).getSecond());
                        outputStream.flush();
                    }
                    case "file" -> {
                        System.out.println(Arrays.toString(args));
                        switch (args[1]) {
                            case "sync" -> {
                                if (manager.Access(0, outputStream, inputStream, download, upload, socket, this) == null) {
                                    LogOut();
                                    return;
                                }
                            }
                            case "unsync" -> {
                                if (manager.Access(1, null, null, null, null, null, this) == null) {
                                    LogOut();
                                    return;
                                }
                            }
                            case "change" -> {
                                if (manager.Access(2, outputStream, inputStream, download, upload, socket, this) == null) {
                                    LogOut();
                                    return;
                                }
                            }
                            default -> {
                                outputStream.writeUTF("failed err");
                                outputStream.flush();
                            }
                        }
                    }
                    default -> {
                        outputStream.writeUTF("failed err");
                        outputStream.flush();
                    }
                }
            } catch (IOException e) {
                return;
            }
        }
    }

    private void LogOut() throws IOException {
        String[] check = {email};
        users.Log(1, check, this);
        manager = null;
        outputStream.writeUTF("logout");
        outputStream.flush();
        email = null;
    }

    private String GenerateKey() {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < 64; i++) {
            Random rand = new Random();
            int num = rand.nextInt(Integer.MAX_VALUE)%52 + 65;
            System.out.println(num);
            if (num >= 91 && num <= 96) num += 6;
            key.append((char) num);
        }
        return key.toString();
    }
}
