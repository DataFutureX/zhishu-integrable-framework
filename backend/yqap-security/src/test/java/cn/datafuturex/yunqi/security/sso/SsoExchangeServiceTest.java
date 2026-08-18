package cn.datafuturex.yunqi.security.sso;

import cn.datafuturex.yunqi.config.SsoProperties;
import cn.datafuturex.yunqi.config.security.JwtUtil;
import cn.datafuturex.yunqi.modules.entity.UserEntity;
import cn.datafuturex.yunqi.modules.service.UserService;
import io.jsonwebtoken.Jwts;
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
    private Path tempPublicKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        wanxiangPrivateKey = keyPair.getPrivate();

        tempPublicKey = Files.createTempFile("wanxiang-public-", ".pem");
        tempPublicKey.toFile().deleteOnExit();
        writePublicPem(tempPublicKey, (RSAPublicKey) keyPair.getPublic());

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
        partner.setAlgorithm("RS256");
        partner.setPublicKey("file:" + tempPublicKey.toAbsolutePath());
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
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(publicKey.getEncoded());
        String pem = "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----\n";
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

    @Test
    @DisplayName("合法 Ticket 换票成功")
    void exchangeSuccess() {
        Instant now = Instant.now();
        String ticket = signTicket("admin", now, now.plusSeconds(60), UUID.randomUUID().toString());

        UserEntity user = new UserEntity();
        user.setUsername("admin");
        user.setStatus(1);
        when(userService.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("admin")).thenReturn("business.jwt.token");
        when(jwtUtil.getExpirationFromToken(anyString())).thenReturn(System.currentTimeMillis() + 86400000L);

        SsoExchangeResult result = exchangeService.exchange(ticket, "/home/dashboard");
        assertEquals("business.jwt.token", result.response().token());
        assertEquals("/home/dashboard", result.response().redirect());
        assertEquals(LoginChannel.WANXIANG, result.channel());
        assertEquals("admin", result.username());
    }

    @Test
    @DisplayName("同一 Ticket 第二次换票拒绝")
    void rejectReplay() {
        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString();
        String ticket = signTicket("admin", now, now.plusSeconds(60), jti);

        UserEntity user = new UserEntity();
        user.setUsername("admin");
        user.setStatus(1);
        when(userService.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("admin")).thenReturn("business.jwt.token");
        when(jwtUtil.getExpirationFromToken(anyString())).thenReturn(System.currentTimeMillis() + 86400000L);

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
}
