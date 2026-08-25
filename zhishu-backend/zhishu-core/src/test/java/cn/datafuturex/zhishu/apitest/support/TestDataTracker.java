package cn.datafuturex.zhishu.apitest.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 追踪本用例产生的业务数据，供测后物理清理
 */
public class TestDataTracker {

    private final Set<Long> userIds = new LinkedHashSet<>();
    private final Set<Long> roleIds = new LinkedHashSet<>();
    private final Set<Long> menuIds = new LinkedHashSet<>();
    private final Set<Long> unitIds = new LinkedHashSet<>();
    private final Set<Long> announcementIds = new LinkedHashSet<>();
    private final Set<String> usernames = new LinkedHashSet<>();
    private final Set<String> roleCodes = new LinkedHashSet<>();
    private final Set<String> unitCodes = new LinkedHashSet<>();
    private final List<String> uploadedIconPaths = new ArrayList<>();

    public void trackUser(Long id, String username) {
        if (id != null) {
            userIds.add(id);
        }
        if (username != null) {
            usernames.add(username);
        }
    }

    public void trackRole(Long id, String roleCode) {
        if (id != null) {
            roleIds.add(id);
        }
        if (roleCode != null) {
            roleCodes.add(roleCode);
        }
    }

    public void trackMenu(Long id) {
        if (id != null) {
            menuIds.add(id);
        }
    }

    public void trackUnit(Long id, String unitCode) {
        if (id != null) {
            unitIds.add(id);
        }
        if (unitCode != null) {
            unitCodes.add(unitCode);
        }
    }

    public void trackAnnouncement(Long id) {
        if (id != null) {
            announcementIds.add(id);
        }
    }

    public void trackUploadedIcon(String path) {
        if (path != null && !path.isBlank()) {
            uploadedIconPaths.add(path);
        }
    }

    public Set<Long> getUserIds() {
        return userIds;
    }

    public Set<Long> getRoleIds() {
        return roleIds;
    }

    public Set<Long> getMenuIds() {
        return menuIds;
    }

    public Set<Long> getUnitIds() {
        return unitIds;
    }

    public Set<Long> getAnnouncementIds() {
        return announcementIds;
    }

    public Set<String> getUsernames() {
        return usernames;
    }

    public Set<String> getRoleCodes() {
        return roleCodes;
    }

    public Set<String> getUnitCodes() {
        return unitCodes;
    }

    public List<String> getUploadedIconPaths() {
        return uploadedIconPaths;
    }

    public void clear() {
        userIds.clear();
        roleIds.clear();
        menuIds.clear();
        unitIds.clear();
        announcementIds.clear();
        usernames.clear();
        roleCodes.clear();
        unitCodes.clear();
        uploadedIconPaths.clear();
    }
}
