import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class SimpleSignUpSignInServer {
    /**
     * common capacity for all self-defined byte buffer
     */
    private int bufferSize = 1 << 10;

    /**
     * non-blocking server socket
     */
    private ServerSocketChannel serverSocketChannel;

    /**
     * Selector for server
     */
    private Selector selector;

    /**
     * TCP port number for server process
     */
    private static int portNumber = 11451;

    /**
     * username-pwd-salt table in runtime
     */
    private HashMap<String, PasswordEntry> userPasswordDictionary;

    /**
     * Map client socket channel to its byte buffer
     */
    private HashMap<SocketChannel, ByteBuffer> socket2BufferDictionary;

    /**
     * Default length when generating the password salt
     */
    private final int saltLength = 8;

    public SimpleSignUpSignInServer(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(port));
        serverSocketChannel.configureBlocking(false);

        selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        // read user passwords from file
        FileReader reader = new FileReader("pwd.txt");
        BufferedReader bufferedReader = new BufferedReader(reader);
        String line;
        userPasswordDictionary = new HashMap<>();
        while ((line = bufferedReader.readLine()) != null) {
            String[] parts = line.split(":");
            String username = parts[0];
            String salt = parts[1];
            String pwd = parts[2];
            PasswordEntry entry = new PasswordEntry(pwd, salt);
            if (!userPasswordDictionary.containsKey(username)) {
                userPasswordDictionary.put(username, entry);
            } else {
                System.out.println("[" + new Date() + "] \"" + username + "\" already exists. Duplicate ones will be ignored." );
            }
        }
        bufferedReader.close();
        reader.close();

        socket2BufferDictionary = new HashMap<>();
    }

    private void accept() throws IOException {
        SocketChannel socketChannel = serverSocketChannel.accept();

        // print log info
        System.out.println("[" + (new Date()).toString() + "] " + socketChannel.getRemoteAddress() + "connected to the server.");

        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

        // allocate a buffer for the socket
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        if (!userPasswordDictionary.containsKey(socketChannel)) {
            socket2BufferDictionary.put(socketChannel, buffer);
        } else {
            // should never happen
            throw new IOException("Duplicate socket channel was added to the dictionary.");
        }
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        // retrieve the buffer from the dictionary
        ByteBuffer buffer = socket2BufferDictionary.get(socketChannel);
        if (buffer == null) {
            // should never happen
            throw new IOException("The given socket channel does not have a buffer.");
        } else {
            // in write mode now
            socketChannel.read(buffer); // bulk read
            // switch to read mode
            buffer.flip();
        }
    }

    private void process(SelectionKey key) throws IOException, NoSuchAlgorithmException {
        // find corresponding socket channel by selection key
        SocketChannel socketChannel = (SocketChannel) key.channel();
        // find corresponding byte buffer by socket channel
        ByteBuffer buffer = socket2BufferDictionary.get(socketChannel);
        // test the buffer
        if (buffer == null) {
            // should never happen
            throw new IOException("The given socket channel does not have a buffer.");
        }

        // process all requests in one go
        while (true) {
            // in read mode now
            // read the msg header
            byte[] header = new byte[8];
            if (buffer.remaining() >= 8) {
                buffer.get(header);
            }
            else {
                // the msg header is not complete
                // switch to the write mode
                buffer.compact();
                return;
            }

            // parse the msg header
            int totalLength = ByteBuffer.wrap(header, 0, 4).getInt();
            int commandID = ByteBuffer.wrap(header, 4, 4).getInt();

            // test whether the msg body is complete
            if (buffer.remaining() < totalLength - 8) {
                // body is not complete
                // put header back to the buffer
                buffer.position(buffer.position() - 8);
                // switch to the write mode
                buffer.compact();
                return;
            }

            // read the msg body
            byte[] body = new byte[totalLength - 8];
            buffer.get(body);

            // initialize an output buffer
            ByteBuffer outputBuffer = ByteBuffer.allocate(bufferSize);

            // common local variables
            String status, description;

            // select the operation
            switch (commandID) {
                case CommandID.REG_REQ: {// reg req, respond with reg resp
                    // parse the msg body
                    // parse username
                    byte[] userNameBytes = new byte[FieldLength.regReqUserName];
                    System.arraycopy(body, 0, userNameBytes, 0, userNameBytes.length);
                    String userName = new String(userNameBytes, StandardCharsets.US_ASCII).trim();

                    // parse pwd
                    byte[] passwdBytes = new byte[FieldLength.regReqPasswd];
                    System.arraycopy(body, 20, passwdBytes, 0, passwdBytes.length);
                    String passwd = new String(passwdBytes, StandardCharsets.US_ASCII).trim();

                    // test whether username is duplicate
                    if (userPasswordDictionary.containsKey(userName)) {
                        System.out.println("[" + new Date() + "] " + socketChannel.getRemoteAddress() + " uses a duplicate username \"" + userName + "\".");

                        // encapsulate the response msg body
                        status = "0";
                        description = "duplicate username";
                    } else {
                        // generate the salt
                        String salt = RandomStringGenerator.generate(saltLength);
                        // generate SHA256 value
                        String sha256 = SHA256Utils.toHexString(SHA256Utils.getSHA(passwd + salt));
                        String line = userName + ":" + salt + ":" + sha256;
                        // add the user pwd info to the file
                        FileWriter writer = new FileWriter("pwd.txt", true);
                        writer.write(line + '\n');
                        writer.close();

                        // encapsulate the msg body to a byte array
                        status = "1";
                        description = "ok";

                        System.out.println("[" + new Date() + "] " + socketChannel.getRemoteAddress() + " registered successfully with the username \"" + userName + "\".");
                    }

                    // encapsulate the response msg body into byte array
                    byte[] effectiveResponseBody = (status + description).getBytes(StandardCharsets.US_ASCII);
                    byte[] responseBodyBytes = new byte[FieldLength.regRespStatus + FieldLength.regRespDescription];
                    System.arraycopy(effectiveResponseBody, 0, responseBodyBytes, 0, effectiveResponseBody.length);
                    Arrays.fill(responseBodyBytes, effectiveResponseBody.length, responseBodyBytes.length, (byte) 0);

                    // encapsulate the response msg header into byte array
                    byte[] responseHeaderBytes = new byte[FieldLength.header];
                    ByteBuffer responseHeaderBytesBuffer = ByteBuffer.allocate(FieldLength.header);
                    int responseMsgTotalLength = responseHeaderBytes.length + responseBodyBytes.length;
                    int responseMsgCommandID = CommandID.REG_RESP;
                    responseHeaderBytesBuffer.putInt(responseMsgTotalLength);
                    responseHeaderBytesBuffer.putInt(responseMsgCommandID);
                    responseHeaderBytesBuffer.flip();
                    responseHeaderBytesBuffer.get(responseHeaderBytes);

                    // concatenate the msg header and body
                    byte[] msg = new byte[responseMsgTotalLength];
                    System.arraycopy(responseHeaderBytes, 0, msg, 0, responseHeaderBytes.length);
                    System.arraycopy(responseBodyBytes, 0, msg, responseHeaderBytes.length, responseBodyBytes.length);

                    // wrap the msg with ByteBuffer
                    outputBuffer.put(msg);
                    outputBuffer.flip();

                    // send the msg
                    socketChannel.write(outputBuffer);
                    break;
                }
                case CommandID.LOGIN_REQ: {// login req
                    // parse the msg body
                    // parse the username
                    byte[] userNameBytes = new byte[FieldLength.loginReqUserName];
                    System.arraycopy(body, 0, userNameBytes, 0, userNameBytes.length);
                    String userName = new String(userNameBytes, StandardCharsets.US_ASCII).trim();

                    // parse the passwd
                    byte[] passwdBytes = new byte[FieldLength.loginReqPasswd];
                    System.arraycopy(body, userNameBytes.length, passwdBytes, 0, passwdBytes.length);
                    String passwd = new String(passwdBytes, StandardCharsets.US_ASCII).trim();

                    // find the username from dictionary
                    if (!userPasswordDictionary.containsKey(userName)) {
                        // username does not exist
                        status = "0";
                        description = "Invalid username or password.";
                    } else {
                        // compare the password
                        PasswordEntry entry = userPasswordDictionary.get(userName);
                        String sha256InDatabase = entry.getPwd();
                        String salt = entry.getSalt();
                        String sha256FromUser = SHA256Utils.toHexString(SHA256Utils.getSHA(passwd + salt));
                        if (sha256InDatabase.equals(sha256FromUser)) {
                            // the passwords match
                            status = "1";
                            description = "ok";
                        } else {
                            // the passwords not match
                            status = "0";
                            description = "Invalid username or password.";
                        }
                    }

                    // encapsulate the response msg body into byte array
                    byte[] effectiveResponseBody = (status + description).getBytes(StandardCharsets.US_ASCII);
                    byte[] responseBodyBytes = new byte[FieldLength.loginRespStatus + FieldLength.loginRespDescription];
                    System.arraycopy(effectiveResponseBody, 0, responseBodyBytes, 0, effectiveResponseBody.length);
                    Arrays.fill(responseBodyBytes, effectiveResponseBody.length, responseBodyBytes.length, (byte) 0);

                    // encapsulate the response msg header into byte array
                    byte[] responseHeaderBytes = new byte[FieldLength.header];
                    ByteBuffer responseHeaderBytesBuffer = ByteBuffer.allocate(FieldLength.header);
                    int responseMsgTotalLength = responseHeaderBytes.length + responseBodyBytes.length;
                    int responseMsgCommandID = CommandID.LOGIN_RESP;
                    responseHeaderBytesBuffer.putInt(responseMsgTotalLength);
                    responseHeaderBytesBuffer.putInt(responseMsgCommandID);
                    responseHeaderBytesBuffer.flip();
                    responseHeaderBytesBuffer.get(responseHeaderBytes);

                    // concatenate the msg header and body
                    byte[] msg = new byte[responseMsgTotalLength];
                    System.arraycopy(responseHeaderBytes, 0, msg, 0, responseHeaderBytes.length);
                    System.arraycopy(responseBodyBytes, 0, msg, responseHeaderBytes.length, responseBodyBytes.length);

                    // wrap the msg with ByteBuffer
                    outputBuffer.put(msg);
                    outputBuffer.flip();

                    // send the msg
                    socketChannel.write(outputBuffer);

                    break;
                }
                default: {// should never happen
                    throw new IOException("invalid command ID.");
                }
            }
        }
    }

    private void launch() throws IOException, NoSuchAlgorithmException {
        while (true) {
            selector.select();
            Set<SelectionKey> selectionKeySet = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeySet.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();

                if (key.isAcceptable()) {
                    accept();
                } else if (key.isReadable()) {
                    read(key);
                    process(key);
                }
            }
        }
    }

    public static void main(String args[]) {
        System.out.println("Please input the port number for the server:");
        Scanner scanner = new Scanner(System.in);
        while ((portNumber = scanner.nextInt()) <= 1024 || portNumber > 65535 ) {
            if (portNumber <= 1024) {
                System.out.println("Invalid port number or the port number conflicts with well-known ones.");
            } else if (portNumber > 65535) {
                System.out.println("Invalid port number.");
            }
        }

        try {
            SimpleSignUpSignInServer server = new SimpleSignUpSignInServer(portNumber);
            server.launch();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
