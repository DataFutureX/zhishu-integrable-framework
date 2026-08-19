package cn.datafuturex.zhishu.modules.service;

import cn.datafuturex.zhishu.common.PageResult;
import cn.datafuturex.zhishu.modules.dto.UserCreateDTO;
import cn.datafuturex.zhishu.modules.dto.UserPasswordChangeDTO;
import cn.datafuturex.zhishu.modules.dto.UserPasswordResetDTO;
import cn.datafuturex.zhishu.modules.dto.UserProfileUpdateDTO;
import cn.datafuturex.zhishu.modules.dto.UserQueryDTO;
import cn.datafuturex.zhishu.modules.dto.UserRoleAssignDTO;
import cn.datafuturex.zhishu.modules.dto.UserStatusUpdateDTO;
import cn.datafuturex.zhishu.modules.dto.UserUpdateDTO;
import cn.datafuturex.zhishu.modules.entity.UserEntity;
import cn.datafuturex.zhishu.modules.vo.UserRoleVO;
import cn.datafuturex.zhishu.modules.vo.UserVO;

import java.util.Optional;

/**
 * 系统用户服务接口
 *
 * @author YunQi Application Platform Team
 */
public interface UserService {

    UserEntity create(UserCreateDTO dto);

    UserEntity update(UserUpdateDTO dto);

    void delete(Long id);

    Optional<UserEntity> findById(Long id);

    Optional<UserVO> findVOById(Long id);

    Optional<UserEntity> findByUsername(String username);

    Optional<UserVO> findVOByUsername(String username);

    /**
     * 当前登录用户信息
     */
    UserVO getCurrentUser();

    /**
     * 当前登录用户更新个人资料
     */
    UserVO updateCurrentProfile(UserProfileUpdateDTO dto);

    PageResult<UserVO> pageQuery(UserQueryDTO queryDTO);

    UserRoleVO getUserRole(Long userId);

    void assignRole(Long userId, UserRoleAssignDTO dto);

    void changePassword(UserPasswordChangeDTO dto);

    /**
     * 管理员重置用户密码
     */
    void resetPassword(Long userId, UserPasswordResetDTO dto);

    /**
     * 启用/禁用用户
     */
    void updateStatus(Long userId, UserStatusUpdateDTO dto);
}
