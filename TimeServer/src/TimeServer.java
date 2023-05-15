import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;

public class TimeServer {
    public static void main(String[] args) {

        try {
            sendTime();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static synchronized void sendTime() throws IOException {
        ServerSocket s=new ServerSocket(4409);
        while (true){
            StringBuilder sb = new StringBuilder();
            System.out.println("Waiting to connection...");
            Socket soc = s.accept();
            long requestIn = System.currentTimeMillis();
            DataOutputStream out = new DataOutputStream(soc.getOutputStream());
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
            long requestOut = System.currentTimeMillis();
            sb.append(requestIn).append("-").append(requestOut);
            out.writeBytes(sb.toString());
            out.close();
            soc.close();
            System.out.println(sdf.format(requestIn));
            System.out.println(sdf.format(requestOut));
        }
    }

    public static String getTimeFromServer(String hostServer) throws Exception {
        Socket soc=new Socket(hostServer,3306);
        BufferedReader in=new BufferedReader(new InputStreamReader(soc.getInputStream()));
        return in.readLine();
    }
}
