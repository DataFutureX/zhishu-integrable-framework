package com.datafuturex.assistant.kg.support;

import com.datafuturex.assistant.shared.UserContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Set;

/**
 * 知枢控制台图谱查询范围。工程 ACL 在万象侧；知枢不再读取 {@code sys_user_project}。
 */
@Component
public class KgProjectAccess {

    /** null=全量；空=无权限 */
    public Set<Long> getAccessibleProjectIds() {
        if (!StringUtils.hasText(UserContext.getUserId()) && !StringUtils.hasText(UserContext.getUsername())) {
            return Collections.emptySet();
        }
        return null;
    }

    public Set<Long> requireProjectScope() {
        Set<Long> ids = getAccessibleProjectIds();
        if (ids != null && ids.isEmpty()) {
            throw new IllegalArgumentException("未登录，无法查询知识图谱");
        }
        return ids;
    }

    public Set<Long> resolveProjectScope(Long requestedProjectId) {
        Set<Long> scope = requireProjectScope();
        if (requestedProjectId == null) {
            return scope;
        }
        if (scope != null && !scope.contains(requestedProjectId)) {
            throw new IllegalArgumentException("无权访问工程: " + requestedProjectId);
        }
        return Set.of(requestedProjectId);
    }

    public void assertProjectAccessible(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("工程 ID 不能为空");
        }
        Set<Long> scope = requireProjectScope();
        if (scope != null && !scope.contains(projectId)) {
            throw new IllegalArgumentException("无权访问工程: " + projectId);
        }
    }
}
