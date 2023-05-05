public class FieldLength {
    /**
     * The length of `totalLength` field in the msg header
     */
    public static final int totalLengthField = 4;

    /**
     * The length of `commandID` field in the msg header
     */
    public static final int commandIDField = 4;

    /**
     * The length of msg header for all packets
     */
    public static final int header = totalLengthField + commandIDField;

    /**
     * The length of `username` field in registration request msg body
     */
    public static final int regReqUserName = 20;

    /**
     * The length of `passwd` field in registration request msg body
     */
    public static final int regReqPasswd = 30;

    /**
     * The length of `status` field in registration response msg body
     */
    public static final int regRespStatus = 1;

    /**
     * The length of `description` field in registration response msg body
     */
    public static final int regRespDescription = 64;

    /**
     * The length of `username` field in login request msg body
     */
    public static final int loginReqUserName = 20;

    /**
     * The length of `passwd` field in login request msg body
     */
    public static final int loginReqPasswd = 30;

    /**
     * The length of `status` field in login response msg body
     */
    public static final int loginRespStatus = 1;

    /**
     * The length of `description` field in login response msg body
     */
    public static final int loginRespDescription = 64;
}
