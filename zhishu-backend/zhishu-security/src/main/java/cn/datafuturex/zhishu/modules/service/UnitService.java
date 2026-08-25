package cn.datafuturex.zhishu.modules.service;

import cn.datafuturex.zhishu.common.PageResult;
import cn.datafuturex.zhishu.modules.dto.UnitCreateDTO;
import cn.datafuturex.zhishu.modules.dto.UnitQueryDTO;
import cn.datafuturex.zhishu.modules.dto.UnitUpdateDTO;
import cn.datafuturex.zhishu.modules.entity.UnitEntity;
import cn.datafuturex.zhishu.modules.vo.UnitVO;

import java.util.List;
import java.util.Optional;

/**
 * 单位管理服务接口
 */
public interface UnitService {

    /**
     * 创建单位
     *
     * @param dto 创建请求
     * @return 单位实体
     */
    UnitEntity create(UnitCreateDTO dto);

    /**
     * 更新单位
     *
     * @param dto 更新请求
     * @return 单位实体
     */
    UnitEntity update(UnitUpdateDTO dto);

    /**
     * 删除单位
     *
     * @param id 单位ID
     */
    void delete(Long id);

    /**
     * 根据ID查询单位
     *
     * @param id 单位ID
     * @return 单位视图
     */
    Optional<UnitVO> findById(Long id);

    /**
     * 分页查询单位（平铺列表）
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<UnitVO> pageQuery(UnitQueryDTO query);

    /**
     * 查询单位树
     *
     * @param status 状态筛选，null 表示全部
     * @return 单位树
     */
    List<UnitVO> listTree(Integer status);

    /**
     * 查询全部启用单位（下拉选择）
     *
     * @return 单位列表
     */
    List<UnitVO> listAllEnabled();
}
