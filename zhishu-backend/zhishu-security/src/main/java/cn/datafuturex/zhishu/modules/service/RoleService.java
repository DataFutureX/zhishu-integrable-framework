package cn.datafuturex.zhishu.modules.service;

import cn.datafuturex.zhishu.common.PageResult;
import cn.datafuturex.zhishu.modules.dto.RoleCreateDTO;
import cn.datafuturex.zhishu.modules.dto.RoleMenuAssignDTO;
import cn.datafuturex.zhishu.modules.dto.RoleQueryDTO;
import cn.datafuturex.zhishu.modules.dto.RoleUpdateDTO;
import cn.datafuturex.zhishu.modules.entity.RoleEntity;
import cn.datafuturex.zhishu.modules.vo.RoleVO;

import java.util.List;
import java.util.Optional;

/**
 * 角色管理服务
 */
public interface RoleService {

    RoleEntity create(RoleCreateDTO dto);

    RoleEntity update(RoleUpdateDTO dto);

    void delete(Long id);

    Optional<RoleVO> findById(Long id);

    PageResult<RoleVO> pageQuery(RoleQueryDTO queryDTO);

    List<RoleVO> listAll();

    List<Long> getMenuIdsByRoleId(Long roleId);

    void assignMenus(Long roleId, RoleMenuAssignDTO dto);
}
