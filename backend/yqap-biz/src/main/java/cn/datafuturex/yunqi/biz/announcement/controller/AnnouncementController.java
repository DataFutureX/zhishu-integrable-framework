package cn.datafuturex.yunqi.biz.announcement.controller;

import cn.datafuturex.yunqi.common.PageResult;
import cn.datafuturex.yunqi.common.Result;
import cn.datafuturex.yunqi.biz.announcement.dto.AnnouncementCreateDTO;
import cn.datafuturex.yunqi.biz.announcement.dto.AnnouncementQueryDTO;
import cn.datafuturex.yunqi.biz.announcement.dto.AnnouncementUpdateDTO;
import cn.datafuturex.yunqi.biz.announcement.entity.AnnouncementEntity;
import cn.datafuturex.yunqi.biz.announcement.service.AnnouncementService;
import cn.datafuturex.yunqi.biz.announcement.service.AnnouncementSseService;
import cn.datafuturex.yunqi.biz.announcement.vo.AnnouncementVO;
import cn.datafuturex.yunqi.modules.constant.PermissionConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 系统公告控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
@Tag(name = "公告管理", description = "系统公告发布、查询与 SSE 实时推送")
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final AnnouncementSseService announcementSseService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "订阅公告 SSE 流",
            description = "前端通过 EventSource 连接，接收新发布公告事件。支持 Header Authorization 或 query 参数 token")
    public SseEmitter streamAnnouncements() {
        return announcementSseService.subscribe();
    }

    @GetMapping("/unread-count")
    @Operation(summary = "未读公告数量", description = "用于右上角铃铛角标")
    public Result<Long> unreadCount() {
        try {
            return Result.success(announcementService.countUnread());
        } catch (Exception e) {
            log.error("查询未读公告数量失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/recent")
    @Operation(summary = "最近公告列表", description = "铃铛下拉展示，默认 10 条")
    public Result<List<AnnouncementVO>> listRecent(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            return Result.success(announcementService.listRecentPublished(limit));
        } catch (Exception e) {
            log.error("查询最近公告失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/published/page")
    @Operation(summary = "分页查询已发布公告", description = "所有登录用户可查看")
    public Result<PageResult<AnnouncementVO>> pagePublished(AnnouncementQueryDTO query) {
        try {
            return Result.success(announcementService.pageQueryPublished(query));
        } catch (Exception e) {
            log.error("分页查询已发布公告失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_ANNOUNCEMENT_QUERY + "')")
    @Operation(summary = "管理员分页查询", description = "含草稿/已发布/已撤回，仅管理员")
    public Result<PageResult<AnnouncementVO>> pageForAdmin(AnnouncementQueryDTO query) {
        try {
            return Result.success(announcementService.pageQueryForAdmin(query));
        } catch (Exception e) {
            log.error("管理员分页查询公告失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询公告详情", description = "已发布公告所有登录用户可查看；草稿/撤回仅管理员")
    public Result<AnnouncementVO> findById(@PathVariable Long id) {
        try {
            return announcementService.findById(id)
                    .map(Result::success)
                    .orElse(Result.error("公告不存在"));
        } catch (Exception e) {
            log.error("查询公告详情失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_ANNOUNCEMENT_ADD + "')")
    @Operation(summary = "创建公告", description = "仅管理员，可选择立即发布")
    public Result<AnnouncementEntity> create(@Valid @RequestBody AnnouncementCreateDTO dto) {
        try {
            return Result.success(announcementService.create(dto));
        } catch (Exception e) {
            log.error("创建公告失败", e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_ANNOUNCEMENT_EDIT + "')")
    @Operation(summary = "更新公告", description = "仅管理员，仅草稿可编辑")
    public Result<AnnouncementEntity> update(@Valid @RequestBody AnnouncementUpdateDTO dto) {
        try {
            return Result.success(announcementService.update(dto));
        } catch (Exception e) {
            log.error("更新公告失败", e);
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_ANNOUNCEMENT_REMOVE + "')")
    @Operation(summary = "删除公告", description = "仅管理员，仅草稿可删除")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            announcementService.delete(id);
            return Result.success();
        } catch (Exception e) {
            log.error("删除公告失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_ANNOUNCEMENT_PUBLISH + "')")
    @Operation(summary = "发布公告", description = "仅管理员，发布后将 SSE 推送给在线用户")
    public Result<AnnouncementEntity> publish(@PathVariable Long id) {
        try {
            return Result.success(announcementService.publish(id));
        } catch (Exception e) {
            log.error("发布公告失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}/revoke")
    @PreAuthorize("hasAuthority('" + PermissionConstants.SYSTEM_ANNOUNCEMENT_PUBLISH + "')")
    @Operation(summary = "撤回公告", description = "仅管理员")
    public Result<AnnouncementEntity> revoke(@PathVariable Long id) {
        try {
            return Result.success(announcementService.revoke(id));
        } catch (Exception e) {
            log.error("撤回公告失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "标记公告已读")
    public Result<Void> markAsRead(@PathVariable Long id) {
        try {
            announcementService.markAsRead(id);
            return Result.success();
        } catch (Exception e) {
            log.error("标记公告已读失败: id={}", id, e);
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/read-all")
    @Operation(summary = "全部标记已读")
    public Result<Integer> markAllAsRead() {
        try {
            return Result.success(announcementService.markAllAsRead());
        } catch (Exception e) {
            log.error("全部标记公告已读失败", e);
            return Result.error(e.getMessage());
        }
    }
}
