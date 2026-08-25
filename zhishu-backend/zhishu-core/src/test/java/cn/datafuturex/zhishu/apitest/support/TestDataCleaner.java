package cn.datafuturex.zhishu.apitest.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 按外键逆序物理删除测试数据，并按 apitest_ 前缀兜底清理
 */
public class TestDataCleaner {

    private static final Logger log = LoggerFactory.getLogger(TestDataCleaner.class);

    private final JdbcTemplate jdbcTemplate;

    public TestDataCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void cleanup(TestDataTracker tracker) {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        deleteAnnouncementReads(tracker);
        deleteAnnouncements(tracker);
        deleteOperationLogs();
        deleteUsers(tracker);
        deleteRoleMenus(tracker);
        deleteRoles(tracker);
        deleteMenus(tracker);
        deleteUnits(tracker);
        deleteUploadedIcons(tracker);
        fallbackByPrefix();
        tracker.clear();
    }

    private void deleteAnnouncementReads(TestDataTracker tracker) {
        if (tracker.getAnnouncementIds().isEmpty() && tracker.getUserIds().isEmpty()) {
            return;
        }
        List<Long> annIds = new ArrayList<>(tracker.getAnnouncementIds());
        List<Long> userIds = new ArrayList<>(tracker.getUserIds());
        if (!annIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM sys_announcement_read WHERE announcement_id IN ("
                    + placeholders(annIds.size()) + ")", annIds.toArray());
        }
        if (!userIds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM sys_announcement_read WHERE user_id IN ("
                    + placeholders(userIds.size()) + ")", userIds.toArray());
        }
    }

    private void deleteAnnouncements(TestDataTracker tracker) {
        if (!tracker.getAnnouncementIds().isEmpty()) {
            List<Long> ids = new ArrayList<>(tracker.getAnnouncementIds());
            jdbcTemplate.update("DELETE FROM sys_announcement WHERE id IN ("
                    + placeholders(ids.size()) + ")", ids.toArray());
        }
        jdbcTemplate.update("DELETE FROM sys_announcement WHERE title LIKE ?",
                ApiTestConstants.PREFIX + "%");
    }

    private void deleteOperationLogs() {
        jdbcTemplate.update(
                "DELETE FROM sys_operation_log WHERE username LIKE ? OR request_params LIKE ?",
                ApiTestConstants.PREFIX + "%",
                "%" + ApiTestConstants.PREFIX + "%");
    }

    private void deleteUsers(TestDataTracker tracker) {
        if (!tracker.getUserIds().isEmpty()) {
            List<Long> ids = new ArrayList<>(tracker.getUserIds());
            jdbcTemplate.update("DELETE FROM sys_user WHERE id IN ("
                    + placeholders(ids.size()) + ")", ids.toArray());
        }
        for (String username : tracker.getUsernames()) {
            jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", username);
        }
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE ?",
                ApiTestConstants.PREFIX + "%");
    }

    private void deleteRoleMenus(TestDataTracker tracker) {
        if (!tracker.getRoleIds().isEmpty()) {
            List<Long> ids = new ArrayList<>(tracker.getRoleIds());
            jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id IN ("
                    + placeholders(ids.size()) + ")", ids.toArray());
        }
        if (!tracker.getMenuIds().isEmpty()) {
            List<Long> ids = new ArrayList<>(tracker.getMenuIds());
            jdbcTemplate.update("DELETE FROM sys_role_menu WHERE menu_id IN ("
                    + placeholders(ids.size()) + ")", ids.toArray());
        }
    }

    private void deleteRoles(TestDataTracker tracker) {
        if (!tracker.getRoleIds().isEmpty()) {
            List<Long> ids = new ArrayList<>(tracker.getRoleIds());
            jdbcTemplate.update("DELETE FROM sys_role WHERE id IN ("
                    + placeholders(ids.size()) + ")", ids.toArray());
        }
        for (String code : tracker.getRoleCodes()) {
            jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM (SELECT id FROM sys_role WHERE role_code = ?) t)",
                    code);
            jdbcTemplate.update("DELETE FROM sys_role WHERE role_code = ?", code);
        }
        jdbcTemplate.update(
                "DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM (SELECT id FROM sys_role WHERE role_code LIKE ?) t)",
                ApiTestConstants.PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code LIKE ?",
                ApiTestConstants.PREFIX + "%");
    }

    private void deleteMenus(TestDataTracker tracker) {
        if (!tracker.getMenuIds().isEmpty()) {
            List<Long> ids = new ArrayList<>(tracker.getMenuIds());
            jdbcTemplate.update("DELETE FROM sys_menu WHERE id IN ("
                    + placeholders(ids.size()) + ")", ids.toArray());
        }
        jdbcTemplate.update("DELETE FROM sys_menu WHERE title LIKE ? OR route_name LIKE ? OR path LIKE ?",
                ApiTestConstants.PREFIX + "%",
                ApiTestConstants.PREFIX + "%",
                "%" + ApiTestConstants.PREFIX + "%");
    }

    private void deleteUnits(TestDataTracker tracker) {
        if (!tracker.getUnitIds().isEmpty()) {
            List<Long> ids = new ArrayList<>(tracker.getUnitIds());
            jdbcTemplate.update("DELETE FROM sys_unit WHERE id IN ("
                    + placeholders(ids.size()) + ")", ids.toArray());
        }
        for (String code : tracker.getUnitCodes()) {
            jdbcTemplate.update("DELETE FROM sys_unit WHERE unit_code = ?", code);
        }
        jdbcTemplate.update("DELETE FROM sys_unit WHERE unit_code LIKE ? OR unit_name LIKE ?",
                ApiTestConstants.PREFIX + "%",
                ApiTestConstants.PREFIX + "%");
    }

    private void deleteUploadedIcons(TestDataTracker tracker) {
        for (String iconPath : tracker.getUploadedIconPaths()) {
            try {
                String relative = iconPath.startsWith("/") ? iconPath.substring(1) : iconPath;
                Path file = Path.of(relative);
                if (!file.isAbsolute()) {
                    Path uploadRoot = Path.of(System.getProperty("java.io.tmpdir"), "zhishu-api-it-uploads");
                    // iconUrl 形如 /uploads/system/xxx.png，实际文件在 uploadPath/system/xxx.png
                    String name = Path.of(relative).getFileName().toString();
                    file = uploadRoot.resolve("system").resolve(name);
                }
                Files.deleteIfExists(file);
            } catch (Exception e) {
                log.warn("删除测试上传图标失败: {}", iconPath, e);
            }
        }
    }

    private void fallbackByPrefix() {
        jdbcTemplate.update("DELETE FROM sys_announcement_read WHERE announcement_id IN ("
                + "SELECT id FROM (SELECT id FROM sys_announcement WHERE title LIKE ?) t)",
                ApiTestConstants.PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_announcement WHERE title LIKE ?",
                ApiTestConstants.PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE ?",
                ApiTestConstants.PREFIX + "%");
        jdbcTemplate.update(
                "DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM (SELECT id FROM sys_role WHERE role_code LIKE ?) t)",
                ApiTestConstants.PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code LIKE ?",
                ApiTestConstants.PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_menu WHERE title LIKE ?",
                ApiTestConstants.PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_unit WHERE unit_name LIKE ? OR unit_code LIKE ?",
                ApiTestConstants.PREFIX + "%",
                ApiTestConstants.PREFIX + "%");
    }

    private static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }
}
