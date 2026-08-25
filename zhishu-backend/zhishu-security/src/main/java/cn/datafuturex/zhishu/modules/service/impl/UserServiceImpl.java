package cn.datafuturex.zhishu.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.datafuturex.zhishu.common.PageResult;
import cn.datafuturex.zhishu.common.SecurityUtils;
import cn.datafuturex.zhishu.modules.dto.UserCreateDTO;
import cn.datafuturex.zhishu.modules.dto.UserPasswordChangeDTO;
import cn.datafuturex.zhishu.modules.dto.UserPasswordResetDTO;
import cn.datafuturex.zhishu.modules.dto.UserProfileUpdateDTO;
import cn.datafuturex.zhishu.modules.dto.UserQueryDTO;
import cn.datafuturex.zhishu.modules.dto.UserRoleAssignDTO;
import cn.datafuturex.zhishu.modules.dto.UserStatusUpdateDTO;
import cn.datafuturex.zhishu.modules.dto.UserUpdateDTO;
import cn.datafuturex.zhishu.modules.entity.RoleEntity;
import cn.datafuturex.zhishu.modules.entity.UserEntity;
import cn.datafuturex.zhishu.modules.mapper.RoleMapper;
import cn.datafuturex.zhishu.modules.mapper.UserMapper;
import cn.datafuturex.zhishu.modules.service.PermissionService;
import cn.datafuturex.zhishu.modules.service.UserService;
import cn.datafuturex.zhishu.modules.vo.UserRoleVO;
import cn.datafuturex.zhishu.modules.vo.UserVO;
import cn.datafuturex.zhishu.security.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 系统用户服务实现类
 *
 * @author YunQi Application Platform Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserEntity create(UserCreateDTO dto) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getUsername, dto.username());
        if (userMapper.selectOne(wrapper) != null) {
            throw new RuntimeException("用户名已存在: " + dto.username());
        }

        RoleEntity role = requireEnabledRole(dto.roleId());

        UserEntity user = new UserEntity();
        user.setUsername(dto.username());
        user.setRealName(dto.realName());
        user.setEmail(dto.email());
        user.setPhone(dto.phone());
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setRole(role.getRoleCode());
        user.setStatus(dto.status() != null ? dto.status() : 1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userMapper.insert(user);
        log.info("创建用户成功: username={}, role={}", dto.username(), role.getRoleCode());

        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserEntity update(UserUpdateDTO dto) {
        UserEntity user = userMapper.selectById(dto.id());
        if (user == null) {
            throw new RuntimeException("用户不存在: id=" + dto.id());
        }

        String previousUsername = user.getUsername();
        boolean passwordChanged = false;
        boolean statusDisabled = false;
        boolean roleChanged = false;

        if (StringUtils.hasText(dto.realName())) {
            user.setRealName(dto.realName());
        }
        if (StringUtils.hasText(dto.email())) {
            user.setEmail(dto.email());
        }
        if (StringUtils.hasText(dto.phone())) {
            user.setPhone(dto.phone());
        }
        if (StringUtils.hasText(dto.password())) {
            user.setPassword(passwordEncoder.encode(dto.password()));
            passwordChanged = true;
        }
        if (dto.roleId() != null) {
            RoleEntity role = requireEnabledRole(dto.roleId());
            user.setRole(role.getRoleCode());
            roleChanged = true;
        }
        if (dto.status() != null) {
            assertNotDisablingSelf(user, dto.status());
            if (Integer.valueOf(0).equals(dto.status()) && !Integer.valueOf(0).equals(user.getStatus())) {
                statusDisabled = true;
            }
            user.setStatus(dto.status());
        }

        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        if (passwordChanged || statusDisabled || roleChanged) {
            permissionService.evictCache(previousUsername);
        }
        if (passwordChanged || statusDisabled) {
            tokenBlacklistService.invalidateUserTokens(previousUsername);
        }

        log.info("更新用户成功: id={}, username={}", dto.id(), user.getUsername());

        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在: id=" + id);
        }

        String currentUsername = SecurityUtils.getCurrentUsername();
        if (currentUsername != null && currentUsername.equals(user.getUsername())) {
            throw new RuntimeException("不能删除当前登录用户");
        }

        userMapper.deleteById(id);
        permissionService.evictCache(user.getUsername());
        tokenBlacklistService.invalidateUserTokens(user.getUsername());
        log.info("删除用户成功: id={}, username={}", id, user.getUsername());
    }

    @Override
    public Optional<UserEntity> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id));
    }

    @Override
    public Optional<UserVO> findVOById(Long id) {
        return findById(id).map(user -> convertToVO(user, loadRoleMap()));
    }

    @Override
    public Optional<UserEntity> findByUsername(String username) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getUsername, username);
        return Optional.ofNullable(userMapper.selectOne(wrapper));
    }

    @Override
    public Optional<UserVO> findVOByUsername(String username) {
        return findByUsername(username).map(user -> convertToVO(user, loadRoleMap()));
    }

    @Override
    public UserVO getCurrentUser() {
        UserEntity user = requireCurrentUser();
        return convertToVO(user, loadRoleMap());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateCurrentProfile(UserProfileUpdateDTO dto) {
        UserEntity user = requireCurrentUser();

        if (StringUtils.hasText(dto.realName())) {
            user.setRealName(dto.realName());
        }
        if (dto.email() != null) {
            user.setEmail(dto.email());
        }
        if (dto.phone() != null) {
            user.setPhone(dto.phone());
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("更新个人资料成功: username={}", user.getUsername());
        return convertToVO(user, loadRoleMap());
    }

    @Override
    public PageResult<UserVO> pageQuery(UserQueryDTO queryDTO) {
        Page<UserEntity> page = new Page<>(queryDTO.pageNum(), queryDTO.pageSize());
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(queryDTO.username())) {
            wrapper.like(UserEntity::getUsername, queryDTO.username());
        }
        if (StringUtils.hasText(queryDTO.realName())) {
            wrapper.like(UserEntity::getRealName, queryDTO.realName());
        }
        if (StringUtils.hasText(queryDTO.phone())) {
            wrapper.like(UserEntity::getPhone, queryDTO.phone());
        }
        if (queryDTO.roleId() != null) {
            RoleEntity role = roleMapper.selectById(queryDTO.roleId());
            if (role != null) {
                wrapper.eq(UserEntity::getRole, role.getRoleCode());
            } else {
                wrapper.eq(UserEntity::getRole, "__NO_MATCH__");
            }
        } else if (StringUtils.hasText(queryDTO.role())) {
            wrapper.eq(UserEntity::getRole, queryDTO.role());
        }
        if (queryDTO.status() != null) {
            wrapper.eq(UserEntity::getStatus, queryDTO.status());
        }

        wrapper.orderByDesc(UserEntity::getCreateTime);

        Page<UserEntity> resultPage = userMapper.selectPage(page, wrapper);
        Map<String, RoleEntity> roleMap = loadRoleMap();

        List<UserVO> voList = resultPage.getRecords().stream()
                .map(user -> convertToVO(user, roleMap))
                .collect(Collectors.toList());

        PageResult<UserVO> pageResult = new PageResult<>();
        pageResult.setCurrent(resultPage.getCurrent());
        pageResult.setSize(resultPage.getSize());
        pageResult.setTotal(resultPage.getTotal());
        pageResult.setPages(resultPage.getPages());
        pageResult.setRecords(voList);

        return pageResult;
    }

    @Override
    public UserRoleVO getUserRole(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在: id=" + userId);
        }

        if (!StringUtils.hasText(user.getRole())) {
            return new UserRoleVO(userId, null, null, null);
        }

        LambdaQueryWrapper<RoleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoleEntity::getRoleCode, user.getRole());
        RoleEntity role = roleMapper.selectOne(wrapper);
        if (role == null) {
            return new UserRoleVO(userId, null, user.getRole(), null);
        }

        return new UserRoleVO(userId, role.getId(), role.getRoleCode(), role.getRoleName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRole(Long userId, UserRoleAssignDTO dto) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在: id=" + userId);
        }

        RoleEntity role = requireEnabledRole(dto.roleId());
        user.setRole(role.getRoleCode());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        permissionService.evictCache(user.getUsername());
        tokenBlacklistService.invalidateUserTokens(user.getUsername());
        log.info("用户角色分配成功: userId={}, roleCode={}", userId, role.getRoleCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(UserPasswordChangeDTO dto) {
        UserEntity user = requireCurrentUser();

        if (!passwordEncoder.matches(dto.oldPassword(), user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }
        if (dto.oldPassword().equals(dto.newPassword())) {
            throw new RuntimeException("新密码不能与原密码相同");
        }

        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        permissionService.evictCache(user.getUsername());
        tokenBlacklistService.invalidateUserTokens(user.getUsername());
        log.info("用户修改密码成功: username={}", user.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, UserPasswordResetDTO dto) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在: id=" + userId);
        }

        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        permissionService.evictCache(user.getUsername());
        tokenBlacklistService.invalidateUserTokens(user.getUsername());
        log.info("管理员重置用户密码成功: userId={}, username={}", userId, user.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, UserStatusUpdateDTO dto) {
        if (dto.status() != 0 && dto.status() != 1) {
            throw new RuntimeException("状态值无效，仅支持 0(禁用) 或 1(启用)");
        }

        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在: id=" + userId);
        }

        assertNotDisablingSelf(user, dto.status());

        Integer previous = user.getStatus();
        user.setStatus(dto.status());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        permissionService.evictCache(user.getUsername());
        if (Integer.valueOf(0).equals(dto.status()) && !Integer.valueOf(0).equals(previous)) {
            tokenBlacklistService.invalidateUserTokens(user.getUsername());
        }

        log.info("更新用户状态成功: userId={}, status={}", userId, dto.status());
    }

    private UserEntity requireCurrentUser() {
        String currentUsername = SecurityUtils.getCurrentUsername();
        if (currentUsername == null) {
            throw new RuntimeException("未登录");
        }
        return findByUsername(currentUsername)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    private void assertNotDisablingSelf(UserEntity user, Integer newStatus) {
        if (!Integer.valueOf(0).equals(newStatus)) {
            return;
        }
        String currentUsername = SecurityUtils.getCurrentUsername();
        if (currentUsername != null && currentUsername.equals(user.getUsername())) {
            throw new RuntimeException("不能禁用当前登录用户");
        }
    }

    private RoleEntity requireEnabledRole(Long roleId) {
        RoleEntity role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new RuntimeException("角色不存在: id=" + roleId);
        }
        if (role.getStatus() == null || role.getStatus() != 1) {
            throw new RuntimeException("角色已禁用: " + role.getRoleName());
        }
        return role;
    }

    private Map<String, RoleEntity> loadRoleMap() {
        return roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(RoleEntity::getRoleCode, Function.identity(), (a, b) -> a));
    }

    private UserVO convertToVO(UserEntity entity, Map<String, RoleEntity> roleMap) {
        Long roleId = null;
        String roleName = null;
        if (StringUtils.hasText(entity.getRole())) {
            RoleEntity role = roleMap.get(entity.getRole());
            if (role != null) {
                roleId = role.getId();
                roleName = role.getRoleName();
            }
        }

        return new UserVO(
                entity.getId(),
                entity.getUsername(),
                entity.getRealName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getRole(),
                roleId,
                roleName,
                entity.getStatus(),
                entity.getCreateTime(),
                entity.getUpdateTime()
        );
    }
}
