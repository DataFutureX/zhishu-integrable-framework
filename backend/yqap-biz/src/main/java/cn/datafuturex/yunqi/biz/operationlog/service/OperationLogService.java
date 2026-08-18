package cn.datafuturex.yunqi.biz.operationlog.service;

import cn.datafuturex.yunqi.common.PageResult;
import cn.datafuturex.yunqi.biz.operationlog.dto.OperationLogQueryDTO;
import cn.datafuturex.yunqi.biz.operationlog.entity.OperationLogEntity;
import cn.datafuturex.yunqi.biz.operationlog.vo.OperationLogVO;

import java.util.Optional;

/**
 * 操作日志服务
 */
public interface OperationLogService {

    /**
     * 异步记录操作日志
     */
    void recordAsync(OperationLogEntity entity);

    /**
     * 记录登录操作
     */
    void recordLogin(String username, String ipAddress, String userAgent, boolean success, String errorMessage);

    /**
     * 分页查询操作日志
     */
    PageResult<OperationLogVO> pageQuery(OperationLogQueryDTO query);

    /**
     * 查询操作日志详情
     */
    Optional<OperationLogVO> findById(Long id);
}
