package cn.datafuturex.zhishu.gateway.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Agent 引用查询：用于删除网关模型前的占用校验。
 */
@Mapper
public interface AgentModelRefMapper {

    /**
     * 统计引用了指定模型 id 的 Agent 数量。
     *
     * @param modelId 网关模型 id
     * @return 引用数量
     */
    @Select("SELECT COUNT(*) FROM ai_agent WHERE model = #{modelId}")
    int countByModel(@Param("modelId") String modelId);

    /**
     * 列出引用了指定模型 id 的 Agent 名称。
     *
     * @param modelId 网关模型 id
     * @return Agent 名称列表
     */
    @Select("SELECT name FROM ai_agent WHERE model = #{modelId} ORDER BY id LIMIT 20")
    List<String> listNamesByModel(@Param("modelId") String modelId);
}
