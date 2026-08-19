package cn.datafuturex.zhishu.biz.operationlog.service;

import cn.datafuturex.zhishu.common.PageResult;
import cn.datafuturex.zhishu.biz.operationlog.dto.OperationLogQueryDTO;
import cn.datafuturex.zhishu.biz.operationlog.entity.OperationLogEntity;
import cn.datafuturex.zhishu.biz.operationlog.vo.OperationLogVO;

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
     * 记录登录操作（默认渠道 LOCAL）
     */
    default void recordLogin(String username, String ipAddress, String userAgent,
                             boolean success, String errorMessage) {
        recordLogin(username, ipAddress, userAgent, success, errorMessage, "LOCAL");
    }

    /**
     * 记录登录操作
     *
     * @param channel 渠道编码：LOCAL / WANXIANG / SHUZHI_IOT 等，写入 requestParams
     */
    void recordLogin(String username, String ipAddress, String userAgent,
                     boolean success, String errorMessage, String channel);

    /**
     * 分页查询操作日志
     */
    PageResult<OperationLogVO> pageQuery(OperationLogQueryDTO query);

    /**
     * 查询操作日志详情
     */
    Optional<OperationLogVO> findById(Long id);
}
