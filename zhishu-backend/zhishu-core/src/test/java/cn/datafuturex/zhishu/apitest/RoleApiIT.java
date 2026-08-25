package cn.datafuturex.zhishu.apitest;

import cn.datafuturex.zhishu.apitest.support.ApiIntegrationTestBase;
import cn.datafuturex.zhishu.apitest.support.ApiTestConstants;
import cn.datafuturex.zhishu.testsupport.report.ApiTestRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 角色管理接口真实 HTTP 集成测试
 */
class RoleApiIT extends ApiIntegrationTestBase {

    @Test
    @DisplayName("分页查询角色-主路径")
    void page_ok() {
        assertBizOk(api.get("/api/v1/roles/page", adminToken, Map.of("pageNum", 1, "pageSize", 10)));
    }

    @Test
    @DisplayName("分页查询角色-未认证401")
    void page_unauthorized() {
        assertUnauthorized(api.get("/api/v1/roles/page", null));
    }

    @Test
    @DisplayName("角色列表-主路径")
    void list_ok() {
        assertBizOk(api.get("/api/v1/roles/list", adminToken));
    }

    @Test
    @DisplayName("角色详情-主路径")
    void findById_ok() {
        String code = ApiTestConstants.unique("r");
        Long id = createRole(code);
        tracker.trackRole(id, code);
        assertBizOk(api.get("/api/v1/roles/" + id, adminToken));
    }

    @Test
    @DisplayName("查询角色菜单-主路径")
    void getMenus_ok() {
        String code = ApiTestConstants.unique("r");
        Long id = createRole(code);
        tracker.trackRole(id, code);
        assertBizOk(api.get("/api/v1/roles/" + id + "/menus", adminToken));
    }

    @Test
    @DisplayName("分配角色菜单-主路径")
    void assignMenus_ok() {
        String code = ApiTestConstants.unique("r");
        Long id = createRole(code);
        tracker.trackRole(id, code);
        assertBizOk(api.putJson("/api/v1/roles/" + id + "/menus",
                Map.of("menuIds", List.of(1L)), adminToken));
    }

    @Test
    @DisplayName("创建角色-主路径")
    void create_ok() {
        String code = ApiTestConstants.unique("r");
        Long id = createRole(code);
        tracker.trackRole(id, code);
        assertNotNull(id);
    }

    @Test
    @DisplayName("更新角色-主路径")
    void update_ok() {
        String code = ApiTestConstants.unique("r");
        Long id = createRole(code);
        tracker.trackRole(id, code);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("roleName", "更新后角色");
        body.put("description", "apitest update");
        body.put("status", 1);
        body.put("sort", 99);
        assertBizOk(api.putJson("/api/v1/roles", body, adminToken));
    }

    @Test
    @DisplayName("删除角色-主路径")
    void delete_ok() {
        String code = ApiTestConstants.unique("r");
        Long id = createRole(code);
        tracker.trackRole(id, code);
        assertBizOk(api.delete("/api/v1/roles/" + id, adminToken));
        tracker.getRoleIds().remove(id);
        tracker.getRoleCodes().remove(code);
    }

    private Long createRole(String code) {
        ApiTestRecorder.step("创建测试角色 " + code);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roleCode", code);
        body.put("roleName", "API测试角色");
        body.put("description", "apitest");
        body.put("status", 1);
        body.put("sort", 100);
        ResponseEntity<String> response = api.postJson("/api/v1/roles", body, adminToken);
        assertBizOk(response);
        Long id = readDataId(response);
        assertNotNull(id);
        return id;
    }
}
