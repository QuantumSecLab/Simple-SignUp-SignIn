import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SimpleSignUpSignInClient {
    /**
     * Client socket
     */
    private SocketChannel socketChannel;

    /**
     * Client selector
     */
    private Selector selector;

    /**
     * self-defined client input buffer for bulk read
     */
    private ByteBuffer inputBuffer;

    /**
     * self-defined client output buffer
     */
    private ByteBuffer outputBuffer;

    /**
     * Global buffer size for client process
     */
    private int bufferLength = 1 << 10;

    public void closeServerSocket() throws IOException {
        this.socketChannel.close();
    }

    static class Worker implements Runnable {
        private Selector selector;

        private ByteBuffer inputBuffer;

        private int bufferLength = 1 << 10;

        public Worker(Selector selector) {
            this.selector = selector;
            this.inputBuffer = ByteBuffer.allocate(bufferLength);
        }
        @Override
        public void run() {
            while (true) {
                try {
                    selector.select();
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = keys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        iterator.remove();
                        if (key.isReadable()) {
                            // generate corresponding socket channel
                            SocketChannel socket = (SocketChannel) key.channel();

                            // bulk read
                            socket.read(inputBuffer);
                            // switch to the read mode
                            inputBuffer.flip();

                            // process all tasks in one go
                            while (true) {
                                // read the msg header
                                byte[] header = new byte[FieldLength.header];
                                if (inputBuffer.remaining() >= FieldLength.header) {
                                    inputBuffer.get(header);
                                }
                                else {
                                    // the msg header is not complete
                                    // switch to the write mode
                                    inputBuffer.compact();
                                    break;
                                }

                                // parse the msg header
                                int totalLength = ByteBuffer.wrap(header, 0, FieldLength.totalLengthField).getInt();
                                int commandID = ByteBuffer.wrap(header, FieldLength.totalLengthField, FieldLength.commandIDField).getInt();

                                // test whether the msg body is complete
                                if (inputBuffer.remaining() < totalLength - FieldLength.header) {
                                    // body is not complete
                                    // put header back to the buffer
                                    inputBuffer.position(inputBuffer.position() - FieldLength.header);
                                    // switch to the write mode
                                    inputBuffer.compact();
                                    break;
                                }

                                // read the msg body
                                byte[] body = new byte[totalLength - FieldLength.header];
                                inputBuffer.get(body);

                                // select the operation by commandID
                                switch (commandID) {
                                    case CommandID.REG_RESP: {
                                        // parse `status` field
                                        byte[] statusByte = new byte[FieldLength.regRespStatus];
                                        System.arraycopy(body, 0, statusByte, 0, FieldLength.regRespStatus);
                                        String status = new String(statusByte, StandardCharsets.US_ASCII);

                                        // parse `description` field
                                        byte[] descriptionBytes = new byte[FieldLength.regRespDescription];
                                        System.arraycopy(body, FieldLength.regRespStatus, descriptionBytes, 0, FieldLength.regRespDescription);
                                        String description = new String(descriptionBytes, StandardCharsets.US_ASCII).trim();

                                        // test the result
                                        switch (status) {
                                            case "0": {
                                                System.out.println("[" + new Date() + "] " + "Registration failed. The description is: " + description);
                                                break;
                                            }
                                            case "1": {
                                                System.out.println("[" + new Date() + "] " + "Registration succeeded. The description is: " + description);
                                                break;
                                            }
                                            default: {
                                                System.out.println("Invalid status code " + status + " was received.");
                                                break;
                                            }
                                        }
                                        break;
                                    }
                                    case CommandID.LOGIN_RESP: {
                                        // parse `status` field
                                        byte[] statusByte = new byte[FieldLength.loginRespStatus];
                                        System.arraycopy(body, 0, statusByte, 0, FieldLength.loginRespStatus);
                                        String status = new String(statusByte, StandardCharsets.US_ASCII);

                                        // parse `description` field
                                        byte[] descriptionBytes = new byte[FieldLength.loginRespDescription];
                                        System.arraycopy(body, FieldLength.loginRespStatus, descriptionBytes, 0, FieldLength.loginRespDescription);
                                        String description = new String(descriptionBytes, StandardCharsets.US_ASCII).trim();

                                        // test the result
                                        switch (status) {
                                            case "0": {
                                                System.out.println("[" + new Date() + "] " + "Login failed. The description is: " + description);
                                                break;
                                            }
                                            case "1": {
                                                System.out.println("[" + new Date() + "] " + "Login succeeded. The description is: " + description);
                                                break;
                                            }
                                            default: {
                                                System.out.println("Invalid status code " + status + " was received.");
                                                break;
                                            }
                                        }

                                        break;
                                    }
                                    default: {
                                        // should never happen
                                        System.out.println("Invalid command ID was received.");
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public Selector getSelector() {
        return this.selector;
    }

    public SimpleSignUpSignInClient(String remoteIP, int remotePort) throws IOException {
        // Establish connection
        InetSocketAddress addr = new InetSocketAddress(remoteIP, remotePort);
        socketChannel = SocketChannel.open(addr);
        socketChannel.configureBlocking(false);
        selector = Selector.open();
        socketChannel.register(selector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE);

        // allocate memory to byte buffer
        inputBuffer = ByteBuffer.allocate(bufferLength);
        outputBuffer = ByteBuffer.allocate(bufferLength);
    }

    private void sendRegReq() throws IOException {
        // msg header
        int totalLength = FieldLength.header + FieldLength.regReqUserName + FieldLength.regReqPasswd;
        int commandID = CommandID.REG_REQ;

        // msg body
        // get username from stdin
        String userName;
        System.out.println("Please input your username (which should no longer than 20 bytes in ASCII): ");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            // remove '\n'
            userName = scanner.nextLine().replaceAll("\n", "");
            if (userName.isEmpty()) {
                System.out.println("Empty username is not allowed.");
            } else if (userName.getBytes(StandardCharsets.US_ASCII).length > 20) {
                System.out.println("Username length should no longer than 20 bytes in ASCII.");
            } else break;
        }

        // get passwd from stdin
        String passwd;
        System.out.println("Please input your password (which should no longer than 30 bytes in ASCII): ");
        while (true) {
            // remove '\n'
            passwd = scanner.nextLine().replaceAll("\n", "");
            if (passwd.isEmpty()) {
                System.out.println("Empty password is not allowed.");
            } else if (passwd.getBytes(StandardCharsets.US_ASCII).length > 30) {
                System.out.println("Password length should no longer than 30 bytes in ASCII.");
            } else break;
        }

        // put the msg header and body to the `outputBuffer`
        outputBuffer.clear();
        outputBuffer.putInt(totalLength);
        outputBuffer.putInt(commandID);

        // put username to `outputBuffer`
        byte[] userNameBytes = new byte[FieldLength.regReqUserName];
        System.arraycopy(userName.getBytes(StandardCharsets.US_ASCII), 0, userNameBytes, 0, userName.getBytes(StandardCharsets.US_ASCII).length);
        Arrays.fill(userNameBytes, userName.getBytes(StandardCharsets.US_ASCII).length, userNameBytes.length, (byte) 0);
        outputBuffer.put(userNameBytes);

        // put passwd to `outputBuffer`
        byte[] passwdBytes = new byte[FieldLength.regReqPasswd];
        System.arraycopy(passwd.getBytes(StandardCharsets.US_ASCII), 0, passwdBytes, 0, passwd.getBytes(StandardCharsets.US_ASCII).length);
        Arrays.fill(passwdBytes, passwd.getBytes(StandardCharsets.US_ASCII).length, passwdBytes.length, (byte) 0);
        outputBuffer.put(passwdBytes);

        // switch to read mode
        outputBuffer.flip();

        // send reg req msg
        socketChannel.write(outputBuffer);
    }

    private void sendLoginReq() throws IOException {
        // msg header
        int totalLength = FieldLength.header + FieldLength.loginReqUserName + FieldLength.loginReqPasswd;
        int commandID = CommandID.LOGIN_REQ;

        // msg body
        // get username from stdin
        String userName;
        System.out.println("Please input your username (which should no longer than 20 bytes in ASCII): ");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            // remove '\n'
            userName = scanner.nextLine().replaceAll("\n", "");
            if (userName.isEmpty()) {
                System.out.println("Empty username is not allowed.");
            } else if (userName.getBytes(StandardCharsets.US_ASCII).length > 20) {
                System.out.println("Username length should no longer than 20 bytes in ASCII.");
            } else break;
        }

        // get passwd from stdin
        String passwd;
        System.out.println("Please input your password (which should no longer than 30 bytes in ASCII): ");
        while (true) {
            // remove '\n'
            passwd = scanner.nextLine().replaceAll("\n", "");
            if (passwd.isEmpty()) {
                System.out.println("Empty password is not allowed.");
            } else if (passwd.getBytes(StandardCharsets.US_ASCII).length > 30) {
                System.out.println("Password length should no longer than 30 bytes in ASCII.");
            } else break;
        }

        // put the msg header and body to the `outputBuffer`
        outputBuffer.clear();
        outputBuffer.putInt(totalLength);
        outputBuffer.putInt(commandID);

        // put username to `outputBuffer`
        byte[] userNameBytes = new byte[FieldLength.loginReqUserName];
        System.arraycopy(userName.getBytes(StandardCharsets.US_ASCII), 0, userNameBytes, 0, userName.getBytes(StandardCharsets.US_ASCII).length);
        Arrays.fill(userNameBytes, userName.getBytes(StandardCharsets.US_ASCII).length, userNameBytes.length, (byte) 0);
        outputBuffer.put(userNameBytes);

        // put passwd to `outputBuffer`
        byte[] passwdBytes = new byte[FieldLength.loginReqPasswd];
        System.arraycopy(passwd.getBytes(StandardCharsets.US_ASCII), 0, passwdBytes, 0, passwd.getBytes(StandardCharsets.US_ASCII).length);
        Arrays.fill(passwdBytes, passwd.getBytes(StandardCharsets.US_ASCII).length, passwdBytes.length, (byte) 0);
        outputBuffer.put(passwdBytes);

        // switch to read mode
        outputBuffer.flip();

        // send reg req msg
        socketChannel.write(outputBuffer);
    }

    public void launch() throws IOException, InterruptedException {
        Thread responseProcessor = new Thread(new Worker(this.selector));
        responseProcessor.setDaemon(true);
        responseProcessor.start();

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
                sendRegReq();
                Thread.sleep(2000);
            } else if (operationCode == 1) {
                // Sign in for the user
                sendLoginReq();
                Thread.sleep(2000);
            } else if (operationCode == 2) {
                // do nothing
                break;
            }
        }
    }

    public static void main(String args[]) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Please input the remote IP: ");
        String remoteIP = scanner.nextLine(); // should be validated

        System.out.println("Please input the remote port: ");
        int remotePort = scanner.nextInt();

        try {
            SimpleSignUpSignInClient client = new SimpleSignUpSignInClient(remoteIP, remotePort);
            client.launch();
            client.closeServerSocket();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
