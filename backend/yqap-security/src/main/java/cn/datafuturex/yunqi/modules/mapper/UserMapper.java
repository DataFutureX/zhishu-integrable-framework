package cn.datafuturex.yunqi.modules.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.datafuturex.yunqi.modules.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 Mapper 接口
 *
 * @author YunQi Application Platform Team
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
