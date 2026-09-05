package cn.datafuturex.zhishu.mcp.tool;

import cn.datafuturex.zhishu.mcp.config.McpServerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * 系统介绍 Tool：对外暴露系统名称、版本、核心功能与架构说明。
 * <p>
 * 实现 {@link ToolCallback} 接口，由 MCP Server 自动扫描并注册。
 */
@RequiredArgsConstructor
@Slf4j
public class SystemIntroductionTool implements ToolCallback {

    private final McpServerProperties properties;

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name("getSystemIntroduction")
                .description("获取知枢可集成框架（ZSIF）的系统介绍，包括系统名称、版本、核心功能与架构说明")
                .inputSchema("""
                        {
                          "type": "object",
                          "properties": {},
                          "required": []
                        }
                        """)
                .build();
    }

    @Override
    public String call(String toolInput) {
        log.info("[MCP Tool] getSystemIntroduction 被调用");
        return """
                {
                  "name": "知枢可集成框架",
                  "englishName": "ZhiShu Integrable Framework (ZSIF)",
                  "version": "1.0.0",
                  "description": "企业级数字化应用后端框架，提供统一技术架构与业务能力。",
                  "features": [
                    "RBAC 权限体系（用户/角色/菜单/单位管理）",
                    "AI 智能中心（Agent 工作流 / RAG 知识问答 / 知识图谱 / MCP 工具集成）",
                    "开放双平面（控制台管理面 + 开放 API 数据面）",
                    "MCP Server 对外服务（Streamable HTTP 协议）",
                    "公告推送与操作日志",
                    "系统监控与滑动验证码"
                  ],
                  "architecture": "单进程 Spring Boot 4 应用，Spring Modulith 模块化，模块间 SPI 解耦",
                  "techStack": [
                    "Java 21",
                    "Spring Boot 4.1",
                    "Spring Security 7",
                    "MyBatis-Plus",
                    "Spring AI 2.0",
                    "PostgreSQL + PgVector"
                  ]
                }
                """;
    }
}
