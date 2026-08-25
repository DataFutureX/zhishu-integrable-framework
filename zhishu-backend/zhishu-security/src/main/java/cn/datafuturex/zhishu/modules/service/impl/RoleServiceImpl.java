package cn.datafuturex.zhishu.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.datafuturex.zhishu.common.PageResult;
import cn.datafuturex.zhishu.modules.dto.RoleCreateDTO;
import cn.datafuturex.zhishu.modules.dto.RoleMenuAssignDTO;
import cn.datafuturex.zhishu.modules.dto.RoleQueryDTO;
import cn.datafuturex.zhishu.modules.dto.RoleUpdateDTO;
import cn.datafuturex.zhishu.modules.entity.MenuEntity;
import cn.datafuturex.zhishu.modules.entity.RoleEntity;
import cn.datafuturex.zhishu.modules.entity.RoleMenuEntity;
import cn.datafuturex.zhishu.modules.entity.UserEntity;
import cn.datafuturex.zhishu.modules.mapper.MenuMapper;
import cn.datafuturex.zhishu.modules.mapper.RoleMapper;
import cn.datafuturex.zhishu.modules.mapper.RoleMenuMapper;
import cn.datafuturex.zhishu.modules.mapper.UserMapper;
import cn.datafuturex.zhishu.modules.service.PermissionService;
import cn.datafuturex.zhishu.modules.service.RoleService;
import cn.datafuturex.zhishu.modules.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final UserMapper userMapper;
    private final MenuMapper menuMapper;
    private final PermissionService permissionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleEntity create(RoleCreateDTO dto) {
        LambdaQueryWrapper<RoleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoleEntity::getRoleCode, dto.roleCode());
        if (roleMapper.selectOne(wrapper) != null) {
            throw new RuntimeException("角色编码已存在: " + dto.roleCode());
        }

        RoleEntity role = new RoleEntity();
        role.setRoleCode(dto.roleCode().toUpperCase());
        role.setRoleName(dto.roleName());
        role.setDescription(dto.description());
        role.setStatus(dto.status() != null ? dto.status() : 1);
        role.setSort(dto.sort() != null ? dto.sort() : 0);
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());

        roleMapper.insert(role);
        log.info("创建角色成功: roleCode={}", role.getRoleCode());
        return role;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleEntity update(RoleUpdateDTO dto) {
        RoleEntity role = roleMapper.selectById(dto.id());
        if (role == null) {
            throw new RuntimeException("角色不存在: id=" + dto.id());
        }

        role.setRoleName(dto.roleName());
        if (dto.description() != null) {
            role.setDescription(dto.description());
        }
        if (dto.status() != null) {
            role.setStatus(dto.status());
        }
        if (dto.sort() != null) {
            role.setSort(dto.sort());
        }
        role.setUpdateTime(LocalDateTime.now());

        roleMapper.updateById(role);
        permissionService.evictAllCache();
        log.info("更新角色成功: id={}, roleCode={}", role.getId(), role.getRoleCode());
        return role;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        RoleEntity role = roleMapper.selectById(id);
        if (role == null) {
            throw new RuntimeException("角色不存在: id=" + id);
        }

        if ("ADMIN".equals(role.getRoleCode())) {
            throw new RuntimeException("系统内置角色 ADMIN 不可删除");
        }

        LambdaQueryWrapper<UserEntity> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(UserEntity::getRole, role.getRoleCode());
        if (userMapper.selectCount(userWrapper) > 0) {
            throw new RuntimeException("仍有用户使用该角色，无法删除");
        }

        LambdaQueryWrapper<RoleMenuEntity> roleMenuWrapper = new LambdaQueryWrapper<>();
        roleMenuWrapper.eq(RoleMenuEntity::getRoleId, id);
        roleMenuMapper.delete(roleMenuWrapper);

        roleMapper.deleteById(id);
        permissionService.evictAllCache();
        log.info("删除角色成功: id={}, roleCode={}", id, role.getRoleCode());
    }

    @Override
    public Optional<RoleVO> findById(Long id) {
        RoleEntity role = roleMapper.selectById(id);
        if (role == null) {
            return Optional.empty();
        }
        return Optional.of(toVO(role, getMenuIdsByRoleId(id)));
    }

    @Override
    public PageResult<RoleVO> pageQuery(RoleQueryDTO queryDTO) {
        Page<RoleEntity> page = new Page<>(queryDTO.pageNum(), queryDTO.pageSize());
        LambdaQueryWrapper<RoleEntity> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(queryDTO.roleCode())) {
            wrapper.like(RoleEntity::getRoleCode, queryDTO.roleCode());
        }
        if (StringUtils.hasText(queryDTO.roleName())) {
            wrapper.like(RoleEntity::getRoleName, queryDTO.roleName());
        }
        if (queryDTO.status() != null) {
            wrapper.eq(RoleEntity::getStatus, queryDTO.status());
        }
        wrapper.orderByAsc(RoleEntity::getSort).orderByAsc(RoleEntity::getId);

        Page<RoleEntity> resultPage = roleMapper.selectPage(page, wrapper);
        List<RoleEntity> roles = resultPage.getRecords();
        Map<Long, List<Long>> menuIdsMap = loadMenuIdsByRoleIds(
                roles.stream().map(RoleEntity::getId).toList());

        List<RoleVO> voList = roles.stream()
                .map(role -> toVO(role, menuIdsMap.getOrDefault(role.getId(), Collections.emptyList())))
                .collect(Collectors.toList());

        PageResult<RoleVO> pageResult = new PageResult<>();
        pageResult.setCurrent(resultPage.getCurrent());
        pageResult.setSize(resultPage.getSize());
        pageResult.setTotal(resultPage.getTotal());
        pageResult.setPages(resultPage.getPages());
        pageResult.setRecords(voList);
        return pageResult;
    }

    @Override
    public List<RoleVO> listAll() {
        LambdaQueryWrapper<RoleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoleEntity::getStatus, 1)
                .orderByAsc(RoleEntity::getSort)
                .orderByAsc(RoleEntity::getId);
        return roleMapper.selectList(wrapper).stream()
                .map(role -> toVO(role, null))
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> getMenuIdsByRoleId(Long roleId) {
        return loadMenuIdsByRoleIds(List.of(roleId)).getOrDefault(roleId, Collections.emptyList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, RoleMenuAssignDTO dto) {
        RoleEntity role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new RuntimeException("角色不存在: id=" + roleId);
        }

        List<Long> menuIds = dto.menuIds() == null ? Collections.emptyList() : dto.menuIds().stream()
                .filter(id -> id != null)
                .distinct()
                .toList();

        if (!menuIds.isEmpty()) {
            List<MenuEntity> menus = menuMapper.selectBatchIds(menuIds);
            if (menus.size() != menuIds.size()) {
                Set<Long> found = menus.stream().map(MenuEntity::getId).collect(Collectors.toSet());
                List<Long> missing = menuIds.stream().filter(id -> !found.contains(id)).toList();
                throw new RuntimeException("菜单不存在: " + missing);
            }
        }

        LambdaQueryWrapper<RoleMenuEntity> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(RoleMenuEntity::getRoleId, roleId);
        roleMenuMapper.delete(deleteWrapper);

        for (Long menuId : menuIds) {
            RoleMenuEntity roleMenu = new RoleMenuEntity();
            roleMenu.setRoleId(roleId);
            roleMenu.setMenuId(menuId);
            roleMenuMapper.insert(roleMenu);
        }

        permissionService.evictAllCache();
        log.info("角色菜单授权成功: roleId={}, menuCount={}", roleId, menuIds.size());
    }

    private Map<Long, List<Long>> loadMenuIdsByRoleIds(List<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<RoleMenuEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(RoleMenuEntity::getRoleId, roleIds);
        Map<Long, List<Long>> result = new HashMap<>();
        for (RoleMenuEntity item : roleMenuMapper.selectList(wrapper)) {
            result.computeIfAbsent(item.getRoleId(), k -> new ArrayList<>()).add(item.getMenuId());
        }
        return result;
    }

    private RoleVO toVO(RoleEntity entity, List<Long> menuIds) {
        return new RoleVO(
                entity.getId(),
                entity.getRoleCode(),
                entity.getRoleName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getSort(),
                menuIds,
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }
}
