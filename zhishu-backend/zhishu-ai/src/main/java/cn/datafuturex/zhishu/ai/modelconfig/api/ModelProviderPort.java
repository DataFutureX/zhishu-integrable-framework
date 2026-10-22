package cn.datafuturex.zhishu.ai.modelconfig.api;

import cn.datafuturex.zhishu.ai.modelconfig.dto.ModelProviderCreateDTO;
import cn.datafuturex.zhishu.ai.modelconfig.dto.ModelProviderUpdateDTO;
import cn.datafuturex.zhishu.ai.modelconfig.vo.ModelProviderVO;

import java.util.List;

/**
 * 供应商端口 —— 只负责「连接与凭据」，模型与生成参数由模型网关侧维护。
 */
public interface ModelProviderPort {

    /**
     * 获取所有供应商。
     *
     * @return 供应商列表
     */
    List<ModelProviderVO> list();

    /**
     * 获取单个供应商。
     *
     * @param id 供应商 ID
     * @return 供应商
     */
    ModelProviderVO get(Long id);

    /**
     * 新建供应商。
     *
     * @param dto 创建参数
     * @return 新建后的供应商
     */
    ModelProviderVO create(ModelProviderCreateDTO dto);

    /**
     * 修改供应商。
     *
     * @param id  供应商 ID
     * @param dto 修改参数
     * @return 修改后的供应商
     */
    ModelProviderVO update(Long id, ModelProviderUpdateDTO dto);

    /**
     * 删除供应商（默认供应商不可删）。
     *
     * @param id 供应商 ID
     */
    void delete(Long id);

    /**
     * 设为默认供应商。
     *
     * @param id 供应商 ID
     * @return 修改后的供应商
     */
    ModelProviderVO setDefault(Long id);

    /**
     * 连通性测试：调用上游 {@code GET /models}。
     *
     * @param id 供应商 ID
     * @return 测试结果说明
     */
    String testConnection(Long id);

    /**
     * 拉取上游模型 id 列表，供「导入模型」使用。
     *
     * @param id 供应商 ID
     * @return 模型 id 列表
     */
    List<String> listUpstreamModels(Long id);

    /**
     * 获取默认供应商。
     *
     * @return 默认供应商（无默认时取第一条）
     */
    ModelProviderVO getDefault();
}
