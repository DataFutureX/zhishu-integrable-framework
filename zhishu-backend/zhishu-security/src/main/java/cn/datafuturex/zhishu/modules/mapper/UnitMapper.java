package cn.datafuturex.zhishu.modules.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.datafuturex.zhishu.modules.entity.UnitEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 单位管理 Mapper
 */
@Mapper
public interface UnitMapper extends BaseMapper<UnitEntity> {
}
