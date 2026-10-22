package cn.datafuturex.zhishu.gateway.catalog;

import java.util.List;
import java.util.Optional;

/**
 * 模型目录服务：对外暴露「已启用且上游可用」的模型集合。
 */
public interface GatewayCatalogService {

    /**
     * 列出当前可用模型（按 sort_order 排序）。
     *
     * @return 模型视图列表
     */
    List<GatewayModelView> listEnabled();

    /**
     * 按对外模型 id 查找。
     *
     * @param modelId 对外模型 id
     * @return 模型视图
     */
    Optional<GatewayModelView> find(String modelId);

    /**
     * 取网关默认模型。
     *
     * @return 模型视图
     */
    Optional<GatewayModelView> findDefault();

    /**
     * 失效缓存（目录增删改后调用）。
     */
    void invalidate();
}
