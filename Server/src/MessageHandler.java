import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class MessageHandler implements Runnable{
    @Override
    public void run() {
        try {
            ServerSocket server = new ServerSocket(8888);
            UserTracking user = null;
            try {
                user = new UserTracking();
            }
            catch (IOException e) {
                System.exit(-1);
            }
            Socket client;
            while (true) {
                Thread.sleep(1000);
                client = server.accept();
                try {
                    Thread thread = new Thread(new MessageHandlerThread(client, user));
                    thread.start();
                }
                catch (IOException e) {
                    client.close();
                }
            }
        } catch (IOException e) {
            System.out.println("Failed to start server, restarting");
            System.out.println(e);
            run();
        } catch (InterruptedException e) {
            System.exit(1);
        }

    }
}
