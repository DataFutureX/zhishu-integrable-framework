package cn.datafuturex.zhishu.ai.agent.service;

import cn.datafuturex.zhishu.ai.agent.domain.dto.AgentPromptDraftDTO;
import cn.datafuturex.zhishu.ai.agent.domain.vo.AgentPromptDraftVO;
import cn.datafuturex.zhishu.ai.agent.domain.vo.ToolInfoVO;
import cn.datafuturex.zhishu.ai.agent.enums.AgentCapability;
import cn.datafuturex.zhishu.ai.agent.enums.WorkflowType;
import cn.datafuturex.zhishu.ai.agent.registry.ToolCapabilityRegistry;
import cn.datafuturex.zhishu.ai.modelconfig.api.ModelConfigPort;
import cn.datafuturex.zhishu.ai.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 按智能体表单字段生成系统提示词初稿：先拼模板，再尽量用模型润色。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentPromptDraftService {

    private static final String WRITER_SYSTEM = """
            你是万象监测平台的智能体人设编辑。根据用户给出的表单配置，撰写一份可直接粘贴的系统提示词初稿。

            硬性要求：
            1. 只输出提示词正文，不要开场白、解释、Markdown 代码围栏。
            2. 使用中文；口吻简洁专业，结构参考：角色定位 → 可用工具 → 使用规则 → 回答要求。
            3. 工具名必须使用配置中给出的英文方法名，禁止编造未列出的工具。
            4. 本平台 Tools 均为只读，禁止让智能体声称已创建/修改/关闭业务数据。
            5. 不要写入具体日历日期或「系统时间」块（运行时会自动注入）。
            6. 相对时间、日报/月报/年报应写「按运行时注入的系统时间换算，禁止臆造日期」。
            7. 若提供了旧稿，在保留有用约束的前提下按新表单改写，不要原文照抄无关段落。
            """;

    private final ToolCapabilityRegistry toolCapabilityRegistry;
    private final ChatModel chatModel;
    private final ModelConfigPort modelConfigPort;

    public AgentPromptDraftVO generate(AgentPromptDraftDTO dto) {
        if (dto == null) {
            throw new BusinessException("请先填写智能体表单");
        }
        boolean hasIdentity = StringUtils.hasText(dto.name()) || StringUtils.hasText(dto.description());
        boolean hasCaps = dto.capabilities() != null && !dto.capabilities().isEmpty();
        if (!hasIdentity && !hasCaps) {
            throw new BusinessException("请先填写名称、简介或勾选能力");
        }
        String template = buildTemplate(dto);
        String polished = polishWithModel(dto, template);
        if (StringUtils.hasText(polished)) {
            return new AgentPromptDraftVO(polished, "LLM");
        }
        return new AgentPromptDraftVO(template, "TEMPLATE");
    }

    private String polishWithModel(AgentPromptDraftDTO dto, String template) {
        try {
            String model = modelConfigPort.currentChatModel();
            if (!StringUtils.hasText(model)) {
                return null;
            }
            var options = OpenAiChatOptions.builder()
                    .model(model.trim())
                    .temperature(0.35)
                    .maxTokens(1800)
                    .build();
            ChatClient client = ChatClient.builder(chatModel)
                    .defaultSystem(WRITER_SYSTEM)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(options.getModel())
                            .maxTokens(options.getMaxTokens())
                            .temperature(options.getTemperature()))
                    .build();
            String content = client.prompt().user(buildWriterUserMessage(dto, template)).call().content();
            return sanitizeModelOutput(content);
        } catch (RuntimeException e) {
            log.warn("系统提示词初稿模型生成失败，回退模板: {}", e.getMessage());
            return null;
        }
    }

    private String buildWriterUserMessage(AgentPromptDraftDTO dto, String template) {
        StringBuilder sb = new StringBuilder();
        sb.append("表单配置：\n");
        sb.append("- 名称：").append(blankToDash(dto.name())).append('\n');
        sb.append("- 简介：").append(blankToDash(dto.description())).append('\n');
        sb.append("- 工作流：").append(workflowLabel(dto.workflowType())).append('\n');
        sb.append("- 多轮记忆：").append(Boolean.TRUE.equals(dto.enableMemory()) ? "开启" : "关闭").append('\n');
        List<String> caps = dto.capabilities() == null ? List.of() : dto.capabilities();
        sb.append("- 能力：").append(caps.isEmpty() ? "（未勾选）" : String.join("、", capLabels(caps))).append('\n');
        if (dto.mcpUpstreamNames() != null && !dto.mcpUpstreamNames().isEmpty()) {
            sb.append("- 上游 MCP：").append(String.join("、", dto.mcpUpstreamNames())).append('\n');
        }
        if (dto.documentNames() != null && !dto.documentNames().isEmpty()) {
            sb.append("- 绑定文档：").append(String.join("、", dto.documentNames())).append('\n');
        } else if (caps.stream().anyMatch(c -> AgentCapability.RAG.name().equalsIgnoreCase(c))) {
            sb.append("- 知识库：已勾选 RAG，文档未限定（默认全部）\n");
        }
        if (StringUtils.hasText(dto.existingPrompt())) {
            sb.append("\n当前旧稿：\n").append(dto.existingPrompt().trim()).append("\n");
        }
        sb.append("\n请在下列骨架基础上改写成完整初稿：\n").append(template);
        return sb.toString();
    }

    private String buildTemplate(AgentPromptDraftDTO dto) {
        String name = StringUtils.hasText(dto.name()) ? dto.name().trim() : "智能助手";
        StringBuilder sb = new StringBuilder();
        sb.append("你是「").append(name).append("」");
        if (StringUtils.hasText(dto.description())) {
            sb.append("，").append(dto.description().trim().replaceAll("[。；;]+$", ""));
        } else {
            sb.append("，服务万象监测平台");
        }
        sb.append("。\n\n");

        List<String> capCodes = dto.capabilities() == null ? List.of() : dto.capabilities();
        List<AgentCapability> caps = new ArrayList<>();
        for (String code : capCodes) {
            AgentCapability.fromCode(code).ifPresent(caps::add);
        }

        List<String> toolLines = new ArrayList<>();
        int idx = 1;
        for (AgentCapability cap : caps) {
            if (!cap.isToolBased()) {
                continue;
            }
            List<ToolInfoVO> tools = toolCapabilityRegistry.describeTools(cap.getToolNames());
            String names = tools.isEmpty()
                    ? String.join("、", cap.getToolNames())
                    : joinToolNames(tools);
            toolLines.add(idx + ". " + cap.getLabel() + "：" + names
                    + (StringUtils.hasText(cap.getDescription()) ? "（" + cap.getDescription() + "）" : ""));
            idx++;
        }
        if (dto.mcpUpstreamNames() != null && !dto.mcpUpstreamNames().isEmpty()) {
            toolLines.add(idx + ". 外部 MCP：" + String.join("、", dto.mcpUpstreamNames())
                    + "（按工具实际名称调用，禁止编造未暴露的方法）");
        }
        if (!toolLines.isEmpty()) {
            sb.append("你可以使用工具：\n");
            for (String line : toolLines) {
                sb.append(line).append('\n');
            }
            sb.append('\n');
        }

        sb.append("使用规则：\n");
        for (String rule : usageRules(caps, dto)) {
            sb.append("- ").append(rule).append('\n');
        }
        sb.append("- 用户未指定日期时：日报=当日、月报=当月、年报=当年（以运行时注入的系统时间为准，禁止臆造日期）\n");
        sb.append("- 相对时间（今日、昨天、上周、过去一个月）请按系统注入时间换算为具体时间范围\n");
        sb.append("- 优先用工具取真实数据，禁止编造站点、数值、告警或关系\n");
        sb.append("- 本智能体只读，不要声称已创建任务、已打卡、已关闭异常或已修改配置\n");
        sb.append("- 不确定或工具失败时明确说明原因，不要用训练知识填空\n");

        String workflowHint = workflowAnswerHint(dto.workflowType());
        sb.append('\n').append("回答要求：\n");
        if (StringUtils.hasText(workflowHint)) {
            sb.append("- ").append(workflowHint).append('\n');
        }
        if (Boolean.TRUE.equals(dto.enableMemory())) {
            sb.append("- 可结合多轮上下文理解指代，但事实仍以本次工具结果为准\n");
        }
        sb.append("- 用中文简洁专业地总结；涉及列表/对比时用 Markdown 表格\n");
        return sb.toString().trim();
    }

    private List<String> usageRules(List<AgentCapability> caps, AgentPromptDraftDTO dto) {
        Set<String> rules = new LinkedHashSet<>();
        for (AgentCapability cap : caps) {
            switch (cap) {
                case MCP_TOOLS -> rules.add("监测/巡检/告警等业务数据请调用已绑定的上游 MCP 工具，禁止编造");
                case KNOWLEDGE_GRAPH -> rules.add("图谱问题走知识图谱引擎；无图谱数据时如实说明");
                case MEMORY, WORKFLOW_GRAPH -> {
                }
                case RAG -> {
                    if (dto.documentNames() != null && !dto.documentNames().isEmpty()) {
                        rules.add("结合知识库文档回答：" + String.join("、", dto.documentNames()) + "；未检索到则说明");
                    } else {
                        rules.add("结合知识库检索结果回答；未命中时明确说明，不要编造条文");
                    }
                }
            }
        }
        return new ArrayList<>(rules);
    }

    private static String workflowAnswerHint(String workflowType) {
        return WorkflowType.fromCode(workflowType)
                .map(type -> switch (type) {
                    case SEQUENTIAL -> "先澄清范围与对象，再调工具，最后输出结构化结论";
                    case ROUTING -> "先判断问题类型再走对应能力，不要混用无关工具";
                    case GRAPH -> "按编排节点完成任务，逐步给出结论";
                    case REACT -> "按需推理并调用工具，完成后再总结";
                })
                .orElse("按需调用工具后给出结论");
    }

    private static String workflowLabel(String workflowType) {
        return WorkflowType.fromCode(workflowType)
                .map(t -> t.name() + "（" + t.getLabel() + "）")
                .orElse(StringUtils.hasText(workflowType) ? workflowType : "REACT");
    }

    private List<String> capLabels(List<String> codes) {
        List<String> labels = new ArrayList<>();
        for (String code : codes) {
            labels.add(AgentCapability.fromCode(code)
                    .map(c -> c.getLabel() + "/" + c.name())
                    .orElse(code));
        }
        return labels;
    }

    private static String joinToolNames(List<ToolInfoVO> tools) {
        List<String> parts = new ArrayList<>();
        for (ToolInfoVO tool : tools) {
            if (StringUtils.hasText(tool.description())) {
                parts.add(tool.name() + "（" + tool.description() + "）");
            } else {
                parts.add(tool.name());
            }
        }
        return String.join("、", parts);
    }

    static String sanitizeModelOutput(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int nl = text.indexOf('\n');
            if (nl > 0) {
                text = text.substring(nl + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
        }
        text = text.replaceFirst("^(?:好的|当然|以下是|这是)[^\\n]{0,40}[:：]\\s*", "");
        text = text.trim();
        if (text.length() < 40 || !text.contains("你")) {
            return null;
        }
        if (text.length() > 6000) {
            text = text.substring(0, 6000).trim();
        }
        return text;
    }

    private static String blankToDash(String value) {
        return StringUtils.hasText(value) ? value.trim() : "（未填）";
    }
}
