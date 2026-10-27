package cn.datafuturex.zhishu.mcp.provide.config;

import cn.datafuturex.zhishu.mcp.common.audit.McpCallLogService;
import cn.datafuturex.zhishu.mcp.common.audit.mapper.AiMcpCallLogMapper;
import cn.datafuturex.zhishu.mcp.common.config.McpModuleConfiguration;
import cn.datafuturex.zhishu.mcp.common.config.McpProperties;
import cn.datafuturex.zhishu.mcp.provide.catalog.McpProvidedTool;
import cn.datafuturex.zhishu.mcp.provide.catalog.McpProvidedToolCatalog;
import cn.datafuturex.zhishu.mcp.provide.guard.McpGuardedToolCallback;
import cn.datafuturex.zhishu.mcp.provide.guard.McpRateLimiter;
import cn.datafuturex.zhishu.mcp.provide.spi.McpToolContributor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * provide 平面装配的上下文级验证：目录收集、守卫包装与开关行为。
 * <p>
 * 特别覆盖「没有任何 {@link McpToolContributor} 贡献者」的场景：空集合依赖必须能让上下文正常启动，
 * 否则 AI 侧贡献关闭或模块被裁剪时会直接启动失败。
 */
class McpProvideAutoConfigurationTest {

    @Test
    @DisplayName("没有贡献者时也能启动，并输出守卫包装后的 Provider")
    void startsWithoutAnyContributor() {
        runner(StaticToolConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            ToolCallbackProvider provider = context.getBean(ToolCallbackProvider.class);
            ToolCallback[] tools = provider.getToolCallbacks();
            assertThat(tools).hasSize(1);
            assertThat(tools[0].getToolDefinition().name()).isEqualTo("demoTool");
            // 必须被守卫包装，否则白名单 / 限流 / 审计会被绕过
            assertThat(tools[0]).isInstanceOf(McpGuardedToolCallback.class);
        });
    }

    @Test
    @DisplayName("贡献 Tool 只有登记在能力字典内的才对外")
    void contributorToolsFilteredByCatalog() {
        runner(StaticToolConfig.class, ContributorConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            List<String> names = java.util.Arrays
                    .stream(context.getBean(ToolCallbackProvider.class).getToolCallbacks())
                    .map(callback -> callback.getToolDefinition().name())
                    .toList();
            assertThat(names).containsExactlyInAnyOrder("demoTool", "getTerminalOnlineOverview");
        });
    }

    @Test
    @DisplayName("provide 开关关闭时不注册 ToolCallbackProvider")
    void disabledProvidePlaneRegistersNoProvider() {
        runner(StaticToolConfig.class)
                .withPropertyValues("zhishu.mcp.provide.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ToolCallbackProvider.class);
                });
    }

    @Test
    @DisplayName("模块总开关关闭时同样不注册")
    void disabledModuleRegistersNoProvider() {
        runner(StaticToolConfig.class)
                .withPropertyValues("zhishu.mcp.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ToolCallbackProvider.class));
    }

    @Test
    @DisplayName("属性树绑定：嵌套默认值与显式覆盖")
    void bindsNestedProperties() {
        runner(StaticToolConfig.class)
                .withPropertyValues(
                        "zhishu.mcp.provide.jwt-default-rpm=30",
                        "zhishu.mcp.consume.max-upstreams-per-agent=2",
                        "zhishu.mcp.crypto-key=secret-key")
                .run(context -> {
                    McpProperties properties = context.getBean(McpProperties.class);
                    assertThat(properties.provideEnabled()).isTrue();
                    assertThat(properties.consumeEnabled()).isTrue();
                    assertThat(properties.getProvide().getJwtDefaultRpm()).isEqualTo(30);
                    assertThat(properties.getProvide().getDefaultRpm()).isEqualTo(60);
                    assertThat(properties.getConsume().getMaxUpstreamsPerAgent()).isEqualTo(2);
                    assertThat(properties.getCryptoKey()).isEqualTo("secret-key");
                });
    }

    @Test
    @DisplayName("内置 Tool 不得以裸 ToolCallback Bean 存在（否则守卫会被 Spring AI 直接聚合绕过）")
    void providedToolMustNotBeToolCallback() {
        assertThat(ToolCallback.class.isAssignableFrom(McpProvidedTool.class)).isFalse();
    }

    private static ApplicationContextRunner runner(Class<?>... userConfigurations) {
        Class<?>[] merged = new Class<?>[userConfigurations.length + 2];
        merged[0] = McpModuleConfiguration.class;
        System.arraycopy(userConfigurations, 0, merged, 1, userConfigurations.length);
        merged[merged.length - 1] = McpProvideAutoConfiguration.class;
        return new ApplicationContextRunner()
                .withBean(AiMcpCallLogMapper.class, () -> mock(AiMcpCallLogMapper.class))
                .withBean(McpCallLogService.class)
                .withBean(McpRateLimiter.class)
                .withBean(McpProvidedToolCatalog.class)
                .withUserConfiguration(merged);
    }

    @Configuration(proxyBeanMethods = false)
    static class StaticToolConfig {

        @Bean
        McpProvidedTool demoTool() {
            return () -> new CallbackTool("demoTool", "测试用内置 Tool");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ContributorConfig {

        @Bean
        McpToolContributor demoContributor() {
            return new McpToolContributor() {
                @Override
                public String source() {
                    return "demo";
                }

                @Override
                public List<ToolCallback> contribute() {
                    return List.of(new CallbackTool("getTerminalOnlineOverview", "在线概览"),
                            new CallbackTool("notRegisteredTool", "未登记的工具"));
                }
            };
        }
    }

    /** 测试用最小 Tool 实现 */
    static class CallbackTool implements ToolCallback {

        private final String name;
        private final String description;

        CallbackTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name(name)
                    .description(description)
                    .inputSchema("{\"type\":\"object\",\"properties\":{},\"required\":[]}")
                    .build();
        }

        @Override
        public String call(String toolInput) {
            return "{\"ok\":true}";
        }
    }
}
