package cn.datafuturex.zhishu.gateway.admin.service;

import cn.datafuturex.zhishu.gateway.admin.dto.GatewayModelBatchImportDTO;
import cn.datafuturex.zhishu.gateway.admin.dto.GatewayModelUpsertDTO;
import cn.datafuturex.zhishu.gateway.admin.vo.GatewayModelAdminVO;

import java.util.List;

/**
 * 模型设置管理服务（供应商下的 CHAT / EMBEDDING 模型）。
 */
public interface GatewayModelAdminService {

    /**
     * 列出模型。
     *
     * @param modelType  类型过滤：CHAT / EMBEDDING，空=全部
     * @param providerId 供应商过滤，空=全部
     * @return 模型列表
     */
    List<GatewayModelAdminVO> list(String modelType, Long providerId);

    /**
     * 列出可供 Agent 绑定的对话模型（启用且上游可用）。
     *
     * @return 模型列表
     */
    List<GatewayModelAdminVO> listAvailable();

    /**
     * 查询单个模型。
     *
     * @param id 主键
     * @return 模型
     */
    GatewayModelAdminVO get(Long id);

    /**
     * 新建模型。
     *
     * @param dto 参数
     * @return 新建后的模型
     */
    GatewayModelAdminVO create(GatewayModelUpsertDTO dto);

    /**
     * 修改模型（model_id 不可改）。
     *
     * @param id  主键
     * @param dto 参数
     * @return 修改后的模型
     */
    GatewayModelAdminVO update(Long id, GatewayModelUpsertDTO dto);

    /**
     * 删除模型（被 Agent 引用时拒绝）。
     *
     * @param id 主键
     */
    void delete(Long id);

    /**
     * 设为该类型的默认模型。
     *
     * @param id 主键
     * @return 修改后的模型
     */
    GatewayModelAdminVO markDefault(Long id);

    /**
     * 启用/停用。
     *
     * @param id      主键
     * @param enabled true=启用
     * @return 修改后的模型
     */
    GatewayModelAdminVO changeStatus(Long id, boolean enabled);

    /**
     * 端到端测试：经网关向上游发一条最小请求。
     *
     * @param id 主键
     * @return 测试结果说明
     */
    String test(Long id);

    /**
     * 从上游模型列表批量导入。
     *
     * @param dto 导入参数
     * @return 新建成功的模型列表
     */
    List<GatewayModelAdminVO> batchImport(GatewayModelBatchImportDTO dto);
}
