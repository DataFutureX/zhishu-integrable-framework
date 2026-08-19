package cn.datafuturex.yunqi.security.sso;

import cn.datafuturex.yunqi.config.SsoProperties;
import cn.datafuturex.yunqi.config.security.JwtUtil;
import cn.datafuturex.yunqi.modules.entity.UserEntity;
import cn.datafuturex.yunqi.modules.service.UserService;
import cn.hutool.crypto.KeyUtil;
import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.asymmetric.SM2;
import io.jsonwebtoken.Jwts;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SsoExchangeServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtUtil jwtUtil;

    private SsoProperties ssoProperties;
    private SsoExchangeService exchangeService;
    private PrivateKey wanxiangPrivateKey;
    private PrivateKey wanxiangSm2PrivateKey;
    private Path tempPublicKey;
    private Path tempSm2PublicKey;

    @BeforeAll
    static void registerBc() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        wanxiangPrivateKey = keyPair.getPrivate();

        tempPublicKey = Files.createTempFile("wanxiang-public-", ".pem");
        tempPublicKey.toFile().deleteOnExit();
        writePublicPem(tempPublicKey, (RSAPublicKey) keyPair.getPublic());

        KeyPair sm2Pair = KeyUtil.generateKeyPair("SM2");
        wanxiangSm2PrivateKey = sm2Pair.getPrivate();
        tempSm2PublicKey = Files.createTempFile("wanxiang-sm2-public-", ".pem");
        tempSm2PublicKey.toFile().deleteOnExit();
        writePemObject(tempSm2PublicKey, "PUBLIC KEY", sm2Pair.getPublic().getEncoded());

        ssoProperties = new SsoProperties();
        ssoProperties.setEnabled(true);
        ssoProperties.setAudience("yunqi-application-platform");
        ssoProperties.setClockSkewSeconds(30);
        ssoProperties.setJtiTtlSeconds(180);
        ssoProperties.setDefaultRedirect("/home/dashboard");

        SsoProperties.Partner partner = new SsoProperties.Partner();
        partner.setEnabled(true);
        partner.setIssuer("wanxiang");
        partner.setDisplayName("万象");
        partner.setAlgorithm("RS256,SM2");
        partner.setPublicKey("file:" + tempPublicKey.toAbsolutePath());
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("wanxiang-2026", "file:" + tempPublicKey.toAbsolutePath());
        keys.put("wanxiang-sm2-2026", "file:" + tempSm2PublicKey.toAbsolutePath());
        partner.setPublicKeys(keys);
        partner.setTicketTtlMaxSeconds(120);
        partner.setUsernameClaim("username");
        Map<String, SsoProperties.Partner> partners = new LinkedHashMap<>();
        partners.put("wanxiang", partner);
        ssoProperties.setPartners(partners);

        exchangeService = new SsoExchangeService(
                ssoProperties,
                new SsoPublicKeyResolver(new DefaultResourceLoader()),
                new SsoJtiStore(),
                userService,
                jwtUtil);
    }

    private void writePublicPem(Path path, RSAPublicKey publicKey) throws Exception {
        writePemObject(path, "PUBLIC KEY", publicKey.getEncoded());
    }

    private void writePemObject(Path path, String type, byte[] der) throws Exception {
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        String pem = "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----\n";
        Files.writeString(path, pem, StandardCharsets.UTF_8);
    }

    private String signTicket(String username, Instant iat, Instant exp, String jti) {
        return Jwts.builder()
                .header().add("kid", "wanxiang-2026").and()
                .issuer("wanxiang")
                .audience().add("yunqi-application-platform").and()
                .subject(username)
                .claim("username", username)
                .id(jti)
                .issuedAt(Date.from(iat))
                .notBefore(Date.from(iat))
                .expiration(Date.from(exp))
                .signWith(wanxiangPrivateKey, Jwts.SIG.RS256)
                .compact();
    }

    private String signSm2Ticket(String username, Instant iat, Instant exp, String jti) {
        long iatSec = iat.getEpochSecond();
        long expSec = exp.getEpochSecond();
        String headerJson = "{\"alg\":\"SM2\",\"typ\":\"JWT\",\"kid\":\"wanxiang-sm2-2026\"}";
        String payloadJson = "{"
                + "\"iss\":\"wanxiang\","
                + "\"aud\":\"yunqi-application-platform\","
                + "\"sub\":\"" + username + "\","
                + "\"username\":\"" + username + "\","
                + "\"iat\":" + iatSec + ","
                + "\"nbf\":" + iatSec + ","
                + "\"exp\":" + expSec + ","
                + "\"jti\":\"" + jti + "\""
                + "}";
        String header = base64Url(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        SM2 sm2 = SmUtil.sm2(wanxiangSm2PrivateKey, null);
        byte[] signature = sm2.sign(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + base64Url(signature);
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private void stubAdminLogin() {
        UserEntity user = new UserEntity();
        user.setUsername("admin");
        user.setStatus(1);
        when(userService.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("admin")).thenReturn("business.jwt.token");
        when(jwtUtil.getExpirationFromToken(anyString())).thenReturn(System.currentTimeMillis() + 86400000L);
    }

    @Test
    @DisplayName("合法 Ticket 换票成功")
    void exchangeSuccess() {
        Instant now = Instant.now();
        String ticket = signTicket("admin", now, now.plusSeconds(60), UUID.randomUUID().toString());
        stubAdminLogin();

        SsoExchangeResult result = exchangeService.exchange(ticket, "/home/dashboard");
        assertEquals("business.jwt.token", result.response().token());
        assertEquals("/home/dashboard", result.response().redirect());
        assertEquals(LoginChannel.WANXIANG, result.channel());
        assertEquals("admin", result.username());
    }

    @Test
    @DisplayName("合法 SM2 Ticket 换票成功")
    void exchangeSuccessSm2() {
        Instant now = Instant.now();
        String ticket = signSm2Ticket("admin", now, now.plusSeconds(60), UUID.randomUUID().toString());
        stubAdminLogin();

        SsoExchangeResult result = exchangeService.exchange(ticket, "/home/dashboard");
        assertEquals("business.jwt.token", result.response().token());
        assertEquals(LoginChannel.WANXIANG, result.channel());
    }

    @Test
    @DisplayName("同一 Ticket 第二次换票拒绝")
    void rejectReplay() {
        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString();
        String ticket = signTicket("admin", now, now.plusSeconds(60), jti);
        stubAdminLogin();

        exchangeService.exchange(ticket, null);
        SsoException ex = assertThrows(SsoException.class, () -> exchangeService.exchange(ticket, null));
        assertEquals(401, ex.getCode());
        assertEquals("票据已使用", ex.getMessage());
    }

    @Test
    @DisplayName("用户不存在时拒绝")
    void rejectUnknownUser() {
        Instant now = Instant.now();
        String ticket = signTicket("ghost", now, now.plusSeconds(60), UUID.randomUUID().toString());
        when(userService.findByUsername("ghost")).thenReturn(Optional.empty());

        SsoException ex = assertThrows(SsoException.class, () -> exchangeService.exchange(ticket, null));
        assertEquals(401, ex.getCode());
        assertTrue(ex.getMessage().contains("账号未开通"));
    }

    @Test
    @DisplayName("SSO 关闭时拒绝")
    void rejectWhenDisabled() {
        ssoProperties.setEnabled(false);
        SsoException ex = assertThrows(SsoException.class, () -> exchangeService.exchange("a.b.c", null));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("非法 redirect 回落默认首页")
    void sanitizeRedirect() {
        assertEquals("/home/dashboard", exchangeService.sanitizeRedirect("https://evil.example"));
        assertEquals("/home/dashboard", exchangeService.sanitizeRedirect("//evil.example"));
        assertEquals("/ok", exchangeService.sanitizeRedirect("/ok"));
    }

    @Test
    @DisplayName("拒绝不支持的 alg")
    void rejectUnsupportedAlg() {
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = base64Url("{\"iss\":\"wanxiang\"}".getBytes(StandardCharsets.UTF_8));
        String ticket = header + "." + payload + ".sig";
        SsoException ex = assertThrows(SsoException.class, () -> exchangeService.exchange(ticket, null));
        assertEquals(401, ex.getCode());
        assertEquals("票据签名无效", ex.getMessage());
    }
}
