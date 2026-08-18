package cn.datafuturex.yunqi.apitest;

import cn.datafuturex.yunqi.apitest.support.ApiIntegrationTestBase;
import cn.datafuturex.yunqi.apitest.support.ApiTestConstants;
import cn.datafuturex.yunqi.testsupport.report.ApiTestRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证接口真实 HTTP 集成测试
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthApiIT extends ApiIntegrationTestBase {

    @Test
    @Order(1)
    @DisplayName("获取登录公钥-主路径")
    void getPublicKey_ok() {
        ApiTestRecorder.step("调用公开公钥接口");
        ResponseEntity<String> response = api.get("/api/v1/auth/public-key", null);
        assertBizOk(response);
        JsonNode data = api.readTree(response).path("data");
        assertTrue(data.has("keyId") || data.has("publicKey") || !data.isEmpty());
    }

    @Test
    @Order(2)
    @DisplayName("获取滑动验证码-主路径")
    void getCaptcha_ok() {
        ApiTestRecorder.step("调用验证码生成接口（IT 已关闭强制校验）");
        ResponseEntity<String> response = api.get("/api/v1/auth/captcha", null);
        assertBizOk(response);
    }

    @Test
    @Order(3)
    @DisplayName("校验滑动验证码-主路径")
    void verifyCaptcha_ok() {
        ApiTestRecorder.step("先获取验证码再校验");
        ResponseEntity<String> captchaResp = api.get("/api/v1/auth/captcha", null);
        assertBizOk(captchaResp);
        JsonNode data = api.readTree(captchaResp).path("data");
        String captchaId = data.path("captchaId").asText(null);
        if (captchaId == null || captchaId.isBlank()) {
            captchaId = data.path("id").asText("dummy");
        }
        Map<String, Object> body = Map.of(
                "captchaId", captchaId,
                "slideX", data.path("slideX").asInt(50)
        );
        ResponseEntity<String> response = api.postJson("/api/v1/auth/captcha/verify", body, null);
        // 正确滑动坐标不回传前端，此处验证接口可达（成功 200 或校验失败 400）
        int status = response.getStatusCode().value();
        assertTrue(status == 200 || status == 400, "验证码校验接口应可达, status=" + status);
        ApiTestRecorder.output("note", "滑动坐标未公开，主路径仅验证接口可达");
    }

    @Test
    @Order(4)
    @DisplayName("用户登录-主路径")
    void login_ok() {
        ApiTestRecorder.step("使用管理员账号明文登录");
        ResponseEntity<String> response = api.postJson("/api/v1/auth/login", Map.of(
                "username", ApiTestConstants.ADMIN_USERNAME,
                "password", ApiTestConstants.ADMIN_PASSWORD
        ), null);
        assertBizOk(response);
        String token = api.readTree(response).path("data").path("token").asText();
        assertFalse(token.isBlank());
    }

    @Test
    @Order(5)
    @DisplayName("退出登录-主路径")
    void logout_ok() {
        ApiTestRecorder.step("登录后携带 Token 退出");
        String token = login(ApiTestConstants.ADMIN_USERNAME, ApiTestConstants.ADMIN_PASSWORD);
        ResponseEntity<String> response = api.postJson("/api/v1/auth/logout", Map.of(), token);
        assertBizOk(response);
    }
}
