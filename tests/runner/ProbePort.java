import java.net.InetAddress;
import java.net.ServerSocket;

/** Select on the same operating system as the JVM which will bind the port. */
class ProbePort {
    public static void main(String[] args) throws Exception {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            System.out.println(socket.getLocalPort());
        }
    }
}
