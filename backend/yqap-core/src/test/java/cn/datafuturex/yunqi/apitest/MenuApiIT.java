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
 * 菜单管理接口真实 HTTP 集成测试
 */
class MenuApiIT extends ApiIntegrationTestBase {

    @Test
    @DisplayName("菜单树-主路径")
    void tree_ok() {
        assertBizOk(api.get("/api/v1/menus/tree", adminToken));
    }

    @Test
    @DisplayName("菜单树-未认证401")
    void tree_unauthorized() {
        assertUnauthorized(api.get("/api/v1/menus/tree", null));
    }

    @Test
    @DisplayName("当前用户菜单-主路径")
    void currentUserMenus_ok() {
        assertBizOk(api.get("/api/v1/menus/current-user", adminToken));
    }

    @Test
    @DisplayName("当前用户权限-主路径")
    void currentUserPermissions_ok() {
        assertBizOk(api.get("/api/v1/menus/current-user/permissions", adminToken));
    }

    @Test
    @DisplayName("按角色查菜单-主路径")
    void byRole_ok() {
        assertBizOk(api.get("/api/v1/menus/role/ADMIN", adminToken));
    }

    @Test
    @DisplayName("菜单详情-主路径")
    void findById_ok() {
        Long id = createMenu();
        tracker.trackMenu(id);
        assertBizOk(api.get("/api/v1/menus/" + id, adminToken));
    }

    @Test
    @DisplayName("创建菜单-主路径")
    void create_ok() {
        Long id = createMenu();
        tracker.trackMenu(id);
        assertNotNull(id);
    }

    @Test
    @DisplayName("更新菜单-主路径")
    void update_ok() {
        Long id = createMenu();
        tracker.trackMenu(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("parentId", 0);
        body.put("title", ApiTestConstants.PREFIX + "菜单更新");
        body.put("path", "/apitest/updated");
        body.put("routeName", ApiTestConstants.unique("rn"));
        body.put("menuType", "MENU");
        body.put("visible", 1);
        body.put("requiresAuth", 1);
        body.put("sort", 1);
        body.put("status", 1);
        assertBizOk(api.putJson("/api/v1/menus", body, adminToken));
    }

    @Test
    @DisplayName("删除菜单-主路径")
    void delete_ok() {
        Long id = createMenu();
        tracker.trackMenu(id);
        assertBizOk(api.delete("/api/v1/menus/" + id, adminToken));
        tracker.getMenuIds().remove(id);
    }

    private Long createMenu() {
        String title = ApiTestConstants.unique("m");
        ApiTestRecorder.step("创建测试菜单 " + title);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parentId", 0);
        body.put("title", title);
        body.put("path", "/apitest/" + title);
        body.put("routeName", title);
        body.put("menuType", "MENU");
        body.put("visible", 1);
        body.put("requiresAuth", 1);
        body.put("sort", 999);
        body.put("status", 1);
        ResponseEntity<String> response = api.postJson("/api/v1/menus", body, adminToken);
        assertBizOk(response);
        Long id = readDataId(response);
        assertNotNull(id);
        return id;
    }
}
