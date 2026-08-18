import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * 本地联调：用 docs/dev-keys 私钥签发伙伴 SSO Ticket，并打印回调 URL。
 *
 * 用法（在仓库根目录）:
 *   javac docs/dev-keys/IssueSsoTicket.java
 *   java -cp docs/dev-keys IssueSsoTicket wanxiang admin
 *   java -cp docs/dev-keys IssueSsoTicket shuzhi-iot admin http://localhost:3000 /home/dashboard
 */
public class IssueSsoTicket {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("用法: IssueSsoTicket <wanxiang|shuzhi-iot> <username> [webBase] [redirect]");
            System.err.println("示例: IssueSsoTicket wanxiang admin http://localhost:3000 /home/dashboard");
            System.exit(1);
        }

        String issuer = args[0].trim();
        String username = args[1].trim();
        String webBase = args.length >= 3 ? trimSlash(args[2]) : "http://localhost:3000";
        String redirect = args.length >= 4 ? args[3] : "/home/dashboard";

        String kid;
        String privateKeyFile;
        if ("wanxiang".equals(issuer)) {
            kid = "wanxiang-2026";
            privateKeyFile = "wanxiang-private.pem";
        } else if ("shuzhi-iot".equals(issuer)) {
            kid = "shuzhi-iot-2026";
            privateKeyFile = "shuzhi-iot-private.pem";
        } else {
            throw new IllegalArgumentException("不支持的 iss: " + issuer);
        }

        Path keyPath = Path.of("docs/dev-keys", privateKeyFile);
        if (!Files.exists(keyPath)) {
            keyPath = Path.of(privateKeyFile);
        }
        if (!Files.exists(keyPath)) {
            throw new IllegalStateException("找不到私钥: " + privateKeyFile);
        }

        PrivateKey privateKey = loadPrivateKey(keyPath);
        long now = System.currentTimeMillis() / 1000;
        String jti = UUID.randomUUID().toString();

        String headerJson = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + kid + "\"}";
        String payloadJson = "{"
                + "\"iss\":\"" + issuer + "\","
                + "\"aud\":\"yunqi-application-platform\","
                + "\"sub\":\"" + username + "\","
                + "\"username\":\"" + username + "\","
                + "\"iat\":" + now + ","
                + "\"nbf\":" + now + ","
                + "\"exp\":" + (now + 60) + ","
                + "\"jti\":\"" + jti + "\""
                + "}";

        String header = base64Url(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        String signature = base64Url(signRs256(privateKey, signingInput.getBytes(StandardCharsets.UTF_8)));
        String ticket = signingInput + "." + signature;

        String callback = webBase + "/sso/callback?ticket=" + urlEncode(ticket) + "&redirect=" + urlEncode(redirect);

        System.out.println("=== SSO Ticket 已签发 ===");
        System.out.println("iss      : " + issuer);
        System.out.println("username : " + username);
        System.out.println("kid      : " + kid);
        System.out.println("jti      : " + jti);
        System.out.println("exp      : iat+60s");
        System.out.println();
        System.out.println("ticket:");
        System.out.println(ticket);
        System.out.println();
        System.out.println("callback:");
        System.out.println(callback);
    }

    private static PrivateKey loadPrivateKey(Path path) throws Exception {
        String pem = Files.readString(path)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static byte[] signRs256(PrivateKey privateKey, byte[] data) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
