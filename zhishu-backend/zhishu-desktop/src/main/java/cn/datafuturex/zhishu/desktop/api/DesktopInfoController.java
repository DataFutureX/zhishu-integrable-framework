package cn.datafuturex.zhishu.desktop.controller;

import cn.datafuturex.zhishu.desktop.config.DesktopApiProperties;
import cn.datafuturex.zhishu.desktop.dto.DesktopInfoDTO;
import cn.datafuturex.zhishu.desktop.dto.DesktopResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 桌面客户端基础接口。
 * <p>
 * 提供健康检查、服务信息和能力列表，供桌面端启动时探测服务端状态。
 * 通过 {@code zhishu.desktop.enabled=false} 可完全关闭本控制器。
 */
@Tag(name = "Desktop API", description = "桌面客户端接口层")
@ConditionalOnProperty(name = "zhishu.desktop.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@RestController
@RequestMapping("/desktop/v1")
public class DesktopInfoController {

    private final DesktopApiProperties properties;

    /**
     * 健康检查（无需鉴权，DesktopAuthFilter 已对此路径放行）。
     *
     * @return 当前时间与服务状态
     */
    @Operation(summary = "健康检查", description = "桌面端启动或心跳时调用，用于探测服务可用性；无需鉴权。")
    @GetMapping("/health")
    public DesktopResult<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("timestamp", LocalDateTime.now().toString());
        return DesktopResult.ok(body);
    }

    /**
     * 获取桌面端服务信息（需鉴权）。
     * <p>
     * 返回服务名称、API 版本及已注册的功能端点列表，供桌面端动态展示可用能力。
     *
     * @return 服务信息
     */
    @Operation(summary = "服务信息", description = "返回服务端能力清单与版本信息，桌面端据此展示可用功能模块。")
    @GetMapping("/info")
    public DesktopResult<DesktopInfoDTO> info() {
        DesktopInfoDTO dto = new DesktopInfoDTO(
                "知枢桌面 API",
                properties.getApiVersion(),
                List.of(
                        "健康检查 (GET /desktop/v1/health)",
                        "获取登录公钥 (GET /desktop/v1/auth/public-key)",
                        "登录 (POST /desktop/v1/auth/login)",
                        "退出登录 (POST /desktop/v1/auth/logout)",
                        "服务信息 (GET /desktop/v1/info)"
                ),
                Map.of(
                        "/desktop/v1/health",           "服务健康检查（无需鉴权）",
                        "/desktop/v1/auth/public-key", "获取 RSA 登录公钥（无需鉴权）",
                        "/desktop/v1/auth/login",       "桌面端登录，返回 JWT（无需 X-Desktop-Key，不验证验证码）",
                        "/desktop/v1/auth/logout",      "退出登录，吊销当前 JWT（需 Bearer Token）",
                        "/desktop/v1/info",              "桌面端服务能力清单"
                )
        );
        return DesktopResult.ok(dto);
    }
}
