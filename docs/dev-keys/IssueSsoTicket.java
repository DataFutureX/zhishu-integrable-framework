import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
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
 *   # 国密 SM2（需 BouncyCastle 在 classpath，且存在 *-sm2-private.pem）:
 *   java -cp "docs/dev-keys;bcprov.jar" IssueSsoTicket wanxiang admin http://localhost:3000 /home/dashboard SM2
 *
 * 或使用: .\docs\dev-keys\issue-ticket.ps1 -Issuer wanxiang -Username admin [-Alg SM2]
 */
public class IssueSsoTicket {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("用法: IssueSsoTicket <wanxiang|shuzhi-iot> <username> [webBase] [redirect] [RS256|SM2]");
            System.err.println("示例: IssueSsoTicket wanxiang admin http://localhost:3000 /home/dashboard");
            System.err.println("示例: IssueSsoTicket wanxiang admin http://localhost:3000 /home/dashboard SM2");
            System.exit(1);
        }

        String issuer = args[0].trim();
        String username = args[1].trim();
        String webBase = args.length >= 3 ? trimSlash(args[2]) : "http://localhost:3000";
        String redirect = args.length >= 4 ? args[3] : "/home/dashboard";
        String alg = args.length >= 5 ? args[4].trim().toUpperCase() : "RS256";
        if (!"RS256".equals(alg) && !"SM2".equals(alg)) {
            throw new IllegalArgumentException("不支持的 alg: " + alg + "（仅 RS256 / SM2）");
        }

        String kid;
        String privateKeyFile;
        if ("wanxiang".equals(issuer)) {
            kid = "SM2".equals(alg) ? "wanxiang-sm2-2026" : "wanxiang-2026";
            privateKeyFile = "SM2".equals(alg) ? "wanxiang-sm2-private.pem" : "wanxiang-private.pem";
        } else if ("shuzhi-iot".equals(issuer)) {
            kid = "SM2".equals(alg) ? "shuzhi-iot-sm2-2026" : "shuzhi-iot-2026";
            privateKeyFile = "SM2".equals(alg) ? "shuzhi-iot-sm2-private.pem" : "shuzhi-iot-private.pem";
        } else {
            throw new IllegalArgumentException("不支持的 iss: " + issuer);
        }

        Path keyPath = Path.of("docs/dev-keys", privateKeyFile);
        if (!Files.exists(keyPath)) {
            keyPath = Path.of(privateKeyFile);
        }
        if (!Files.exists(keyPath)) {
            throw new IllegalStateException("找不到私钥: " + privateKeyFile
                    + ("SM2".equals(alg) ? "（请先生成 SM2 密钥对放入 docs/dev-keys）" : ""));
        }

        PrivateKey privateKey = loadPrivateKey(keyPath, alg);
        long now = System.currentTimeMillis() / 1000;
        String jti = UUID.randomUUID().toString();

        String headerJson = "{\"alg\":\"" + alg + "\",\"typ\":\"JWT\",\"kid\":\"" + kid + "\"}";
        String payloadJson = "{"
                + "\"iss\":\"" + issuer + "\","
                + "\"aud\":\"zhishu-integrable-framework\","
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
        byte[] sigBytes = "SM2".equals(alg)
                ? signSm2(privateKey, signingInput.getBytes(StandardCharsets.US_ASCII))
                : signRs256(privateKey, signingInput.getBytes(StandardCharsets.UTF_8));
        String signature = base64Url(sigBytes);
        String ticket = signingInput + "." + signature;

        String callback = webBase + "/sso/callback?ticket=" + urlEncode(ticket) + "&redirect=" + urlEncode(redirect);

        System.out.println("=== SSO Ticket 已签发 ===");
        System.out.println("iss      : " + issuer);
        System.out.println("username : " + username);
        System.out.println("alg      : " + alg);
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

    private static PrivateKey loadPrivateKey(Path path, String alg) throws Exception {
        String pem = Files.readString(path)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN EC PRIVATE KEY-----", "")
                .replace("-----END EC PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(pem);
        if ("SM2".equals(alg)) {
            ensureBc();
            return KeyFactory.getInstance("EC", "BC").generatePrivate(new PKCS8EncodedKeySpec(der));
        }
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static byte[] signRs256(PrivateKey privateKey, byte[] data) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    private static byte[] signSm2(PrivateKey privateKey, byte[] data) throws Exception {
        ensureBc();
        Signature signature = Signature.getInstance("SM3withSM2", "BC");
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    private static void ensureBc() throws Exception {
        if (Security.getProvider("BC") == null) {
            Class<?> clazz = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
            Security.addProvider((java.security.Provider) clazz.getDeclaredConstructor().newInstance());
        }
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
