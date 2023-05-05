public class PasswordEntry {
    private String pwd;

    private String salt;

    public PasswordEntry(String pwd, String salt) {
        this.pwd = pwd;
        this.salt = salt;
    }

    public String getPwd() {
        return this.pwd;
    }

    public String getSalt() {
        return  this.salt;
    }
}
