package cn.datafuturex.zhishu.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.datafuturex.zhishu.modules.constant.PermissionConstants;
import cn.datafuturex.zhishu.modules.entity.MenuEntity;
import cn.datafuturex.zhishu.modules.entity.RoleEntity;
import cn.datafuturex.zhishu.modules.entity.RoleMenuEntity;
import cn.datafuturex.zhishu.modules.entity.UserEntity;
import cn.datafuturex.zhishu.modules.mapper.MenuMapper;
import cn.datafuturex.zhishu.modules.mapper.RoleMapper;
import cn.datafuturex.zhishu.modules.mapper.RoleMenuMapper;
import cn.datafuturex.zhishu.modules.mapper.UserMapper;
import cn.datafuturex.zhishu.modules.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 按钮权限查询实现（含短时本地缓存）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private static final long CACHE_TTL_MS = 60_000L;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    public List<String> listPermissionCodesByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }

        CacheEntry cached = cache.get(username);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expireAt() > now) {
            return cached.codes();
        }

        List<String> codes = loadFromDb(username);
        // 不缓存 null（用户不存在/禁用），避免短暂异常状态被缓存 60s
        if (codes != null) {
            cache.put(username, new CacheEntry(codes, now + CACHE_TTL_MS));
        } else {
            cache.remove(username);
        }
        return codes;
    }

    @Override
    public void evictCache(String username) {
        if (StringUtils.hasText(username)) {
            cache.remove(username);
        }
    }

    @Override
    public void evictAllCache() {
        cache.clear();
    }

    private List<String> loadFromDb(String username) {
        LambdaQueryWrapper<UserEntity> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(UserEntity::getUsername, username);
        UserEntity user = userMapper.selectOne(userWrapper);
        if (user == null) {
            return null;
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.debug("用户已禁用，拒绝加载权限: username={}", username);
            return null;
        }
        if (!StringUtils.hasText(user.getRole())) {
            return Collections.emptyList();
        }

        String roleCode = user.getRole().trim();
        if (PermissionConstants.ROLE_ADMIN.equalsIgnoreCase(roleCode)) {
            return listAllEnabledButtonPermissions();
        }

        LambdaQueryWrapper<RoleEntity> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq(RoleEntity::getRoleCode, roleCode)
                .eq(RoleEntity::getStatus, 1);
        RoleEntity role = roleMapper.selectOne(roleWrapper);
        if (role == null) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<RoleMenuEntity> roleMenuWrapper = new LambdaQueryWrapper<>();
        roleMenuWrapper.eq(RoleMenuEntity::getRoleId, role.getId());
        Set<Long> menuIds = roleMenuMapper.selectList(roleMenuWrapper).stream()
                .map(RoleMenuEntity::getMenuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<MenuEntity> menuWrapper = new LambdaQueryWrapper<>();
        menuWrapper.in(MenuEntity::getId, menuIds)
                .eq(MenuEntity::getMenuType, PermissionConstants.MENU_TYPE_BUTTON)
                .eq(MenuEntity::getStatus, 1);
        return menuMapper.selectList(menuWrapper).stream()
                .map(MenuEntity::getRouteName)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> listAllEnabledButtonPermissions() {
        LambdaQueryWrapper<MenuEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MenuEntity::getMenuType, PermissionConstants.MENU_TYPE_BUTTON)
                .eq(MenuEntity::getStatus, 1)
                .orderByAsc(MenuEntity::getId);
        return menuMapper.selectList(wrapper).stream()
                .map(MenuEntity::getRouteName)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private record CacheEntry(List<String> codes, long expireAt) {
    }
}
