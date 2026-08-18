package cn.datafuturex.yunqi.apitest.support;

import cn.datafuturex.yunqi.testsupport.http.RecordingRestClient;
import cn.datafuturex.yunqi.testsupport.report.ApiTestRecorder;
import cn.datafuturex.yunqi.testsupport.report.ApiTestReportExtension;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 HTTP + MySQL 测试库集成测试基类
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "api-it"})
@TestPropertySource(properties = {
        "yunqi.captcha.enabled=false",
        "yunqi.login-crypto.enabled=false",
        "yunqi.table-sharding.enabled=false",
        "jwt.secret=yqap-api-it-secret-key-must-be-at-least-256-bits-long-for-hs256"
})
@ExtendWith(ApiTestReportExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ApiIntegrationTestBase {

    @DynamicPropertySource
    static void registerUploadPath(DynamicPropertyRegistry registry) {
        try {
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), "yqap-api-it-uploads");
            Files.createDirectories(dir);
            registry.add("yunqi.upload.path", () -> dir.toAbsolutePath().toString());
        } catch (Exception e) {
            throw new IllegalStateException("无法准备上传目录", e);
        }
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected RecordingRestClient api;
    protected TestDataTracker tracker;
    protected TestDataCleaner cleaner;

    protected String adminToken;

    @BeforeAll
    void bootClientAndLogin() {
        api = new RecordingRestClient(RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build());
        cleaner = new TestDataCleaner(jdbcTemplate);
        adminToken = login(ApiTestConstants.ADMIN_USERNAME, ApiTestConstants.ADMIN_PASSWORD);
    }

    @BeforeEach
    void initTracker() {
        tracker = new TestDataTracker();
        if (api == null) {
            api = new RecordingRestClient(RestClient.builder()
                    .baseUrl("http://localhost:" + port)
                    .build());
        }
    }

    @AfterEach
    void cleanupTrackedData() {
        if (cleaner != null && tracker != null) {
            ApiTestRecorder.step("清理本用例写入的测试数据");
            cleaner.cleanup(tracker);
        }
    }

    protected String login(String username, String password) {
        ApiTestRecorder.step("登录获取 Token: " + username);
        Map<String, Object> body = Map.of(
                "username", username,
                "password", password
        );
        ResponseEntity<String> response = api.postJson("/api/v1/auth/login", body, null);
        assertEquals(200, response.getStatusCode().value(), "登录 HTTP 状态");
        JsonNode root = api.readTree(response);
        assertEquals(200, root.path("code").asInt(), "登录业务码: " + response.getBody());
        String token = root.path("data").path("token").asText();
        assertTrue(token != null && !token.isBlank(), "Token 不应为空");
        return token;
    }

    protected void assertBizOk(ResponseEntity<String> response) {
        assertEquals(200, response.getStatusCode().value(), "HTTP 状态: " + response.getBody());
        assertEquals(200, api.resultCode(response), "业务 code: " + response.getBody());
    }

    protected void assertUnauthorized(ResponseEntity<String> response) {
        assertEquals(401, response.getStatusCode().value(), "应返回 401: " + response.getBody());
    }

    protected Long readDataId(ResponseEntity<String> response) {
        JsonNode data = api.readTree(response).path("data");
        if (data.isMissingNode() || data.isNull()) {
            return null;
        }
        if (data.isNumber()) {
            return data.asLong();
        }
        return data.path("id").isMissingNode() ? null : data.path("id").asLong();
    }
}
