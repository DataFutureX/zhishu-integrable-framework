package cn.datafuturex.yunqi.modules.service;

import cn.datafuturex.yunqi.common.PageResult;
import cn.datafuturex.yunqi.modules.dto.RoleCreateDTO;
import cn.datafuturex.yunqi.modules.dto.RoleMenuAssignDTO;
import cn.datafuturex.yunqi.modules.dto.RoleQueryDTO;
import cn.datafuturex.yunqi.modules.dto.RoleUpdateDTO;
import cn.datafuturex.yunqi.modules.entity.RoleEntity;
import cn.datafuturex.yunqi.modules.vo.RoleVO;

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
