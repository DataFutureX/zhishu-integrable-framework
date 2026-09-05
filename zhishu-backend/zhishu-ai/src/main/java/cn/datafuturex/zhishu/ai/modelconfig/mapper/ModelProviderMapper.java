package cn.datafuturex.zhishu.ai.modelconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.datafuturex.zhishu.ai.modelconfig.domain.ModelProviderEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模型设置 Mapper
 */
@Mapper
public interface ModelProviderMapper extends BaseMapper<ModelProviderEntity> {
}
