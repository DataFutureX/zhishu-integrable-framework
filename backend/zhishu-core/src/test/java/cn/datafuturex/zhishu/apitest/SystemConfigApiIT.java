package cn.datafuturex.zhishu.apitest;

import cn.datafuturex.zhishu.apitest.support.ApiIntegrationTestBase;
import cn.datafuturex.zhishu.testsupport.report.ApiTestRecorder;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 系统配置接口真实 HTTP 集成测试
 */
class SystemConfigApiIT extends ApiIntegrationTestBase {

    @Test
    @DisplayName("获取系统配置-公开主路径")
    void getConfig_ok() {
        assertBizOk(api.get("/api/v1/system-config", null));
    }

    @Test
    @DisplayName("更新系统配置-主路径并还原")
    void update_ok() {
        ApiTestRecorder.step("读取原配置 → 更新 → 还原");
        ResponseEntity<String> before = api.get("/api/v1/system-config", adminToken);
        assertBizOk(before);
        JsonNode data = api.readTree(before).path("data");

        Map<String, Object> update = toUpdateBody(data);
        update.put("systemName", data.path("systemName").asText() + "-apitest");
        assertBizOk(api.putJson("/api/v1/system-config", update, adminToken));

        Map<String, Object> restore = toUpdateBody(data);
        assertBizOk(api.putJson("/api/v1/system-config", restore, adminToken));
    }

    @Test
    @DisplayName("更新系统配置-未认证应拒绝")
    void update_unauthorized() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemName", "x");
        body.put("loginRetryLimitEnabled", true);
        body.put("loginMaxRetryAttempts", 5);
        body.put("loginLockMinutes", 3);
        ResponseEntity<String> response = api.putJson("/api/v1/system-config", body, null);
        int status = response.getStatusCode().value();
        // permitAll 路径 + @PreAuthorize：匿名通常为 403；部分环境为 401
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "应拒绝未认证更新: " + status + " body=" + response.getBody());
    }

    @Test
    @DisplayName("上传系统图标-主路径")
    void uploadIcon_ok() {
        ApiTestRecorder.step("上传小 PNG 图标");
        byte[] png = minimalPng();
        MultiValueMap<String, org.springframework.core.io.Resource> resources = new LinkedMultiValueMap<>();
        resources.add("file", new ByteArrayResource(png) {
            @Override
            public String getFilename() {
                return "apitest-icon.png";
            }
        });

        ResponseEntity<String> response = api.postMultipart("/api/v1/system-config/icon", resources, adminToken);
        assertBizOk(response);
        String iconUrl = api.readTree(response).path("data").asText();
        assertNotNull(iconUrl);
        tracker.trackUploadedIcon(iconUrl);

        ApiTestRecorder.step("清空测试图标 URL");
        ResponseEntity<String> cfg = api.get("/api/v1/system-config", adminToken);
        JsonNode data = api.readTree(cfg).path("data");
        Map<String, Object> restore = toUpdateBody(data);
        restore.put("systemIcon", "");
        assertBizOk(api.putJson("/api/v1/system-config", restore, adminToken));
    }

    private static byte[] minimalPng() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
                (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
                0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF,
                (byte) 0xC0, 0x00, 0x00, 0x00, 0x03, 0x00, 0x01,
                0x00, 0x05, (byte) 0xFE, 0x02, (byte) 0xFE,
                (byte) 0xDC, (byte) 0xCC, 0x59, (byte) 0xE7, 0x00, 0x00,
                0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
                0x42, 0x60, (byte) 0x82
        };
    }

    private Map<String, Object> toUpdateBody(JsonNode data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemName", data.path("systemName").asText("系统"));
        body.put("englishTitle", textOrNull(data, "englishTitle"));
        body.put("systemIcon", textOrNull(data, "systemIcon"));
        body.put("copyright", textOrNull(data, "copyright"));
        body.put("systemIntroduction", textOrNull(data, "systemIntroduction"));
        body.put("projectSite", textOrNull(data, "projectSite"));
        body.put("loginRetryLimitEnabled", data.path("loginRetryLimitEnabled").asBoolean(true));
        body.put("loginMaxRetryAttempts", data.path("loginMaxRetryAttempts").asInt(5));
        body.put("loginLockMinutes", data.path("loginLockMinutes").asInt(3));
        return body;
    }

    private String textOrNull(JsonNode data, String field) {
        JsonNode n = data.path(field);
        return n.isMissingNode() || n.isNull() ? null : n.asText();
    }
}
