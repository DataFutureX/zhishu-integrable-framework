package cn.datafuturex.zhishu.mcp.provide.catalog;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

/**
 * 系统介绍 Tool：对外暴露系统名称、版本、核心功能与架构说明。
 * <p>
 * 作为 {@link McpProvidedTool} 注册，由 {@link cn.datafuturex.zhishu.mcp.provide.config.McpProvideAutoConfiguration}
 * 统一加守卫（白名单 / RPM / 审计）后经 {@code ToolCallbackProvider} 暴露到 {@code /mcp}；
 * 本身不是 {@code ToolCallback} Bean，避免被 Spring AI 直接聚合成未加守卫的对外工具。
 * 该工具属于公开目录，无需 Client 额外授权即可调用。
 */
@Component
public class SystemIntroductionTool implements McpProvidedTool {

    private static final ToolCallback CALLBACK = new Callback();

    @Override
    public ToolCallback toolCallback() {
        return CALLBACK;
    }

    /** Tool 定义与执行体 */
    private static final class Callback implements ToolCallback {

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
                        "MCP 双平面（对外 Server + 接入他方上游）",
                        "统一模型网关（OpenAI 兼容 /v1）",
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
}
