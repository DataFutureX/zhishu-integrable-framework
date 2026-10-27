package cn.datafuturex.zhishu.gateway.port;

import java.util.List;

/**
 * 模型引用查询端口 —— 由业务侧（Agent 模块）实现，供模型网关在删除/停用模型前做引用检查。
 * <p>
 * 存在的意义：{@code ai_agent} 等表归业务模块所有，网关不得直连查询；
 * 网关只认「引用数量 + 引用名称」，绑定语义变化只需调整业务侧实现。
 * 未装配实现时视为无引用方，网关跳过引用检查。
 */
public interface ModelReferencePort {

    /**
     * 统计绑定了指定网关模型 id 的引用数量。
     *
     * @param gatewayModelId 网关模型 id（业务侧存的引用值，如 {@code ai_agent.model}）
     * @return 引用数
     */
    int countReferences(String gatewayModelId);

    /**
     * 取若干引用方名称，用于错误提示。
     *
     * @param gatewayModelId 网关模型 id
     * @return 名称列表（最多 20 个）
     */
    List<String> listReferenceNames(String gatewayModelId);
}
