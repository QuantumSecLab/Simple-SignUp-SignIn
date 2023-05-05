import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class SimpleSignUpSignInClient {

    class CommandID {
        public static final int REG_REQ = 1;

        public static final int REG_RESP = 2;

        public static final int LOGIN_REQ = 3;

        public static final int LOGIN_RESP = 4;
    }

    class StatusCode {
        public static final int SUCCESS = 0;

        public static final int FAIL = -1;
    }

    private SocketChannel socketChannel;

    private Selector selector;

    private ByteBuffer buffer;

    private int bufferLength = 1 << 10;

    public SimpleSignUpSignInClient(String remoteIP, int remotePort) throws IOException {
        // Establish connection
        InetSocketAddress addr = new InetSocketAddress(remoteIP, remotePort);
        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        selector = Selector.open();
        socketChannel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        socketChannel.connect(addr);

        // allocate memory to byte buffer
        buffer = ByteBuffer.allocate(bufferLength);
    }

    public int sendRegReq() {
        // msg header
        int totalLength = 58;
        int commandID = CommandID.REG_REQ;

        // msg body
        String userName;
        System.out.println("Please input your username (which should no longer than 20 bytes in UTF-8): ");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            userName = scanner.nextLine().replaceAll("\n", "");
            if (userName.isEmpty()) {
                System.out.println("Empty username is not allowed.");
            } else if (userName.getBytes(StandardCharsets.UTF_8).length > 20) {
                System.out.println("Username length should no longer than 20 bytes in UTF-8.");
            } else break;
        }

        String pwd;
        System.out.println("Please input your password (which should no longer than 30 bytes in UTF-8): ");
        while (true) {
            pwd = scanner.nextLine().replaceAll("\n", "");
            if (pwd.isEmpty()) {
                System.out.println("Empty password is not allowed.");
            } else if (pwd.getBytes(StandardCharsets.UTF_8).length > 30) {
                System.out.println("Password length should no longer than 30 bytes in UTF-8.");
            } else break;
        }

        // put the msg header and body to the `buffer`
        buffer.clear();
        buffer.putInt(totalLength);
        buffer.putInt(commandID);

        buffer.put(userName.getBytes(StandardCharsets.US_ASCII));
        // padding
        int paddingLength = -1;
        byte[] zeros = null;
        if ((paddingLength = 20 - userName.getBytes(StandardCharsets.US_ASCII).length) != 0) {
            zeros = new byte[paddingLength];
            buffer.put(zeros);
        }

        buffer.put(pwd.getBytes(StandardCharsets.US_ASCII));
        // padding
        if ((paddingLength = 30 - pwd.getBytes(StandardCharsets.US_ASCII).length) != 0) {
            zeros = new byte[paddingLength];
            buffer.put(zeros);
        }

        if ()

    }

    public void launch() throws IOException {
        // Welcome info
        System.out.println("Welcome! Please select the operation you would like to do: \n" +
                "0: Sign Up\n" +
                "1: Sign in\n" +
                "2: Exit");

        // User input
        int operationCode = -1;
        while (true) {
            Scanner scanner = new Scanner(System.in);
            operationCode = scanner.nextInt();

            // Select the operation
            if (operationCode == 0) {
                // Sign up for the user
                int statusCode = sendRegReq();
                if (statusCode == StatusCode.SUCCESS) {

                } else if (statusCode == StatusCode.FAIL) {
                    
                } else {
                    // should never happen
                    System.out.println("Unknown error.");
                }

                break;
            } else if (operationCode == 1) {
                // Sign in for the user

                break;
            } else if (operationCode == 2) {
                // do nothing
                break;
            }
        }

        socketChannel.close();
    }

    public static void main(String args[]) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Please input the remote IP: ");
        String remoteIP = scanner.nextLine(); // should be validated
        scanner.nextLine(); // consume a newline character

        System.out.println("Please input the remote port: ");
        int remotePort = scanner.nextInt();

        try {
            SimpleSignUpSignInClient client = new SimpleSignUpSignInClient(remoteIP, remotePort);
            client.launch();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
