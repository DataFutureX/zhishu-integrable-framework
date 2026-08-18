package cn.datafuturex.yunqi.apitest;

import cn.datafuturex.yunqi.apitest.support.ApiIntegrationTestBase;
import cn.datafuturex.yunqi.apitest.support.ApiTestConstants;
import cn.datafuturex.yunqi.testsupport.report.ApiTestRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 用户管理接口真实 HTTP 集成测试
 */
class UserApiIT extends ApiIntegrationTestBase {

    @Test
    @DisplayName("获取当前用户-主路径")
    void getMe_ok() {
        ResponseEntity<String> response = api.get("/api/v1/users/me", adminToken);
        assertBizOk(response);
        assertEquals("admin", api.readTree(response).path("data").path("username").asText());
    }

    @Test
    @DisplayName("获取当前用户-未认证401")
    void getMe_unauthorized() {
        assertUnauthorized(api.get("/api/v1/users/me", null));
    }

    @Test
    @DisplayName("更新当前用户资料-主路径")
    void updateMe_ok() {
        ApiTestRecorder.step("备份并更新 admin 资料后还原");
        ResponseEntity<String> before = api.get("/api/v1/users/me", adminToken);
        assertBizOk(before);
        JsonNode me = api.readTree(before).path("data");
        String originalName = me.path("realName").asText(null);
        String originalEmail = me.path("email").asText(null);
        String originalPhone = me.path("phone").asText(null);

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("realName", "API测试管理员");
        update.put("email", originalEmail);
        update.put("phone", originalPhone);
        ResponseEntity<String> response = api.putJson("/api/v1/users/me", update, adminToken);
        assertBizOk(response);

        Map<String, Object> restore = new LinkedHashMap<>();
        restore.put("realName", originalName);
        restore.put("email", originalEmail);
        restore.put("phone", originalPhone);
        assertBizOk(api.putJson("/api/v1/users/me", restore, adminToken));
    }

    @Test
    @DisplayName("修改当前用户密码-主路径")
    void changePassword_ok() {
        String username = ApiTestConstants.unique("u");
        Long userId = createUser(username, "Pass1234");
        tracker.trackUser(userId, username);

        String token = login(username, "Pass1234");
        ApiTestRecorder.step("测试用户修改自身密码");
        ResponseEntity<String> response = api.putJson("/api/v1/users/me/password", Map.of(
                "oldPassword", "Pass1234",
                "newPassword", "Pass5678"
        ), token);
        assertBizOk(response);

        String newToken = login(username, "Pass5678");
        assertNotNull(newToken);
    }

    @Test
    @DisplayName("创建用户-主路径")
    void create_ok() {
        String username = ApiTestConstants.unique("u");
        Long id = createUser(username, "Pass1234");
        tracker.trackUser(id, username);
        assertNotNull(id);
    }

    @Test
    @DisplayName("更新用户-主路径")
    void update_ok() {
        String username = ApiTestConstants.unique("u");
        Long id = createUser(username, "Pass1234");
        tracker.trackUser(id, username);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("realName", "更新后姓名");
        body.put("email", "apitest@example.com");
        body.put("phone", "13800138000");
        body.put("status", 1);
        assertBizOk(api.putJson("/api/v1/users", body, adminToken));
    }

    @Test
    @DisplayName("启用禁用用户-主路径")
    void updateStatus_ok() {
        String username = ApiTestConstants.unique("u");
        Long id = createUser(username, "Pass1234");
        tracker.trackUser(id, username);
        assertBizOk(api.putJson("/api/v1/users/" + id + "/status", Map.of("status", 0), adminToken));
        assertBizOk(api.putJson("/api/v1/users/" + id + "/status", Map.of("status", 1), adminToken));
    }

    @Test
    @DisplayName("重置用户密码-主路径")
    void resetPassword_ok() {
        String username = ApiTestConstants.unique("u");
        Long id = createUser(username, "Pass1234");
        tracker.trackUser(id, username);
        assertBizOk(api.putJson("/api/v1/users/" + id + "/password/reset",
                Map.of("newPassword", "Reset999"), adminToken));
        assertNotNull(login(username, "Reset999"));
    }

    @Test
    @DisplayName("查询用户详情-主路径")
    void findById_ok() {
        String username = ApiTestConstants.unique("u");
        Long id = createUser(username, "Pass1234");
        tracker.trackUser(id, username);
        ResponseEntity<String> response = api.get("/api/v1/users/" + id, adminToken);
        assertBizOk(response);
        assertEquals(username, api.readTree(response).path("data").path("username").asText());
    }

    @Test
    @DisplayName("查询用户角色-主路径")
    void getUserRole_ok() {
        String username = ApiTestConstants.unique("u");
        Long id = createUser(username, "Pass1234");
        tracker.trackUser(id, username);
        assertBizOk(api.get("/api/v1/users/" + id + "/role", adminToken));
    }

    @Test
    @DisplayName("分配用户角色-主路径")
    void assignRole_ok() {
        String username = ApiTestConstants.unique("u");
        Long id = createUser(username, "Pass1234");
        tracker.trackUser(id, username);
        assertBizOk(api.putJson("/api/v1/users/" + id + "/role",
                Map.of("roleId", ApiTestConstants.ADMIN_ROLE_ID), adminToken));
    }

    @Test
    @DisplayName("按用户名查询-主路径")
    void findByUsername_ok() {
        String username = ApiTestConstants.unique("u");
        Long id = createUser(username, "Pass1234");
        tracker.trackUser(id, username);
        ResponseEntity<String> response = api.get("/api/v1/users/username/" + username, adminToken);
        assertBizOk(response);
    }

    @Test
    @DisplayName("分页查询用户-主路径")
    void page_ok() {
        ResponseEntity<String> response = api.get("/api/v1/users/page", adminToken,
                Map.of("pageNum", 1, "pageSize", 10));
        assertBizOk(response);
    }

    @Test
    @DisplayName("分页查询用户-未认证401")
    void page_unauthorized() {
        assertUnauthorized(api.get("/api/v1/users/page", null));
    }

    @Test
    @DisplayName("删除用户-主路径")
    void delete_ok() {
        String username = ApiTestConstants.unique("u");
        Long id = createUser(username, "Pass1234");
        tracker.trackUser(id, username);
        assertBizOk(api.delete("/api/v1/users/" + id, adminToken));
        tracker.getUserIds().remove(id);
        tracker.getUsernames().remove(username);
    }

    private Long createUser(String username, String password) {
        ApiTestRecorder.step("创建测试用户 " + username);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("realName", "API测试用户");
        body.put("email", username + "@example.com");
        body.put("phone", "13900000001");
        body.put("password", password);
        body.put("roleId", ApiTestConstants.ADMIN_ROLE_ID);
        body.put("status", 1);
        ResponseEntity<String> response = api.postJson("/api/v1/users", body, adminToken);
        assertBizOk(response);
        Long id = readDataId(response);
        assertNotNull(id);
        return id;
    }
}
