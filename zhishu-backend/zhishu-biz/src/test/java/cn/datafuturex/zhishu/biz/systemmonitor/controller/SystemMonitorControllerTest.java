package cn.datafuturex.zhishu.biz.systemmonitor.controller;

import cn.datafuturex.zhishu.biz.operationlog.service.OperationLogService;
import cn.datafuturex.zhishu.biz.systemmonitor.dto.ComponentHealthDTO;
import cn.datafuturex.zhishu.biz.systemmonitor.dto.SystemHealthDTO;
import cn.datafuturex.zhishu.biz.systemmonitor.service.SystemMonitorService;
import cn.datafuturex.zhishu.config.SecurityProperties;
import cn.datafuturex.zhishu.config.security.CommittedResponseSecurityExceptionFilter;
import cn.datafuturex.zhishu.config.security.JwtAuthenticationFilter;
import cn.datafuturex.zhishu.config.security.JwtUtil;
import cn.datafuturex.zhishu.config.security.RestAccessDeniedHandler;
import cn.datafuturex.zhishu.config.security.RestAuthenticationEntryPoint;
import cn.datafuturex.zhishu.config.security.SecurityConfig;
import cn.datafuturex.zhishu.modules.service.PermissionService;
import cn.datafuturex.zhishu.security.TokenBlacklistService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 系统监控控制器单元测试
 */
@WebMvcTest(SystemMonitorController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        SecurityProperties.class,
        CommittedResponseSecurityExceptionFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class SystemMonitorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemMonitorService systemMonitorService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private PermissionService permissionService;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private OperationLogService operationLogService;

    @Test
    @DisplayName("健康检查接口无需认证")
    void testHealthEndpointIsPublic() throws Exception {
        when(systemMonitorService.getSystemHealth()).thenReturn(sampleHealth());

        mockMvc.perform(get("/api/v1/system/health")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(ComponentHealthDTO.UP));
    }

    @Test
    @DisplayName("系统状态接口未认证应拒绝访问")
    void testStatusEndpointRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/system/status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    private SystemHealthDTO sampleHealth() {
        return new SystemHealthDTO(
                ComponentHealthDTO.UP,
                LocalDateTime.now(),
                List.of(new ComponentHealthDTO("database", ComponentHealthDTO.UP, "连接正常", 5L))
        );
    }
}
