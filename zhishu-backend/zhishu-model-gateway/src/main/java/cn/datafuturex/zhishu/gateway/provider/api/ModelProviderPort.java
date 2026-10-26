package cn.datafuturex.zhishu.gateway.provider.api;

import cn.datafuturex.zhishu.gateway.provider.dto.ModelProviderCreateDTO;
import cn.datafuturex.zhishu.gateway.provider.dto.ModelProviderTestDTO;
import cn.datafuturex.zhishu.gateway.provider.dto.ModelProviderUpdateDTO;
import cn.datafuturex.zhishu.gateway.provider.vo.ModelProviderVO;
import cn.datafuturex.zhishu.gateway.provider.vo.UpstreamModelInfo;

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
     * 连通性测试：调用上游模型列表（OpenAI 兼容 {@code GET /models}，Ollama 为 {@code GET /api/tags}）。
     *
     * @param id 供应商 ID
     * @return 测试结果说明
     */
    String testConnection(Long id);

    /**
     * 保存前试跑：用表单里的临时连接信息探测上游（不落库）。
     * <p>
     * {@code apiKey} 可为空：为空且带 {@code providerId} 时回落到已保存的 Key，
     * 两者都没有则按匿名方式探测（适配 Ollama / vLLM 等无需鉴权的上游）。
     *
     * @param dto 临时连接信息
     * @return 测试结果说明
     */
    String testConnection(ModelProviderTestDTO dto);

    /**
     * 拉取上游模型 id 列表，供「导入模型」使用（Ollama 供应商走 {@code GET /api/tags}）。
     *
     * @param id 供应商 ID
     * @return 模型 id 列表
     */
    List<UpstreamModelInfo> listUpstreamModels(Long id);

    /**
     * 获取默认供应商。
     *
     * @return 默认供应商（无默认时取第一条）
     */
    ModelProviderVO getDefault();
}
