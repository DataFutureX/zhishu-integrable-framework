package cn.datafuturex.zhishu.biz.operationlog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.datafuturex.zhishu.biz.operationlog.entity.OperationLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogEntity> {
}
