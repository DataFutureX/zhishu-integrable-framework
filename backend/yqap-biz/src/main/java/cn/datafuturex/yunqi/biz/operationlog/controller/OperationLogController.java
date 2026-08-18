package cn.datafuturex.yunqi.biz.operationlog.controller;

import cn.datafuturex.yunqi.common.PageResult;
import cn.datafuturex.yunqi.common.Result;
import cn.datafuturex.yunqi.biz.operationlog.dto.OperationLogQueryDTO;
import cn.datafuturex.yunqi.biz.operationlog.service.OperationLogService;
import cn.datafuturex.yunqi.biz.operationlog.vo.OperationLogVO;
import cn.datafuturex.yunqi.modules.constant.PermissionConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/operation-logs")
@RequiredArgsConstructor
@Tag(name = "操作日志", description = "系统操作日志查询")
public class OperationLogController {

    private final OperationLogService operationLogService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_OPERLOG_QUERY + "')")
    @Operation(summary = "分页查询操作日志")
    public Result<PageResult<OperationLogVO>> pageQuery(OperationLogQueryDTO query) {
        try {
            return Result.success(operationLogService.pageQuery(query));
        } catch (Exception e) {
            log.error("分页查询操作日志失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_OPERLOG_QUERY + "')")
    @Operation(summary = "查询操作日志详情")
    public Result<OperationLogVO> findById(@PathVariable Long id) {
        try {
            return operationLogService.findById(id)
                    .map(Result::success)
                    .orElse(Result.error("操作日志不存在"));
        } catch (Exception e) {
            log.error("查询操作日志失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }
}
