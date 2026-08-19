package cn.datafuturex.zhishu.biz.operationlog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.datafuturex.zhishu.common.PageResult;
import cn.datafuturex.zhishu.biz.operationlog.dto.OperationLogQueryDTO;
import cn.datafuturex.zhishu.biz.operationlog.entity.OperationLogEntity;
import cn.datafuturex.zhishu.modules.entity.UserEntity;
import cn.datafuturex.zhishu.biz.operationlog.mapper.OperationLogMapper;
import cn.datafuturex.zhishu.modules.mapper.UserMapper;
import cn.datafuturex.zhishu.biz.operationlog.service.OperationLogService;
import cn.datafuturex.zhishu.biz.operationlog.vo.OperationLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 操作日志服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;
    private final UserMapper userMapper;

    private final Map<String, UserEntity> userCache = new ConcurrentHashMap<>();

    @Override
    @Async("operationLogExecutor")
    public void recordAsync(OperationLogEntity entity) {
        try {
            fillUserInfo(entity);
            if (entity.getCreateTime() == null) {
                entity.setCreateTime(LocalDateTime.now());
            }
            operationLogMapper.insert(entity);
        } catch (Exception e) {
            log.error("记录操作日志失败: method={}", entity.getMethod(), e);
        }
    }

    @Override
    public void recordLogin(String username, String ipAddress, String userAgent,
                            boolean success, String errorMessage, String channel) {
        String safeChannel = StringUtils.hasText(channel) ? channel : "LOCAL";
        String method = "LOCAL".equals(safeChannel)
                ? "POST /api/v1/auth/login"
                : "POST /api/v1/auth/sso/exchange";
        OperationLogEntity entity = new OperationLogEntity();
        entity.setUsername(username);
        entity.setModule("认证");
        entity.setOperation("LOGIN");
        entity.setMethod(method);
        entity.setRequestParams("{\"username\":\"" + maskUsername(username)
                + "\",\"channel\":\"" + escapeJson(safeChannel) + "\"}");
        entity.setResponseCode(success ? 200 : 500);
        entity.setIpAddress(ipAddress);
        entity.setUserAgent(truncate(userAgent, 500));
        entity.setDurationMs(0);
        entity.setStatus(success ? 1 : 0);
        entity.setErrorMessage(errorMessage);
        entity.setCreateTime(LocalDateTime.now());
        recordAsync(entity);
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public PageResult<OperationLogVO> pageQuery(OperationLogQueryDTO query) {
        LambdaQueryWrapper<OperationLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.username())) {
            wrapper.like(OperationLogEntity::getUsername, query.username());
        }
        if (StringUtils.hasText(query.module())) {
            wrapper.like(OperationLogEntity::getModule, query.module());
        }
        if (StringUtils.hasText(query.operation())) {
            wrapper.eq(OperationLogEntity::getOperation, query.operation());
        }
        if (query.status() != null) {
            wrapper.eq(OperationLogEntity::getStatus, query.status());
        }
        if (query.startTime() != null) {
            wrapper.ge(OperationLogEntity::getCreateTime, query.startTime());
        }
        if (query.endTime() != null) {
            wrapper.le(OperationLogEntity::getCreateTime, query.endTime());
        }
        wrapper.orderByDesc(OperationLogEntity::getCreateTime);

        Page<OperationLogEntity> page = operationLogMapper.selectPage(
                new Page<>(query.pageNum(), query.pageSize()), wrapper);
        Page<OperationLogVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return PageResult.of(voPage);
    }

    @Override
    public Optional<OperationLogVO> findById(Long id) {
        OperationLogEntity entity = operationLogMapper.selectById(id);
        return Optional.ofNullable(entity).map(this::toVO);
    }

    private void fillUserInfo(OperationLogEntity entity) {
        if (!StringUtils.hasText(entity.getUsername())) {
            return;
        }
        UserEntity user = userCache.computeIfAbsent(entity.getUsername(), this::loadUser);
        if (user != null) {
            entity.setUserId(user.getId());
            if (!StringUtils.hasText(entity.getRealName())) {
                entity.setRealName(user.getRealName());
            }
        }
    }

    private UserEntity loadUser(String username) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserEntity::getUsername, username);
        return userMapper.selectOne(wrapper);
    }

    private OperationLogVO toVO(OperationLogEntity entity) {
        return new OperationLogVO(
                entity.getId(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getRealName(),
                entity.getModule(),
                entity.getOperation(),
                entity.getMethod(),
                entity.getRequestParams(),
                entity.getResponseCode(),
                entity.getIpAddress(),
                entity.getUserAgent(),
                entity.getDurationMs(),
                entity.getStatus(),
                entity.getErrorMessage(),
                entity.getCreateTime()
        );
    }

    private static String maskUsername(String username) {
        if (!StringUtils.hasText(username) || username.length() <= 2) {
            return "***";
        }
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
