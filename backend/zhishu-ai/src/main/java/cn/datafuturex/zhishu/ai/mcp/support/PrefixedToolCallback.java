package cn.datafuturex.zhishu.ai.mcp.support;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class PrefixedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolDefinition definition;

    public PrefixedToolCallback(ToolCallback delegate, String exposedName, String descriptionPrefix) {
        this.delegate = delegate;
        ToolDefinition orig = delegate.getToolDefinition();
        String desc = orig.description() == null ? "" : orig.description();
        this.definition = ToolDefinition.builder()
                .name(exposedName)
                .description(descriptionPrefix + desc)
                .inputSchema(orig.inputSchema())
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        return toolContext == null ? delegate.call(toolInput) : delegate.call(toolInput, toolContext);
    }
}
