import java.security.SecureRandom;
import java.util.Base64;

public class RandomStringGenerator {
    public static String generate(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        String encoded = Base64.getEncoder().withoutPadding().encodeToString(bytes);

        return encoded.substring(0, length);
    }
}
