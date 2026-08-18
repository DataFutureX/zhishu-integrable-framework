package cn.datafuturex.yunqi.apitest;

import cn.datafuturex.yunqi.apitest.support.ApiIntegrationTestBase;
import cn.datafuturex.yunqi.apitest.support.ApiTestConstants;
import cn.datafuturex.yunqi.testsupport.report.ApiTestRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公告管理接口真实 HTTP 集成测试
 */
class AnnouncementApiIT extends ApiIntegrationTestBase {

    @Test
    @DisplayName("订阅公告SSE-主路径")
    void stream_ok() throws Exception {
        ApiTestRecorder.step("建立 SSE 连接并立即断开");
        ApiTestRecorder.target("GET", "/api/v1/announcements/stream");
        HttpURLConnection conn = (HttpURLConnection) URI.create(
                "http://localhost:" + port + "/api/v1/announcements/stream").toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + adminToken);
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(1000);
        try {
            int code = conn.getResponseCode();
            String contentType = conn.getContentType();
            ApiTestRecorder.output("httpStatus", String.valueOf(code));
            ApiTestRecorder.output("contentType", contentType == null ? "" : contentType);
            assertEquals(200, code);
            assertTrue(contentType != null && contentType.contains("text/event-stream"),
                    "Content-Type: " + contentType);
        } finally {
            conn.disconnect();
        }
    }

    @Test
    @DisplayName("未读数量-主路径")
    void unreadCount_ok() {
        assertBizOk(api.get("/api/v1/announcements/unread-count", adminToken));
    }

    @Test
    @DisplayName("最近公告-主路径")
    void recent_ok() {
        assertBizOk(api.get("/api/v1/announcements/recent", adminToken, Map.of("limit", 5)));
    }

    @Test
    @DisplayName("已发布分页-主路径")
    void publishedPage_ok() {
        assertBizOk(api.get("/api/v1/announcements/published/page", adminToken,
                Map.of("pageNum", 1, "pageSize", 10)));
    }

    @Test
    @DisplayName("管理员分页-主路径")
    void adminPage_ok() {
        assertBizOk(api.get("/api/v1/announcements/page", adminToken,
                Map.of("pageNum", 1, "pageSize", 10)));
    }

    @Test
    @DisplayName("管理员分页-未认证401")
    void adminPage_unauthorized() {
        assertUnauthorized(api.get("/api/v1/announcements/page", null));
    }

    @Test
    @DisplayName("公告详情-主路径")
    void findById_ok() {
        Long id = createDraft();
        tracker.trackAnnouncement(id);
        assertBizOk(api.get("/api/v1/announcements/" + id, adminToken));
    }

    @Test
    @DisplayName("创建公告-主路径")
    void create_ok() {
        Long id = createDraft();
        tracker.trackAnnouncement(id);
        assertNotNull(id);
    }

    @Test
    @DisplayName("更新公告-主路径")
    void update_ok() {
        Long id = createDraft();
        tracker.trackAnnouncement(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("title", ApiTestConstants.PREFIX + "更新标题");
        body.put("content", "更新内容");
        body.put("priority", 1);
        assertBizOk(api.putJson("/api/v1/announcements", body, adminToken));
    }

    @Test
    @DisplayName("发布与撤回公告-主路径")
    void publishAndRevoke_ok() {
        Long id = createDraft();
        tracker.trackAnnouncement(id);
        assertBizOk(api.putJson("/api/v1/announcements/" + id + "/publish", Map.of(), adminToken));
        assertBizOk(api.putJson("/api/v1/announcements/" + id + "/revoke", Map.of(), adminToken));
    }

    @Test
    @DisplayName("标记已读与全部已读-主路径")
    void markRead_ok() {
        Long id = createAndPublish();
        tracker.trackAnnouncement(id);
        assertBizOk(api.putJson("/api/v1/announcements/" + id + "/read", Map.of(), adminToken));
        assertBizOk(api.putJson("/api/v1/announcements/read-all", Map.of(), adminToken));
    }

    @Test
    @DisplayName("删除公告-主路径")
    void delete_ok() {
        Long id = createDraft();
        tracker.trackAnnouncement(id);
        assertBizOk(api.delete("/api/v1/announcements/" + id, adminToken));
        tracker.getAnnouncementIds().remove(id);
    }

    private Long createDraft() {
        ApiTestRecorder.step("创建草稿公告");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", ApiTestConstants.unique("ann"));
        body.put("content", "apitest content");
        body.put("priority", 0);
        body.put("publishImmediately", false);
        ResponseEntity<String> response = api.postJson("/api/v1/announcements", body, adminToken);
        assertBizOk(response);
        Long id = readDataId(response);
        assertNotNull(id);
        return id;
    }

    private Long createAndPublish() {
        Long id = createDraft();
        assertBizOk(api.putJson("/api/v1/announcements/" + id + "/publish", Map.of(), adminToken));
        return id;
    }
}
