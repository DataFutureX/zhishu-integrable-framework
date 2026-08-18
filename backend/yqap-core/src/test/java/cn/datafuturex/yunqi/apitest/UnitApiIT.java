package cn.datafuturex.yunqi.apitest;

import cn.datafuturex.yunqi.apitest.support.ApiIntegrationTestBase;
import cn.datafuturex.yunqi.apitest.support.ApiTestConstants;
import cn.datafuturex.yunqi.testsupport.report.ApiTestRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 单位管理接口真实 HTTP 集成测试
 */
class UnitApiIT extends ApiIntegrationTestBase {

    @Test
    @DisplayName("分页查询单位-主路径")
    void page_ok() {
        assertBizOk(api.get("/api/v1/units/page", adminToken, Map.of("pageNum", 1, "pageSize", 10)));
    }

    @Test
    @DisplayName("分页查询单位-未认证401")
    void page_unauthorized() {
        assertUnauthorized(api.get("/api/v1/units/page", null));
    }

    @Test
    @DisplayName("单位树-主路径")
    void tree_ok() {
        assertBizOk(api.get("/api/v1/units/tree", adminToken));
    }

    @Test
    @DisplayName("单位列表-主路径")
    void list_ok() {
        assertBizOk(api.get("/api/v1/units/list", adminToken));
    }

    @Test
    @DisplayName("单位详情-主路径")
    void findById_ok() {
        String code = ApiTestConstants.unique("unit");
        Long id = createUnit(code);
        tracker.trackUnit(id, code);
        assertBizOk(api.get("/api/v1/units/" + id, adminToken));
    }

    @Test
    @DisplayName("创建单位-主路径")
    void create_ok() {
        String code = ApiTestConstants.unique("unit");
        Long id = createUnit(code);
        tracker.trackUnit(id, code);
        assertNotNull(id);
    }

    @Test
    @DisplayName("更新单位-主路径")
    void update_ok() {
        String code = ApiTestConstants.unique("unit");
        Long id = createUnit(code);
        tracker.trackUnit(id, code);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("parentId", 0);
        body.put("unitCode", code);
        body.put("unitName", ApiTestConstants.PREFIX + "单位更新");
        body.put("status", 1);
        body.put("sort", 1);
        assertBizOk(api.putJson("/api/v1/units", body, adminToken));
    }

    @Test
    @DisplayName("删除单位-主路径")
    void delete_ok() {
        String code = ApiTestConstants.unique("unit");
        Long id = createUnit(code);
        tracker.trackUnit(id, code);
        assertBizOk(api.delete("/api/v1/units/" + id, adminToken));
        tracker.getUnitIds().remove(id);
        tracker.getUnitCodes().remove(code);
    }

    private Long createUnit(String code) {
        ApiTestRecorder.step("创建测试单位 " + code);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parentId", 0);
        body.put("unitCode", code);
        body.put("unitName", code);
        body.put("unitType", "测试单位");
        body.put("status", 1);
        body.put("sort", 1);
        ResponseEntity<String> response = api.postJson("/api/v1/units", body, adminToken);
        assertBizOk(response);
        Long id = readDataId(response);
        assertNotNull(id);
        return id;
    }
}
