package cn.datafuturex.zhishu.apitest;

import cn.datafuturex.zhishu.apitest.support.ApiIntegrationTestBase;
import cn.datafuturex.zhishu.testsupport.report.ApiTestRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 系统监控接口真实 HTTP 集成测试
 */
class SystemMonitorApiIT extends ApiIntegrationTestBase {

    @Test
    @DisplayName("健康检查-公开主路径")
    void health_ok() {
        ApiTestRecorder.step("无 Token 调用健康检查");
        ResponseEntity<String> response = api.get("/api/v1/system/health", null);
        assertBizOk(response);
        JsonNode status = api.readTree(response).path("data").path("status");
        assertTrue(status.isTextual() || !status.isMissingNode());
    }

    @Test
    @DisplayName("系统状态-授权主路径")
    void status_ok() {
        ApiTestRecorder.step("携带 admin Token 查询系统状态");
        ResponseEntity<String> response = api.get("/api/v1/system/status", adminToken);
        assertBizOk(response);
        assertTrue(api.readTree(response).path("data").has("jvm")
                || api.readTree(response).path("data").has("status"));
    }

    @Test
    @DisplayName("系统状态-未认证401")
    void status_unauthorized() {
        ApiTestRecorder.step("无 Token 访问受保护状态接口");
        ResponseEntity<String> response = api.get("/api/v1/system/status", null);
        assertUnauthorized(response);
    }
}
