package cn.datafuturex.zhishu.ai.modelconfig.api;

import cn.datafuturex.zhishu.ai.modelconfig.dto.ModelProviderCreateDTO;
import cn.datafuturex.zhishu.ai.modelconfig.dto.ModelProviderUpdateDTO;
import cn.datafuturex.zhishu.ai.modelconfig.vo.ModelProviderVO;

import java.util.List;

/**
 * 模型设置端口 —— 多供应商管理
 */
public interface ModelProviderPort {

    /**
     * 获取所有模型设置列表
     *
     * @return 模型设置列表
     */
    List<ModelProviderVO> list();

    /**
     * 获取单个模型设置详情
     *
     * @param id 模型设置 ID
     * @return 模型设置详情
     */
    ModelProviderVO get(Long id);

    /**
     * 新建模型设置
     *
     * @param dto 创建参数
     * @return 新建后的模型设置
     */
    ModelProviderVO create(ModelProviderCreateDTO dto);

    /**
     * 更新模型设置
     *
     * @param id  模型设置 ID
     * @param dto 更新参数
     * @return 更新后的模型设置
     */
    ModelProviderVO update(Long id, ModelProviderUpdateDTO dto);

    /**
     * 删除模型设置（默认不可删）
     *
     * @param id 模型设置 ID
     */
    void delete(Long id);

    /**
     * 连通性测试：发送 ping 验证 baseUrl + apiKey 可用
     *
     * @param id 模型设置 ID
     * @return 测试结果消息
     */
    String testConnection(Long id);

    /**
     * 获取默认模型设置
     *
     * @return 默认模型设置 VO
     */
    ModelProviderVO getDefault();
}
