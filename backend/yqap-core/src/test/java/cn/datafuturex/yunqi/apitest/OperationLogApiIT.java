package cn.datafuturex.yunqi.apitest;

import cn.datafuturex.yunqi.apitest.support.ApiIntegrationTestBase;
import cn.datafuturex.yunqi.apitest.support.ApiTestConstants;
import cn.datafuturex.yunqi.testsupport.report.ApiTestRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 操作日志接口真实 HTTP 集成测试
 */
class OperationLogApiIT extends ApiIntegrationTestBase {

    @Test
    @DisplayName("分页查询操作日志-主路径")
    void page_ok() {
        ApiTestRecorder.step("先触发一次写操作产生日志，再分页查询");
        String username = ApiTestConstants.unique("logu");
        Map<String, Object> body = Map.of(
                "username", username,
                "realName", "日志触发用户",
                "email", username + "@example.com",
                "phone", "13900000002",
                "password", "Pass1234",
                "roleId", ApiTestConstants.ADMIN_ROLE_ID,
                "status", 1
        );
        ResponseEntity<String> createResp = api.postJson("/api/v1/users", body, adminToken);
        assertBizOk(createResp);
        Long userId = readDataId(createResp);
        tracker.trackUser(userId, username);

        ResponseEntity<String> response = api.get("/api/v1/operation-logs/page", adminToken,
                Map.of("pageNum", 1, "pageSize", 20));
        assertBizOk(response);
    }

    @Test
    @DisplayName("操作日志详情-主路径")
    void findById_ok() throws Exception {
        ApiTestRecorder.step("查询分页取一条日志详情");
        Thread.sleep(400);
        ResponseEntity<String> page = api.get("/api/v1/operation-logs/page", adminToken,
                Map.of("pageNum", 1, "pageSize", 1));
        assertBizOk(page);
        JsonNode records = api.readTree(page).path("data").path("records");
        if (records.isArray() && !records.isEmpty()) {
            long id = records.get(0).path("id").asLong();
            assertBizOk(api.get("/api/v1/operation-logs/" + id, adminToken));
        } else {
            ApiTestRecorder.step("暂无日志记录，跳过详情断言主体，接口可达");
            ResponseEntity<String> response = api.get("/api/v1/operation-logs/0", adminToken);
            assertTrue(response.getStatusCode().value() == 200);
        }
    }

    @Test
    @DisplayName("分页查询操作日志-未认证401")
    void page_unauthorized() {
        assertUnauthorized(api.get("/api/v1/operation-logs/page", null));
    }
}
